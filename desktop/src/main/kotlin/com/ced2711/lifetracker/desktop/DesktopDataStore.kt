package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.data.backup.BackupAttachment
import com.ced2711.lifetracker.data.backup.BackupAttachmentSource
import com.ced2711.lifetracker.data.backup.BackupCrypto
import com.ced2711.lifetracker.data.backup.BackupLimits
import com.ced2711.lifetracker.data.backup.BackupSettings
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.data.backup.MergeSide
import com.ced2711.lifetracker.data.backup.attachmentArchivePath
import com.ced2711.lifetracker.data.backup.mergeSnapshots
import com.ced2711.lifetracker.data.backup.validate
import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.data.local.DiaryEntryEntity
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.NoteEntity
import com.ced2711.lifetracker.data.local.NoteFolderEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.AppIdentity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.MAX_DIARY_LENGTH
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import com.ced2711.lifetracker.domain.model.TodoDraft
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.VaultEntry
import com.ced2711.lifetracker.domain.model.WeekStart
import com.ced2711.lifetracker.domain.model.normalizeCustomTodoOrders
import com.ced2711.lifetracker.domain.model.swapTodoIds
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

/** Local data merged with cloud versions, encrypted with the data password, not applied yet. */
data class DesktopMergedFile(val file: File, val fingerprint: String, val textConflicts: Int)

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

    /** USE_CLOUD recovery snapshots; the newest [KEPT_RECOVERY_COPIES] are kept. */
    val cloudRecoveryDirectory: File get() = cloudRecovery

    suspend fun open(password: CharArray, createIfMissing: Boolean = true): Boolean = mutex.withLock {
        withContext(ioDispatcher) {
            try {
                appDirectory.mkdirs()
                if (!acquireInstanceLock()) {
                    _state.value = DesktopStoreState.Error(DesktopPlatform.text("${AppIdentity.NAME} is already open on this Windows account.", "${AppIdentity.NAME} is already open for this user."))
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
            snapshot.diaryEntries.isEmpty() &&
            snapshot.attachments.isEmpty() && snapshot.vaultEntries.isEmpty()
    } ?: true

    suspend fun mutate(transform: (BackupSnapshot) -> BackupSnapshot): Boolean = mutex.withLock {
        withContext(ioDispatcher) {
            val current = currentSnapshot() ?: return@withContext false
            val updated = transform(current)
                .copy(formatVersion = BackupLimits.SNAPSHOT_VERSION, createdAt = System.currentTimeMillis())
                .validateForDesktop()
            saveLocked(updated)
            _state.value = DesktopStoreState.Open(updated)
            val retainedIds = updated.attachments.mapTo(HashSet(), BackupAttachment::id)
            val removedFiles = attachmentFiles.filterKeys { it !in retainedIds }
            attachmentFiles = attachmentFiles.filterKeys { it in retainedIds }
            removedFiles.values.forEach(File::delete)
            true
        }
    }

    /** Quick edit that keeps the todo's subtasks, reminders, time and series slot. */
    suspend fun upsertTodo(
        id: Long?,
        title: String,
        description: String,
        deadlineEpochDay: Long?,
        priority: TodoPriority,
        categoryId: Long?,
        tagsCsv: String,
        completed: Boolean,
    ): Boolean {
        val snapshot = currentSnapshot() ?: return false
        val existing = id?.let { todoId -> snapshot.todos.firstOrNull { it.id == todoId } }
        val draft = TodoDraft(
            id = existing?.id,
            title = title,
            description = description,
            categoryId = categoryId,
            deadlineEpochDay = deadlineEpochDay,
            deadlineMinute = existing?.deadlineMinute.takeIf { deadlineEpochDay != null },
            priority = priority,
            tags = tagsCsv.split(','),
            reminderOffsetsMinutes = existing?.let { todo -> snapshot.todoReminders.filter { it.todoId == todo.id }.map { it.offsetMinutes } }
                ?: snapshot.settings.defaultReminderOffsetsMinutes.toList().takeIf { deadlineEpochDay != null }.orEmpty(),
            subtasks = existing?.let { todo -> snapshot.subtasks.filter { it.todoId == todo.id }.sortedBy { it.sortOrder }.map { it.description } }.orEmpty(),
            // A quick edit of one occurrence keeps it in its series.
            recurrence = existing?.seriesId?.takeIf { deadlineEpochDay != null }?.let { seriesId ->
                snapshot.todoSeries.firstOrNull { it.id == seriesId }?.let { RecurrenceRule(it.recurrenceUnit, it.intervalCount, it.endEpochDay) }
            },
        )
        return saveTodo(draft, SeriesEditScope.ONLY_THIS_OCCURRENCE, completed) != null
    }

    /** Saves a todo with Android's rules (subtasks, reminders, recurrence); returns its id. */
    suspend fun saveTodo(draft: TodoDraft, scope: SeriesEditScope, completed: Boolean? = null): Long? {
        var savedId: Long? = null
        val saved = mutate { snapshot ->
            val now = System.currentTimeMillis()
            val (updated, id) = DesktopTodoOps.save(snapshot, draft, scope, completed, now)
            savedId = id
            DesktopTodoOps.materialize(updated, LocalDate.now().toEpochDay(), now)
        }
        return savedId.takeIf { saved }
    }

    suspend fun setTodoCompleted(id: Long, completed: Boolean, completeSubtasks: Boolean = false) = mutate { snapshot ->
        DesktopTodoOps.setCompleted(snapshot, id, completed, completeSubtasks, System.currentTimeMillis())
    }

    suspend fun setSubtaskCompleted(subtaskId: Long, completed: Boolean) = mutate { snapshot ->
        DesktopTodoOps.setSubtaskCompleted(snapshot, subtaskId, completed, System.currentTimeMillis())
    }

    /** Deletes a todo (or this and future occurrences); keep the result to offer Undo. */
    suspend fun deleteTodoWithUndo(id: Long, scope: SeriesEditScope = SeriesEditScope.ONLY_THIS_OCCURRENCE): TodoDeletion? {
        var deletion: TodoDeletion? = null
        val done = mutate { snapshot ->
            val (updated, result) = DesktopTodoOps.delete(snapshot, id, scope, System.currentTimeMillis())
            deletion = result
            updated
        }
        return deletion.takeIf { done }
    }

    /** Swaps a todo with its visible neighbour in the custom order, like the phone's move buttons. */
    suspend fun moveTodo(id: Long, neighborId: Long) = mutate { snapshot ->
        val ordered = snapshot.todos.filter { it.deletedAt == null && it.completedAt == null }
            .sortedWith(compareByDescending<TodoEntity> { it.customOrder }.thenByDescending { it.createdAt }.thenByDescending { it.id })
            .map(TodoEntity::id)
        val reordered = swapTodoIds(ordered, id, neighborId)
        if (reordered == ordered) return@mutate snapshot
        val now = System.currentTimeMillis()
        val orders = normalizeCustomTodoOrders(reordered).toMap()
        snapshot.copy(todos = snapshot.todos.map { todo ->
            orders[todo.id]?.takeIf { it != todo.customOrder }?.let { todo.copy(customOrder = it, updatedAt = maxOf(now, todo.updatedAt)) } ?: todo
        })
    }

    suspend fun undoDeleteTodo(deletion: TodoDeletion) = mutate { snapshot ->
        DesktopTodoOps.undoDelete(snapshot, deletion, System.currentTimeMillis())
    }

    suspend fun stopTodoSeries(seriesId: Long) = mutate { snapshot ->
        DesktopTodoOps.stopTodoSeries(snapshot, seriesId, System.currentTimeMillis())
    }

    /**
     * Creates due recurring todos and ledger entries and clears deletions whose undo window has
     * passed. Runs when the data opens and from time to time while the app is open. Optional
     * [planningThroughEpochDay] also creates future todos for a calendar view, like the phone.
     */
    suspend fun runMaintenance(planningThroughEpochDay: Long? = null): Boolean {
        val snapshot = currentSnapshot() ?: return false
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        val updated = DesktopTodoOps.purgeDeleted(
            DesktopTodoOps.materialize(snapshot, today, now, planningThroughEpochDay ?: today),
            now - DesktopTodoOps.UNDO_WINDOW_MILLIS,
        )
        if (updated == snapshot) return false
        return mutate { current ->
            DesktopTodoOps.purgeDeleted(
                DesktopTodoOps.materialize(current, today, now, planningThroughEpochDay ?: today),
                now - DesktopTodoOps.UNDO_WINDOW_MILLIS,
            )
        }
    }

    suspend fun toggleTodo(id: Long) = mutate { snapshot ->
        val todo = snapshot.todos.firstOrNull { it.id == id } ?: return@mutate snapshot
        DesktopTodoOps.setCompleted(snapshot, id, todo.completedAt == null, completeSubtasks = false, now = System.currentTimeMillis())
    }

    suspend fun deleteTodo(id: Long) = deleteTodoWithUndo(id) != null

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

    /** Saves the page for [epochDay]; a blank body removes it. */
    suspend fun upsertDiary(epochDay: Long, body: String) = mutate { snapshot ->
        require(body.length <= MAX_DIARY_LENGTH) { "Diary entry is too long" }
        val existing = snapshot.diaryEntries.firstOrNull { it.epochDay == epochDay }
        val others = snapshot.diaryEntries.filterNot { it.epochDay == epochDay }
        when {
            body.isBlank() -> snapshot.copy(diaryEntries = others)
            existing?.body == body -> snapshot
            else -> {
                val now = System.currentTimeMillis()
                val entry = existing?.copy(body = body, updatedAt = maxOf(now, existing.createdAt))
                    ?: DiaryEntryEntity(
                        id = snapshot.diaryEntries.maxOfOrNull(DiaryEntryEntity::id)?.plus(1) ?: 1L,
                        epochDay = epochDay,
                        body = body,
                        createdAt = now,
                        updatedAt = now,
                    )
                snapshot.copy(diaryEntries = others + entry)
            }
        }
    }

    suspend fun deleteDiary(epochDay: Long) = mutate { snapshot ->
        snapshot.copy(diaryEntries = snapshot.diaryEntries.filterNot { it.epochDay == epochDay })
    }

    /** Constant-time check of the open data password, used by the optional app lock. */
    fun verifyPassword(candidate: CharArray): Boolean {
        val current = password
        try {
            if (current == null) return false
            var difference = current.size xor candidate.size
            for (index in current.indices) {
                difference = difference or (current[index].code xor candidate.getOrElse(index) { '\u0000' }.code)
            }
            return difference == 0
        } finally {
            candidate.fill('\u0000')
        }
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
                formatVersion = BackupLimits.SNAPSHOT_VERSION,
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
                pruneRecoveryCopies(cloudRecovery, keep = KEPT_RECOVERY_COPIES)
                recovery
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }
    }

    /**
     * Merges each of [remotes] into the current local data, using [base] (the last version both
     * sides agreed on, or null when unknown) to tell edits from deletions. The result goes to a new
     * encrypted file that [replaceFromEncrypted] applies once it is safely in the cloud. Returns
     * null when local data no longer matches [expectedLocalFingerprint].
     */
    suspend fun createMergedFile(
        base: File?,
        remotes: List<File>,
        expectedLocalFingerprint: String,
        now: Long = System.currentTimeMillis(),
    ): DesktopMergedFile? = mutex.withLock {
        withContext(ioDispatcher) {
            val secret = password?.copyOf() ?: error("Desktop data is locked")
            val loaded = ArrayList<LoadedSnapshot>()
            try {
                if (fingerprintLocked() != expectedLocalFingerprint) return@withContext null
                val baseLoaded = base?.let { file -> runCatching { loadSnapshot(file, secret) }.getOrNull() }?.also(loaded::add)
                var merged = requireNotNull(currentSnapshot()) { "Desktop data is locked" }
                var files: Map<Long, File> = attachmentFiles
                var textConflicts = 0
                remotes.forEach { file ->
                    val remote = loadSnapshot(file, secret).also(loaded::add)
                    val result = mergeSnapshots(baseLoaded?.snapshot, merged, remote.snapshot, now)
                    val previousFiles = files
                    files = result.attachmentSources.mapValues { (id, source) ->
                        when (source.side) {
                            MergeSide.LOCAL -> previousFiles[source.originalId]
                            MergeSide.REMOTE -> remote.files[source.originalId]
                            MergeSide.BASE -> baseLoaded?.files?.get(source.originalId)
                        } ?: error("Attachment $id is missing")
                    }
                    merged = result.snapshot
                    textConflicts += result.textConflicts
                }
                workDirectory.mkdirs()
                val target = File(workDirectory, "merged-${UUID.randomUUID()}.tlb")
                try {
                    target.outputStream().buffered().use { output ->
                        BackupCrypto.encrypt(
                            merged,
                            secret.copyOf(),
                            output,
                            BackupAttachmentSource { attachment ->
                                files[attachment.id]?.inputStream() ?: error("Attachment ${attachment.id} is missing")
                            },
                        )
                    }
                } catch (error: Throwable) {
                    target.delete()
                    throw error
                }
                DesktopMergedFile(target, fingerprint(target), textConflicts)
            } finally {
                secret.fill('\u0000')
                loaded.forEach { it.directory.deleteRecursively() }
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
        const val KEPT_RECOVERY_COPIES = 5

        /** Deletes all but the [keep] newest recovery copies in [directory]. */
        fun pruneRecoveryCopies(directory: File, keep: Int) {
            directory.listFiles().orEmpty()
                .filter { it.isFile && it.name.endsWith(".tlb") }
                .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
                .drop(keep)
                .forEach(File::delete)
        }

        fun defaultAppDirectory(): File = DesktopPlatform.appDirectory()

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
