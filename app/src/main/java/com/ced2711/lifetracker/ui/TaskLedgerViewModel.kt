package com.ced2711.lifetracker.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ced2711.lifetracker.AppContainer
import com.ced2711.lifetracker.data.attachment.AttachmentDeletionToken
import com.ced2711.lifetracker.data.repository.DeleteUndoResult
import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerSaveResult
import com.ced2711.lifetracker.domain.model.NoteDraft
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurringDeleteResult
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TodoDraft
import com.ced2711.lifetracker.domain.model.TodoQuickAddField
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.WeekStart
import com.ced2711.lifetracker.domain.model.parseTags
import com.ced2711.lifetracker.worker.WorkScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import com.ced2711.lifetracker.worker.ReminderReschedulePolicy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskLedgerViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val settingsRepository = container.settingsRepository
    private val attachmentStore = container.attachmentStore

    init {
        launchAction {
            val catchUp = repository.catchUpRecurring()
            WorkScheduler.rescheduleReminders(
                container.appContext,
                ReminderReschedulePolicy.INCLUDE_DUE_WINDOW,
            )
            if (catchUp.hasMore) {
                WorkScheduler.enqueueCatchUpContinuation(
                    container.appContext,
                    ReminderReschedulePolicy.INCLUDE_DUE_WINDOW,
                    catchUp.nextCursor,
                )
            }
        }
    }

    val activeTodos = repository.activeTodos.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val completedTodos = repository.completedTodos.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val categories = repository.categories.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val ledgerEntries = repository.ledgerEntries.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val todoSeries = repository.todoSeries.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val ledgerSeries = repository.ledgerSeries.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val noteFolders = repository.noteFolders.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val notes = repository.notes.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val settings = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors = _errors.asSharedFlow()

    fun addQuickTodo(
        draft: TodoDraft,
        onSaved: (Long) -> Unit = {},
        onFailure: (String) -> Unit = {},
    ): Job = saveTodo(draft, onSaved = onSaved, onFailure = onFailure)

    fun saveTodo(
        draft: TodoDraft,
        scope: SeriesEditScope = SeriesEditScope.ONLY_THIS_OCCURRENCE,
        clientOperationToken: String? = null,
        onSaved: (Long) -> Unit = {},
        onFailure: (String) -> Unit = {},
    ): Job = launchAction(onFailure) {
        val savedTodoId = repository.saveTodo(draft, scope, clientOperationToken).also(onSaved)
        WorkScheduler.rescheduleRemindersAfterTodoSave(container.appContext, savedTodoId)
        if (draft.recurrence != null) {
            WorkScheduler.enqueueCatchUpContinuation(
                container.appContext,
                ReminderReschedulePolicy.FUTURE_ONLY,
            )
        }
    }

    fun loadTodoDraft(id: Long, onLoaded: (TodoDraft) -> Unit) = launchAction {
        val todo = repository.getTodo(id) ?: return@launchAction
        val subtasks = repository.getSubtasks(id).map { it.description }
        val reminders = repository.getReminders(id).map { it.offsetMinutes }
        val recurrence = todo.seriesId?.let { repository.getTodoSeries(it) }?.let {
            RecurrenceRule(it.recurrenceUnit, it.intervalCount, it.endEpochDay)
        }
        onLoaded(
            TodoDraft(
                id = todo.id,
                title = todo.title,
                description = todo.description,
                categoryId = todo.categoryId,
                deadlineEpochDay = todo.deadlineEpochDay,
                deadlineMinute = todo.deadlineMinute,
                priority = todo.priority,
                tags = parseTags(todo.tagsCsv),
                reminderOffsetsMinutes = reminders,
                subtasks = subtasks,
                recurrence = recurrence,
            ),
        )
    }

    fun completeTodo(
        id: Long,
        completeSubtasks: Boolean,
        onCompleted: () -> Unit = {},
    ) =
        launchAction {
            repository.completeTodo(id, completeSubtasks)
            WorkScheduler.rescheduleReminders(container.appContext)
            onCompleted()
        }

    fun restoreTodo(id: Long) = launchAction {
        repository.restoreTodo(id)
        WorkScheduler.rescheduleReminders(container.appContext)
    }
    fun deleteTodo(
        id: Long,
        scope: SeriesEditScope = SeriesEditScope.ONLY_THIS_OCCURRENCE,
        onDeleted: (RecurringDeleteResult) -> Unit = {},
    ) = launchAction {
        repository.deleteTodoWithResult(id, scope).also(onDeleted)
        WorkScheduler.rescheduleReminders(container.appContext)
    }
    fun undoDeleteTodo(
        result: RecurringDeleteResult,
        onResult: (DeleteUndoResult) -> Unit = {},
    ) = launchAction {
        val undoResult = repository.undoDeleteTodo(result)
        onResult(undoResult)
        reportDeleteUndoResult(undoResult)
        if (undoResult == DeleteUndoResult.RESTORED) {
            WorkScheduler.rescheduleReminders(container.appContext)
        }
    }
    fun setSubtaskCompleted(subtask: com.ced2711.lifetracker.data.local.SubtaskEntity, completed: Boolean) =
        launchAction { repository.setSubtaskCompleted(subtask, completed) }

    fun moveTodoRelativeToVisibleNeighbor(todoId: Long, adjacentVisibleTodoId: Long) =
        launchAction { repository.moveTodoRelativeToVisibleNeighbor(todoId, adjacentVisibleTodoId) }

    fun renameTodoTag(
        sourceTag: String,
        replacementTag: String,
        onRenamed: () -> Unit = {},
    ) = launchAction {
        repository.renameTodoTag(sourceTag, replacementTag)
        onRenamed()
    }

    fun deleteTodoTag(tag: String, onDeleted: () -> Unit = {}) = launchAction {
        repository.deleteTodoTag(tag)
        onDeleted()
    }

    fun addCategory(name: String, parentId: Long? = null) =
        launchAction { repository.addCategory(name, parentId) }

    fun deleteCategory(id: Long) = launchAction { repository.deleteCategory(id) }

    fun saveLedger(draft: LedgerDraft, onSaved: (Long) -> Unit = {}) = launchAction {
        repository.saveLedger(draft).also(onSaved)
        if (draft.recurrence != null) {
            WorkScheduler.enqueueCatchUpContinuation(
                container.appContext,
                ReminderReschedulePolicy.FUTURE_ONLY,
            )
        }
    }

    fun saveLedgerWithResult(
        draft: LedgerDraft,
        clientOperationToken: String? = null,
        onSaved: (LedgerSaveResult) -> Unit = {},
        onFailure: (String) -> Unit = {},
    ) = launchAction(onFailure) {
        onSaved(repository.saveLedgerWithResult(draft, clientOperationToken = clientOperationToken))
        if (draft.recurrence != null) {
            WorkScheduler.enqueueCatchUpContinuation(
                container.appContext,
                ReminderReschedulePolicy.FUTURE_ONLY,
            )
        }
    }

    fun editLedgerSeriesForFuture(
        seriesId: Long,
        effectiveEpochDay: Long,
        draft: LedgerDraft,
        onSaved: (LedgerSaveResult) -> Unit = {},
        onFailure: (String) -> Unit = {},
    ) = launchAction(onFailure) {
        onSaved(repository.editLedgerSeriesForFuture(seriesId, effectiveEpochDay, draft))
        WorkScheduler.enqueueCatchUpContinuation(
            container.appContext,
            ReminderReschedulePolicy.FUTURE_ONLY,
        )
    }

    fun loadLedgerDraft(id: Long, onLoaded: (LedgerDraft) -> Unit) = launchAction {
        val entry = repository.getLedgerEntry(id) ?: return@launchAction
        val recurrence = entry.seriesId?.let { repository.getLedgerSeries(it) }?.let {
            RecurrenceRule(it.recurrenceUnit, it.intervalCount, it.endEpochDay)
        }
        onLoaded(
            LedgerDraft(
                id = entry.id,
                type = entry.type,
                amountCents = entry.amountCents,
                epochDay = entry.epochDay,
                minuteOfDay = entry.minuteOfDay,
                note = entry.note,
                merchant = entry.merchant,
                tags = parseTags(entry.tagsCsv),
                recurrence = recurrence,
            ),
        )
    }

    fun deleteLedgerEntry(id: Long) = launchAction { repository.softDeleteLedgerEntry(id) }
    fun deleteLedgerEntry(
        id: Long,
        onDeleted: (RecurringDeleteResult) -> Unit,
        onFailure: (String) -> Unit = {},
    ) = launchAction(onFailure) {
        onDeleted(
            repository.deleteLedgerEntryWithResult(
                id,
                SeriesEditScope.ONLY_THIS_OCCURRENCE,
            ),
        )
    }
    fun undoDeleteLedgerEntry(
        result: RecurringDeleteResult,
        onResult: (DeleteUndoResult) -> Unit = {},
    ) = launchAction {
        val undoResult = repository.undoDeleteLedgerEntry(result)
        onResult(undoResult)
        reportDeleteUndoResult(undoResult)
    }
    fun stopTodoSeries(id: Long) = launchAction { repository.deactivateTodoSeries(id) }
    fun stopLedgerSeries(id: Long) = launchAction { repository.deactivateLedgerSeries(id) }

    fun addNoteFolder(
        name: String,
        parentId: Long? = null,
        onSaved: (Long) -> Unit = {},
        onFailure: (String) -> Unit = {},
    ) = launchAction(onFailure) { onSaved(repository.addNoteFolder(name, parentId)) }

    fun renameNoteFolder(
        folderId: Long,
        name: String,
        onSaved: () -> Unit = {},
        onFailure: (String) -> Unit = {},
    ) = launchAction(onFailure) {
        repository.renameNoteFolder(folderId, name)
        onSaved()
    }

    fun deleteNoteFolder(folderId: Long, onFailure: (String) -> Unit = {}) =
        launchAction(onFailure) { repository.deleteNoteFolder(folderId) }

    fun saveNote(
        draft: NoteDraft,
        onSaved: (Long) -> Unit = {},
        onFailure: (String) -> Unit = {},
    ) = launchAction(onFailure) { onSaved(repository.saveNote(draft)) }

    fun deleteNote(
        noteId: Long,
        onDeleted: () -> Unit = {},
        onFailure: (String) -> Unit = {},
    ) = launchAction(onFailure) {
        repository.deleteNote(noteId)
        onDeleted()
    }

    fun materializeCalendarTodoOccurrencesThrough(throughEpochDay: Long) = launchAction {
        val materialization = repository.materializeTodoOccurrencesThrough(throughEpochDay)
        if (materialization.hasMore) {
            WorkScheduler.enqueueTodoMaterializationContinuation(
                container.appContext,
                throughEpochDay,
                ReminderReschedulePolicy.FUTURE_ONLY,
                materialization.nextCursor,
            )
        }
    }

    fun setTheme(value: ThemeMode) = launchAction { settingsRepository.setTheme(value) }
    fun setAccentColor(value: AccentColor) = launchAction { settingsRepository.setAccentColor(value) }
    fun setUiLanguage(value: UiLanguage) = launchAction { settingsRepository.setUiLanguage(value) }
    fun setWeekStart(value: WeekStart) = launchAction { settingsRepository.setWeekStart(value) }
    fun setTimeFormat(value: TimeFormatOption) = launchAction { settingsRepository.setTimeFormat(value) }
    fun setDateFormat(value: DateFormatOption) = launchAction { settingsRepository.setDateFormat(value) }
    fun setNotificationsEnabled(value: Boolean) = launchAction {
        settingsRepository.setNotificationsEnabled(value)
        WorkScheduler.rescheduleReminders(container.appContext)
    }
    fun setAllDayReminderMinute(value: Int) =
        launchAction {
            settingsRepository.setDefaultAllDayReminderMinute(value)
            WorkScheduler.rescheduleReminders(container.appContext)
        }
    fun setDefaultReminderOffsetsMinutes(value: Set<Long>) =
        launchAction { settingsRepository.setDefaultReminderOffsetsMinutes(value) }
    fun setTodoQuickAddFields(value: Set<TodoQuickAddField>) =
        launchAction { settingsRepository.setTodoQuickAddFields(value) }
    fun setLastDestination(value: TopLevelDestination) =
        launchAction { settingsRepository.setLastDestination(value) }

    fun addAttachments(
        ownerType: AttachmentOwnerType,
        ownerId: Long,
        uris: List<Uri>,
        copyAttemptId: String,
        onCopied: () -> Unit = {},
        onCopyFailed: (String) -> Unit = {},
    ): Job = launchAction(onCopyFailed) {
        attachmentStore.copyAttachments(ownerType, ownerId, uris, copyAttemptId)
        onCopied()
    }

    fun attachments(ownerType: AttachmentOwnerType, ownerId: Long) =
        repository.attachments(ownerType, ownerId)

    fun attachmentOwnerExists(
        ownerType: AttachmentOwnerType,
        ownerId: Long,
        onResult: (Boolean) -> Unit,
        onFailure: (String) -> Unit = {},
    ): Job = launchAction(onFailure) {
        onResult(repository.attachmentOwnerExists(ownerType, ownerId))
    }

    fun removeAttachment(
        attachmentId: Long,
        onRemoved: (AttachmentDeletionToken) -> Unit = {},
    ) = launchAction {
        val token = requireNotNull(attachmentStore.markAttachmentForDeletion(attachmentId)) {
            "Attachment is no longer available."
        }
        onRemoved(token)
    }

    fun undoRemoveAttachment(token: AttachmentDeletionToken) = launchAction {
        check(attachmentStore.undoAttachmentDeletion(token)) {
            "The attachment can no longer be restored."
        }
    }

    fun subtasks(todoId: Long) = repository.subtasks(todoId)

    private suspend fun reportDeleteUndoResult(result: DeleteUndoResult) {
        val message = when (result) {
            DeleteUndoResult.RESTORED -> return
            DeleteUndoResult.EXPIRED -> "The Undo window has expired."
            DeleteUndoResult.UNAVAILABLE -> "This item can no longer be restored."
        }
        _errors.emit(message)
    }

    private fun launchAction(
        onFailure: (String) -> Unit = {},
        block: suspend () -> Unit,
    ): Job = viewModelScope.launch {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            val message = error.message ?: "Something went wrong"
            onFailure(message)
            _errors.emit(message)
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TaskLedgerViewModel(container) as T
    }
}
