package com.ced2711.lifetracker.data.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.data.local.RestoreCommitEntity
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.data.settings.SettingsRepository
import com.ced2711.lifetracker.data.vault.VaultKeyManager
import com.ced2711.lifetracker.data.vault.VaultRepository
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TodoQuickAddField
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.WeekStart
import java.io.DataOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRecoveryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: TaskLedgerDatabase
    private lateinit var root: File
    private lateinit var attachmentDirectory: File
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, TaskLedgerDatabase::class.java).build()
        root = File(context.cacheDir, "backup-recovery-${System.nanoTime()}").apply { mkdirs() }
        attachmentDirectory = File(root, "attachments").apply { mkdir() }
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settingsRepository = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = dataStoreScope) {
                File(root, "settings.preferences_pb")
            },
        )
        repository = BackupRepository(
            backupDao = database.backupDao(),
            settingsRepository = settingsRepository,
            vaultRepository = VaultRepository(database.vaultDao(), VaultKeyManager(context)),
            attachmentDirectory = attachmentDirectory,
            workDirectory = File(root, "work"),
        )
    }

    @After
    fun tearDown() {
        database.close()
        dataStoreScope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun committedTokenWinsEvenAfterBusinessRowsChange() = runBlocking {
        val dao = database.backupDao()
        val snapshot = minimalSnapshot()
        val stage = AttachmentRestoreStage.create(attachmentDirectory)
        try {
            dao.replaceSnapshot(snapshot, null, stage.attachments, RESTORE_TOKEN)
            stage.commit()
        } finally {
            stage.close()
        }
        dao.insertTodos(
            listOf(
                TodoEntity(
                    id = 21,
                    title = "Created after commit",
                    description = "Must survive recovery",
                    priority = TodoPriority.NONE,
                ),
            ),
        )
        val newSettings = AppSettings(notificationsEnabled = true)
        writeJournal(RESTORE_TOKEN, AppSettings(), newSettings)

        val result = repository.recoverPendingRestore()

        assertEquals(true, result?.restoredDatabaseWasCommitted)
        assertEquals(listOf(20L, 21L), dao.backupTodos().map { it.id })
        assertEquals(newSettings, settingsRepository.snapshot())
        assertNull(dao.restoreCommitToken())
        assertFalse(journalFile().exists())
    }

    @Test
    fun missingMatchingTokenRollsBackSettingsAndDeletesOnlyUnusedStage() = runBlocking {
        val dao = database.backupDao()
        dao.insertCategories(listOf(CategoryEntity(99, "Existing")))
        dao.writeRestoreCommit(RestoreCommitEntity(restoreToken = DIFFERENT_TOKEN))
        val stage = File(attachmentDirectory, "taskledger-restore-uncommitted").apply { mkdir() }
        File(stage, "unused.bin").writeText("unused")
        val oldSettings = AppSettings()
        settingsRepository.replace(AppSettings(notificationsEnabled = true))
        writeJournal(RESTORE_TOKEN, oldSettings, AppSettings(notificationsEnabled = true), stage)

        val result = repository.recoverPendingRestore()

        assertEquals(false, result?.restoredDatabaseWasCommitted)
        assertEquals(listOf(99L), dao.backupCategories().map { it.id })
        assertEquals(oldSettings, settingsRepository.snapshot())
        assertFalse(stage.exists())
        assertEquals(DIFFERENT_TOKEN, dao.restoreCommitToken())
        assertNull(repository.recoverPendingRestore())
        assertNull(dao.restoreCommitToken())
    }

    @Test
    fun unreadableJournalIsQuarantinedWithoutMutatingLiveStateAndStartupCleansWorkArtifacts() = runBlocking {
        val dao = database.backupDao()
        dao.insertCategories(listOf(CategoryEntity(99, "Live")))
        dao.writeRestoreCommit(RestoreCommitEntity(restoreToken = DIFFERENT_TOKEN))
        val liveSettings = AppSettings(notificationsEnabled = true)
        settingsRepository.replace(liveSettings)
        val uncertainStage = File(attachmentDirectory, "taskledger-restore-uncertain").apply { mkdir() }
        val uncertainFile = File(uncertainStage, "possibly-live.bin").apply { writeText("keep") }
        journalFile().writeBytes(byteArrayOf(0x54, 0x4c, 0x52))
        val work = File(root, "work").apply { mkdir() }
        File(work, "taskledger-encrypted-abandoned.backup").writeText("ciphertext")
        File(work, "taskledger-authenticated-abandoned.snapshot").writeText("legacy plaintext")

        val result = repository.recoverPendingRestore()

        assertEquals(false, result?.restoredDatabaseWasCommitted)
        assertEquals(listOf(99L), dao.backupCategories().map { it.id })
        assertEquals(liveSettings, settingsRepository.snapshot())
        assertEquals(DIFFERENT_TOKEN, dao.restoreCommitToken())
        assertTrue(uncertainFile.isFile)
        assertFalse(journalFile().exists())
        assertTrue(File(root, "taskledger-restore.journal.quarantined").isFile)
        assertTrue(work.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun legacyPreparedJournalUsesItsFingerprintAndCompletesCommittedRecovery() = runBlocking {
        val dao = database.backupDao()
        dao.insertCategories(listOf(CategoryEntity(99, "Legacy committed")))
        val oldSettings = AppSettings()
        val newSettings = AppSettings(notificationsEnabled = true)
        val stage = File(attachmentDirectory, "taskledger-restore-legacy").apply { mkdir() }
        writeLegacyJournal(
            phase = LegacyRestoreJournalPhase.PREPARED,
            fingerprint = fullDatabaseFingerprint(dao.backupState()),
            oldSettings = oldSettings,
            newSettings = newSettings,
            stage = stage,
        )

        val result = repository.recoverPendingRestore()

        assertEquals(true, result?.restoredDatabaseWasCommitted)
        assertEquals(listOf(99L), dao.backupCategories().map { it.id })
        assertEquals(newSettings, settingsRepository.snapshot())
        assertFalse(journalFile().exists())
        assertNull(dao.restoreCommitToken())
    }

    private fun writeJournal(
        token: String,
        oldSettings: AppSettings,
        newSettings: AppSettings,
        stage: File = File(attachmentDirectory, "taskledger-restore-empty"),
    ) {
        BackupRestoreJournal(journalFile(), root, attachmentDirectory).write(
            RestoreJournalRecord(
                restoreToken = token,
                oldSettings = oldSettings,
                newSettings = newSettings,
                stageRoot = stage.absolutePath,
                oldAttachmentPaths = emptyList(),
            ),
        )
        assertTrue(journalFile().isFile)
    }

    private fun writeLegacyJournal(
        phase: LegacyRestoreJournalPhase,
        fingerprint: String,
        oldSettings: AppSettings,
        newSettings: AppSettings,
        stage: File,
    ) {
        DataOutputStream(journalFile().outputStream().buffered()).use { output ->
            output.writeInt(0x544C524A)
            output.writeInt(1)
            output.writeText(phase.name)
            output.writeText(fingerprint)
            output.writeSettings(oldSettings)
            output.writeSettings(newSettings)
            output.writeText(stage.absolutePath)
            output.writeInt(0)
        }
    }

    private fun DataOutputStream.writeText(value: String) {
        val bytes = value.encodeToByteArray()
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.writeSettings(value: AppSettings) {
        writeText(value.themeMode.name)
        writeText(value.weekStart.name)
        writeText(value.timeFormat.name)
        writeText(value.dateFormat.name)
        writeBoolean(value.notificationsEnabled)
        writeInt(value.defaultAllDayReminderMinute)
        writeInt(value.defaultReminderOffsetsMinutes.size)
        value.defaultReminderOffsetsMinutes.sorted().forEach(::writeLong)
        val quickFields = TodoQuickAddField.entries.filter(value.todoQuickAddFields::contains)
        writeInt(quickFields.size)
        quickFields.forEach { writeText(it.name) }
        writeText(value.lastDestination.name)
    }

    private fun journalFile() = File(root, "taskledger-restore.journal")

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
        categories = emptyList(),
        todoSeries = emptyList(),
        todoSeriesSubtasks = emptyList(),
        todoOccurrenceExceptions = emptyList(),
        todos = listOf(
            TodoEntity(
                id = 20,
                title = "Restored",
                description = "Committed restore",
                priority = TodoPriority.NONE,
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
        const val DIFFERENT_TOKEN = "223e4567-e89b-12d3-a456-426614174000"
    }
}
