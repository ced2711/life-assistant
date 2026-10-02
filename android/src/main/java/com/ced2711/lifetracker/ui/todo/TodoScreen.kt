package com.ced2711.lifetracker.ui.todo

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import com.ced2711.lifetracker.domain.model.TodoDraft
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.collectDistinctTodoTags
import com.ced2711.lifetracker.ui.TaskLedgerViewModel
import com.ced2711.lifetracker.ui.attachment.rememberAttachmentOpener
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.translateUiText
import com.ced2711.lifetracker.ui.localization.uiLocale
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The Todo page: connects the list ([TodoContent]) and the editor ([TodoEditor]) to the data, and
 * owns what must survive rotation and folding: the open editor, the filters and unfinished saves.
 */
@Composable
fun TodoScreen(
    viewModel: TaskLedgerViewModel,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
    requestedTodoId: Long? = null,
    quickAddRequestToken: String? = null,
    onQuickAddRequestHandled: (String) -> Unit = {},
    onRequestedTodoHandled: (Long) -> Unit = {},
) {
    val uiOperations: TodoUiOperationsViewModel = composeViewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, uiOperations) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) uiOperations.reconcileUndoWindows()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val activeTodos by viewModel.activeTodos.collectAsStateWithLifecycle()
    val completedTodos by viewModel.completedTodos.collectAsStateWithLifecycle()
    val todoSeries by viewModel.todoSeries.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val subtasksByTodo by viewModel.subtasksByTodo.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val editorSnackbarHostState = remember { SnackbarHostState() }
    val screenScope = rememberCoroutineScope()
    val uiLanguage = LocalUiLanguage.current
    val listState = rememberLazyListState()
    val quickAddFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    var quick by rememberSaveable(stateSaver = TodoQuickAddSaver) { mutableStateOf(TodoQuickAdd()) }
    var filter by rememberSaveable(stateSaver = TodoFilterSaver) { mutableStateOf(TodoFilter()) }
    var observedCategoryIds by remember { mutableStateOf<Set<Long>?>(null) }
    var editorDraftBundle by rememberSaveable { mutableStateOf<Bundle?>(null) }
    var editorRequestId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorSessionKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editorLoadGeneration by rememberSaveable { mutableIntStateOf(0) }
    var editorIsSeries by rememberSaveable { mutableStateOf(false) }
    var deleteTargetId by rememberSaveable { mutableStateOf<Long?>(null) }

    val editorDraft = remember(editorDraftBundle) { editorDraftBundle?.toTodoDraft() }
    val allTodos = activeTodos + completedTodos
    val deleteTarget = allTodos.firstOrNull { it.id == deleteTargetId }
    // The inline editor of wide screens shows messages in the page; the sheet has its own host.
    val inlineEditor = isWide
    val editorMessages = if (inlineEditor) snackbarHostState else editorSnackbarHostState

    // A widget asked to add a todo: bring the quick add line into view and open the keyboard.
    LaunchedEffect(quickAddRequestToken, editorDraftBundle != null, deleteTarget != null) {
        val requestToken = quickAddRequestToken ?: return@LaunchedEffect
        if (editorDraftBundle != null || deleteTarget != null) return@LaunchedEffect
        listState.scrollToItem(0)
        runCatching { quickAddFocus.requestFocus() }
        keyboard?.show()
        onQuickAddRequestHandled(requestToken)
    }

    LaunchedEffect(viewModel, editorDraft != null, uiLanguage) {
        viewModel.errors.collect { message ->
            (if (editorDraft != null) editorMessages else snackbarHostState).showSnackbar(translateUiText(message, uiLanguage))
        }
    }

    val pendingDelete = uiOperations.pendingDeletes.firstOrNull()
    LaunchedEffect(pendingDelete, uiLanguage) {
        val item = pendingDelete ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage(item.title, item.includedFuture, uiLanguage),
            actionLabel = translateUiText("Undo", uiLanguage),
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteTodo(item.receipt)
        uiOperations.consumeDelete(item)
    }

    val pendingAttachmentDelete = uiOperations.pendingAttachmentDeletes.firstOrNull()
    LaunchedEffect(pendingAttachmentDelete, uiLanguage) {
        val item = pendingAttachmentDelete ?: return@LaunchedEffect
        val result = editorMessages.showSnackbar(
            message = removedMessage(item.originalName, uiLanguage),
            actionLabel = translateUiText("Undo", uiLanguage),
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoRemoveAttachment(item.token)
        uiOperations.consumeAttachmentDelete(item)
    }

    LaunchedEffect(editorRequestId, editorLoadGeneration, editorDraftBundle == null) {
        val requestedId = editorRequestId ?: return@LaunchedEffect
        if (editorDraftBundle != null) return@LaunchedEffect
        viewModel.loadTodoDraft(requestedId) { loadedDraft ->
            if (editorRequestId == requestedId) {
                editorIsSeries = loadedDraft.recurrence != null
                editorDraftBundle = loadedDraft.toBundle()
            }
        }
    }

    // A category that was deleted elsewhere (sync, another screen) must not stay selected.
    val currentCategoryIds = remember(categories) { categories.mapTo(hashSetOf()) { it.id } }
    LaunchedEffect(currentCategoryIds) {
        val previous = observedCategoryIds
        if (previous != null && previous != currentCategoryIds) {
            filter = filter.copy(categories = sanitizeTodoCategoryFilters(filter.categories, currentCategoryIds))
        }
        observedCategoryIds = currentCategoryIds
    }

    val seriesTags = remember(todoSeries) { todoSeries.map { it.tagsCsv } }
    val availableTags = remember(activeTodos, completedTodos, seriesTags) {
        collectDistinctTodoTags((activeTodos + completedTodos).map(TodoEntity::tagsCsv) + seriesTags)
    }

    fun deleteWithUndo(todo: TodoEntity, deleteScope: SeriesEditScope) {
        viewModel.deleteTodo(todo.id, deleteScope) { receipt ->
            uiOperations.publishDelete(
                PendingTodoDelete(
                    title = todo.title,
                    includedFuture = deleteScope == SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES,
                    receipt = receipt,
                ),
            )
        }
    }

    fun requestDelete(todo: TodoEntity) {
        if (todo.seriesId == null) deleteWithUndo(todo, SeriesEditScope.ONLY_THIS_OCCURRENCE) else deleteTargetId = todo.id
    }

    fun completeWithMomentum(todoId: Long, completeSubtasks: Boolean) {
        val today = LocalDate.now().toEpochDay()
        val completedCount = dailyTodoMomentum(activeTodos, completedTodos, today).completedToday + 1
        viewModel.completeTodo(todoId, completeSubtasks) {
            screenScope.launch {
                snackbarHostState.showSnackbar(translateUiText(completionCelebrationMessage(completedCount), uiLanguage))
            }
        }
    }

    fun addQuick() {
        if (quick.inFlight) return
        val result = buildQuickTodoDraft(
            description = quick.description,
            enabledFields = settings.todoQuickAddFields,
            deadlineText = quick.deadlineText,
            priority = quick.priority,
            categoryId = quick.categoryId,
            tagsText = quick.tagsText,
            todayEpochDay = LocalDate.now().toEpochDay(),
            defaultReminderOffsets = settings.defaultReminderOffsetsMinutes,
            dateFormat = settings.dateFormat,
            locale = uiLocale(uiLanguage),
        )
        when (result.error) {
            QuickTodoDraftError.DESCRIPTION_REQUIRED -> quick = quick.copy(error = "Description is required")
            QuickTodoDraftError.INVALID_DEADLINE -> quick = quick.copy(error = "Enter a valid deadline")
            null -> {
                var draft = result.draft ?: return
                // A todo added while looking at Today or the next 7 days is due today.
                if (draft.deadlineEpochDay == null && (filter.view == TodoView.TODAY || filter.view == TodoView.UPCOMING)) {
                    draft = draft.copy(
                        deadlineEpochDay = LocalDate.now().toEpochDay(),
                        reminderOffsetsMinutes = settings.defaultReminderOffsetsMinutes.filter { it >= 0 }.sorted(),
                    )
                }
                quick = quick.copy(inFlight = true, error = null)
                viewModel.addQuickTodo(
                    draft = draft,
                    onSaved = { quick = TodoQuickAdd() },
                    onFailure = { quick = quick.copy(inFlight = false) },
                )
            }
        }
    }

    fun requestEdit(todoId: Long, isSeries: Boolean) {
        uiOperations.abandonEditor(editorSessionKey)
        editorDraftBundle = null
        editorRequestId = todoId
        editorSessionKey = UUID.randomUUID().toString()
        editorLoadGeneration += 1
        editorIsSeries = isSeries
    }

    LaunchedEffect(requestedTodoId) {
        val todoId = requestedTodoId ?: return@LaunchedEffect
        requestEdit(todoId, isSeries = allTodos.firstOrNull { it.id == todoId }?.seriesId != null)
        onRequestedTodoHandled(todoId)
    }

    fun closeEditor() {
        val closingSessionKey = editorSessionKey
        editorDraftBundle = null
        editorRequestId = null
        editorSessionKey = null
        editorIsSeries = false
        uiOperations.abandonEditor(closingSessionKey)
    }

    LaunchedEffect(uiOperations.savedEditorKey, editorSessionKey) {
        val savedKey = uiOperations.savedEditorKey ?: return@LaunchedEffect
        if (savedKey == editorSessionKey) {
            uiOperations.consumeSaved(savedKey)
            closeEditor()
        }
    }

    fun save(sessionKey: String, savedDraft: TodoDraft, editScope: SeriesEditScope, attachmentUris: List<Uri>) {
        val saveAttempt = uiOperations.beginSave(sessionKey) ?: return
        val ownerRequest = "$savedDraft|scope=$editScope"
        if (savedDraft.id == null) uiOperations.clientOperationToken(sessionKey, ownerRequest)
        val previouslySavedOwnerId = uiOperations.savedOwnerId(sessionKey, ownerRequest)

        fun copyAttachmentsAndFinish(todoId: Long) {
            if (attachmentUris.isEmpty()) {
                uiOperations.markSaveSucceeded(sessionKey, saveAttempt)
                return
            }
            val copyAttemptId = uiOperations.attachmentCopyAttemptId(sessionKey, attachmentUris.map(Uri::toString))
            val copyJob = viewModel.addAttachments(
                ownerType = AttachmentOwnerType.TODO,
                ownerId = todoId,
                uris = attachmentUris,
                copyAttemptId = copyAttemptId,
                onCopied = { uiOperations.markSaveSucceeded(sessionKey, saveAttempt) },
                onCopyFailed = { uiOperations.markSaveFailed(sessionKey, saveAttempt) },
            )
            uiOperations.trackSaveJob(sessionKey, saveAttempt, copyJob)
        }

        // A retry after the todo was saved but its files were not must update that todo, never
        // create a second one.
        fun saveOwnerAndCopy(ownerId: Long?) {
            val retrySafeDraft = ownerId?.let { savedDraft.copy(id = it) } ?: savedDraft
            val operationToken = if (savedDraft.id == null && ownerId == null) {
                uiOperations.clientOperationToken(sessionKey, ownerRequest)
            } else {
                null
            }
            val saveJob = viewModel.saveTodo(
                draft = retrySafeDraft,
                scope = editScope,
                clientOperationToken = operationToken,
                onSaved = { todoId ->
                    uiOperations.markOwnerSaved(sessionKey, saveAttempt, todoId, ownerRequest)
                    copyAttachmentsAndFinish(todoId)
                },
                onFailure = { uiOperations.markSaveFailed(sessionKey, saveAttempt) },
            )
            uiOperations.trackSaveJob(sessionKey, saveAttempt, saveJob)
        }

        if (previouslySavedOwnerId == null) {
            saveOwnerAndCopy(null)
        } else {
            val validationJob = viewModel.attachmentOwnerExists(
                ownerType = AttachmentOwnerType.TODO,
                ownerId = previouslySavedOwnerId,
                onResult = { ownerExists ->
                    if (ownerExists) {
                        saveOwnerAndCopy(previouslySavedOwnerId)
                    } else {
                        uiOperations.rejectSavedOwner(sessionKey, previouslySavedOwnerId)
                        saveOwnerAndCopy(null)
                    }
                },
                onFailure = { uiOperations.markSaveFailed(sessionKey, saveAttempt) },
            )
            uiOperations.trackSaveJob(sessionKey, saveAttempt, validationJob)
        }
    }

    val openAttachment = rememberAttachmentOpener { message ->
        screenScope.launch { editorMessages.showSnackbar(translateUiText(message, uiLanguage)) }
    }

    val editor: (@Composable () -> Unit)? = editorDraft?.let { draft ->
        val sessionKey = editorSessionKey ?: return@let null
        {
            val attachmentFlow = remember(draft.id) {
                draft.id?.let { viewModel.attachments(AttachmentOwnerType.TODO, it) } ?: flowOf(emptyList())
            }
            val existingAttachments by attachmentFlow.collectAsStateWithLifecycle(initialValue = emptyList())
            TodoEditor(
                initialDraft = draft,
                inline = inlineEditor,
                categories = categories,
                availableTags = availableTags,
                settings = settings,
                existingAttachments = existingAttachments,
                editingSeriesOccurrence = editorIsSeries,
                isSaving = uiOperations.savingEditorKey == sessionKey,
                recordSavedAwaitingAttachments = uiOperations.hasSavedOwner(sessionKey),
                todoCompleted = draft.id?.let { id -> completedTodos.any { it.id == id } },
                snackbarHostState = editorMessages,
                onCompletionChange = { completed ->
                    draft.id?.let { id -> if (completed) viewModel.completeTodo(id, completeSubtasks = false) else viewModel.restoreTodo(id) }
                },
                onDismiss = ::closeEditor,
                onSave = { savedDraft, editScope, uris -> save(sessionKey, savedDraft, editScope, uris) },
                onDelete = draft.id?.let { id ->
                    {
                        val todo = allTodos.firstOrNull { it.id == id }
                        closeEditor()
                        if (todo != null) requestDelete(todo)
                    }
                },
                onOpenAttachment = openAttachment,
                onRemoveAttachment = { attachment ->
                    viewModel.removeAttachment(attachment.id) { token ->
                        uiOperations.publishAttachmentDelete(PendingTodoAttachmentDelete(attachment.originalName, token))
                    }
                },
            )
        }
    }

    BackHandler(enabled = inlineEditor && editor != null && uiOperations.savingEditorKey == null) { closeEditor() }

    TodoContent(
        active = activeTodos,
        completed = completedTodos,
        subtasksByTodo = subtasksByTodo,
        categories = categories,
        seriesTags = seriesTags,
        settings = settings,
        filter = filter,
        onFilterChange = { filter = it },
        quick = quick,
        onQuickChange = { quick = it },
        onQuickAdd = ::addQuick,
        onOpen = { todo -> requestEdit(todo.id, todo.seriesId != null) },
        onNew = {
            uiOperations.abandonEditor(editorSessionKey)
            editorRequestId = null
            editorSessionKey = UUID.randomUUID().toString()
            editorIsSeries = false
            editorDraftBundle = TodoDraft(description = "").toBundle()
        },
        onToggle = { todo, done, withSubtasks ->
            if (done) completeWithMomentum(todo.id, withSubtasks) else viewModel.restoreTodo(todo.id)
        },
        onToggleSubtask = viewModel::setSubtaskCompleted,
        onDelete = ::requestDelete,
        onMove = viewModel::moveTodoRelativeToVisibleNeighbor,
        onAddCategory = viewModel::addCategory,
        onDeleteCategory = viewModel::deleteCategory,
        onRenameTag = { source, replacement -> viewModel.renameTodoTag(source, replacement) },
        onDeleteTag = { tag -> viewModel.deleteTodoTag(tag) },
        modifier = modifier,
        isWide = isWide,
        selectedTodoId = editorDraft?.id.takeIf { inlineEditor },
        quickAddFocus = quickAddFocus,
        listState = listState,
        snackbarHostState = snackbarHostState,
        editorPane = editor.takeIf { inlineEditor },
    )

    if (!inlineEditor) editor?.invoke()

    deleteTarget?.let { todo ->
        RecurringDeleteDialog(
            onDismiss = { deleteTargetId = null },
            onOnlyThis = {
                deleteWithUndo(todo, SeriesEditScope.ONLY_THIS_OCCURRENCE)
                deleteTargetId = null
            },
            onThisAndFuture = {
                deleteWithUndo(todo, SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES)
                deleteTargetId = null
            },
        )
    }
}

private fun deletedMessage(title: String, includedFuture: Boolean, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> if (includedFuture) "已删除“$title”及之后的重复" else "已删除“$title”"
    UiLanguage.ENGLISH -> if (includedFuture) "Deleted $title and future occurrences" else "Deleted $title"
}

private fun removedMessage(name: String, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> "已移除 $name"
    UiLanguage.ENGLISH -> "Removed $name"
}

private fun TodoDraft.toBundle(): Bundle = Bundle().apply {
    id?.let { putLong("id", it) }
    putString("title", title)
    putString("description", description)
    categoryId?.let { putLong("categoryId", it) }
    deadlineEpochDay?.let { putLong("deadlineEpochDay", it) }
    deadlineMinute?.let { putInt("deadlineMinute", it) }
    putString("priority", priority.name)
    putStringArrayList("tags", ArrayList(tags))
    putLongArray("reminderOffsets", reminderOffsetsMinutes.toLongArray())
    putStringArrayList("subtasks", ArrayList(subtasks))
    recurrence?.let { rule ->
        putString("recurrenceUnit", rule.unit.name)
        putInt("recurrenceInterval", rule.interval)
        rule.endEpochDay?.let { putLong("recurrenceEndEpochDay", it) }
    }
}

private fun Bundle.toTodoDraft(): TodoDraft = TodoDraft(
    id = nullableLong("id"),
    title = getString("title").orEmpty(),
    description = getString("description").orEmpty(),
    categoryId = nullableLong("categoryId"),
    deadlineEpochDay = nullableLong("deadlineEpochDay"),
    deadlineMinute = if (containsKey("deadlineMinute")) getInt("deadlineMinute") else null,
    priority = getString("priority")
        ?.let { saved -> TodoPriority.entries.firstOrNull { it.name == saved } }
        ?: TodoPriority.NONE,
    tags = getStringArrayList("tags").orEmpty(),
    reminderOffsetsMinutes = getLongArray("reminderOffsets")?.toList().orEmpty(),
    subtasks = getStringArrayList("subtasks").orEmpty(),
    recurrence = getString("recurrenceUnit")?.let { savedUnit ->
        RecurrenceRule(
            unit = RecurrenceUnit.entries.firstOrNull { it.name == savedUnit } ?: RecurrenceUnit.WEEK,
            interval = getInt("recurrenceInterval", 1),
            endEpochDay = nullableLong("recurrenceEndEpochDay"),
        )
    },
)

private fun Bundle.nullableLong(key: String): Long? = if (containsKey(key)) getLong(key) else null
