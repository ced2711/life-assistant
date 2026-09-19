package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.data.backup.BackupAttachment
import com.ced2711.lifetracker.data.backup.BackupAttachmentSource
import com.ced2711.lifetracker.data.backup.BackupCrypto
import com.ced2711.lifetracker.data.backup.BackupLimits
import com.ced2711.lifetracker.data.backup.BackupSettings
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.data.backup.attachmentArchivePath
import com.ced2711.lifetracker.data.backup.validate
import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.NoteEntity
import com.ced2711.lifetracker.data.local.NoteFolderEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.AppIdentity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.VaultEntry
import com.ced2711.lifetracker.domain.model.WeekStart
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface DesktopStoreState {
    data object Locked : DesktopStoreState
    data class Open(val snapshot: BackupSnapshot) : DesktopStoreState
    data class Error(val message: String) : DesktopStoreState
}

data class DesktopUploadSnapshot(
    val file: File,
    val fingerprint: String,
)

sealed interface DesktopReplaceResult {
    data class Applied(val fingerprint: String) : DesktopReplaceResult
    data object LocalChanged : DesktopReplaceResult
    data object Invalid : DesktopReplaceResult
}

class DesktopDataStore(
    val appDirectory: File = defaultAppDirectory(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutoCloseable {
    private val mutex = Mutex()
    private val localBackup = File(appDirectory, "life-tracker-local.tlb")
    private val attachmentsDirectory = File(appDirectory, "attachments")
    private val workDirectory = File(appDirectory, "work")
    private val cloudRecovery = File(appDirectory, "cloud-recovery")
    private val _state = MutableStateFlow<DesktopStoreState>(DesktopStoreState.Locked)
    val state: StateFlow<DesktopStoreState> = _state.asStateFlow()
    private var password: CharArray? = null
    private var attachmentFiles: Map<Long, File> = emptyMap()
    private var activeAttachmentDirectory: File? = null
    private var lockChannel: FileChannel? = null
    private var instanceLock: FileLock? = null

    val encryptedFile: File get() = localBackup

    /** Persistent USE_CLOUD recovery snapshots. Entries are intentionally never pruned here. */
    val cloudRecoveryDirectory: File get() = cloudRecovery

    suspend fun open(password: CharArray, createIfMissing: Boolean = true): Boolean = mutex.withLock {
        withContext(ioDispatcher) {
            try {
                appDirectory.mkdirs()
                if (!acquireInstanceLock()) {
                    _state.value = DesktopStoreState.Error("${AppIdentity.NAME} is already open on this Windows account.")
                    return@withContext false
                }
                cleanupStaleWorkingFiles()
                attachmentsDirectory.mkdirs()
                workDirectory.mkdirs()
                val loaded = if (localBackup.isFile) {
                    loadSnapshot(localBackup, password)
                } else {
                    if (!createIfMissing) return@withContext false
                    null
                }
                this@DesktopDataStore.password?.fill('\u0000')
                this@DesktopDataStore.password = password.copyOf()
                if (loaded != null) {
                    adoptLoadedSnapshot(loaded)
                } else {
                    val snapshot = defaultSnapshot()
                    activeAttachmentDirectory = createAttachmentGeneration()
                    attachmentFiles = emptyMap()
                    saveLocked(snapshot)
                    _state.value = DesktopStoreState.Open(snapshot)
                }
                true
            } catch (_: Throwable) {
                _state.value = DesktopStoreState.Error("The local data password is incorrect or the file is damaged.")
                false
            } finally {
                password.fill('\u0000')
            }
        }
    }

    suspend fun replaceFromEncrypted(
        source: File,
        expectedLocalFingerprint: String? = null,
    ): DesktopReplaceResult = mutex.withLock {
        withContext(ioDispatcher) {
            val secret = password?.copyOf() ?: return@withContext DesktopReplaceResult.Invalid
            if (expectedLocalFingerprint != null && fingerprintLocked() != expectedLocalFingerprint) {
                secret.fill('\u0000')
                return@withContext DesktopReplaceResult.LocalChanged
            }
            var loaded: LoadedSnapshot? = null
            try {
                loaded = loadSnapshot(source, secret)
                val replacement = File(appDirectory, "life-tracker-local.tlb.download")
                try {
                    Files.copy(source.toPath(), replacement.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    replaceFile(replacement, localBackup)
                } finally {
                    replacement.delete()
                }
                adoptLoadedSnapshot(loaded)
                DesktopReplaceResult.Applied(fingerprintLocked())
            } catch (_: Throwable) {
                loaded?.directory?.deleteRecursively()
                DesktopReplaceResult.Invalid
            } finally {
                secret.fill('\u0000')
            }
        }
    }

    /** Manual migration path: decrypt with the source password, then encrypt with this store's password. */
    suspend fun importFromEncrypted(
        source: File,
        sourcePassword: CharArray,
        expectedLocalFingerprint: String,
    ): DesktopReplaceResult = mutex.withLock {
        withContext(ioDispatcher) {
            if (password == null) {
                sourcePassword.fill('\u0000')
                return@withContext DesktopReplaceResult.Invalid
            }
            if (fingerprintLocked() != expectedLocalFingerprint) {
                sourcePassword.fill('\u0000')
                return@withContext DesktopReplaceResult.LocalChanged
            }
            var loaded: LoadedSnapshot? = null
            try {
                loaded = loadSnapshot(source, sourcePassword)
                saveLocked(loaded.snapshot, loaded.files)
                adoptLoadedSnapshot(loaded)
                DesktopReplaceResult.Applied(fingerprintLocked())
            } catch (_: Throwable) {
                loaded?.directory?.deleteRecursively()
                DesktopReplaceResult.Invalid
            } finally {
                sourcePassword.fill('\u0000')
            }
        }
    }

    fun currentSnapshot(): BackupSnapshot? = (_state.value as? DesktopStoreState.Open)?.snapshot

    fun isUserDataEmpty(): Boolean = currentSnapshot()?.let { snapshot ->
        val defaults = defaultSnapshot().settings
        val synchronizedSettingsAreDefault =
            snapshot.settings.copy(lastDestination = defaults.lastDestination) == defaults
        synchronizedSettingsAreDefault &&
            snapshot.categories.isEmpty() && snapshot.todos.isEmpty() && snapshot.todoSeries.isEmpty() &&
            snapshot.ledgerEntries.isEmpty() && snapshot.ledgerSeries.isEmpty() &&
            snapshot.noteFolders.isEmpty() && snapshot.notes.isEmpty() &&
            snapshot.attachments.isEmpty() && snapshot.vaultEntries.isEmpty()
    } ?: true

    suspend fun mutate(transform: (BackupSnapshot) -> BackupSnapshot): Boolean = mutex.withLock {
        withContext(ioDispatcher) {
            val current = currentSnapshot() ?: return@withContext false
            val updated = transform(current).copy(createdAt = System.currentTimeMillis()).validateForDesktop()
            saveLocked(updated)
            _state.value = DesktopStoreState.Open(updated)
            val retainedIds = updated.attachments.mapTo(HashSet(), BackupAttachment::id)
            val removedFiles = attachmentFiles.filterKeys { it !in retainedIds }
            attachmentFiles = attachmentFiles.filterKeys { it in retainedIds }
            removedFiles.values.forEach(File::delete)
            true
        }
    }

    suspend fun upsertTodo(
        id: Long?,
        title: String,
        description: String,
        deadlineEpochDay: Long?,
        priority: TodoPriority,
        categoryId: Long?,
        tagsCsv: String,
        completed: Boolean,
    ) = mutate { snapshot ->
        val now = System.currentTimeMillis()
        val effectiveId = id ?: snapshot.todos.maxOfOrNull(TodoEntity::id)?.plus(1) ?: 1L
        val existing = snapshot.todos.firstOrNull { it.id == effectiveId }
        val effectiveTitle = title.ifBlank { description.lineSequence().firstOrNull().orEmpty().take(40) }
        val item = existing?.copy(
            title = effectiveTitle,
            description = description,
            categoryId = categoryId,
            deadlineEpochDay = deadlineEpochDay,
            deadlineMinute = existing.deadlineMinute.takeIf { deadlineEpochDay != null },
            priority = priority,
            tagsCsv = normalizeTags(tagsCsv),
            completedAt = when {
                completed && existing.completedAt == null -> now
                completed -> existing.completedAt
                else -> null
            },
            updatedAt = now,
        ) ?: TodoEntity(
            id = effectiveId,
            title = effectiveTitle,
            description = description,
            categoryId = categoryId,
            deadlineEpochDay = deadlineEpochDay,
            priority = priority,
            tagsCsv = normalizeTags(tagsCsv),
            completedAt = now.takeIf { completed },
            createdAt = now,
            updatedAt = now,
            customOrder = now,
        )
        snapshot.copy(todos = snapshot.todos.filterNot { it.id == effectiveId } + item)
    }

    suspend fun toggleTodo(id: Long) = mutate { snapshot ->
        val now = System.currentTimeMillis()
        snapshot.copy(todos = snapshot.todos.map { todo ->
            if (todo.id == id) todo.copy(
                completedAt = if (todo.completedAt == null) now else null,
                updatedAt = now,
            ) else todo
        })
    }

    suspend fun deleteTodo(id: Long) = mutate { snapshot ->
        val now = System.currentTimeMillis()
        snapshot.copy(todos = snapshot.todos.map { todo ->
            if (todo.id == id) todo.copy(deletedAt = now, updatedAt = now) else todo
        })
    }

    suspend fun addCategory(name: String, parentId: Long? = null) = mutate { snapshot ->
        val id = snapshot.categories.maxOfOrNull(CategoryEntity::id)?.plus(1) ?: 1L
        snapshot.copy(categories = snapshot.categories + CategoryEntity(id = id, name = name.trim(), parentId = parentId))
    }

    suspend fun setAccentColor(accentColor: AccentColor) = mutate { snapshot ->
        snapshot.copy(settings = snapshot.settings.copy(accentColor = accentColor))
    }

    suspend fun setThemeMode(themeMode: ThemeMode) = mutate { snapshot ->
        snapshot.copy(settings = snapshot.settings.copy(themeMode = themeMode))
    }

    suspend fun setWeekStart(weekStart: WeekStart) = mutate { snapshot ->
        snapshot.copy(settings = snapshot.settings.copy(weekStart = weekStart))
    }

    suspend fun setTimeFormat(timeFormat: TimeFormatOption) = mutate { snapshot ->
        snapshot.copy(settings = snapshot.settings.copy(timeFormat = timeFormat))
    }

    suspend fun setDateFormat(dateFormat: DateFormatOption) = mutate { snapshot ->
        snapshot.copy(settings = snapshot.settings.copy(dateFormat = dateFormat))
    }

    suspend fun upsertLedger(
        id: Long?,
        type: LedgerType,
        amountCents: Long,
        epochDay: Long,
        note: String,
        merchant: String,
        tagsCsv: String,
    ) = mutate { snapshot ->
        val now = System.currentTimeMillis()
        val effectiveId = id ?: snapshot.ledgerEntries.maxOfOrNull(LedgerEntryEntity::id)?.plus(1) ?: 1L
        val existing = snapshot.ledgerEntries.firstOrNull { it.id == effectiveId }
        val entry = existing?.copy(
            type = type,
            amountCents = amountCents.coerceIn(1, 99_999_999_999L),
            epochDay = epochDay,
            note = note,
            merchant = merchant,
            tagsCsv = normalizeTags(tagsCsv),
            updatedAt = now,
        ) ?: LedgerEntryEntity(
            id = effectiveId,
            type = type,
            amountCents = amountCents.coerceIn(1, 99_999_999_999L),
            epochDay = epochDay,
            minuteOfDay = LocalTime.now().let { it.hour * 60 + it.minute },
            note = note,
            merchant = merchant,
            tagsCsv = normalizeTags(tagsCsv),
            createdAt = now,
            updatedAt = now,
        )
        snapshot.copy(ledgerEntries = snapshot.ledgerEntries.filterNot { it.id == effectiveId } + entry)
    }

    suspend fun deleteLedger(id: Long) = mutate { snapshot ->
        val now = System.currentTimeMillis()
        snapshot.copy(ledgerEntries = snapshot.ledgerEntries.map { entry ->
            if (entry.id == id) entry.copy(deletedAt = now, updatedAt = now) else entry
        })
    }

    suspend fun upsertNote(
        id: Long?,
        folderId: Long?,
        title: String,
        body: String,
        pinned: Boolean,
    ) = mutate { snapshot ->
        val now = System.currentTimeMillis()
        val effectiveId = id ?: snapshot.notes.maxOfOrNull(NoteEntity::id)?.plus(1) ?: 1L
        val existing = snapshot.notes.firstOrNull { it.id == effectiveId }
        val note = NoteEntity(
            id = effectiveId,
            folderId = folderId,
            title = title.ifBlank { body.lineSequence().firstOrNull().orEmpty().take(60).ifBlank { "Untitled" } },
            body = body,
            pinned = pinned,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        snapshot.copy(notes = snapshot.notes.filterNot { it.id == effectiveId } + note)
    }

    suspend fun addNoteFolder(name: String, parentId: Long? = null) = mutate { snapshot ->
        val normalizedName = normalizeNoteFolderName(name)
        require(parentId == null || snapshot.noteFolders.any { it.id == parentId }) {
            "The parent folder no longer exists"
        }
        require(snapshot.noteFolders.none {
            it.parentId == parentId && it.name.equals(normalizedName, ignoreCase = true)
        }) { "A folder with this name already exists here" }
        val id = snapshot.noteFolders.maxOfOrNull(NoteFolderEntity::id)?.plus(1) ?: 1L
        snapshot.copy(noteFolders = snapshot.noteFolders + NoteFolderEntity(id = id, name = normalizedName, parentId = parentId))
    }

    suspend fun renameNoteFolder(id: Long, name: String) = mutate { snapshot ->
        require(id > 0) { "Folder id is invalid" }
        val folder = requireNotNull(snapshot.noteFolders.firstOrNull { it.id == id }) {
            "Folder no longer exists"
        }
        val normalizedName = normalizeNoteFolderName(name)
        require(snapshot.noteFolders.none {
            it.id != id && it.parentId == folder.parentId &&
                it.name.equals(normalizedName, ignoreCase = true)
        }) { "A folder with this name already exists here" }
        snapshot.copy(
            noteFolders = snapshot.noteFolders.map { candidate ->
                if (candidate.id == id) candidate.copy(name = normalizedName) else candidate
            },
        )
    }

    suspend fun deleteNoteFolder(id: Long) = mutate { snapshot ->
        require(id > 0) { "Folder id is invalid" }
        val folder = snapshot.noteFolders.firstOrNull { it.id == id }
            ?: return@mutate snapshot
        snapshot.copy(
            noteFolders = snapshot.noteFolders
                .filterNot { it.id == id }
                .map { candidate ->
                    if (candidate.parentId == id) candidate.copy(parentId = folder.parentId) else candidate
                },
            notes = snapshot.notes.map { note ->
                if (note.folderId == id) note.copy(folderId = null) else note
            },
        )
    }

    suspend fun deleteNote(id: Long) = mutate { snapshot ->
        snapshot.copy(
            notes = snapshot.notes.filterNot { it.id == id },
            attachments = snapshot.attachments.filterNot { it.ownerType == AttachmentOwnerType.NOTE && it.ownerId == id },
        )
    }

    suspend fun upsertVault(
        id: String?,
        label: String,
        account: String,
        password: String,
        website: String,
        notes: String,
    ) = mutate { snapshot ->
        val now = System.currentTimeMillis()
        val effectiveId = id ?: UUID.randomUUID().toString()
        val existing = snapshot.vaultEntries.firstOrNull { it.id == effectiveId }
        val entry = VaultEntry(
            id = effectiveId,
            label = label,
            account = account,
            password = password,
            website = website,
            notes = notes,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        snapshot.copy(vaultEntries = snapshot.vaultEntries.filterNot { it.id == effectiveId } + entry)
    }

    suspend fun deleteVault(id: String) = mutate { snapshot ->
        snapshot.copy(vaultEntries = snapshot.vaultEntries.filterNot { it.id == id })
    }

    suspend fun attachFile(ownerType: AttachmentOwnerType, ownerId: Long, source: File) = mutex.withLock {
        withContext(ioDispatcher) {
            val snapshot = currentSnapshot() ?: return@withContext false
            require(source.isFile && source.length() in 0..BackupLimits.MAX_ATTACHMENT_BYTES)
            require(snapshot.attachments.count { it.ownerType == ownerType && it.ownerId == ownerId } < 10)
            require(ownerExists(snapshot, ownerType, ownerId))
            val id = snapshot.attachments.maxOfOrNull(BackupAttachment::id)?.plus(1) ?: 1L
            val bytesHash = source.inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
                buffer.fill(0)
                digest.digest()
            }
            val targetDirectory = activeAttachmentDirectory ?: createAttachmentGeneration().also {
                activeAttachmentDirectory = it
            }
            val target = File(targetDirectory, attachmentWorkingName(id, source.name))
            source.copyTo(target, overwrite = true)
            val attachment = BackupAttachment(
                id = id,
                ownerType = ownerType,
                ownerId = ownerId,
                archivePath = attachmentArchivePath(id, bytesHash),
                originalName = source.name,
                mimeType = Files.probeContentType(source.toPath()) ?: "application/octet-stream",
                sizeBytes = source.length(),
                sha256 = bytesHash,
                createdAt = System.currentTimeMillis(),
                pendingDeleteAt = null,
            )
            val candidateFiles = attachmentFiles + (id to target)
            val updated = snapshot.copy(
                createdAt = System.currentTimeMillis(),
                attachments = snapshot.attachments + attachment,
            ).validateForDesktop()
            try {
                saveLocked(updated, candidateFiles)
                attachmentFiles = candidateFiles
                _state.value = DesktopStoreState.Open(updated)
                true
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
        }
    }

    suspend fun removeAttachment(id: Long): Boolean = mutate { snapshot ->
        snapshot.copy(attachments = snapshot.attachments.filterNot { it.id == id })
    }

    suspend fun verifyEncrypted(source: File): Boolean = mutex.withLock {
        withContext(ioDispatcher) {
            val secret = password?.copyOf() ?: return@withContext false
            var loaded: LoadedSnapshot? = null
            try {
                loaded = loadSnapshot(source, secret)
                true
            } catch (_: Throwable) {
                false
            } finally {
                loaded?.directory?.deleteRecursively()
                secret.fill('\u0000')
            }
        }
    }

    fun attachmentFile(id: Long): File? = attachmentFiles[id]?.takeIf(File::isFile)

    suspend fun createUploadSnapshot(): DesktopUploadSnapshot = mutex.withLock {
        withContext(ioDispatcher) {
            check(localBackup.isFile) { "Local encrypted data is unavailable." }
            workDirectory.mkdirs()
            val copy = File(workDirectory, "upload-${UUID.randomUUID()}.tlb")
            Files.copy(localBackup.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING)
            DesktopUploadSnapshot(copy, fingerprint(copy))
        }
    }

    /**
     * Capture the current encrypted local file for USE_CLOUD recovery while holding the store
     * lock. A null result means the local file changed since the caller's expected fingerprint.
     * The returned snapshot is durable and is never removed by sync or store cleanup.
     */
    suspend fun captureCloudRecovery(expectedLocalFingerprint: String): File? = mutex.withLock {
        withContext(ioDispatcher) {
            if (password == null || !localBackup.isFile || fingerprintLocked() != expectedLocalFingerprint) {
                return@withContext null
            }
            cloudRecovery.mkdirs()
            val temporary = File(cloudRecovery, ".recovery-${UUID.randomUUID()}.part")
            val recovery = File(
                cloudRecovery,
                "life-tracker-local-${System.currentTimeMillis()}-${UUID.randomUUID()}.tlb",
            )
            try {
                Files.copy(localBackup.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING)
                try {
                    Files.move(
                        temporary.toPath(),
                        recovery.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                } catch (_: Exception) {
                    Files.move(temporary.toPath(), recovery.toPath())
                }
                recovery
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }
    }

    suspend fun localFingerprint(): String = mutex.withLock {
        withContext(ioDispatcher) { fingerprintLocked() }
    }

    override fun close() {
        password?.fill('\u0000')
        password = null
        attachmentFiles = emptyMap()
        activeAttachmentDirectory?.deleteRecursively()
        activeAttachmentDirectory = null
        runCatching { instanceLock?.release() }
        runCatching { lockChannel?.close() }
        instanceLock = null
        lockChannel = null
        _state.value = DesktopStoreState.Locked
    }

    private fun loadSnapshot(source: File, password: CharArray): LoadedSnapshot {
        val decoded = source.inputStream().buffered().use {
            BackupCrypto.decrypt(it, password.copyOf(), workDirectory, workDirectory)
        }
        val staged = decoded.attachmentStage.commit()
        var directory: File? = null
        return try {
            val snapshot = decoded.snapshot.validateForDesktop()
            val entities = staged.entities(snapshot)
            val newDirectory = createAttachmentGeneration()
            directory = newDirectory
            val mapped = mutableMapOf<Long, File>()
            entities.forEach { entity ->
                val from = File(entity.privatePath)
                val target = File(newDirectory, attachmentWorkingName(entity.id, entity.originalName))
                from.copyTo(target, overwrite = true)
                mapped[entity.id] = target
            }
            LoadedSnapshot(snapshot, mapped, newDirectory)
        } catch (error: Throwable) {
            directory?.deleteRecursively()
            throw error
        } finally {
            staged.root.deleteRecursively()
        }
    }

    private fun saveLocked(
        snapshot: BackupSnapshot,
        sources: Map<Long, File> = attachmentFiles,
    ) {
        val secret = password?.copyOf() ?: error("Desktop data is locked")
        appDirectory.mkdirs()
        val temporary = File(appDirectory, "life-tracker-local.tlb.part")
        try {
            temporary.outputStream().buffered().use { output ->
                BackupCrypto.encrypt(
                    snapshot,
                    secret,
                    output,
                    BackupAttachmentSource { attachment ->
                        sources[attachment.id]?.inputStream()
                            ?: error("Attachment ${attachment.id} is missing")
                    },
                )
            }
            replaceFile(temporary, localBackup)
        } finally {
            secret.fill('\u0000')
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun adoptLoadedSnapshot(loaded: LoadedSnapshot) {
        val previousDirectory = activeAttachmentDirectory
        attachmentFiles = loaded.files
        activeAttachmentDirectory = loaded.directory
        _state.value = DesktopStoreState.Open(loaded.snapshot)
        if (previousDirectory != null && previousDirectory != loaded.directory) {
            previousDirectory.deleteRecursively()
        }
        attachmentsDirectory.deleteRecursively()
        cleanupStaleWorkingFiles(keep = loaded.directory)
    }

    private fun createAttachmentGeneration(): File {
        workDirectory.mkdirs()
        return File(workDirectory, "session-${UUID.randomUUID()}").also {
            check(it.mkdir()) { "Could not create the attachment directory." }
        }
    }

    private fun acquireInstanceLock(): Boolean {
        if (instanceLock?.isValid == true) return true
        val channel = RandomAccessFile(File(appDirectory, ".desktop.lock"), "rw").channel
        val acquired = try {
            channel.tryLock()
        } catch (_: Throwable) {
            null
        }
        if (acquired == null) {
            channel.close()
            return false
        }
        lockChannel = channel
        instanceLock = acquired
        return true
    }

    private fun cleanupStaleWorkingFiles(keep: File? = null) {
        workDirectory.listFiles()?.forEach { candidate ->
            if (candidate != keep && (
                    candidate.name.startsWith("session-") ||
                        candidate.name.startsWith("upload-") ||
                        candidate.name.startsWith("taskledger-restore-")
                    )
            ) {
                if (candidate.isDirectory) candidate.deleteRecursively() else candidate.delete()
            }
        }
    }

    private fun replaceFile(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun fingerprintLocked(): String = if (!localBackup.isFile) {
        "0".repeat(64)
    } else {
        fingerprint(localBackup)
    }

    private fun fingerprint(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        buffer.fill(0)
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun ownerExists(snapshot: BackupSnapshot, type: AttachmentOwnerType, id: Long): Boolean = when (type) {
        AttachmentOwnerType.TODO -> snapshot.todos.any { it.id == id && it.deletedAt == null }
        AttachmentOwnerType.LEDGER -> snapshot.ledgerEntries.any { it.id == id && it.deletedAt == null }
        AttachmentOwnerType.NOTE -> snapshot.notes.any { it.id == id }
    }

    private fun attachmentWorkingName(id: Long, originalName: String): String {
        val extension = originalName.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.length in 1..16 && it.all(Char::isLetterOrDigit) }
        return if (extension == null) "$id.bin" else "$id.$extension"
    }

    private fun normalizeTags(csv: String): String = csv.split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy(String::lowercase)
        .joinToString(",")

    private fun normalizeNoteFolderName(name: String): String = name.trim().also {
        require(it.isNotEmpty()) { "Folder name is required" }
        require(it.length <= 120) { "Folder name is too long" }
    }

    private fun BackupSnapshot.validateForDesktop(): BackupSnapshot = validate()

    companion object {
        fun defaultAppDirectory(): File {
            val base = System.getenv("APPDATA")?.takeIf(String::isNotBlank)
                ?: File(System.getProperty("user.home"), ".config").absolutePath
            return File(base, "Life Tracker")
        }

        fun defaultSnapshot(now: Long = System.currentTimeMillis()) = BackupSnapshot(
            createdAt = now,
            settings = BackupSettings(
                themeMode = ThemeMode.DARK,
                accentColor = AccentColor.TEAL,
                weekStart = WeekStart.SUNDAY,
                timeFormat = TimeFormatOption.HOUR_12,
                dateFormat = DateFormatOption.MONTH_DAY_YEAR,
                notificationsEnabled = false,
                defaultAllDayReminderMinute = 0,
                defaultReminderOffsetsMinutes = setOf(0L),
                todoQuickAddFields = emptySet(),
                lastDestination = TopLevelDestination.TODO,
            ),
            categories = emptyList(),
            todoSeries = emptyList(),
            todoSeriesSubtasks = emptyList(),
            todoOccurrenceExceptions = emptyList(),
            todos = emptyList(),
            subtasks = emptyList(),
            todoReminders = emptyList(),
            ledgerSeries = emptyList(),
            ledgerOccurrenceExceptions = emptyList(),
            ledgerEntries = emptyList(),
            attachments = emptyList(),
            vaultEntries = emptyList(),
            noteFolders = emptyList(),
            notes = emptyList(),
        )
    }

    private data class LoadedSnapshot(
        val snapshot: BackupSnapshot,
        val files: Map<Long, File>,
        val directory: File,
    )
}
