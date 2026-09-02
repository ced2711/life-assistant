package com.ced2711.lifetracker.data.repository

import android.os.SystemClock
import androidx.room.withTransaction
import com.ced2711.lifetracker.data.monotonicMutationTimestamp
import com.ced2711.lifetracker.data.persistedDeadlineTimestamp
import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.LedgerOccurrenceExceptionEntity
import com.ced2711.lifetracker.data.local.LedgerSeriesEntity
import com.ced2711.lifetracker.data.local.NoteEntity
import com.ced2711.lifetracker.data.local.NoteFolderEntity
import com.ced2711.lifetracker.data.local.SubtaskEntity
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.local.TodoOccurrenceExceptionEntity
import com.ced2711.lifetracker.data.local.TodoReminderEntity
import com.ced2711.lifetracker.data.local.TodoSeriesEntity
import com.ced2711.lifetracker.data.local.TodoSeriesSubtaskEntity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerSaveResult
import com.ced2711.lifetracker.domain.model.NoteDraft
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurringDeleteResult
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import com.ced2711.lifetracker.domain.model.TodoDraft
import com.ced2711.lifetracker.domain.model.deriveTodoTitle
import com.ced2711.lifetracker.domain.model.deleteTodoTagCsv
import com.ced2711.lifetracker.domain.model.normalizeTags
import com.ced2711.lifetracker.domain.model.normalizeCustomTodoOrders
import com.ced2711.lifetracker.domain.model.renameTodoTagCsv
import com.ced2711.lifetracker.domain.model.requireValidLedgerAmount
import com.ced2711.lifetracker.domain.model.swapTodoIds
import com.ced2711.lifetracker.domain.recurrence.RecurrenceEngine
import java.time.LocalDate
import java.util.ArrayDeque
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class DeleteUndoResult {
    RESTORED,
    EXPIRED,
    UNAVAILABLE,
}

data class RecurrenceCatchUpResult(
    val insertedOccurrences: Int,
    val hasMore: Boolean,
    val nextCursor: String? = null,
)

internal fun isUndoWindowOpen(expiresAtMillis: Long, nowMillis: Long): Boolean =
    nowMillis < expiresAtMillis

internal fun validateExistingLedgerUpdate(
    existing: LedgerEntryEntity,
    draft: LedgerDraft,
    todayEpochDay: Long,
) {
    check(existing.deletedAt == null) { "Ledger entry is already deleted" }
    require(
        existing.seriesId != null ||
            draft.recurrence == null ||
            draft.epochDay <= todayEpochDay,
    ) {
        "An existing entry cannot be converted to a recurring rule that starts after today"
    }
}

class TaskLedgerRepository(
    private val database: TaskLedgerDatabase,
    private val maxOccurrencesPerCatchUp: Int = MAX_OCCURRENCES_PER_CATCH_UP,
    private val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.dao()
    private val recurrenceMutex = Mutex()

    init {
        require(maxOccurrencesPerCatchUp > 0) { "Catch-up budget must be positive" }
    }

    val activeTodos = dao.observeActiveTodos()
    val completedTodos = dao.observeCompletedTodos()
    val categories = dao.observeCategories()
    val ledgerEntries = dao.observeLedgerEntries()
    val todoSeries = dao.observeTodoSeries()
    val ledgerSeries = dao.observeLedgerSeries()
    val noteFolders = dao.observeNoteFolders()
    val notes = dao.observeNotes()

    fun activeTodosForDeadlineDay(epochDay: Long) =
        dao.observeActiveTodosForDeadlineDay(epochDay)

    fun todosInRange(startEpochDay: Long, endEpochDay: Long) =
        dao.observeTodosInRange(startEpochDay, endEpochDay)

    fun ledgerEntriesInRange(startEpochDay: Long, endEpochDay: Long) =
        dao.observeLedgerEntriesInRange(startEpochDay, endEpochDay)

    fun dailyLedgerTotals(startEpochDay: Long, endEpochDay: Long) =
        dao.observeDailyLedgerTotals(startEpochDay, endEpochDay)

    fun subtasks(todoId: Long) = dao.observeSubtasks(todoId)
    fun reminders(todoId: Long) = dao.observeReminders(todoId)
    fun attachments(ownerType: AttachmentOwnerType, ownerId: Long) =
        dao.observeAttachments(ownerType.name, ownerId)

    suspend fun getAttachments(ownerType: AttachmentOwnerType, ownerId: Long) =
        dao.getAttachments(ownerType.name, ownerId)

    suspend fun attachmentOwnerExists(ownerType: AttachmentOwnerType, ownerId: Long): Boolean =
        when (ownerType) {
            AttachmentOwnerType.TODO -> dao.activeTodoExists(ownerId)
            AttachmentOwnerType.LEDGER -> dao.activeLedgerEntryExists(ownerId)
            AttachmentOwnerType.NOTE -> dao.activeNoteExists(ownerId)
        }

    suspend fun addNoteFolder(name: String, parentId: Long? = null): Long =
        database.withTransaction {
            val normalizedName = name.trim()
            require(normalizedName.isNotEmpty()) { "Folder name is required" }
            require(normalizedName.length <= 120) { "Folder name is too long" }
            val folders = dao.getNoteFolders()
            require(parentId == null || folders.any { it.id == parentId }) {
                "The parent folder no longer exists"
            }
            require(
                folders.none {
                    it.parentId == parentId && it.name.equals(normalizedName, ignoreCase = true)
                },
            ) { "A folder with this name already exists here" }
            val nextOrder = folders.filter { it.parentId == parentId }
                .maxOfOrNull(NoteFolderEntity::sortOrder)
                ?.plus(1L)
                ?: 0L
            dao.insertNoteFolder(
                NoteFolderEntity(
                    name = normalizedName,
                    parentId = parentId,
                    sortOrder = nextOrder,
                    createdAt = monotonicMutationTimestamp(wallClockMillis()),
                ),
            )
        }

    suspend fun renameNoteFolder(folderId: Long, name: String) = database.withTransaction {
        val folder = requireNotNull(dao.getNoteFolder(folderId)) { "Folder no longer exists" }
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Folder name is required" }
        require(normalizedName.length <= 120) { "Folder name is too long" }
        require(
            dao.getNoteFolders().none {
                it.id != folderId &&
                    it.parentId == folder.parentId &&
                    it.name.equals(normalizedName, ignoreCase = true)
            },
        ) { "A folder with this name already exists here" }
        dao.updateNoteFolder(folder.copy(name = normalizedName))
    }

    suspend fun deleteNoteFolder(folderId: Long) =
        database.withTransaction { dao.deleteNoteFolderAndPromote(folderId) }

    suspend fun getNote(noteId: Long): NoteEntity? = dao.getNote(noteId)

    suspend fun saveNote(draft: NoteDraft): Long = database.withTransaction {
        require(draft.title.length <= 500) { "Note title is too long" }
        require(draft.body.length <= 1_000_000) { "Note is too long" }
        require(draft.folderId == null || dao.getNoteFolder(draft.folderId) != null) {
            "The selected folder no longer exists"
        }
        val title = draft.title.trim().ifEmpty {
            draft.body.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty)
                ?.let { firstLine ->
                    if (firstLine.length <= 60) firstLine else firstLine.take(59).trimEnd() + "…"
                }
                ?: "Untitled note"
        }
        if (draft.id == null) {
            val now = monotonicMutationTimestamp(wallClockMillis())
            dao.insertNote(
                NoteEntity(
                    folderId = draft.folderId,
                    title = title,
                    body = draft.body,
                    pinned = draft.pinned,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            val existing = requireNotNull(dao.getNote(draft.id)) { "Note no longer exists" }
            val updatedAt = monotonicMutationTimestamp(
                wallClockMillis(),
                existing.createdAt,
                existing.updatedAt,
            )
            dao.updateNote(
                existing.copy(
                    folderId = draft.folderId,
                    title = title,
                    body = draft.body,
                    pinned = draft.pinned,
                    updatedAt = updatedAt,
                ),
            )
            existing.id
        }
    }

    suspend fun deleteNote(noteId: Long) = database.withTransaction {
        requireNotNull(dao.getNote(noteId)) { "Note no longer exists" }
        dao.markActiveOwnerAttachmentsForDeletion(
            AttachmentOwnerType.NOTE.name,
            noteId,
            persistedDeadlineTimestamp(
                wallClockMillis(),
                DEFAULT_UNDO_WINDOW,
                dao.getMaxAttachmentCreatedAt(AttachmentOwnerType.NOTE.name, listOf(noteId)),
            ),
        )
        dao.deleteNoteById(noteId)
    }

    suspend fun saveTodo(
        draft: TodoDraft,
        scope: SeriesEditScope = SeriesEditScope.ONLY_THIS_OCCURRENCE,
        clientOperationToken: String? = null,
    ): Long = database.withTransaction {
        require(clientOperationToken == null || draft.id == null) {
            "Client operation tokens are only valid for new tasks"
        }
        var saveDraft = draft
        var resolvedCreationOwner = false
        clientOperationToken?.let { token ->
            val priorOwner = dao.getTodoByClientOperationToken(token)
            if (priorOwner != null && priorOwner.deletedAt == null) {
                saveDraft = draft.copy(id = priorOwner.id)
                resolvedCreationOwner = true
            } else if (priorOwner != null) {
                dao.clearTodoClientOperationToken(priorOwner.id, token)
            }
        }
        require(saveDraft.description.isNotBlank()) { "Description is required" }
        require(saveDraft.deadlineMinute == null || saveDraft.deadlineEpochDay != null) { "A time requires a date" }
        require(saveDraft.deadlineMinute == null || saveDraft.deadlineMinute in 0..1_439) { "Invalid time" }
        require(saveDraft.recurrence == null || saveDraft.deadlineEpochDay != null) { "Repeating todos require a deadline" }
        require(saveDraft.recurrence == null || saveDraft.recurrence.interval > 0) {
            "Recurrence interval must be positive"
        }
        require(saveDraft.reminderOffsetsMinutes.all { it >= 0 }) { "Reminder offsets must not be negative" }
        require(
            saveDraft.recurrence?.endEpochDay == null ||
                saveDraft.recurrence.endEpochDay >= saveDraft.deadlineEpochDay!!,
        ) { "Repeat end date cannot be before its first occurrence" }

        val wallClock = wallClockMillis()
        val title = saveDraft.title.trim().ifEmpty { deriveTodoTitle(saveDraft.description) }
        val tagsCsv = normalizeTags(saveDraft.tags)
        val existing = saveDraft.id?.let { dao.getTodo(it) }
        val subtaskDescriptions = normalizeSubtaskDescriptions(saveDraft.subtasks)
        val oldSeriesId = existing?.seriesId
        val oldOccurrenceEpochDay = existing?.occurrenceEpochDay
        val oldSeries = oldSeriesId?.let { dao.getTodoSeries(it) }

        if (
            resolvedCreationOwner &&
            oldSeriesId != null &&
            saveDraft.recurrence != null &&
            todoSeriesMatchesCreationRequest(
                series = requireNotNull(oldSeries),
                draft = saveDraft,
                title = title,
                tagsCsv = tagsCsv,
                subtaskDescriptions = subtaskDescriptions,
            )
        ) {
            return@withTransaction requireNotNull(existing).id
        }

        val affectedGeneratedIds = when {
            resolvedCreationOwner && oldSeriesId != null ->
                dao.getCleanableTodoOccurrenceIdsForSeriesReplacement(
                    seriesId = oldSeriesId,
                    ownerId = requireNotNull(existing).id,
                    fromEpochDay = LocalDate.now().toEpochDay(),
                    ownerType = AttachmentOwnerType.TODO.name,
                )

            oldSeriesId != null &&
                scope == SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES ->
                dao.getActiveTodoOccurrenceIdsFrom(
                    oldSeriesId,
                    requireNotNull(oldOccurrenceEpochDay) + 1,
                )

            else -> emptyList()
        }
        val now = if (existing == null && oldSeries == null) {
            wallClock
        } else {
            monotonicMutationTimestamp(
                wallClock,
                existing?.createdAt,
                existing?.updatedAt,
                oldSeries?.createdAt,
                oldSeries?.updatedAt,
                maxTodoMutationPredecessor(affectedGeneratedIds),
            )
        }

        val seriesId: Long?
        val occurrenceEpochDay: Long?
        when {
            resolvedCreationOwner && oldSeriesId != null -> {
                dao.deactivateTodoSeries(oldSeriesId, now)
                cleanGeneratedTodoOccurrencesForSeriesReplacement(
                    seriesId = oldSeriesId,
                    ownerId = requireNotNull(existing).id,
                    fromEpochDay = LocalDate.now().toEpochDay(),
                    deletedAt = now,
                    knownAffectedIds = affectedGeneratedIds,
                )
                seriesId = saveDraft.recurrence?.let {
                    insertTodoSeries(saveDraft, title, tagsCsv, subtaskDescriptions, now)
                }
                occurrenceEpochDay = seriesId?.let { saveDraft.deadlineEpochDay }
            }

            oldSeriesId != null && scope == SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES -> {
                val boundary = requireNotNull(oldOccurrenceEpochDay) {
                    "A repeating task must retain its occurrence identity"
                }
                if (saveDraft.recurrence != null) {
                    require(requireNotNull(saveDraft.deadlineEpochDay) >= boundary) {
                        "The replacement series cannot start before the edited occurrence"
                    }
                }
                dao.deactivateTodoSeries(oldSeriesId, now)
                softDeleteTodoSeriesTail(
                    seriesId = oldSeriesId,
                    fromEpochDay = boundary + 1,
                    deletedAt = now,
                    attachmentsPendingAt = persistedDeadlineTimestamp(
                        wallClock,
                        DEFAULT_UNDO_WINDOW,
                        maxAttachmentCreatedAt(
                            AttachmentOwnerType.TODO,
                            affectedGeneratedIds,
                        ),
                    ),
                    knownAffectedIds = affectedGeneratedIds,
                )
                seriesId = saveDraft.recurrence?.let {
                    insertTodoSeries(saveDraft, title, tagsCsv, subtaskDescriptions, now)
                }
                occurrenceEpochDay = seriesId?.let { saveDraft.deadlineEpochDay }
            }

            oldSeriesId != null && saveDraft.recurrence == null -> {
                val occurrence = requireNotNull(oldOccurrenceEpochDay) {
                    "A repeating task must retain its occurrence identity"
                }
                dao.insertTodoOccurrenceException(
                    TodoOccurrenceExceptionEntity(oldSeriesId, occurrence, now),
                )
                seriesId = null
                occurrenceEpochDay = null
            }

            oldSeriesId != null -> {
                // An occurrence key identifies the slot in its series, not its user-editable date.
                seriesId = oldSeriesId
                occurrenceEpochDay = requireNotNull(oldOccurrenceEpochDay) {
                    "A repeating task must retain its occurrence identity"
                }
            }

            saveDraft.recurrence != null -> {
                seriesId = insertTodoSeries(saveDraft, title, tagsCsv, subtaskDescriptions, now)
                occurrenceEpochDay = saveDraft.deadlineEpochDay
            }

            else -> {
                seriesId = null
                occurrenceEpochDay = null
            }
        }

        val entity = TodoEntity(
            id = existing?.id ?: 0,
            seriesId = seriesId,
            occurrenceEpochDay = occurrenceEpochDay,
            title = title,
            description = saveDraft.description.trim(),
            categoryId = saveDraft.categoryId,
            deadlineEpochDay = saveDraft.deadlineEpochDay,
            deadlineMinute = saveDraft.deadlineMinute,
            priority = saveDraft.priority,
            tagsCsv = tagsCsv,
            completedAt = existing?.completedAt,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            customOrder = existing?.customOrder ?: now,
            deletedAt = existing?.deletedAt,
            clientOperationToken = existing?.clientOperationToken ?: clientOperationToken,
        )

        val todoId = if (existing == null) {
            dao.insertTodo(entity).also { require(it > 0) { "This occurrence already exists" } }
        } else {
            dao.updateTodo(entity)
            entity.id
        }

        val existingSubtasks = if (existing != null) dao.getSubtasks(todoId) else emptyList()
        dao.deleteSubtasksForTodo(todoId)
        val subtasks = mergeSubtasks(todoId, subtaskDescriptions, existingSubtasks)
        if (subtasks.isNotEmpty()) dao.insertSubtasks(subtasks)

        dao.deleteRemindersForTodo(todoId)
        if (saveDraft.deadlineEpochDay != null) {
            val reminders = saveDraft.reminderOffsetsMinutes
                .distinct()
                .map { offset -> TodoReminderEntity(todoId = todoId, offsetMinutes = offset) }
            if (reminders.isNotEmpty()) dao.insertReminders(reminders)
        }
        todoId
    }

    suspend fun completeTodo(todoId: Long, completeSubtasks: Boolean) = database.withTransaction {
        val todo = requireNotNull(dao.getTodo(todoId)) { "Task does not exist" }
        val now = monotonicMutationTimestamp(
            wallClockMillis(),
            todo.createdAt,
            todo.updatedAt,
            todo.completedAt,
        )
        dao.setTodoCompleted(todoId, now, now)
        if (completeSubtasks) dao.setAllSubtasksCompleted(todoId, true)
    }

    suspend fun restoreTodo(todoId: Long) = database.withTransaction {
        val todo = requireNotNull(dao.getTodo(todoId)) { "Task does not exist" }
        val now = monotonicMutationTimestamp(
            wallClockMillis(),
            todo.createdAt,
            todo.updatedAt,
            todo.completedAt,
        )
        dao.setTodoCompleted(todoId, null, now)
    }

    suspend fun setSubtaskCompleted(subtask: SubtaskEntity, completed: Boolean) =
        dao.updateSubtask(subtask.copy(isCompleted = completed))

    /** Swaps two rows in the complete custom order, then compacts ranks to deterministic values. */
    suspend fun moveTodoRelativeToVisibleNeighbor(todoId: Long, adjacentVisibleTodoId: Long) =
        database.withTransaction {
            val orderedIds = dao.getActiveTodosInCustomOrder().map(TodoEntity::id)
            val reorderedIds = swapTodoIds(orderedIds, todoId, adjacentVisibleTodoId)
            if (reorderedIds == orderedIds) return@withTransaction
            normalizeCustomTodoOrders(reorderedIds).forEach { (id, customOrder) ->
                dao.updateTodoCustomOrder(id, customOrder)
            }
        }

    /** Renames exact Todo tags in existing rows and recurrence templates as one atomic operation. */
    suspend fun renameTodoTag(sourceTag: String, replacementTag: String) {
        renameTodoTagCsv("", sourceTag, replacementTag)
        database.withTransaction {
            updateTodoTagCsv { csv -> renameTodoTagCsv(csv, sourceTag, replacementTag) }
        }
    }

    /** Deletes an exact Todo tag from existing rows and recurrence templates atomically. */
    suspend fun deleteTodoTag(tag: String) {
        deleteTodoTagCsv("", tag)
        database.withTransaction {
            updateTodoTagCsv { csv -> deleteTodoTagCsv(csv, tag) }
        }
    }

    private suspend fun updateTodoTagCsv(transform: (String) -> String) {
        val now = wallClockMillis()
        dao.getAllTodosForTagMaintenance().forEach { todo ->
            val tagsCsv = transform(todo.tagsCsv)
            if (tagsCsv != todo.tagsCsv) {
                dao.updateTodoTags(
                    id = todo.id,
                    tagsCsv = tagsCsv,
                    updatedAt = monotonicMutationTimestamp(now, todo.createdAt, todo.updatedAt),
                )
            }
        }
        dao.getAllTodoSeriesForTagMaintenance().forEach { series ->
            val tagsCsv = transform(series.tagsCsv)
            if (tagsCsv != series.tagsCsv) {
                dao.updateTodoSeriesTags(
                    id = series.id,
                    tagsCsv = tagsCsv,
                    updatedAt = monotonicMutationTimestamp(now, series.createdAt, series.updatedAt),
                )
            }
        }
    }

    suspend fun softDeleteTodo(
        todoId: Long,
        undoWindowMillis: Long = DEFAULT_UNDO_WINDOW,
    ): Long = deleteTodo(todoId, SeriesEditScope.ONLY_THIS_OCCURRENCE, undoWindowMillis)

    /**
     * Soft-deletes one generated slot or atomically stops and deletes this-and-future slots.
     * The returned timestamp is the attachment deletion token accepted by [undoDeleteTodo].
     */
    suspend fun deleteTodo(
        todoId: Long,
        scope: SeriesEditScope,
        undoWindowMillis: Long = DEFAULT_UNDO_WINDOW,
    ): Long = deleteTodoWithResult(todoId, scope, undoWindowMillis).attachmentsPendingAt

    suspend fun deleteTodoWithResult(
        todoId: Long,
        scope: SeriesEditScope,
        undoWindowMillis: Long = DEFAULT_UNDO_WINDOW,
    ): RecurringDeleteResult = database.withTransaction {
        require(undoWindowMillis >= 0) { "Undo window must not be negative" }
        val todo = requireNotNull(dao.getTodo(todoId)) { "Task does not exist" }
        check(todo.deletedAt == null) { "Task is already deleted" }
        val seriesId = todo.seriesId
        val occurrenceEpochDay = todo.occurrenceEpochDay
        val series = seriesId?.let { dao.getTodoSeries(it) }
        val deletionWallClock = wallClockMillis()
        val isTailDeletion =
            scope == SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES &&
                seriesId != null &&
                occurrenceEpochDay != null
        val affectedIds = if (isTailDeletion) {
            dao.getActiveTodoOccurrenceIdsFrom(
                requireNotNull(seriesId),
                requireNotNull(occurrenceEpochDay),
            )
        } else {
            listOf(todoId)
        }
        val now = monotonicMutationTimestamp(
            deletionWallClock,
            todo.createdAt,
            todo.updatedAt,
            series?.createdAt,
            series?.updatedAt,
            maxTodoMutationPredecessor(affectedIds),
        )
        val attachmentsPendingAt = uniqueOwnerAttachmentDeletionDeadline(
            ownerType = AttachmentOwnerType.TODO,
            ownerIds = affectedIds,
            minimumDeadline = persistedDeadlineTimestamp(
                deletionWallClock,
                undoWindowMillis,
                maxAttachmentCreatedAt(AttachmentOwnerType.TODO, affectedIds),
            ),
        )
        val undoExpiresAtElapsedRealtime = undoDeadline(elapsedRealtimeMillis(), undoWindowMillis)
        val seriesWasActive = series?.active ?: false

        val exceptionCreated = if (seriesId != null && occurrenceEpochDay != null) {
            dao.insertTodoOccurrenceException(
                TodoOccurrenceExceptionEntity(seriesId, occurrenceEpochDay, now),
            ) > 0
        } else false

        if (isTailDeletion) {
            val tailSeriesId = requireNotNull(seriesId)
            val tailBoundary = requireNotNull(occurrenceEpochDay)
            dao.deactivateTodoSeries(tailSeriesId, now)
            softDeleteTodoSeriesTail(
                tailSeriesId,
                tailBoundary,
                now,
                attachmentsPendingAt,
                affectedIds,
            )
        } else {
            check(dao.softDeleteTodo(todoId, now) == 1) { "Task deletion state changed" }
            dao.markActiveOwnerAttachmentsForDeletion(
                AttachmentOwnerType.TODO.name,
                todoId,
                attachmentsPendingAt,
            )
        }
        RecurringDeleteResult(
            ownerType = AttachmentOwnerType.TODO,
            itemId = todoId,
            scope = scope,
            seriesId = seriesId,
            boundaryEpochDay = occurrenceEpochDay,
            seriesWasActive = seriesWasActive,
            seriesUpdatedAt = if (isTailDeletion) now else series?.updatedAt,
            deletedAt = now,
            undoExpiresAtElapsedRealtime = undoExpiresAtElapsedRealtime,
            attachmentsPendingAt = attachmentsPendingAt,
            exceptionCreated = exceptionCreated,
        )
    }

    /** Restores the complete tail affected by [deleteTodoWithResult], not just its first row. */
    suspend fun undoDeleteTodo(
        result: RecurringDeleteResult,
        nowElapsedRealtimeMillis: Long = elapsedRealtimeMillis(),
    ): DeleteUndoResult = database.withTransaction {
        require(result.ownerType == AttachmentOwnerType.TODO) { "Not a task deletion receipt" }
        if (!isUndoWindowOpen(result.undoExpiresAtElapsedRealtime, nowElapsedRealtimeMillis)) {
            return@withTransaction DeleteUndoResult.EXPIRED
        }
        val seriesId = result.seriesId
        val boundary = result.boundaryEpochDay
        val isTailUndo =
            result.scope == SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES &&
                seriesId != null &&
                boundary != null &&
                result.seriesUpdatedAt != null
        val restoredIds = if (isTailUndo) {
            dao.getTodoOccurrenceIdsDeletedAtFrom(
                requireNotNull(seriesId),
                requireNotNull(boundary),
                result.deletedAt,
            )
        } else {
            listOf(result.itemId)
        }
        val currentSeries = seriesId?.let { dao.getTodoSeries(it) }
        val now = monotonicMutationTimestamp(
            wallClockMillis(),
            result.deletedAt,
            result.seriesUpdatedAt,
            currentSeries?.createdAt,
            currentSeries?.updatedAt,
            maxTodoMutationPredecessor(restoredIds),
        )
        var undoApplied = false
        if (isTailUndo) {
            if (
                restoredIds.isNotEmpty() &&
                dao.restoreTodoSeriesStateIfUpdatedAt(
                    seriesId = requireNotNull(seriesId),
                    active = result.seriesWasActive,
                    expectedUpdatedAt = requireNotNull(result.seriesUpdatedAt),
                    updatedAt = now,
                ) == 1
            ) {
                val restoredCount = dao.restoreTodoOccurrencesDeletedAtFrom(
                    requireNotNull(seriesId),
                    requireNotNull(boundary),
                    result.deletedAt,
                    now,
                )
                check(restoredCount == restoredIds.size) { "Task deletion state changed during Undo" }
                restoredIds.forEach { todoId ->
                    dao.undoOwnerAttachmentsMarkedAt(
                        AttachmentOwnerType.TODO.name,
                        todoId,
                        result.attachmentsPendingAt,
                    )
                }
                undoApplied = true
            }
        } else {
            val seriesUnchanged = seriesId == null || (
                result.seriesUpdatedAt != null &&
                    dao.getTodoSeries(seriesId)?.updatedAt == result.seriesUpdatedAt
                )
            if (seriesUnchanged) {
                undoApplied = dao.undoDeleteTodoIfDeletedAt(result.itemId, result.deletedAt, now) == 1
                if (undoApplied) {
                    dao.undoOwnerAttachmentsMarkedAt(
                        AttachmentOwnerType.TODO.name,
                        result.itemId,
                        result.attachmentsPendingAt,
                    )
                }
            }
        }
        if (undoApplied && result.exceptionCreated && seriesId != null && boundary != null) {
            dao.deleteTodoOccurrenceExceptionIfCreatedAt(seriesId, boundary, result.deletedAt)
        }
        if (undoApplied) DeleteUndoResult.RESTORED else DeleteUndoResult.UNAVAILABLE
    }

    suspend fun addCategory(name: String, parentId: Long? = null): Long {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Category name is required" }
        if (parentId != null) require(dao.getCategory(parentId) != null) { "Parent category does not exist" }
        return dao.insertCategory(CategoryEntity(name = cleanName, parentId = parentId))
    }

    suspend fun deleteCategory(categoryId: Long) = database.withTransaction {
        val category = dao.getCategory(categoryId) ?: return@withTransaction
        val categories = dao.getCategories()
        val children = categories
            .asSequence()
            .filter { it.parentId == categoryId }
            .sortedWith(
                compareBy<CategoryEntity> { it.sortOrder }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.name }
                    .thenBy { it.id },
            )
            .toList()
        val occupiedNames = categories
            .asSequence()
            .filter { it.parentId == category.parentId && it.id != categoryId }
            .mapTo(mutableSetOf()) { it.name }
        val promotions = children.map { child ->
            val promotedName = uniquePromotedCategoryName(child.name, occupiedNames)
            occupiedNames += promotedName
            child.copy(name = promotedName, parentId = category.parentId)
        }
        val now = monotonicMutationTimestamp(
            wallClockMillis(),
            dao.getMaxTodoMutationPredecessorInCategory(categoryId),
            dao.getMaxTodoSeriesMutationPredecessorInCategory(categoryId),
        )
        dao.uncategorizeTodos(categoryId, now)
        dao.uncategorizeTodoSeries(categoryId, now)
        // Delete first so SET_NULL removes the old link without colliding with the category being
        // removed. Intermediate states are hidden by the surrounding transaction.
        dao.deleteCategoryById(categoryId)
        for (promotion in promotions) dao.updateCategory(promotion)
    }

    /** Compatibility API. A zero result means a future series was saved but has no due entry yet. */
    suspend fun saveLedger(
        draft: LedgerDraft,
        scope: SeriesEditScope = SeriesEditScope.ONLY_THIS_OCCURRENCE,
        clientOperationToken: String? = null,
    ): Long = saveLedgerWithResult(draft, scope, clientOperationToken = clientOperationToken)
        .entryId ?: NO_LEDGER_ENTRY_ID

    suspend fun saveLedgerWithResult(
        draft: LedgerDraft,
        scope: SeriesEditScope = SeriesEditScope.ONLY_THIS_OCCURRENCE,
        throughEpochDay: Long = LocalDate.now().toEpochDay(),
        clientOperationToken: String? = null,
    ): LedgerSaveResult = database.withTransaction {
        saveLedgerInTransaction(
            draft,
            scope,
            throughEpochDay,
            wallClockMillis(),
            clientOperationToken,
        )
    }

    /**
     * Replaces only the not-yet-generated portion of a rule. Every existing entry and attachment
     * remains independent and unchanged; the replacement therefore must start strictly after
     * [throughEpochDay] as well as on or after [effectiveEpochDay].
     */
    suspend fun editLedgerSeriesForFuture(
        seriesId: Long,
        effectiveEpochDay: Long,
        draft: LedgerDraft,
        throughEpochDay: Long = LocalDate.now().toEpochDay(),
    ): LedgerSaveResult = database.withTransaction {
        validateLedgerDraft(draft, requireRecurrence = true)
        val earliestReplacementEpochDay = RecurrenceEngine.firstUnmaterializedEpochDay(
            effectiveEpochDay = effectiveEpochDay,
            materializedThroughEpochDay = throughEpochDay,
        )
        require(draft.epochDay >= earliestReplacementEpochDay) {
            "The replacement rule must start on an ungenerated future date"
        }
        val existing = requireNotNull(dao.getLedgerSeries(seriesId)) {
            "Ledger series does not exist"
        }

        val now = monotonicMutationTimestamp(
            wallClockMillis(),
            existing.createdAt,
            existing.updatedAt,
        )
        dao.deactivateLedgerSeries(seriesId, now)
        val replacement = insertLedgerSeries(draft, now)
        LedgerSaveResult(entryId = null, seriesId = replacement.id)
    }

    suspend fun softDeleteLedgerEntry(
        entryId: Long,
        undoWindowMillis: Long = DEFAULT_UNDO_WINDOW,
    ): Long = deleteLedgerEntry(
        entryId,
        SeriesEditScope.ONLY_THIS_OCCURRENCE,
        undoWindowMillis,
    )

    suspend fun deleteLedgerEntry(
        entryId: Long,
        scope: SeriesEditScope,
        undoWindowMillis: Long = DEFAULT_UNDO_WINDOW,
    ): Long = deleteLedgerEntryWithResult(entryId, scope, undoWindowMillis).attachmentsPendingAt

    suspend fun deleteLedgerEntryWithResult(
        entryId: Long,
        scope: SeriesEditScope,
        undoWindowMillis: Long = DEFAULT_UNDO_WINDOW,
    ): RecurringDeleteResult = database.withTransaction {
        require(undoWindowMillis >= 0) { "Undo window must not be negative" }
        val entry = requireNotNull(dao.getLedgerEntry(entryId)) { "Ledger entry does not exist" }
        check(entry.deletedAt == null) { "Ledger entry is already deleted" }
        val seriesId = entry.seriesId
        val occurrenceEpochDay = entry.occurrenceEpochDay
        val series = seriesId?.let { dao.getLedgerSeries(it) }
        val deletionWallClock = wallClockMillis()
        val isTailDeletion =
            scope == SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES &&
                seriesId != null &&
                occurrenceEpochDay != null
        val affectedIds = if (isTailDeletion) {
            dao.getActiveLedgerOccurrenceIdsFrom(
                requireNotNull(seriesId),
                requireNotNull(occurrenceEpochDay),
            )
        } else {
            listOf(entryId)
        }
        val now = monotonicMutationTimestamp(
            deletionWallClock,
            entry.createdAt,
            entry.updatedAt,
            series?.createdAt,
            series?.updatedAt,
            maxLedgerMutationPredecessor(affectedIds),
        )
        val attachmentsPendingAt = uniqueOwnerAttachmentDeletionDeadline(
            ownerType = AttachmentOwnerType.LEDGER,
            ownerIds = affectedIds,
            minimumDeadline = persistedDeadlineTimestamp(
                deletionWallClock,
                undoWindowMillis,
                maxAttachmentCreatedAt(AttachmentOwnerType.LEDGER, affectedIds),
            ),
        )
        val undoExpiresAtElapsedRealtime = undoDeadline(elapsedRealtimeMillis(), undoWindowMillis)
        val seriesWasActive = series?.active ?: false

        val exceptionCreated = if (seriesId != null && occurrenceEpochDay != null) {
            dao.insertLedgerOccurrenceException(
                LedgerOccurrenceExceptionEntity(seriesId, occurrenceEpochDay, now),
            ) > 0
        } else false

        if (isTailDeletion) {
            val tailSeriesId = requireNotNull(seriesId)
            val tailBoundary = requireNotNull(occurrenceEpochDay)
            dao.deactivateLedgerSeries(tailSeriesId, now)
            softDeleteLedgerSeriesTail(
                tailSeriesId,
                tailBoundary,
                now,
                attachmentsPendingAt,
                affectedIds,
            )
        } else {
            check(dao.softDeleteLedgerEntry(entryId, now) == 1) {
                "Ledger entry deletion state changed"
            }
            dao.markActiveOwnerAttachmentsForDeletion(
                AttachmentOwnerType.LEDGER.name,
                entryId,
                attachmentsPendingAt,
            )
        }

        RecurringDeleteResult(
            ownerType = AttachmentOwnerType.LEDGER,
            itemId = entryId,
            scope = scope,
            seriesId = seriesId,
            boundaryEpochDay = occurrenceEpochDay,
            seriesWasActive = seriesWasActive,
            seriesUpdatedAt = if (isTailDeletion) now else series?.updatedAt,
            deletedAt = now,
            undoExpiresAtElapsedRealtime = undoExpiresAtElapsedRealtime,
            attachmentsPendingAt = attachmentsPendingAt,
            exceptionCreated = exceptionCreated,
        )
    }

    suspend fun undoDeleteLedgerEntry(
        result: RecurringDeleteResult,
        nowElapsedRealtimeMillis: Long = elapsedRealtimeMillis(),
    ): DeleteUndoResult = database.withTransaction {
        require(result.ownerType == AttachmentOwnerType.LEDGER) { "Not a ledger deletion receipt" }
        if (!isUndoWindowOpen(result.undoExpiresAtElapsedRealtime, nowElapsedRealtimeMillis)) {
            return@withTransaction DeleteUndoResult.EXPIRED
        }
        val seriesId = result.seriesId
        val boundary = result.boundaryEpochDay
        val isTailUndo =
            result.scope == SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES &&
                seriesId != null &&
                boundary != null &&
                result.seriesUpdatedAt != null
        val restoredIds = if (isTailUndo) {
            dao.getLedgerOccurrenceIdsDeletedAtFrom(
                requireNotNull(seriesId),
                requireNotNull(boundary),
                result.deletedAt,
            )
        } else {
            listOf(result.itemId)
        }
        val currentSeries = seriesId?.let { dao.getLedgerSeries(it) }
        val now = monotonicMutationTimestamp(
            wallClockMillis(),
            result.deletedAt,
            result.seriesUpdatedAt,
            currentSeries?.createdAt,
            currentSeries?.updatedAt,
            maxLedgerMutationPredecessor(restoredIds),
        )
        var undoApplied = false
        if (isTailUndo) {
            if (
                restoredIds.isNotEmpty() &&
                dao.restoreLedgerSeriesStateIfUpdatedAt(
                    seriesId = requireNotNull(seriesId),
                    active = result.seriesWasActive,
                    expectedUpdatedAt = requireNotNull(result.seriesUpdatedAt),
                    updatedAt = now,
                ) == 1
            ) {
                val restoredCount = dao.restoreLedgerOccurrencesDeletedAtFrom(
                    requireNotNull(seriesId),
                    requireNotNull(boundary),
                    result.deletedAt,
                    now,
                )
                check(restoredCount == restoredIds.size) { "Ledger deletion state changed during Undo" }
                restoredIds.forEach { entryId ->
                    dao.undoOwnerAttachmentsMarkedAt(
                        AttachmentOwnerType.LEDGER.name,
                        entryId,
                        result.attachmentsPendingAt,
                    )
                }
                undoApplied = true
            }
        } else {
            val seriesUnchanged = seriesId == null || (
                result.seriesUpdatedAt != null &&
                    dao.getLedgerSeries(seriesId)?.updatedAt == result.seriesUpdatedAt
                )
            if (seriesUnchanged) {
                undoApplied = dao.undoDeleteLedgerEntryIfDeletedAt(
                    result.itemId,
                    result.deletedAt,
                    now,
                ) == 1
                if (undoApplied) {
                    dao.undoOwnerAttachmentsMarkedAt(
                        AttachmentOwnerType.LEDGER.name,
                        result.itemId,
                        result.attachmentsPendingAt,
                    )
                }
            }
        }
        if (undoApplied && result.exceptionCreated && seriesId != null && boundary != null) {
            dao.deleteLedgerOccurrenceExceptionIfCreatedAt(seriesId, boundary, result.deletedAt)
        }
        if (undoApplied) DeleteUndoResult.RESTORED else DeleteUndoResult.UNAVAILABLE
    }

    suspend fun deactivateLedgerSeries(seriesId: Long) = database.withTransaction {
        val series = requireNotNull(dao.getLedgerSeries(seriesId)) { "Ledger series does not exist" }
        dao.deactivateLedgerSeries(
            seriesId,
            monotonicMutationTimestamp(wallClockMillis(), series.createdAt, series.updatedAt),
        )
    }

    suspend fun deactivateTodoSeries(seriesId: Long) = database.withTransaction {
        val series = requireNotNull(dao.getTodoSeries(seriesId)) { "Task series does not exist" }
        dao.deactivateTodoSeries(
            seriesId,
            monotonicMutationTimestamp(wallClockMillis(), series.createdAt, series.updatedAt),
        )
    }

    /** Materializes Todo occurrences needed by a planning view without generating future ledger rows. */
    suspend fun materializeTodoOccurrencesThrough(
        throughEpochDay: Long,
        afterCursor: String? = null,
    ): RecurrenceCatchUpResult = recurrenceMutex.withLock {
        val items = dao.getActiveTodoSeries().map { series ->
            RecurringSeriesWork.Todo(
                seriesId = series.id,
                generationThroughEpochDay = throughEpochDay,
                deactivateThroughEpochDay = throughEpochDay,
            )
        }
        processRecurringWork(rotatedWorkQueue(items, afterCursor))
    }

    suspend fun catchUpRecurring(
        throughEpochDay: Long = LocalDate.now().toEpochDay(),
        afterCursor: String? = null,
    ): RecurrenceCatchUpResult = recurrenceMutex.withLock {
        val items = mutableListOf<RecurringSeriesWork>()
        dao.getActiveTodoSeries().forEach { series ->
            items += RecurringSeriesWork.Todo(
                seriesId = series.id,
                generationThroughEpochDay = todoGenerationHorizon(
                    throughEpochDay,
                    series.reminderOffsetsCsv,
                ),
                deactivateThroughEpochDay = throughEpochDay,
            )
        }
        dao.getActiveLedgerSeries().forEach { series ->
            items += RecurringSeriesWork.Ledger(
                seriesId = series.id,
                generationThroughEpochDay = throughEpochDay,
                deactivateThroughEpochDay = throughEpochDay,
            )
        }
        processRecurringWork(rotatedWorkQueue(items, afterCursor))
    }

    private suspend fun processRecurringWork(
        work: ArrayDeque<RecurringSeriesWork>,
    ): RecurrenceCatchUpResult {
        var remainingBudget = maxOccurrencesPerCatchUp
        var insertedOccurrences = 0
        var lastProcessedCursor: RecurrenceCursor? = null
        while (remainingBudget > 0 && work.isNotEmpty()) {
            val passSize = work.size
            val fairPageLimit = minOf(
                OCCURRENCE_PAGE_SIZE,
                maxOf(1, remainingBudget / passSize),
            )
            repeat(passSize) {
                if (remainingBudget <= 0 || work.isEmpty()) return@repeat
                val item = work.removeFirst()
                val pageLimit = minOf(fairPageLimit, remainingBudget)
                val progress = when (item) {
                    is RecurringSeriesWork.Todo -> materializeTodoSeriesPage(item, pageLimit)
                    is RecurringSeriesWork.Ledger -> materializeLedgerSeriesPage(item, pageLimit)
                }
                lastProcessedCursor = item.cursor
                remainingBudget -= progress.processedOccurrences
                insertedOccurrences += progress.insertedOccurrences
                if (progress.hasMore) {
                    check(progress.processedOccurrences > 0) { "A recurrence page made no progress" }
                    work.addLast(item)
                }
            }
        }
        val hasMore = work.isNotEmpty()
        return RecurrenceCatchUpResult(
            insertedOccurrences = insertedOccurrences,
            hasMore = hasMore,
            nextCursor = if (hasMore) lastProcessedCursor?.encode() else null,
        )
    }

    private suspend fun materializeTodoSeriesPage(
        work: RecurringSeriesWork.Todo,
        limit: Int,
    ): RecurrencePageProgress = database.withTransaction {
        val series = dao.getTodoSeries(work.seriesId)
            ?.takeIf(TodoSeriesEntity::active)
            ?: return@withTransaction RecurrencePageProgress.DONE
        val rowWatermark = dao.getMaxTodoOccurrenceDay(series.id)
        val exceptionWatermark = dao.getMaxTodoOccurrenceExceptionDay(series.id)
        val watermark = when {
            rowWatermark != null -> maxNullable(rowWatermark, exceptionWatermark)
            exceptionWatermark != null && series.startEpochDay in
                dao.getTodoOccurrenceExceptionDays(series.id, series.startEpochDay) ->
                exceptionWatermark
            else -> null
        }
        val page = RecurrenceEngine.generateOccurrencesAfter(
            startEpochDay = series.startEpochDay,
            rule = RecurrenceRule(series.recurrenceUnit, series.intervalCount, series.endEpochDay),
            afterEpochDayExclusive = watermark,
            throughEpochDay = work.generationThroughEpochDay,
            limit = limit,
        )
        val excluded = page.epochDays.lastOrNull()?.let { pageEnd ->
            if (watermark == null) {
                dao.getTodoOccurrenceExceptionDays(series.id, pageEnd)
            } else {
                dao.getTodoOccurrenceExceptionDaysAfter(series.id, watermark, pageEnd)
            }
        }.orEmpty().toHashSet()
        val templates = dao.getTodoSeriesSubtasks(series.id)
        val reminderOffsets = series.reminderOffsetsCsv
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it >= 0 }
            .distinct()
        val now = wallClockMillis()
        var inserted = 0
        page.epochDays.filterNot(excluded::contains).forEach { occurrence ->
            val todoId = dao.insertTodo(
                TodoEntity(
                    seriesId = series.id,
                    occurrenceEpochDay = occurrence,
                    title = series.title,
                    description = series.description,
                    categoryId = series.categoryId,
                    deadlineEpochDay = occurrence,
                    deadlineMinute = series.startMinute,
                    priority = series.priority,
                    tagsCsv = series.tagsCsv,
                    createdAt = now,
                    updatedAt = now,
                    customOrder = now,
                ),
            )
            if (todoId > 0) {
                inserted++
                if (templates.isNotEmpty()) {
                    dao.insertSubtasks(
                        templates.map { template ->
                            SubtaskEntity(
                                todoId = todoId,
                                description = template.description,
                                sortOrder = template.sortOrder,
                            )
                        },
                    )
                }
                if (reminderOffsets.isNotEmpty()) {
                    dao.insertReminders(
                        reminderOffsets.map { offset ->
                            TodoReminderEntity(todoId = todoId, offsetMinutes = offset)
                        },
                    )
                }
            }
        }
        if (
            !page.hasMore &&
            series.endEpochDay != null &&
            series.endEpochDay <= work.deactivateThroughEpochDay
        ) {
            dao.deactivateTodoSeries(
                series.id,
                monotonicMutationTimestamp(now, series.createdAt, series.updatedAt),
            )
        }
        RecurrencePageProgress(page.epochDays.size, inserted, page.hasMore)
    }

    private suspend fun materializeLedgerSeriesPage(
        work: RecurringSeriesWork.Ledger,
        limit: Int,
    ): RecurrencePageProgress = database.withTransaction {
        val series = dao.getLedgerSeries(work.seriesId)
            ?.takeIf(LedgerSeriesEntity::active)
            ?: return@withTransaction RecurrencePageProgress.DONE
        requireValidLedgerAmount(series.amountCents)
        val rowWatermark = dao.getMaxLedgerOccurrenceDay(series.id)
        val exceptionWatermark = dao.getMaxLedgerOccurrenceExceptionDay(series.id)
        val watermark = when {
            rowWatermark != null -> maxNullable(rowWatermark, exceptionWatermark)
            exceptionWatermark != null && series.startEpochDay in
                dao.getLedgerOccurrenceExceptionDays(series.id, series.startEpochDay) ->
                exceptionWatermark
            else -> null
        }
        val page = RecurrenceEngine.generateOccurrencesAfter(
            startEpochDay = series.startEpochDay,
            rule = RecurrenceRule(series.recurrenceUnit, series.intervalCount, series.endEpochDay),
            afterEpochDayExclusive = watermark,
            throughEpochDay = work.generationThroughEpochDay,
            limit = limit,
        )
        val excluded = page.epochDays.lastOrNull()?.let { pageEnd ->
            if (watermark == null) {
                dao.getLedgerOccurrenceExceptionDays(series.id, pageEnd)
            } else {
                dao.getLedgerOccurrenceExceptionDaysAfter(series.id, watermark, pageEnd)
            }
        }.orEmpty().toHashSet()
        val now = wallClockMillis()
        var inserted = 0
        page.epochDays.filterNot(excluded::contains).forEach { occurrence ->
            val insertedId = dao.insertLedgerEntry(
                LedgerEntryEntity(
                    seriesId = series.id,
                    occurrenceEpochDay = occurrence,
                    type = series.type,
                    amountCents = series.amountCents,
                    epochDay = occurrence,
                    minuteOfDay = GENERATED_LEDGER_MINUTE,
                    note = series.note,
                    merchant = series.merchant,
                    tagsCsv = series.tagsCsv,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            if (insertedId > 0) inserted++
        }
        if (
            !page.hasMore &&
            series.endEpochDay != null &&
            series.endEpochDay <= work.deactivateThroughEpochDay
        ) {
            dao.deactivateLedgerSeries(
                series.id,
                monotonicMutationTimestamp(now, series.createdAt, series.updatedAt),
            )
        }
        RecurrencePageProgress(page.epochDays.size, inserted, page.hasMore)
    }

    private fun rotatedWorkQueue(
        items: List<RecurringSeriesWork>,
        afterCursor: String?,
    ): ArrayDeque<RecurringSeriesWork> {
        val ordered = items.sortedWith(
            compareBy<RecurringSeriesWork>({ it.seriesId }, { it.kindOrder }),
        )
        val cursor = RecurrenceCursor.parse(afterCursor)
        val rotationIndex = cursor?.let { boundary ->
            ordered.indexOfFirst { it.cursor > boundary }.let { index ->
                if (index < 0) 0 else index
            }
        } ?: 0
        val rotated = ordered.drop(rotationIndex) + ordered.take(rotationIndex)
        return ArrayDeque<RecurringSeriesWork>().apply { addAll(rotated) }
    }

    private val RecurringSeriesWork.cursor: RecurrenceCursor
        get() = RecurrenceCursor(seriesId, kindOrder)

    private data class RecurrenceCursor(
        val seriesId: Long,
        val kindOrder: Int,
    ) : Comparable<RecurrenceCursor> {
        override fun compareTo(other: RecurrenceCursor): Int =
            compareValuesBy(this, other, RecurrenceCursor::seriesId, RecurrenceCursor::kindOrder)

        fun encode(): String = "$seriesId:$kindOrder"

        companion object {
            fun parse(value: String?): RecurrenceCursor? {
                val parts = value?.split(':', limit = 2) ?: return null
                if (parts.size != 2) return null
                val seriesId = parts[0].toLongOrNull() ?: return null
                val kindOrder = parts[1].toIntOrNull()?.takeIf { it in 0..1 } ?: return null
                return RecurrenceCursor(seriesId, kindOrder)
            }
        }
    }

    private sealed interface RecurringSeriesWork {
        val seriesId: Long
        val kindOrder: Int

        data class Todo(
            override val seriesId: Long,
            val generationThroughEpochDay: Long,
            val deactivateThroughEpochDay: Long,
        ) : RecurringSeriesWork {
            override val kindOrder: Int = 0
        }

        data class Ledger(
            override val seriesId: Long,
            val generationThroughEpochDay: Long,
            val deactivateThroughEpochDay: Long,
        ) : RecurringSeriesWork {
            override val kindOrder: Int = 1
        }
    }

    private data class RecurrencePageProgress(
        val processedOccurrences: Int,
        val insertedOccurrences: Int,
        val hasMore: Boolean,
    ) {
        companion object {
            val DONE = RecurrencePageProgress(0, 0, false)
        }
    }

    private fun maxNullable(first: Long?, second: Long?): Long? = when {
        first == null -> second
        second == null -> first
        else -> maxOf(first, second)
    }

    private suspend fun insertTodoSeries(
        draft: TodoDraft,
        title: String,
        tagsCsv: String,
        subtaskDescriptions: List<String>,
        now: Long,
    ): Long {
        val recurrence = requireNotNull(draft.recurrence)
        require(recurrence.interval > 0) { "Recurrence interval must be positive" }
        val seriesId = dao.insertTodoSeries(
            TodoSeriesEntity(
                title = title,
                description = draft.description.trim(),
                categoryId = draft.categoryId,
                startEpochDay = requireNotNull(draft.deadlineEpochDay),
                startMinute = draft.deadlineMinute,
                recurrenceUnit = recurrence.unit,
                intervalCount = recurrence.interval,
                endEpochDay = recurrence.endEpochDay,
                priority = draft.priority,
                tagsCsv = tagsCsv,
                reminderOffsetsCsv = draft.reminderOffsetsMinutes
                    .distinct()
                    .sorted()
                    .joinToString(","),
                createdAt = now,
                updatedAt = now,
            ),
        )
        if (subtaskDescriptions.isNotEmpty()) {
            dao.insertTodoSeriesSubtasks(
                subtaskDescriptions.mapIndexed { index, description ->
                    TodoSeriesSubtaskEntity(seriesId, index, description)
                },
            )
        }
        return seriesId
    }

    private suspend fun todoSeriesMatchesCreationRequest(
        series: TodoSeriesEntity,
        draft: TodoDraft,
        title: String,
        tagsCsv: String,
        subtaskDescriptions: List<String>,
    ): Boolean {
        val recurrence = requireNotNull(draft.recurrence)
        val templateDescriptions = dao.getTodoSeriesSubtasks(series.id).map { it.description }
        return series.title == title &&
            series.description == draft.description.trim() &&
            series.categoryId == draft.categoryId &&
            series.startEpochDay == draft.deadlineEpochDay &&
            series.startMinute == draft.deadlineMinute &&
            series.recurrenceUnit == recurrence.unit &&
            series.intervalCount == recurrence.interval &&
            series.endEpochDay == recurrence.endEpochDay &&
            series.priority == draft.priority &&
            series.tagsCsv == tagsCsv &&
            series.reminderOffsetsCsv == draft.reminderOffsetsMinutes
                .distinct()
                .sorted()
                .joinToString(",") &&
            templateDescriptions == subtaskDescriptions
    }

    private suspend fun cleanGeneratedTodoOccurrencesForSeriesReplacement(
        seriesId: Long,
        ownerId: Long,
        fromEpochDay: Long,
        deletedAt: Long,
        knownAffectedIds: List<Long>? = null,
    ) {
        val affectedIds = knownAffectedIds
            ?: dao.getCleanableTodoOccurrenceIdsForSeriesReplacement(
                seriesId = seriesId,
                ownerId = ownerId,
                fromEpochDay = fromEpochDay,
                ownerType = AttachmentOwnerType.TODO.name,
            )
        affectedIds.forEach { todoId ->
            dao.deleteRemindersForTodo(todoId)
            dao.softDeleteTodo(todoId, deletedAt)
        }
    }

    private fun normalizeSubtaskDescriptions(subtasks: List<String>): List<String> = subtasks
        .map(String::trim)
        .filter(String::isNotEmpty)

    private fun mergeSubtasks(
        todoId: Long,
        descriptions: List<String>,
        existing: List<SubtaskEntity>,
    ): List<SubtaskEntity> {
        val consumed = BooleanArray(existing.size)
        val preservedIndices = arrayOfNulls<Int>(descriptions.size)

        // Match every stable description before considering a rename, so insertions and reorders
        // cannot steal the completion state of an item that still exists elsewhere in the list.
        descriptions.forEachIndexed { newIndex, description ->
            val oldIndex = existing.indices.firstOrNull { candidate ->
                !consumed[candidate] &&
                    existing[candidate].description.equals(description, ignoreCase = true)
            }
            if (oldIndex != null) {
                consumed[oldIndex] = true
                preservedIndices[newIndex] = oldIndex
            }
        }

        // With equal list sizes, an unmatched item at the same position is an unambiguous rename.
        // Structural insertions/deletions deliberately get no positional fallback.
        if (descriptions.size == existing.size) {
            descriptions.indices.forEach { index ->
                if (preservedIndices[index] == null && !consumed[index]) {
                    consumed[index] = true
                    preservedIndices[index] = index
                }
            }
        }

        return descriptions.mapIndexed { index, description ->
            SubtaskEntity(
                todoId = todoId,
                description = description,
                isCompleted = preservedIndices[index]?.let { existing[it].isCompleted } ?: false,
                sortOrder = index,
            )
        }
    }

    private suspend fun softDeleteTodoSeriesTail(
        seriesId: Long,
        fromEpochDay: Long,
        deletedAt: Long,
        attachmentsPendingAt: Long,
        knownAffectedIds: List<Long>? = null,
    ) {
        val affectedIds = knownAffectedIds
            ?: dao.getActiveTodoOccurrenceIdsFrom(seriesId, fromEpochDay)
        dao.softDeleteTodoOccurrencesFrom(seriesId, fromEpochDay, deletedAt)
        affectedIds.forEach { todoId ->
            dao.markActiveOwnerAttachmentsForDeletion(
                AttachmentOwnerType.TODO.name,
                todoId,
                attachmentsPendingAt,
            )
        }
    }

    private fun validateLedgerDraft(draft: LedgerDraft, requireRecurrence: Boolean = false) {
        requireValidLedgerAmount(draft.amountCents)
        require(draft.minuteOfDay in 0..1_439) { "Invalid time" }
        if (requireRecurrence) requireNotNull(draft.recurrence) { "A recurrence rule is required" }
        draft.recurrence?.let { recurrence ->
            require(recurrence.interval > 0) { "Recurrence interval must be positive" }
            require(recurrence.endEpochDay == null || recurrence.endEpochDay >= draft.epochDay) {
                "Repeat end date cannot be before its first occurrence"
            }
        }
    }

    private suspend fun saveLedgerInTransaction(
        draft: LedgerDraft,
        scope: SeriesEditScope,
        throughEpochDay: Long,
        now: Long,
        clientOperationToken: String?,
    ): LedgerSaveResult {
        val wallClock = now
        var mutationTime = now
        require(clientOperationToken == null || draft.id == null) {
            "Client operation tokens are only valid for new ledger entries"
        }
        var saveDraft = draft
        var resolvedCreationSeries: LedgerSeriesEntity? = null
        var resolvedCreationEntry: LedgerEntryEntity? = null
        var resolvedCreationEntryByToken = false
        clientOperationToken?.let { token ->
            val priorSeries = dao.getLedgerSeriesByClientOperationToken(token)
            if (priorSeries != null && priorSeries.active) {
                val startEntry = dao.getLedgerOccurrence(priorSeries.id, priorSeries.startEpochDay)
                if (startEntry == null || startEntry.deletedAt == null) {
                    resolvedCreationSeries = priorSeries
                    resolvedCreationEntry = startEntry
                } else {
                    dao.clearLedgerClientOperationToken(startEntry.id, token)
                    dao.clearLedgerSeriesClientOperationToken(priorSeries.id, token)
                }
            } else if (priorSeries != null) {
                dao.clearLedgerSeriesClientOperationToken(priorSeries.id, token)
            }

            if (resolvedCreationSeries == null) {
                val priorOwner = dao.getLedgerEntryByClientOperationToken(token)
                if (priorOwner != null && priorOwner.deletedAt == null) {
                    saveDraft = draft.copy(id = priorOwner.id)
                    resolvedCreationEntryByToken = true
                } else if (priorOwner != null) {
                    dao.clearLedgerClientOperationToken(priorOwner.id, token)
                }
            }
        }
        validateLedgerDraft(saveDraft)
        resolvedCreationSeries?.let { series ->
            return updateCreatedLedgerSeries(
                existingSeries = series,
                existingStartEntry = resolvedCreationEntry,
                draft = saveDraft,
                throughEpochDay = throughEpochDay,
                now = now,
                clientOperationToken = requireNotNull(clientOperationToken),
            )
        }
        val existing = saveDraft.id?.let { id ->
            requireNotNull(dao.getLedgerEntry(id)) { "Ledger entry does not exist" }
        }
        existing?.let { entry ->
            validateExistingLedgerUpdate(
                existing = entry,
                draft = saveDraft,
                todayEpochDay = LocalDate.now().toEpochDay(),
            )
        }
        val oldSeriesId = existing?.seriesId
        val oldOccurrenceEpochDay = existing?.occurrenceEpochDay

        if (existing == null) {
            if (saveDraft.recurrence == null) {
                return LedgerSaveResult(
                    entryId = insertStandaloneLedgerEntry(
                        saveDraft,
                        null,
                        now,
                        clientOperationToken,
                    ),
                    seriesId = null,
                )
            }
            val series = insertLedgerSeries(saveDraft, now, clientOperationToken)
            return LedgerSaveResult(
                entryId = insertLedgerStartOccurrenceIfDue(
                    series,
                    throughEpochDay,
                    now,
                    clientOperationToken,
                ),
                seriesId = series.id,
            )
        }

        if (resolvedCreationEntryByToken && oldSeriesId != null) {
            return updateCreatedLedgerSeries(
                existingSeries = requireNotNull(dao.getLedgerSeries(oldSeriesId)),
                existingStartEntry = existing,
                draft = saveDraft,
                throughEpochDay = throughEpochDay,
                now = now,
                clientOperationToken = requireNotNull(clientOperationToken),
            )
        }

        if (oldSeriesId != null && scope == SeriesEditScope.ONLY_THIS_OCCURRENCE) {
            val oldOccurrence = requireNotNull(oldOccurrenceEpochDay) {
                "A recurring ledger entry must retain its occurrence identity"
            }
            val retainedSeriesId = if (saveDraft.recurrence == null) {
                dao.insertLedgerOccurrenceException(
                    LedgerOccurrenceExceptionEntity(oldSeriesId, oldOccurrence, now),
                )
                null
            } else oldSeriesId
            val entryId = updateLedgerEntryFromDraft(
                existing = existing,
                draft = saveDraft,
                seriesId = retainedSeriesId,
                occurrenceEpochDay = retainedSeriesId?.let { oldOccurrence },
                minuteOfDay = saveDraft.minuteOfDay,
                now = now,
            )
            return LedgerSaveResult(entryId, retainedSeriesId)
        }

        if (oldSeriesId != null) {
            val boundary = requireNotNull(oldOccurrenceEpochDay) {
                "A recurring ledger entry must retain its occurrence identity"
            }
            if (saveDraft.recurrence != null) {
                require(saveDraft.epochDay >= boundary) {
                    "The replacement series cannot start before the edited occurrence"
                }
            }
            val oldSeries = requireNotNull(dao.getLedgerSeries(oldSeriesId)) {
                "Ledger series does not exist"
            }
            val affectedIds = dao.getActiveLedgerOccurrenceIdsFrom(oldSeriesId, boundary + 1)
            mutationTime = monotonicMutationTimestamp(
                wallClock,
                existing.createdAt,
                existing.updatedAt,
                oldSeries.createdAt,
                oldSeries.updatedAt,
                maxLedgerMutationPredecessor(affectedIds),
            )
            dao.deactivateLedgerSeries(oldSeriesId, mutationTime)
            softDeleteLedgerSeriesTail(
                oldSeriesId,
                boundary + 1,
                mutationTime,
                persistedDeadlineTimestamp(
                    wallClock,
                    DEFAULT_UNDO_WINDOW,
                    maxAttachmentCreatedAt(AttachmentOwnerType.LEDGER, affectedIds),
                ),
                affectedIds,
            )
        }

        if (saveDraft.recurrence == null) {
            val entryId = updateLedgerEntryFromDraft(
                existing = existing,
                draft = saveDraft,
                seriesId = null,
                occurrenceEpochDay = null,
                minuteOfDay = saveDraft.minuteOfDay,
                now = mutationTime,
            )
            return LedgerSaveResult(entryId, null)
        }

        val replacement = insertLedgerSeries(
            saveDraft,
            mutationTime,
            clientOperationToken.takeIf { resolvedCreationEntryByToken },
        )
        if (saveDraft.epochDay > throughEpochDay) {
            val deletionTime = monotonicMutationTimestamp(
                mutationTime,
                existing.createdAt,
                existing.updatedAt,
            )
            dao.softDeleteLedgerEntry(existing.id, deletionTime)
            dao.markActiveOwnerAttachmentsForDeletion(
                AttachmentOwnerType.LEDGER.name,
                existing.id,
                persistedDeadlineTimestamp(
                    wallClock,
                    DEFAULT_UNDO_WINDOW,
                    maxAttachmentCreatedAt(
                        AttachmentOwnerType.LEDGER,
                        listOf(existing.id),
                    ),
                ),
            )
            return LedgerSaveResult(entryId = null, seriesId = replacement.id)
        }

        val entryId = updateLedgerEntryFromDraft(
            existing = existing,
            draft = saveDraft,
            seriesId = replacement.id,
            occurrenceEpochDay = saveDraft.epochDay,
            minuteOfDay = GENERATED_LEDGER_MINUTE,
            now = mutationTime,
        )
        return LedgerSaveResult(entryId = entryId, seriesId = replacement.id)
    }

    private suspend fun updateCreatedLedgerSeries(
        existingSeries: LedgerSeriesEntity,
        existingStartEntry: LedgerEntryEntity?,
        draft: LedgerDraft,
        throughEpochDay: Long,
        now: Long,
        clientOperationToken: String,
    ): LedgerSaveResult {
        val recurrence = draft.recurrence
        if (recurrence != null && ledgerSeriesMatchesCreationRequest(existingSeries, draft)) {
            return LedgerSaveResult(
                entryId = existingStartEntry?.id ?: insertLedgerStartOccurrenceIfDue(
                    series = existingSeries,
                    throughEpochDay = throughEpochDay,
                    now = now,
                    clientOperationToken = clientOperationToken,
                ),
                seriesId = existingSeries.id,
            )
        }

        val seriesMutationTime = monotonicMutationTimestamp(
            now,
            existingSeries.createdAt,
            existingSeries.updatedAt,
        )
        dao.updateLedgerSeries(
            existingSeries.copy(
                active = false,
                updatedAt = seriesMutationTime,
                clientOperationToken = null,
            ),
        )
        if (recurrence == null) {
            val entryId = insertStandaloneLedgerEntry(
                draft = draft,
                existing = existingStartEntry,
                now = seriesMutationTime,
                clientOperationToken = clientOperationToken,
            )
            return LedgerSaveResult(entryId = entryId, seriesId = null)
        }

        val replacementSeries = insertLedgerSeries(draft, seriesMutationTime, clientOperationToken)

        val entryId = if (existingStartEntry == null) {
            insertLedgerStartOccurrenceIfDue(
                series = replacementSeries,
                throughEpochDay = throughEpochDay,
                now = seriesMutationTime,
                clientOperationToken = clientOperationToken,
            )
        } else {
            dao.updateLedgerEntry(
                existingStartEntry.copy(
                    seriesId = replacementSeries.id,
                    occurrenceEpochDay = draft.epochDay,
                    type = draft.type,
                    amountCents = draft.amountCents,
                    epochDay = draft.epochDay,
                    minuteOfDay = GENERATED_LEDGER_MINUTE,
                    note = draft.note.trim(),
                    merchant = draft.merchant.trim(),
                    tagsCsv = normalizeTags(draft.tags),
                    updatedAt = monotonicMutationTimestamp(
                        seriesMutationTime,
                        existingStartEntry.createdAt,
                        existingStartEntry.updatedAt,
                    ),
                    clientOperationToken = clientOperationToken,
                ),
            )
            existingStartEntry.id
        }
        return LedgerSaveResult(entryId = entryId, seriesId = replacementSeries.id)
    }

    private fun ledgerSeriesMatchesCreationRequest(
        series: LedgerSeriesEntity,
        draft: LedgerDraft,
    ): Boolean {
        val recurrence = requireNotNull(draft.recurrence)
        return series.type == draft.type &&
            series.amountCents == draft.amountCents &&
            series.startEpochDay == draft.epochDay &&
            series.recurrenceUnit == recurrence.unit &&
            series.intervalCount == recurrence.interval &&
            series.endEpochDay == recurrence.endEpochDay &&
            series.note == draft.note.trim() &&
            series.merchant == draft.merchant.trim() &&
            series.tagsCsv == normalizeTags(draft.tags)
    }

    private suspend fun insertLedgerStartOccurrenceIfDue(
        series: LedgerSeriesEntity,
        throughEpochDay: Long,
        now: Long,
        clientOperationToken: String? = null,
    ): Long? {
        if (series.startEpochDay > throughEpochDay) return null
        val occurrence = LedgerEntryEntity(
            seriesId = series.id,
            occurrenceEpochDay = series.startEpochDay,
            type = series.type,
            amountCents = series.amountCents,
            epochDay = series.startEpochDay,
            minuteOfDay = GENERATED_LEDGER_MINUTE,
            note = series.note,
            merchant = series.merchant,
            tagsCsv = series.tagsCsv,
            createdAt = now,
            updatedAt = now,
            clientOperationToken = clientOperationToken,
        )
        val insertedId = dao.insertLedgerEntry(occurrence)
        return if (insertedId > 0) {
            insertedId
        } else {
            dao.getLedgerOccurrence(series.id, series.startEpochDay)?.id
        }
    }

    private suspend fun insertStandaloneLedgerEntry(
        draft: LedgerDraft,
        existing: LedgerEntryEntity?,
        now: Long,
        clientOperationToken: String? = null,
    ): Long {
        val updatedAt = existing?.let { row ->
            monotonicMutationTimestamp(now, row.createdAt, row.updatedAt)
        } ?: now
        val entity = LedgerEntryEntity(
            id = existing?.id ?: 0,
            type = draft.type,
            amountCents = draft.amountCents,
            epochDay = draft.epochDay,
            minuteOfDay = draft.minuteOfDay,
            note = draft.note.trim(),
            merchant = draft.merchant.trim(),
            tagsCsv = normalizeTags(draft.tags),
            createdAt = existing?.createdAt ?: now,
            updatedAt = updatedAt,
            deletedAt = existing?.deletedAt,
            clientOperationToken = existing?.clientOperationToken ?: clientOperationToken,
        )
        return if (existing == null) {
            dao.insertLedgerEntry(entity).also { require(it > 0) { "Unable to save ledger entry" } }
        } else {
            dao.updateLedgerEntry(entity)
            entity.id
        }
    }

    private suspend fun updateLedgerEntryFromDraft(
        existing: LedgerEntryEntity,
        draft: LedgerDraft,
        seriesId: Long?,
        occurrenceEpochDay: Long?,
        minuteOfDay: Int,
        now: Long,
    ): Long {
        val updatedAt = monotonicMutationTimestamp(now, existing.createdAt, existing.updatedAt)
        dao.updateLedgerEntry(
            existing.copy(
                seriesId = seriesId,
                occurrenceEpochDay = occurrenceEpochDay,
                type = draft.type,
                amountCents = draft.amountCents,
                epochDay = draft.epochDay,
                minuteOfDay = minuteOfDay,
                note = draft.note.trim(),
                merchant = draft.merchant.trim(),
                tagsCsv = normalizeTags(draft.tags),
                updatedAt = updatedAt,
            ),
        )
        return existing.id
    }

    private suspend fun insertLedgerSeries(
        draft: LedgerDraft,
        now: Long,
        clientOperationToken: String? = null,
    ): LedgerSeriesEntity {
        val recurrence = requireNotNull(draft.recurrence)
        val series = LedgerSeriesEntity(
            type = draft.type,
            amountCents = draft.amountCents,
            startEpochDay = draft.epochDay,
            recurrenceUnit = recurrence.unit,
            intervalCount = recurrence.interval,
            endEpochDay = recurrence.endEpochDay,
            note = draft.note.trim(),
            merchant = draft.merchant.trim(),
            tagsCsv = normalizeTags(draft.tags),
            createdAt = now,
            updatedAt = now,
            clientOperationToken = clientOperationToken,
        )
        return series.copy(id = dao.insertLedgerSeries(series))
    }

    private suspend fun softDeleteLedgerSeriesTail(
        seriesId: Long,
        fromEpochDay: Long,
        deletedAt: Long,
        attachmentsPendingAt: Long,
        knownAffectedIds: List<Long>? = null,
    ) {
        val affectedIds = knownAffectedIds
            ?: dao.getActiveLedgerOccurrenceIdsFrom(seriesId, fromEpochDay)
        dao.softDeleteLedgerOccurrencesFrom(seriesId, fromEpochDay, deletedAt)
        affectedIds.forEach { entryId ->
            dao.markActiveOwnerAttachmentsForDeletion(
                AttachmentOwnerType.LEDGER.name,
                entryId,
                attachmentsPendingAt,
            )
        }
    }

    private fun todoGenerationHorizon(throughEpochDay: Long, reminderOffsetsCsv: String): Long {
        val offsets = reminderOffsetsCsv
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
        return RecurrenceEngine.reminderAwareGenerationHorizon(
            throughEpochDay = throughEpochDay,
            reminderOffsetsMinutes = offsets,
            maxLookaheadDays = MAX_TODO_REMINDER_LOOKAHEAD_DAYS,
        )
    }

    private fun undoDeadline(now: Long, undoWindowMillis: Long): Long = try {
        Math.addExact(now, undoWindowMillis)
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("Undo window is too large")
    }

    private suspend fun maxTodoMutationPredecessor(ids: List<Long>): Long? = ids
        .chunked(SQLITE_SAFE_ID_CHUNK_SIZE)
        .mapNotNull { chunk -> dao.getMaxTodoMutationPredecessor(chunk) }
        .maxOrNull()

    private suspend fun maxLedgerMutationPredecessor(ids: List<Long>): Long? = ids
        .chunked(SQLITE_SAFE_ID_CHUNK_SIZE)
        .mapNotNull { chunk -> dao.getMaxLedgerMutationPredecessor(chunk) }
        .maxOrNull()

    private suspend fun maxAttachmentCreatedAt(
        ownerType: AttachmentOwnerType,
        ownerIds: List<Long>,
    ): Long? = ownerIds
        .chunked(SQLITE_SAFE_ID_CHUNK_SIZE)
        .mapNotNull { chunk -> dao.getMaxAttachmentCreatedAt(ownerType.name, chunk) }
        .maxOrNull()

    /**
     * Makes the deadline double as an operation-scoped Undo token without changing its cleanup
     * semantics. A pre-existing independently pending attachment owned by any affected row must
     * never share the value, otherwise Undo could revive a file this delete did not mark.
     */
    private suspend fun uniqueOwnerAttachmentDeletionDeadline(
        ownerType: AttachmentOwnerType,
        ownerIds: List<Long>,
        minimumDeadline: Long,
    ): Long {
        var candidate = minimumDeadline
        while (
            ownerIds.any { ownerId ->
                dao.ownerHasAttachmentMarkedAt(ownerType.name, ownerId, candidate)
            }
        ) {
            if (candidate == Long.MAX_VALUE) {
                throw IllegalArgumentException("No attachment deletion deadline is available")
            }
            candidate += 1L
        }
        return candidate
    }

    suspend fun purgeSoftDeleted(before: Long = System.currentTimeMillis() - DEFAULT_UNDO_WINDOW) =
        database.withTransaction {
            dao.purgeDeletedTodos(before)
            dao.purgeDeletedLedgerEntries(before)
        }

    suspend fun getTodo(todoId: Long) = dao.getTodo(todoId)
    suspend fun getLedgerEntry(entryId: Long) = dao.getLedgerEntry(entryId)
    suspend fun getSubtasks(todoId: Long) = dao.getSubtasks(todoId)
    suspend fun getReminders(todoId: Long) = dao.getReminders(todoId)
    suspend fun getTodoSeries(seriesId: Long) = dao.getTodoSeries(seriesId)
    suspend fun getLedgerSeries(seriesId: Long) = dao.getLedgerSeries(seriesId)

    companion object {
        const val DEFAULT_UNDO_WINDOW = 6_000L
        const val NO_LEDGER_ENTRY_ID = 0L

        /** Bounds eager task materialization even if imported data contains an extreme offset. */
        const val MAX_TODO_REMINDER_LOOKAHEAD_DAYS = 366L

        internal const val OCCURRENCE_PAGE_SIZE = 256
        internal const val MAX_OCCURRENCES_PER_CATCH_UP = 2_048

        private const val GENERATED_LEDGER_MINUTE = 0
        private const val SQLITE_SAFE_ID_CHUNK_SIZE = 900
    }
}

internal fun uniquePromotedCategoryName(
    preferredName: String,
    occupiedNames: Set<String>,
): String {
    if (preferredName !in occupiedNames) return preferredName

    var suffix = 2
    while (true) {
        val candidate = "$preferredName ($suffix)"
        if (candidate !in occupiedNames) return candidate
        suffix += 1
    }
}
