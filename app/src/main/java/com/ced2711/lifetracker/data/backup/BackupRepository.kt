package com.ced2711.lifetracker.data.backup

import android.content.Context
import com.ced2711.lifetracker.data.attachment.AttachmentStore
import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.data.local.BackupDatabaseState
import com.ced2711.lifetracker.data.local.BackupDao
import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.data.settings.SettingsRepository
import com.ced2711.lifetracker.data.vault.VaultAuthenticationRequiredException
import com.ced2711.lifetracker.data.vault.VaultBackupCipher
import com.ced2711.lifetracker.data.vault.VaultRepository
import com.ced2711.lifetracker.data.vault.VaultSession
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class BackupPreview(
    val createdAt: Long,
    val todoCount: Int,
    val ledgerCount: Int,
    val vaultCount: Int,
    val attachmentCount: Int,
    val attachmentBytes: Long,
    val totalBytes: Long = 0,
    val noteCount: Int = 0,
)

data class BackupExportResult(
    val preview: BackupPreview,
    val encryptedBytesWritten: Long,
)

enum class BackupRebuildHint {
    RESCHEDULE_REMINDERS,
    REGENERATE_RECURRENCES,
    REFRESH_WIDGETS,
}

data class BackupRestoreResult(
    val preview: BackupPreview,
    val warnings: List<String>,
    val rebuildHints: Set<BackupRebuildHint> = BackupRebuildHint.entries.toSet(),
)

data class BackupRecoveryResult(
    val restoredDatabaseWasCommitted: Boolean,
    val warnings: List<String>,
    val rebuildHints: Set<BackupRebuildHint> = BackupRebuildHint.entries.toSet(),
)

/**
 * A fully authenticated and validated backup that has not changed live application data.
 * Call [close] when the preview is discarded so its private attachment stage is removed promptly.
 */
class PreparedBackupRestore internal constructor(
    snapshot: BackupSnapshot,
    attachmentStage: AttachmentRestoreStage,
    val preview: BackupPreview,
    val requiresVaultAuthentication: Boolean,
) : AutoCloseable {
    private val lifecycle = AtomicReference(PreparedRestoreLifecycle.READY)
    @Volatile private var retainedSnapshot: BackupSnapshot? = snapshot
    @Volatile private var retainedAttachmentStage: AttachmentRestoreStage? = attachmentStage

    internal fun requireSnapshot(): BackupSnapshot = retainedSnapshot
        ?: error("This prepared restore was discarded.")

    internal fun requireAttachmentStage(): AttachmentRestoreStage = retainedAttachmentStage
        ?: error("This prepared restore was discarded.")

    internal fun beginCommit() {
        check(lifecycle.compareAndSet(PreparedRestoreLifecycle.READY, PreparedRestoreLifecycle.COMMITTING)) {
            "This prepared restore is not ready to commit."
        }
    }

    internal fun releaseAfterFailure() {
        check(lifecycle.compareAndSet(PreparedRestoreLifecycle.COMMITTING, PreparedRestoreLifecycle.READY)) {
            "This prepared restore is not committing."
        }
    }

    internal fun completeCommit() {
        check(lifecycle.compareAndSet(PreparedRestoreLifecycle.COMMITTING, PreparedRestoreLifecycle.CONSUMED)) {
            "This prepared restore is not committing."
        }
        retainedAttachmentStage?.commit()
        retainedAttachmentStage = null
        retainedSnapshot = null
    }

    override fun close() {
        while (true) {
            when (lifecycle.get()) {
                PreparedRestoreLifecycle.READY -> if (
                    lifecycle.compareAndSet(PreparedRestoreLifecycle.READY, PreparedRestoreLifecycle.CLOSED)
                ) {
                    releasePreparedData()
                    return
                }
                PreparedRestoreLifecycle.CLOSED,
                PreparedRestoreLifecycle.CONSUMED,
                -> return
                PreparedRestoreLifecycle.COMMITTING -> error("A restore commit is in progress.")
            }
        }
    }

    internal fun lifecycleForTest(): PreparedRestoreLifecycle = lifecycle.get()

    private fun releasePreparedData() {
        retainedSnapshot = null
        retainedAttachmentStage?.close()
        retainedAttachmentStage = null
    }
}

internal enum class PreparedRestoreLifecycle { READY, COMMITTING, CONSUMED, CLOSED }

/** Coordinates the encrypted backup core without exposing device-specific paths in the format. */
class BackupRepository(
    private val backupDao: BackupDao,
    private val settingsRepository: SettingsRepository,
    private val vaultRepository: VaultRepository,
    private val attachmentDirectory: File,
    private val workDirectory: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val filesRoot = requireNotNull(attachmentDirectory.parentFile).absoluteFile
    private val restoreJournal = BackupRestoreJournal(
        journalFile = File(filesRoot, RESTORE_JOURNAL_FILE),
        filesRoot = filesRoot,
        attachmentRoot = attachmentDirectory,
    )

    constructor(
        context: Context,
        backupDao: BackupDao,
        settingsRepository: SettingsRepository,
        vaultRepository: VaultRepository,
    ) : this(
        backupDao = backupDao,
        settingsRepository = settingsRepository,
        vaultRepository = vaultRepository,
        attachmentDirectory = File(context.filesDir, AttachmentStore.ATTACHMENT_DIRECTORY),
        workDirectory = File(context.cacheDir, BACKUP_WORK_DIRECTORY),
    )

    /**
     * Creates one logical snapshot and writes only its password-encrypted representation to the
     * caller-owned stream. The password array is consumed on every outcome.
     */
    suspend fun export(
        destination: OutputStream,
        password: CharArray,
        vaultSession: VaultSession? = null,
    ): BackupExportResult = withContext(ioDispatcher) {
        try {
            validateBackupPassword(password)
            val plan = createExportPlan(vaultSession)
            val currentSnapshot = plan.snapshot
            val countingDestination = CountingOutputStream(destination)
            BackupCrypto.encrypt(currentSnapshot, password, countingDestination, plan)
            val preview = buildBackupPreview(currentSnapshot).copy(totalBytes = countingDestination.count)
            BackupExportResult(preview, countingDestination.count)
        } finally {
            password.fill('\u0000')
        }
    }

    /**
     * Fully decrypts, authenticates, decodes, and validates before returning a preview. No Room,
     * DataStore, Vault, or live attachment mutation occurs here. The password array is consumed.
     */
    suspend fun prepareRestore(
        source: InputStream,
        password: CharArray,
    ): PreparedBackupRestore = withContext(ioDispatcher) {
        var decoded: DecryptedBackup? = null
        try {
            validateBackupPassword(password)
            val countingSource = CountingInputStream(source)
            val currentDecoded = BackupCrypto.decrypt(
                countingSource,
                password,
                workDirectory,
                attachmentDirectory,
            )
            decoded = currentDecoded
            val currentSnapshot = currentDecoded.snapshot.validate()
            val targetHasVault = backupDao.backupVaultIds().isNotEmpty()
            PreparedBackupRestore(
                snapshot = currentSnapshot,
                attachmentStage = currentDecoded.attachmentStage,
                preview = buildBackupPreview(currentSnapshot).copy(totalBytes = countingSource.count),
                requiresVaultAuthentication = targetHasVault || currentSnapshot.vaultEntries.isNotEmpty(),
            ).also { decoded = null }
        } finally {
            password.fill('\u0000')
            decoded?.attachmentStage?.close()
        }
    }

    /**
     * Replaces all backed-up state. Room mutations are transactional; Settings and newly staged
     * files are rolled back best-effort if Room does not commit. Old files are deleted only after
     * the new Room graph has committed.
     */
    suspend fun restore(
        prepared: PreparedBackupRestore,
        vaultSession: VaultSession? = null,
    ): BackupRestoreResult = withContext(ioDispatcher) {
        prepared.beginCommit()
        var succeeded = false
        var settingsApplied = false
        var roomCommitted = false
        var oldSettings: AppSettings? = null
        var journalWritten = false
        try {
            if (restoreJournal.read() != null) {
                throw InvalidBackupException("A previous restore must be recovered before starting another one.")
            }
            val snapshot = prepared.requireSnapshot().validate()
            val settingsBeforeVerification = settingsRepository.snapshot()
            val stateBeforeVerification = backupDao.backupState()
            val targetVaultRows = stateBeforeVerification.vaultEntries
            val targetHasVault = targetVaultRows.isNotEmpty()
            if ((targetHasVault || snapshot.vaultEntries.isNotEmpty()) && vaultSession == null) {
                throw VaultAuthenticationRequiredException()
            }
            if (targetHasVault) {
                // Authenticate the actual current ciphertext version before staging or any write.
                vaultRepository.verifySession(targetVaultRows, requireNotNull(vaultSession))
            }
            val oldState = backupDao.backupState()
            oldSettings = settingsRepository.snapshot()
            if (
                settingsBeforeVerification != oldSettings ||
                fullDatabaseFingerprint(stateBeforeVerification) != fullDatabaseFingerprint(oldState)
            ) {
                throw InvalidBackupException("Data changed while the restore was being prepared. Please try again.")
            }

            val oldAttachments = oldState.attachments
            ensureAttachmentDirectory()
            val currentStage = prepared.requireAttachmentStage()
            val restoredEntities = currentStage.attachments.entities(snapshot)
            val restoredPaths = restoredEntities.mapTo(HashSet(), AttachmentEntity::privatePath)
            val importedSettings = snapshot.settings.toAppSettings()
            val encryptedVault = if (snapshot.vaultEntries.isEmpty()) {
                emptyList()
            } else {
                val session = requireNotNull(vaultSession)
                snapshot.vaultEntries.map { VaultBackupCipher.encrypt(it, session) }
            }
            if (
                settingsRepository.snapshot() != oldSettings ||
                fullDatabaseFingerprint(backupDao.backupState()) != fullDatabaseFingerprint(oldState)
            ) {
                throw InvalidBackupException("Data changed before the restore commit. Please try again.")
            }
            val restoreToken = UUID.randomUUID().toString()
            val journalRecord = RestoreJournalRecord(
                restoreToken = restoreToken,
                oldSettings = requireNotNull(oldSettings),
                newSettings = importedSettings,
                stageRoot = currentStage.root.absolutePath,
                oldAttachmentPaths = managedAttachmentPaths(attachmentDirectory, oldAttachments),
            )
            restoreJournal.write(journalRecord)
            journalWritten = true

            withContext(NonCancellable) {
                // Once persistence starts, cancellation must not strand Room rows pointing at
                // attachment files that the finally block would otherwise discard.
                settingsApplied = true
                settingsRepository.replace(importedSettings)
                backupDao.replaceSnapshot(
                    snapshot,
                    encryptedVault,
                    currentStage.attachments,
                    restoreToken,
                )
                roomCommitted = true
                prepared.completeCommit()
                succeeded = true

                val warnings = mutableListOf<String>()
                warnings += deleteObsoleteAttachmentFiles(
                    attachmentDirectory = attachmentDirectory,
                    oldAttachments = oldAttachments,
                    keepPaths = restoredPaths,
                )
                cleanupUnreferencedRestoreDirectories(attachmentDirectory, restoredPaths)
                val journalCleared = runCatching { restoreJournal.clear() }
                    .onFailure { warnings += "Restore recovery metadata will be cleaned on next launch." }
                    .isSuccess
                if (journalCleared) {
                    runCatching { backupDao.clearRestoreCommitToken(restoreToken) }
                        .onFailure { warnings += "Restore recovery metadata will be cleaned on next launch." }
                }
                BackupRestoreResult(prepared.preview, warnings)
            }
        } catch (failure: Throwable) {
            if (settingsApplied && !roomCommitted && oldSettings != null) {
                val settingsToRestore = requireNotNull(oldSettings)
                withContext(NonCancellable) {
                    val settingsRollback = runCatching {
                        settingsRepository.replace(settingsToRestore)
                    }.onFailure(failure::addSuppressed)
                    if (journalWritten && settingsRollback.isSuccess) {
                        runCatching { restoreJournal.clear() }
                            .onFailure(failure::addSuppressed)
                    }
                }
            } else if (journalWritten && !roomCommitted) {
                withContext(NonCancellable) {
                    runCatching { restoreJournal.clear() }.onFailure(failure::addSuppressed)
                }
            }
            throw failure
        } finally {
            if (!succeeded) prepared.releaseAfterFailure()
        }
    }

    /**
     * Resolves a process-death window before normal scheduling starts. It is idempotent: the
     * marker written in the Room transaction decides which side committed. The journal is cleared
     * before its matching marker so a second process death cannot reverse that decision.
     */
    suspend fun recoverPendingRestore(): BackupRecoveryResult? = withContext(ioDispatcher) {
        BackupCrypto.cleanupStaleWorkArtifacts(workDirectory)
        ensureAttachmentDirectory()
        val journal = restoreJournal.readForRecovery()
        val record = when (journal) {
            RestoreJournalReadResult.None -> {
                val currentPaths = backupDao.backupAttachments()
                    .mapTo(HashSet(), AttachmentEntity::privatePath)
                cleanupUnreferencedRestoreDirectories(attachmentDirectory, currentPaths)
                backupDao.clearStaleRestoreCommitToken()
                return@withContext null
            }
            RestoreJournalReadResult.Quarantined -> return@withContext BackupRecoveryResult(
                restoredDatabaseWasCommitted = false,
                warnings = listOf("Unreadable restore recovery metadata was quarantined; live data was left unchanged."),
            )
            is RestoreJournalReadResult.Legacy -> return@withContext recoverLegacyRestore(journal.record)
            is RestoreJournalReadResult.Current -> journal.record
        }

        withContext(NonCancellable) {
            val currentState = backupDao.backupState()
            val committed = backupDao.restoreCommitToken() == record.restoreToken
            val currentPaths = currentState.attachments.mapTo(HashSet(), AttachmentEntity::privatePath)
            val warnings = mutableListOf<String>()
            if (committed) {
                settingsRepository.replace(record.newSettings)
                warnings += deleteManagedAttachmentPaths(
                    attachmentDirectory,
                    record.oldAttachmentPaths,
                    currentPaths,
                )
            } else {
                settingsRepository.replace(record.oldSettings)
                if (!deleteRestoreStage(attachmentDirectory, File(record.stageRoot))) {
                    warnings += "An unused restore stage could not be removed."
                }
            }
            cleanupUnreferencedRestoreDirectories(attachmentDirectory, currentPaths)
            restoreJournal.clear()
            backupDao.clearRestoreCommitToken(record.restoreToken)
            BackupRecoveryResult(committed, warnings)
        }
    }

    private suspend fun recoverLegacyRestore(record: LegacyRestoreJournalRecord): BackupRecoveryResult =
        withContext(NonCancellable) {
            val currentState = backupDao.backupState()
            val committed = record.phase == LegacyRestoreJournalPhase.ROOM_COMMITTED ||
                fullDatabaseFingerprint(currentState) == record.expectedDatabaseFingerprint
            val currentPaths = currentState.attachments.mapTo(HashSet(), AttachmentEntity::privatePath)
            val warnings = mutableListOf<String>()
            if (committed) {
                settingsRepository.replace(record.newSettings)
                warnings += deleteManagedAttachmentPaths(
                    attachmentDirectory,
                    record.oldAttachmentPaths,
                    currentPaths,
                )
            } else {
                settingsRepository.replace(record.oldSettings)
                if (!deleteRestoreStage(attachmentDirectory, File(record.stageRoot))) {
                    warnings += "An unused restore stage could not be removed."
                }
            }
            cleanupUnreferencedRestoreDirectories(attachmentDirectory, currentPaths)
            restoreJournal.clear()
            backupDao.clearStaleRestoreCommitToken()
            BackupRecoveryResult(committed, warnings)
        }

    private suspend fun createExportPlan(vaultSession: VaultSession?): BackupExportPlan {
        val captured = retryStableCapture(MAX_SNAPSHOT_ATTEMPTS) {
            val settingsBefore = settingsRepository.snapshot()
            val stateBefore = backupDao.backupState()
            val vaultEntries = if (stateBefore.vaultEntries.isEmpty()) {
                emptyList()
            } else {
                vaultRepository.loadEntries(
                    stateBefore.vaultEntries,
                    vaultSession ?: throw VaultAuthenticationRequiredException(),
                )
            }
            val attachmentPlan = planManagedAttachments(attachmentDirectory, stateBefore.attachments)
            val settingsAfter = settingsRepository.snapshot()
            val stateAfter = backupDao.backupState()
            val stable = settingsBefore == settingsAfter &&
                fullDatabaseFingerprint(stateBefore) == fullDatabaseFingerprint(stateAfter)
            if (stable) {
                val snapshot = stateBefore.toBackupSnapshot(
                    settings = settingsBefore.toBackupSettings(),
                    vaultEntries = vaultEntries,
                    attachmentPlan = attachmentPlan,
                    createdAt = now(),
                )
                BackupExportPlan(snapshot, attachmentPlan.associate { it.metadata.id to it.file })
            } else {
                null
            }
        }
        return captured ?: throw InvalidBackupException(
            "Data kept changing while the backup was created. Please try again.",
        )
    }

    private fun ensureAttachmentDirectory() {
        if (!attachmentDirectory.exists() && !attachmentDirectory.mkdirs()) {
            throw InvalidBackupException("Could not create the private attachment directory.")
        }
        if (!attachmentDirectory.isDirectory) {
            throw InvalidBackupException("The private attachment path is not a directory.")
        }
    }

    private companion object {
        const val BACKUP_WORK_DIRECTORY = "taskledger-backup-work"
        const val RESTORE_JOURNAL_FILE = "taskledger-restore.journal"
        const val MAX_SNAPSHOT_ATTEMPTS = 3
    }
}

internal suspend fun <T> retryStableCapture(
    maxAttempts: Int,
    attempt: suspend (attemptIndex: Int) -> T?,
): T? {
    require(maxAttempts > 0)
    repeat(maxAttempts) { index -> attempt(index)?.let { return it } }
    return null
}

internal fun validateBackupPassword(password: CharArray) {
    if (password.size < 8) {
        throw InvalidBackupException("Backup password must contain at least 8 characters.")
    }
}

internal fun buildBackupPreview(snapshot: BackupSnapshot): BackupPreview = BackupPreview(
    createdAt = snapshot.createdAt,
    todoCount = snapshot.todos.size,
    ledgerCount = snapshot.ledgerEntries.size,
    vaultCount = snapshot.vaultEntries.size,
    attachmentCount = snapshot.attachments.size,
    attachmentBytes = snapshot.attachments.sumOf(BackupAttachment::sizeBytes),
    noteCount = snapshot.notes.size,
)

internal fun AppSettings.toBackupSettings() = BackupSettings(
    themeMode = themeMode,
    accentColor = accentColor,
    weekStart = weekStart,
    timeFormat = timeFormat,
    dateFormat = dateFormat,
    notificationsEnabled = notificationsEnabled,
    defaultAllDayReminderMinute = defaultAllDayReminderMinute,
    defaultReminderOffsetsMinutes = defaultReminderOffsetsMinutes,
    todoQuickAddFields = todoQuickAddFields,
    lastDestination = lastDestination,
)

internal fun BackupSettings.toAppSettings() = AppSettings(
    themeMode = themeMode,
    accentColor = accentColor,
    weekStart = weekStart,
    timeFormat = timeFormat,
    dateFormat = dateFormat,
    notificationsEnabled = notificationsEnabled,
    defaultAllDayReminderMinute = defaultAllDayReminderMinute,
    defaultReminderOffsetsMinutes = defaultReminderOffsetsMinutes,
    todoQuickAddFields = todoQuickAddFields,
    lastDestination = lastDestination,
)

internal fun BackupDatabaseState.toBackupSnapshot(
    settings: BackupSettings,
    vaultEntries: List<com.ced2711.lifetracker.domain.model.VaultEntry>,
    attachmentPlan: List<PlannedAttachment>,
    createdAt: Long,
): BackupSnapshot {
    val expectedAttachmentIds = attachments.mapTo(HashSet(), AttachmentEntity::id)
    if (attachmentPlan.mapTo(HashSet()) { it.metadata.id } != expectedAttachmentIds) {
        throw InvalidBackupException("Attachment plan does not match the captured database version.")
    }
    if (vaultEntries.map { it.id }.sorted() != this.vaultEntries.map { it.id }.sorted()) {
        throw InvalidBackupException("Unlocked vault entries do not match the captured database version.")
    }
    val portableAttachments = attachmentPlan.associateBy { it.metadata.id }.let { byId ->
        attachments.map { row ->
            val metadata = requireNotNull(byId[row.id]).metadata
            if (
                metadata.ownerType != row.ownerType || metadata.ownerId != row.ownerId ||
                metadata.originalName != row.originalName || metadata.mimeType != row.mimeType ||
                metadata.sizeBytes != row.sizeBytes || metadata.createdAt != row.createdAt ||
                metadata.pendingDeleteAt != row.pendingDeleteAt
            ) {
                throw InvalidBackupException("Attachment metadata changed during backup planning.")
            }
            metadata
        }
    }
    return BackupSnapshot(
        createdAt = createdAt,
        settings = settings,
        categories = categories,
        todoSeries = todoSeries,
        todoSeriesSubtasks = todoSeriesSubtasks,
        todoOccurrenceExceptions = todoOccurrenceExceptions,
        todos = todos,
        subtasks = subtasks,
        todoReminders = todoReminders,
        ledgerSeries = ledgerSeries,
        ledgerOccurrenceExceptions = ledgerOccurrenceExceptions,
        ledgerEntries = ledgerEntries,
        attachments = portableAttachments,
        vaultEntries = vaultEntries,
        noteFolders = noteFolders,
        notes = notes,
    ).validate()
}

internal data class PlannedAttachment(
    val metadata: BackupAttachment,
    val file: File,
)

internal class BackupExportPlan(
    val snapshot: BackupSnapshot,
    private val filesById: Map<Long, File>,
) : BackupAttachmentSource {
    override fun open(attachment: BackupAttachment): InputStream {
        val file = filesById[attachment.id]
            ?: throw InvalidBackupException("Attachment source is missing from the export plan.")
        if (!file.isFile || file.length() != attachment.sizeBytes) {
            throw InvalidBackupException("Attachment changed before it could be exported.")
        }
        return try {
            file.inputStream()
        } catch (error: IOException) {
            throw InvalidBackupException("Attachment ${attachment.id} could not be opened.", error)
        }
    }
}

internal fun planManagedAttachments(
    attachmentDirectory: File,
    rows: List<AttachmentEntity>,
): List<PlannedAttachment> {
    val root = attachmentDirectory.canonicalFile
    val prefix = root.path + File.separator
    val ids = HashSet<Long>(rows.size)
    return rows.map { row ->
        if (!ids.add(row.id)) throw InvalidBackupException("Duplicate attachment id ${row.id}.")
        if (row.sizeBytes !in 0..BackupLimits.MAX_ATTACHMENT_BYTES) {
            throw InvalidBackupException("Attachment ${row.id} has an invalid stored size.")
        }
        val file = try {
            File(row.privatePath).canonicalFile
        } catch (error: IOException) {
            throw InvalidBackupException("Attachment ${row.id} has an invalid private path.", error)
        }
        if (!file.path.startsWith(prefix) || !file.isFile || file.length() != row.sizeBytes) {
            throw InvalidBackupException("Attachment ${row.id} is missing or outside private storage.")
        }
        val digest = hashManagedAttachment(file, row.id, row.sizeBytes)
        PlannedAttachment(
            metadata = BackupAttachment(
                id = row.id,
                ownerType = row.ownerType,
                ownerId = row.ownerId,
                archivePath = attachmentArchivePath(row.id, digest),
                originalName = row.originalName,
                mimeType = row.mimeType,
                sizeBytes = row.sizeBytes,
                sha256 = digest,
                createdAt = row.createdAt,
                pendingDeleteAt = row.pendingDeleteAt,
            ),
            file = file,
        )
    }
}

private fun hashManagedAttachment(file: File, id: Long, expectedSize: Long): ByteArray {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var count = 0L
    try {
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                count = Math.addExact(count, read.toLong())
                if (count > expectedSize) {
                    throw InvalidBackupException("Attachment $id changed while the backup was planned.")
                }
                digest.update(buffer, 0, read)
            }
        }
    } catch (error: BackupException) {
        throw error
    } catch (error: Exception) {
        throw InvalidBackupException("Attachment $id could not be read.", error)
    } finally {
        buffer.fill(0)
    }
    if (count != expectedSize || file.length() != expectedSize) {
        throw InvalidBackupException("Attachment $id changed while the backup was planned.")
    }
    return digest.digest()
}

internal fun deleteObsoleteAttachmentFiles(
    attachmentDirectory: File,
    oldAttachments: List<AttachmentEntity>,
    keepPaths: Set<String>,
): List<String> = deleteManagedAttachmentPaths(
    attachmentDirectory,
    oldAttachments.map(AttachmentEntity::privatePath),
    keepPaths,
)

internal fun managedAttachmentPaths(
    attachmentDirectory: File,
    attachments: List<AttachmentEntity>,
): List<String> {
    val root = runCatching { attachmentDirectory.canonicalFile }.getOrElse {
        return emptyList()
    }
    val prefix = root.path + File.separator
    return attachments.mapNotNull { attachment ->
        runCatching { File(attachment.privatePath).canonicalFile }
            .getOrNull()
            ?.takeIf { it.path.startsWith(prefix) }
            ?.absolutePath
    }.distinct()
}

internal fun deleteManagedAttachmentPaths(
    attachmentDirectory: File,
    oldPaths: List<String>,
    keepPaths: Set<String>,
): List<String> {
    val root = runCatching { attachmentDirectory.canonicalFile }.getOrElse {
        return listOf("Old attachment files could not be checked after restore.")
    }
    val prefix = root.path + File.separator
    val canonicalKeep = keepPaths.mapNotNullTo(HashSet()) { path ->
        runCatching { File(path).canonicalPath }.getOrNull()
    }
    val warnings = ArrayList<String>()
    oldPaths.distinct().forEach { path ->
        val file = runCatching { File(path).canonicalFile }.getOrElse {
            warnings += "An old attachment path could not be checked."
            return@forEach
        }
        if (!file.path.startsWith(prefix) || file.path in canonicalKeep) return@forEach
        if (file.exists() && (!file.isFile || !file.delete())) {
            warnings += "An obsolete attachment file could not be removed."
        }
        runCatching { removeEmptyRestoreParent(file.parentFile, root) }
    }
    return warnings
}

internal fun cleanupUnreferencedRestoreDirectories(
    attachmentDirectory: File,
    referencedPaths: Set<String>,
) {
    val root = runCatching { attachmentDirectory.canonicalFile }.getOrNull() ?: return
    val canonicalReferences = referencedPaths.mapNotNullTo(HashSet()) {
        runCatching { File(it).canonicalPath }.getOrNull()
    }
    root.listFiles()?.filter { candidate ->
        candidate.isDirectory && candidate.name.startsWith("taskledger-restore-")
    }?.forEach { directory ->
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return@forEach
        if (canonicalDirectory.parentFile != root) return@forEach
        val prefix = canonicalDirectory.path + File.separator
        if (canonicalReferences.none { it.startsWith(prefix) }) {
            canonicalDirectory.deleteRecursively()
        }
    }
}

internal fun deleteRestoreStage(attachmentDirectory: File, stageRoot: File): Boolean {
    val root = runCatching { attachmentDirectory.canonicalFile }.getOrNull() ?: return false
    val stage = runCatching { stageRoot.canonicalFile }.getOrNull() ?: return false
    if (stage.parentFile != root || !stage.name.startsWith("taskledger-restore-")) return false
    return !stage.exists() || stage.deleteRecursively()
}

private fun removeEmptyRestoreParent(parent: File?, root: File) {
    val directory = parent ?: return
    if (
        directory.parentFile?.canonicalFile == root &&
        directory.name.startsWith("taskledger-restore-") &&
        directory.list()?.isEmpty() == true
    ) {
        directory.delete()
    }
}

private class CountingOutputStream(
    private val destination: OutputStream,
) : OutputStream() {
    var count: Long = 0
        private set

    override fun write(value: Int) {
        destination.write(value)
        count = Math.addExact(count, 1L)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        destination.write(buffer, offset, length)
        count = Math.addExact(count, length.toLong())
    }

    override fun flush() = destination.flush()
}

private class CountingInputStream(
    private val source: InputStream,
) : InputStream() {
    var count: Long = 0
        private set

    override fun read(): Int = source.read().also { value ->
        if (value >= 0) count = Math.addExact(count, 1L)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        source.read(buffer, offset, length).also { read ->
            if (read > 0) count = Math.addExact(count, read.toLong())
        }
}
