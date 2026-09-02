package com.ced2711.lifetracker.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.data.local.RestoreCommitEntity
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.vault.VaultSession
import com.ced2711.lifetracker.data.vault.VaultEntryCipher
import com.ced2711.lifetracker.data.vault.VaultCorruptEntryException
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.WeekStart
import com.ced2711.lifetracker.domain.model.VaultEntry
import java.io.File
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupDaoInstrumentedTest {
    private lateinit var database: TaskLedgerDatabase
    private lateinit var context: Context

    @Before fun createDatabase() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, TaskLedgerDatabase::class.java).build()
    }

    @After fun closeDatabase() = database.close()

    @Test fun replacePreservesExplicitIdsAndRestoreTokenLifecycle() = runBlocking {
        val dao = database.backupDao()
        dao.insertCategories(listOf(CategoryEntity(99, "old")))
        dao.writeRestoreCommit(RestoreCommitEntity(restoreToken = OLD_RESTORE_TOKEN))
        val snapshot = minimalSnapshot()
        val stage = AttachmentRestoreStage.create(File(context.cacheDir, "backup-dao-test"))
        try {
            VaultSession(ByteArray(32) { it.toByte() }).use { session ->
                dao.replaceSnapshot(snapshot, session, stage.attachments, RESTORE_TOKEN)
            }
            stage.commit()
        } finally { stage.close() }

        assertEquals(listOf(1L), dao.backupCategories().map { it.id })
        assertEquals(listOf(20L), dao.backupTodos().map { it.id })
        assertEquals(RESTORE_TOKEN, dao.restoreCommitToken())
        assertEquals(0, dao.clearRestoreCommitToken("223e4567-e89b-12d3-a456-426614174000"))
        assertEquals(RESTORE_TOKEN, dao.restoreCommitToken())
        assertEquals(1, dao.clearRestoreCommitToken(RESTORE_TOKEN))
        assertEquals(null, dao.restoreCommitToken())
        val state = dao.backupState()
        assertEquals(snapshot.categories, state.categories)
        assertEquals(snapshot.todos, state.todos)
    }

    @Test fun roomRollsBackAllDeletesWhenAnInsertFails() = runBlocking {
        val dao = database.backupDao()
        dao.insertCategories(listOf(CategoryEntity(99, "old")))
        dao.writeRestoreCommit(RestoreCommitEntity(restoreToken = OLD_RESTORE_TOKEN))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_restore BEFORE INSERT ON todos BEGIN SELECT RAISE(ABORT, 'test'); END",
        )
        val snapshot = minimalSnapshot()
        val stage = AttachmentRestoreStage.create(File(context.cacheDir, "backup-rollback-test"))
        try {
            assertThrows(Exception::class.java) {
                runBlocking {
                    VaultSession(ByteArray(32)).use { session ->
                        dao.replaceSnapshot(snapshot, session, stage.attachments, RESTORE_TOKEN)
                    }
                }
            }
        } finally { stage.close() }
        assertEquals(listOf(99L), dao.backupCategories().map { it.id })
        assertEquals(emptyList<Long>(), dao.backupTodos().map { it.id })
        assertEquals(OLD_RESTORE_TOKEN, dao.restoreCommitToken())
    }

    @Test fun missingVaultSessionFailsBeforeAnyLiveDelete() = runBlocking {
        val dao = database.backupDao()
        dao.insertCategories(listOf(CategoryEntity(99, "old")))
        val snapshot = minimalSnapshot().copy(vaultEntries = listOf(vaultEntry()))
        val stage = AttachmentRestoreStage.create(File(context.cacheDir, "backup-vault-session-test"))
        try {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { dao.replaceSnapshot(snapshot, null, stage.attachments, RESTORE_TOKEN) }
            }
        } finally { stage.close() }
        assertEquals(listOf(99L), dao.backupCategories().map { it.id })
        assertEquals(emptyList<Long>(), dao.backupTodos().map { it.id })
    }

    @Test fun vaultEntriesAreReencryptedForTheTargetSession() = runBlocking {
        val snapshot = minimalSnapshot().copy(vaultEntries = listOf(vaultEntry()))
        val stage = AttachmentRestoreStage.create(File(context.cacheDir, "backup-vault-reencrypt-test"))
        val sourceSession = VaultSession(ByteArray(32) { 7 })
        val targetSession = VaultSession(ByteArray(32) { 9 })
        try {
            dao().replaceSnapshot(snapshot, targetSession, stage.attachments, RESTORE_TOKEN)
            stage.commit()
            val row = database.vaultDao().getEncrypted().single()
            assertEquals(vaultEntry(), VaultEntryCipher.decrypt(row, targetSession))
            assertThrows(VaultCorruptEntryException::class.java) { VaultEntryCipher.decrypt(row, sourceSession) }
            Unit
        } finally {
            sourceSession.close(); targetSession.close(); stage.close()
        }
    }

    @Test fun attachmentContentAndMetadataRoundTripThroughRestoreAndExport() = runBlocking {
        val content = "receipt-content".encodeToByteArray()
        val digest = sha256(content)
        val attachment = BackupAttachment(
            id = 70, ownerType = AttachmentOwnerType.TODO, ownerId = 20,
            archivePath = attachmentArchivePath(70, digest), originalName = "receipt.txt",
            mimeType = "text/plain", sizeBytes = content.size.toLong(), sha256 = digest,
            createdAt = 10, pendingDeleteAt = null,
        )
        val snapshot = minimalSnapshot().copy(attachments = listOf(attachment))
        val stage = AttachmentRestoreStage.create(File(context.cacheDir, "backup-attachment-test"))
        stage.write(attachment, ByteArrayInputStream(content))
        val persistedRoot = stage.attachments.root
        try {
            dao().replaceSnapshot(snapshot, null, stage.attachments, RESTORE_TOKEN)
            stage.commit()
            val actual = dao().backupAttachments().single()
            assertEquals(attachment.toEntity(actual.privatePath), actual)
            assertArrayEquals(content, File(actual.privatePath).readBytes())
        } finally {
            stage.close()
            persistedRoot.deleteRecursively()
        }
    }

    private fun dao() = database.backupDao()

    private fun vaultEntry() = VaultEntry(
        "123e4567-e89b-12d3-a456-426614174000", "label", "account", "password",
        "website", "notes", 10, 11,
    )

    private fun minimalSnapshot() = BackupSnapshot(
        createdAt = 1,
        settings = BackupSettings(
            themeMode = ThemeMode.DARK,
            weekStart = WeekStart.SUNDAY,
            timeFormat = TimeFormatOption.HOUR_12,
            dateFormat = DateFormatOption.MONTH_DAY_YEAR,
            notificationsEnabled = false,
            defaultAllDayReminderMinute = 0,
            defaultReminderOffsetsMinutes = emptySet(),
            todoQuickAddFields = emptySet(),
            lastDestination = TopLevelDestination.TODO,
        ),
        categories = listOf(CategoryEntity(1, "new")),
        todoSeries = emptyList(),
        todoSeriesSubtasks = emptyList(),
        todoOccurrenceExceptions = emptyList(),
        todos = listOf(
            TodoEntity(
                id = 20, title = "todo", description = "description", categoryId = 1,
                deadlineEpochDay = null, deadlineMinute = null, priority = TodoPriority.NONE,
            ),
        ),
        subtasks = emptyList(),
        todoReminders = emptyList(),
        ledgerSeries = emptyList(),
        ledgerOccurrenceExceptions = emptyList(),
        ledgerEntries = emptyList(),
        attachments = emptyList(),
        vaultEntries = emptyList(),
    )

    private companion object {
        const val RESTORE_TOKEN = "123e4567-e89b-12d3-a456-426614174000"
        const val OLD_RESTORE_TOKEN = "223e4567-e89b-12d3-a456-426614174000"
    }
}
