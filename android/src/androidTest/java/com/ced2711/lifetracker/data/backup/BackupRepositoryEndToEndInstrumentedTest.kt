package com.ced2711.lifetracker.data.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ced2711.lifetracker.data.attachment.AttachmentStore
import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.data.local.BackupDatabaseState
import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.LedgerOccurrenceExceptionEntity
import com.ced2711.lifetracker.data.local.LedgerSeriesEntity
import com.ced2711.lifetracker.data.local.SubtaskEntity
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.local.TodoOccurrenceExceptionEntity
import com.ced2711.lifetracker.data.local.TodoReminderEntity
import com.ced2711.lifetracker.data.local.TodoSeriesEntity
import com.ced2711.lifetracker.data.local.TodoSeriesSubtaskEntity
import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.data.settings.SettingsRepository
import com.ced2711.lifetracker.data.vault.VaultKeyManager
import com.ced2711.lifetracker.data.vault.VaultRepository
import com.ced2711.lifetracker.data.vault.VaultSession
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.TodoQuickAddField
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.VaultEntry
import com.ced2711.lifetracker.domain.model.VaultEntryDraft
import com.ced2711.lifetracker.domain.model.WeekStart
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
class BackupRepositoryEndToEndInstrumentedTest {
    private lateinit var database: TaskLedgerDatabase
    private lateinit var root: File
    private lateinit var attachmentDirectory: File
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var vaultRepository: VaultRepository
    private lateinit var attachmentStore: AttachmentStore
    private lateinit var repository: BackupRepository
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var vaultSession: VaultSession

    @Before
    fun setUp() {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        root = File(applicationContext.cacheDir, "backup-e2e-${System.nanoTime()}").apply {
            check(mkdirs())
        }
        val isolatedContext = IsolatedFilesContext(applicationContext, root)
        attachmentDirectory = File(root, AttachmentStore.ATTACHMENT_DIRECTORY).apply {
            check(mkdir())
        }
        database = Room.inMemoryDatabaseBuilder(
            applicationContext,
            TaskLedgerDatabase::class.java,
        ).build()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settingsRepository = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = dataStoreScope) {
                File(root, "settings.preferences_pb")
            },
        )
        vaultRepository = VaultRepository(
            database.vaultDao(),
            VaultKeyManager(applicationContext),
        )
        attachmentStore = AttachmentStore(isolatedContext, database.dao())
        repository = BackupRepository(
            backupDao = database.backupDao(),
            settingsRepository = settingsRepository,
            vaultRepository = vaultRepository,
            attachmentDirectory = attachmentDirectory,
            workDirectory = File(root, "work"),
            now = { BACKUP_CREATED_AT },
        )
        val keyBytes = ByteArray(32) { index -> (index + 1).toByte() }
        vaultSession = VaultSession(keyBytes)
        keyBytes.fill(0)
    }

    @After
    fun tearDown() {
        vaultSession.close()
        database.close()
        dataStoreScope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun exportPrepareAndRestoreRoundTripsEveryPersistentLayer() = runBlocking {
        val source = seedSourceState()
        val backupBytes = exportBackup()

        seedDifferentLiveState()
        val beforePrepare = captureExactLiveState()

        val prepared = repository.prepareRestore(
            ByteArrayInputStream(backupBytes),
            BACKUP_PASSWORD.toCharArray(),
        )
        assertEquals(1, prepared.preview.todoCount)
        assertEquals(1, prepared.preview.ledgerCount)
        assertEquals(1, prepared.preview.attachmentCount)
        assertEquals(1, prepared.preview.vaultCount)
        assertTrue(prepared.requiresVaultAuthentication)
        assertExactLiveState(beforePrepare)

        val result = repository.restore(prepared, vaultSession)

        assertTrue(result.warnings.isEmpty())
        assertEquals(source.settings, settingsRepository.snapshot())
        assertEquals(source.categories, database.backupDao().backupCategories())
        assertEquals(source.todos, database.backupDao().backupTodos())
        assertEquals(source.ledgerEntries, database.backupDao().backupLedgerEntries())
        assertEquals(source.vaultEntries, vaultRepository.loadEntries(vaultSession))
        assertRestoredAttachment(source.attachment)
        assertNull(database.backupDao().restoreCommitToken())
        assertFalse(File(root, "taskledger-restore.journal").exists())
    }

    @Test
    fun wrongPasswordAndTamperedContainerLeaveEveryPersistentLayerUnchanged() = runBlocking {
        seedSourceState()
        val backupBytes = exportBackup()
        val unchanged = captureExactLiveState()
        val unchangedAttachmentTree = captureAttachmentTree()

        val wrongPasswordFailure = runCatching {
            repository.prepareRestore(
                ByteArrayInputStream(backupBytes),
                WRONG_PASSWORD.toCharArray(),
            )
        }.exceptionOrNull()
        assertTrue(wrongPasswordFailure is BackupAuthenticationException)
        assertExactLiveState(unchanged)
        assertEquals(unchangedAttachmentTree, captureAttachmentTree())

        val tampered = backupBytes.clone().also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        }
        val tamperedFailure = runCatching {
            repository.prepareRestore(
                ByteArrayInputStream(tampered),
                BACKUP_PASSWORD.toCharArray(),
            )
        }.exceptionOrNull()
        assertTrue(tamperedFailure is BackupAuthenticationException)
        assertExactLiveState(unchanged)
        assertEquals(unchangedAttachmentTree, captureAttachmentTree())
        assertNull(database.backupDao().restoreCommitToken())
        assertFalse(File(root, "taskledger-restore.journal").exists())
    }

    private suspend fun seedSourceState(): LogicalState {
        clearLiveState()
        val category = CategoryEntity(
            id = SOURCE_CATEGORY_ID,
            name = "School / 学校",
            sortOrder = 3,
            createdAt = 1_000,
        )
        val todo = TodoEntity(
            id = SOURCE_TODO_ID,
            title = "Submit project",
            description = "Upload the final 报告",
            categoryId = SOURCE_CATEGORY_ID,
            deadlineEpochDay = 20_500,
            deadlineMinute = 21 * 60 + 15,
            priority = TodoPriority.HIGH,
            tagsCsv = "school,important",
            createdAt = 2_000,
            updatedAt = 2_100,
            customOrder = 2_000,
        )
        val ledgerEntry = LedgerEntryEntity(
            id = SOURCE_LEDGER_ID,
            type = LedgerType.EXPENSE,
            amountCents = 12_345,
            epochDay = 20_499,
            minuteOfDay = 12 * 60 + 30,
            note = "Textbooks",
            merchant = "Campus shop",
            tagsCsv = "school,books",
            createdAt = 3_000,
            updatedAt = 3_100,
        )
        val dao = database.backupDao()
        dao.insertCategories(listOf(category))
        dao.insertTodos(listOf(todo))
        dao.insertLedgerEntries(listOf(ledgerEntry))

        val attachmentBytes = "receipt:\n收据 #42".encodeToByteArray()
        val attachmentFile = File(attachmentDirectory, "source-receipt.txt").apply {
            writeBytes(attachmentBytes)
        }
        val attachment = AttachmentEntity(
            id = SOURCE_ATTACHMENT_ID,
            ownerType = AttachmentOwnerType.LEDGER,
            ownerId = SOURCE_LEDGER_ID,
            privatePath = attachmentFile.absolutePath,
            originalName = "receipt-收据.txt",
            mimeType = "text/plain",
            sizeBytes = attachmentBytes.size.toLong(),
            createdAt = 4_000,
        )
        dao.insertAttachments(listOf(attachment))
        assertEquals(listOf(attachment), attachmentStore.getAttachments(AttachmentOwnerType.LEDGER, SOURCE_LEDGER_ID))

        val settings = AppSettings(
            themeMode = ThemeMode.LIGHT,
            accentColor = AccentColor.BLUE,
            weekStart = WeekStart.MONDAY,
            timeFormat = TimeFormatOption.HOUR_24,
            dateFormat = DateFormatOption.YEAR_MONTH_DAY,
            notificationsEnabled = true,
            defaultAllDayReminderMinute = 8 * 60 + 45,
            defaultReminderOffsetsMinutes = setOf(0, 60, 1_440),
            todoQuickAddFields = setOf(TodoQuickAddField.DEADLINE, TodoQuickAddField.TAGS),
            lastDestination = TopLevelDestination.CALENDAR,
        )
        settingsRepository.replace(settings)
        val vaultEntry = vaultRepository.save(
            VaultEntryDraft(
                label = "Student portal",
                account = "student@example.test",
                password = "offline-secret",
                website = "https://portal.example.test",
                notes = "Backup codes: 一 二 三",
            ),
            vaultSession,
        )
        return LogicalState(
            settings = settings,
            categories = listOf(category),
            todos = listOf(todo),
            ledgerEntries = listOf(ledgerEntry),
            attachment = LogicalAttachment(attachment, attachmentBytes.toList()),
            vaultEntries = listOf(vaultEntry),
        )
    }

    private suspend fun seedDifferentLiveState() {
        clearLiveState()
        val todo = TodoEntity(
            id = TARGET_TODO_ID,
            title = "Do not keep",
            description = "Target sentinel",
            priority = TodoPriority.LOW,
            createdAt = 10_000,
            updatedAt = 10_000,
            customOrder = 10_000,
        )
        val ledger = LedgerEntryEntity(
            id = TARGET_LEDGER_ID,
            type = LedgerType.INCOME,
            amountCents = 999,
            epochDay = 20_600,
            minuteOfDay = 1,
            note = "Target sentinel",
            createdAt = 11_000,
            updatedAt = 11_000,
        )
        val dao = database.backupDao()
        dao.insertTodos(listOf(todo))
        dao.insertLedgerEntries(listOf(ledger))
        val bytes = "target attachment".encodeToByteArray()
        val file = File(attachmentDirectory, "target-sentinel.txt").apply { writeBytes(bytes) }
        dao.insertAttachments(
            listOf(
                AttachmentEntity(
                    id = TARGET_ATTACHMENT_ID,
                    ownerType = AttachmentOwnerType.TODO,
                    ownerId = TARGET_TODO_ID,
                    privatePath = file.absolutePath,
                    originalName = "target.txt",
                    mimeType = "text/plain",
                    sizeBytes = bytes.size.toLong(),
                    createdAt = 12_000,
                ),
            ),
        )
        settingsRepository.replace(AppSettings(themeMode = ThemeMode.SYSTEM))
        vaultRepository.save(
            VaultEntryDraft(
                label = "Target vault sentinel",
                password = "keep-until-commit",
            ),
            vaultSession,
        )
    }

    private suspend fun clearLiveState() {
        val dao = database.backupDao()
        dao.deleteAllAttachments()
        dao.deleteAllTodoReminders()
        dao.deleteAllSubtasks()
        dao.deleteAllTodos()
        dao.deleteAllTodoExceptions()
        dao.deleteAllTodoSeriesSubtasks()
        dao.deleteAllTodoSeries()
        dao.deleteAllCategories()
        dao.deleteAllLedgerEntries()
        dao.deleteAllLedgerExceptions()
        dao.deleteAllLedgerSeries()
        dao.deleteAllVaultEntries()
        dao.clearStaleRestoreCommitToken()
    }

    private suspend fun exportBackup(): ByteArray {
        val destination = ByteArrayOutputStream()
        val result = repository.export(
            destination,
            BACKUP_PASSWORD.toCharArray(),
            vaultSession,
        )
        assertEquals(1, result.preview.todoCount)
        assertEquals(1, result.preview.ledgerCount)
        assertEquals(1, result.preview.attachmentCount)
        assertEquals(1, result.preview.vaultCount)
        assertEquals(destination.size().toLong(), result.encryptedBytesWritten)
        return destination.toByteArray()
    }

    private suspend fun captureExactLiveState(): ExactLiveState {
        val state = database.backupDao().backupState()
        assertAttachmentStoreMatches(state.attachments)
        return ExactLiveState(
            database = ExactDatabaseState.from(state),
            settings = settingsRepository.snapshot(),
            attachmentFiles = state.attachments.associate { attachment ->
                attachment.privatePath to File(attachment.privatePath).readBytes().toList()
            },
            vaultEntries = vaultRepository.loadEntries(vaultSession),
        )
    }

    private suspend fun assertExactLiveState(expected: ExactLiveState) {
        assertEquals(expected, captureExactLiveState())
    }

    private fun captureAttachmentTree() = AttachmentTree(
        directories = attachmentDirectory.walkTopDown()
            .filter(File::isDirectory)
            .map { directory -> directory.relativeTo(attachmentDirectory).path }
            .toSet(),
        files = attachmentDirectory.walkTopDown()
            .filter(File::isFile)
            .associate { file -> file.relativeTo(attachmentDirectory).path to file.readBytes().toList() },
    )

    private suspend fun assertAttachmentStoreMatches(attachments: List<AttachmentEntity>) {
        attachments.groupBy { it.ownerType to it.ownerId }.forEach { (owner, expected) ->
            assertEquals(expected, attachmentStore.getAttachments(owner.first, owner.second))
        }
    }

    private suspend fun assertRestoredAttachment(expected: LogicalAttachment) {
        val restored = database.backupDao().backupAttachments().single()
        assertEquals(expected.entity.id, restored.id)
        assertEquals(expected.entity.ownerType, restored.ownerType)
        assertEquals(expected.entity.ownerId, restored.ownerId)
        assertEquals(expected.entity.originalName, restored.originalName)
        assertEquals(expected.entity.mimeType, restored.mimeType)
        assertEquals(expected.entity.sizeBytes, restored.sizeBytes)
        assertEquals(expected.entity.createdAt, restored.createdAt)
        assertEquals(expected.entity.pendingDeleteAt, restored.pendingDeleteAt)
        assertTrue(File(restored.privatePath).isFile)
        assertEquals(expected.bytes, File(restored.privatePath).readBytes().toList())
        assertEquals(
            listOf(restored),
            attachmentStore.getAttachments(restored.ownerType, restored.ownerId),
        )
    }

    private class IsolatedFilesContext(
        base: Context,
        private val isolatedFilesDir: File,
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = isolatedFilesDir
    }

    private data class LogicalState(
        val settings: AppSettings,
        val categories: List<CategoryEntity>,
        val todos: List<TodoEntity>,
        val ledgerEntries: List<LedgerEntryEntity>,
        val attachment: LogicalAttachment,
        val vaultEntries: List<VaultEntry>,
    )

    private data class LogicalAttachment(
        val entity: AttachmentEntity,
        val bytes: List<Byte>,
    )

    private data class ExactLiveState(
        val database: ExactDatabaseState,
        val settings: AppSettings,
        val attachmentFiles: Map<String, List<Byte>>,
        val vaultEntries: List<VaultEntry>,
    )

    private data class ExactDatabaseState(
        val categories: List<CategoryEntity>,
        val todoSeries: List<TodoSeriesEntity>,
        val todoSeriesSubtasks: List<TodoSeriesSubtaskEntity>,
        val todoOccurrenceExceptions: List<TodoOccurrenceExceptionEntity>,
        val todos: List<TodoEntity>,
        val subtasks: List<SubtaskEntity>,
        val todoReminders: List<TodoReminderEntity>,
        val ledgerSeries: List<LedgerSeriesEntity>,
        val ledgerOccurrenceExceptions: List<LedgerOccurrenceExceptionEntity>,
        val ledgerEntries: List<LedgerEntryEntity>,
        val attachments: List<AttachmentEntity>,
        val vaultRows: List<ExactVaultRow>,
    ) {
        companion object {
            fun from(state: BackupDatabaseState) = ExactDatabaseState(
                categories = state.categories,
                todoSeries = state.todoSeries,
                todoSeriesSubtasks = state.todoSeriesSubtasks,
                todoOccurrenceExceptions = state.todoOccurrenceExceptions,
                todos = state.todos,
                subtasks = state.subtasks,
                todoReminders = state.todoReminders,
                ledgerSeries = state.ledgerSeries,
                ledgerOccurrenceExceptions = state.ledgerOccurrenceExceptions,
                ledgerEntries = state.ledgerEntries,
                attachments = state.attachments,
                vaultRows = state.vaultEntries.map { row ->
                    ExactVaultRow(
                        id = row.id,
                        formatVersion = row.formatVersion,
                        payloadIv = row.payloadIv.toList(),
                        payloadCiphertext = row.payloadCiphertext.toList(),
                        createdAt = row.createdAt,
                        updatedAt = row.updatedAt,
                    )
                },
            )
        }
    }

    private data class ExactVaultRow(
        val id: String,
        val formatVersion: Int,
        val payloadIv: List<Byte>,
        val payloadCiphertext: List<Byte>,
        val createdAt: Long,
        val updatedAt: Long,
    )

    private data class AttachmentTree(
        val directories: Set<String>,
        val files: Map<String, List<Byte>>,
    )

    private companion object {
        const val BACKUP_PASSWORD = "correct horse battery staple"
        const val WRONG_PASSWORD = "incorrect horse battery staple"
        const val BACKUP_CREATED_AT = 50_000L
        const val SOURCE_CATEGORY_ID = 101L
        const val SOURCE_TODO_ID = 201L
        const val SOURCE_LEDGER_ID = 301L
        const val SOURCE_ATTACHMENT_ID = 401L
        const val TARGET_TODO_ID = 202L
        const val TARGET_LEDGER_ID = 302L
        const val TARGET_ATTACHMENT_ID = 402L
    }
}
