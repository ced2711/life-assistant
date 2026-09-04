package com.ced2711.lifetracker.ui.todo

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.translateUiText
import com.ced2711.lifetracker.ui.localization.uiLocale
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.ced2711.lifetracker.data.attachment.AttachmentDeletionToken
import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.data.local.SubtaskEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.date.SmartDateParser
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.RecurringDeleteResult
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import com.ced2711.lifetracker.domain.model.TodoDraft
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.TodoQuickAddField
import com.ced2711.lifetracker.domain.model.acceptTodoTagSuggestion
import com.ced2711.lifetracker.domain.model.categoryIdsIncludedByTodoFilter
import com.ced2711.lifetracker.domain.model.categoryPathLabel
import com.ced2711.lifetracker.domain.model.collectDistinctTodoTags
import com.ced2711.lifetracker.domain.model.todoTagSuggestions
import com.ced2711.lifetracker.ui.TaskLedgerViewModel
import com.ced2711.lifetracker.ui.remainingUndoMillis
import com.ced2711.lifetracker.ui.adaptive.HingeSafeAlertDialog
import com.ced2711.lifetracker.ui.adaptive.HingeSafeDialog
import com.ced2711.lifetracker.ui.adaptive.hingeSafeDialogSurface
import com.ced2711.lifetracker.ui.adaptive.rememberHingeSafePlatformDialogLauncher
import com.ced2711.lifetracker.ui.attachment.rememberAttachmentOpener
import com.ced2711.lifetracker.ui.components.CustomReminderOffsetInput
import com.ced2711.lifetracker.ui.components.formatReminderOffset
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private enum class TodoSort(val label: String) {
    DEADLINE("Deadline"),
    CREATED("Created"),
    CUSTOM("Custom"),
}

private const val MAX_ATTACHMENTS = 10
internal const val ALL_CATEGORIES_FILTER_KEY = Long.MIN_VALUE
internal const val UNCATEGORIZED_FILTER_KEY = Long.MIN_VALUE + 1

private val stringListSaver = listSaver<List<String>, String>(
    save = { it },
    restore = { it.toList() },
)

private val longListSaver = listSaver<List<Long>, Long>(
    save = { it },
    restore = { it.toList() },
)

private val longSetSaver = listSaver<Set<Long>, Long>(
    save = { it.toList() },
    restore = { it.toSet() },
)

internal fun toggleTodoCategoryFilter(current: Set<Long>, key: Long): Set<Long> {
    if (key == ALL_CATEGORIES_FILTER_KEY) return setOf(ALL_CATEGORIES_FILTER_KEY)
    val specific = current - ALL_CATEGORIES_FILTER_KEY
    return if (key in specific) {
        (specific - key).ifEmpty { setOf(ALL_CATEGORIES_FILTER_KEY) }
    } else {
        specific + key
    }
}

internal fun sanitizeTodoCategoryFilters(
    current: Set<Long>,
    validCategoryIds: Set<Long>,
): Set<Long> {
    if (ALL_CATEGORIES_FILTER_KEY in current) return setOf(ALL_CATEGORIES_FILTER_KEY)
    return current
        .filterTo(linkedSetOf()) { it == UNCATEGORIZED_FILTER_KEY || it in validCategoryIds }
        .ifEmpty { setOf(ALL_CATEGORIES_FILTER_KEY) }
}

internal data class PendingTodoDelete(
    val title: String,
    val includedFuture: Boolean,
    val receipt: RecurringDeleteResult,
)

internal data class PendingTodoAttachmentDelete(
    val originalName: String,
    val token: AttachmentDeletionToken,
)

/** Retains in-flight UI operation receipts across activity recreation without persisting stale work. */
internal class TodoUiOperationsViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    var savingEditorKey by mutableStateOf<String?>(null)
        private set
    var savedEditorKey by mutableStateOf<String?>(null)
        private set
    var pendingDeletes by mutableStateOf<List<PendingTodoDelete>>(emptyList())
        private set
    var pendingAttachmentDeletes by mutableStateOf<List<PendingTodoAttachmentDelete>>(emptyList())
        private set

    private var ownerSessionKey: String? = savedStateHandle[OWNER_SESSION_KEY]
    private var ownerRequestDigest: String? = savedStateHandle[OWNER_REQUEST_DIGEST_KEY]
    private var clientOperationToken: String? = savedStateHandle[CLIENT_OPERATION_TOKEN_KEY]
    private var savedOwnerId by mutableStateOf(savedStateHandle.get<Long>(OWNER_ID_KEY))
    private var saveTimeoutJob: Job? = null
    private var activeSaveJob: Job? = null
    private var nextSaveAttempt = 0L
    private var activeSaveAttempt: Long? = null
    private var activeSaveEditorKey: String? = null
    private var attachmentRequestEditorKey: String? = savedStateHandle[COPY_EDITOR_KEY]
    private var attachmentRequestDigest: String? = savedStateHandle[COPY_REQUEST_DIGEST_KEY]
    private var attachmentCopyAttemptId: String? = savedStateHandle[COPY_ATTEMPT_ID_KEY]
    private val deleteExpiryJobs = mutableMapOf<String, Job>()
    private val attachmentExpiryJobs = mutableMapOf<Long, Job>()
    private var elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime
    private var expiryScheduler: (Long, () -> Unit) -> Job = { delayMillis, onExpired ->
        viewModelScope.launch {
            delay(delayMillis)
            onExpired()
        }
    }

    internal constructor(
        savedStateHandle: SavedStateHandle,
        elapsedRealtimeMillis: () -> Long,
        expiryScheduler: (Long, () -> Unit) -> Job,
    ) : this(savedStateHandle) {
        this.elapsedRealtimeMillis = elapsedRealtimeMillis
        this.expiryScheduler = expiryScheduler
    }

    init {
        val completeOperationRecovery = ownerSessionKey != null &&
            ownerRequestDigest != null && clientOperationToken != null
        if (!completeOperationRecovery) clearOwnerRecovery()
        val completeCopyRecovery = attachmentRequestEditorKey != null &&
            attachmentRequestDigest != null && attachmentCopyAttemptId != null
        if (!completeCopyRecovery) clearAttachmentCopyRequest()
    }

    fun beginSave(editorKey: String): Long? {
        if (savingEditorKey != null) return null
        saveTimeoutJob?.cancel()
        val attempt = ++nextSaveAttempt
        activeSaveAttempt = attempt
        activeSaveEditorKey = editorKey
        savingEditorKey = editorKey
        savedEditorKey = null
        saveTimeoutJob = viewModelScope.launch {
            delay(SAVE_TIMEOUT_MILLIS)
            if (!isActive(editorKey, attempt)) return@launch

            // Invalidate callbacks first, then wait for the real I/O job to finish cancelling.
            // Retry stays disabled until the old copy releases AttachmentStore's mutex.
            activeSaveAttempt = null
            activeSaveJob?.cancelAndJoin()
            if (activeSaveEditorKey == editorKey && activeSaveAttempt == null) {
                activeSaveJob = null
                activeSaveEditorKey = null
                savingEditorKey = null
                saveTimeoutJob = null
            }
        }
        return attempt
    }

    fun trackSaveJob(editorKey: String, attempt: Long, job: Job) {
        if (!isActive(editorKey, attempt)) {
            job.cancel()
            return
        }
        activeSaveJob = job
    }

    fun attachmentCopyAttemptId(editorKey: String, uriStrings: List<String>): String {
        val requestDigest = digest(uriStrings.distinct().joinToString(separator = "\u0000"))
        if (
            attachmentRequestEditorKey == editorKey &&
            attachmentRequestDigest == requestDigest &&
            attachmentCopyAttemptId != null
        ) {
            return requireNotNull(attachmentCopyAttemptId)
        }
        clearAttachmentCopyRequest()
        return deterministicUuid(
            "todo-copy\u0000$editorKey\u0000${ownerRequestDigest.orEmpty()}\u0000$requestDigest",
        ).also { attemptId ->
            attachmentRequestEditorKey = editorKey
            attachmentRequestDigest = requestDigest
            attachmentCopyAttemptId = attemptId
            savedStateHandle[COPY_EDITOR_KEY] = editorKey
            savedStateHandle[COPY_REQUEST_DIGEST_KEY] = requestDigest
            savedStateHandle[COPY_ATTEMPT_ID_KEY] = attemptId
        }
    }

    fun hasSavedOwner(editorKey: String): Boolean =
        ownerSessionKey == editorKey && savedOwnerId != null

    fun clientOperationToken(editorKey: String, ownerRequest: String): String {
        val requestDigest = digest(ownerRequest)
        if (ownerSessionKey == editorKey && clientOperationToken != null) {
            updateOwnerRequestDigest(editorKey, requestDigest)
            return requireNotNull(clientOperationToken)
        }
        clearOwnerRecovery()
        clearAttachmentCopyRequest()
        return deterministicUuid("todo-owner\u0000$editorKey").also { token ->
            ownerSessionKey = editorKey
            ownerRequestDigest = requestDigest
            clientOperationToken = token
            savedStateHandle[OWNER_SESSION_KEY] = editorKey
            savedStateHandle[OWNER_REQUEST_DIGEST_KEY] = requestDigest
            savedStateHandle[CLIENT_OPERATION_TOKEN_KEY] = token
        }
    }

    fun savedOwnerId(editorKey: String, ownerRequest: String): Long? {
        if (ownerSessionKey != editorKey) return null
        updateOwnerRequestDigest(editorKey, digest(ownerRequest))
        return savedOwnerId
    }

    fun markOwnerSaved(editorKey: String, attempt: Long, ownerId: Long, ownerRequest: String) {
        if (!isActive(editorKey, attempt)) return
        clientOperationToken(editorKey, ownerRequest)
        savedOwnerId = ownerId
        savedStateHandle[OWNER_ID_KEY] = ownerId
    }

    fun rejectSavedOwner(editorKey: String, ownerId: Long) {
        if (ownerSessionKey != editorKey || savedOwnerId != ownerId) return
        clearOwnerRecovery()
        clearAttachmentCopyRequest(editorKey)
    }

    fun markSaveSucceeded(editorKey: String, attempt: Long) {
        if (!isActive(editorKey, attempt)) return
        saveTimeoutJob?.cancel()
        saveTimeoutJob = null
        activeSaveJob = null
        activeSaveAttempt = null
        activeSaveEditorKey = null
        savingEditorKey = null
        clearOwnerRecovery()
        clearAttachmentCopyRequest(editorKey)
        savedEditorKey = editorKey
    }

    fun markSaveFailed(editorKey: String, attempt: Long) {
        if (!isActive(editorKey, attempt)) return
        saveTimeoutJob?.cancel()
        saveTimeoutJob = null
        activeSaveJob = null
        activeSaveAttempt = null
        activeSaveEditorKey = null
        savingEditorKey = null
    }

    fun consumeSaved(editorKey: String) {
        if (savedEditorKey == editorKey) savedEditorKey = null
    }

    fun abandonEditor(editorKey: String?) {
        if (editorKey == null) return
        if (savingEditorKey == editorKey) {
            saveTimeoutJob?.cancel()
            saveTimeoutJob = null
            activeSaveJob?.cancel()
            activeSaveJob = null
            savingEditorKey = null
        }
        if (activeSaveEditorKey == editorKey) {
            activeSaveAttempt = null
            activeSaveEditorKey = null
        }
        if (savedEditorKey == editorKey) savedEditorKey = null
        if (ownerSessionKey == editorKey) {
            clearOwnerRecovery()
        }
        clearAttachmentCopyRequest(editorKey)
    }

    fun publishDelete(
        item: PendingTodoDelete,
        nowElapsedRealtimeMillis: Long = elapsedRealtimeMillis(),
    ) {
        reconcileUndoWindows(nowElapsedRealtimeMillis)
        val key = deleteKey(item)
        val remaining = remainingUndoMillis(
            item.receipt.undoExpiresAtElapsedRealtime,
            nowElapsedRealtimeMillis,
        )
        if (remaining == 0L) return
        pendingDeletes = pendingDeletes.filterNot { deleteKey(it) == key } + item
        scheduleDeleteExpiry(item, remaining)
    }

    fun consumeDelete(item: PendingTodoDelete) {
        reconcileUndoWindows()
        pendingDeletes = pendingDeletes.filterNot { it == item }
        deleteExpiryJobs.remove(deleteKey(item))?.cancel()
    }

    fun publishAttachmentDelete(
        item: PendingTodoAttachmentDelete,
        nowElapsedRealtimeMillis: Long = elapsedRealtimeMillis(),
    ) {
        reconcileUndoWindows(nowElapsedRealtimeMillis)
        val token = item.token
        val remaining = remainingUndoMillis(
            token.undoExpiresAtElapsedRealtime,
            nowElapsedRealtimeMillis,
        )
        if (remaining == 0L) return
        pendingAttachmentDeletes =
            pendingAttachmentDeletes.filterNot { it.token.attachmentId == token.attachmentId } + item
        scheduleAttachmentExpiry(item, remaining)
    }

    fun consumeAttachmentDelete(item: PendingTodoAttachmentDelete) {
        reconcileUndoWindows()
        pendingAttachmentDeletes = pendingAttachmentDeletes.filterNot { it == item }
        attachmentExpiryJobs.remove(item.token.attachmentId)?.cancel()
    }

    fun reconcileUndoWindows(nowElapsedRealtimeMillis: Long = elapsedRealtimeMillis()) {
        deleteExpiryJobs.values.forEach(Job::cancel)
        deleteExpiryJobs.clear()
        pendingDeletes = pendingDeletes.filter { item ->
            remainingUndoMillis(
                item.receipt.undoExpiresAtElapsedRealtime,
                nowElapsedRealtimeMillis,
            ) > 0L
        }
        pendingDeletes.forEach { item ->
            scheduleDeleteExpiry(
                item,
                remainingUndoMillis(
                    item.receipt.undoExpiresAtElapsedRealtime,
                    nowElapsedRealtimeMillis,
                ),
            )
        }

        attachmentExpiryJobs.values.forEach(Job::cancel)
        attachmentExpiryJobs.clear()
        pendingAttachmentDeletes = pendingAttachmentDeletes.filter { item ->
            remainingUndoMillis(
                item.token.undoExpiresAtElapsedRealtime,
                nowElapsedRealtimeMillis,
            ) > 0L
        }
        pendingAttachmentDeletes.forEach { item ->
            scheduleAttachmentExpiry(
                item,
                remainingUndoMillis(
                    item.token.undoExpiresAtElapsedRealtime,
                    nowElapsedRealtimeMillis,
                ),
            )
        }
    }

    private fun scheduleDeleteExpiry(item: PendingTodoDelete, remainingMillis: Long) {
        val key = deleteKey(item)
        deleteExpiryJobs.remove(key)?.cancel()
        deleteExpiryJobs[key] = expiryScheduler(remainingMillis) {
            deleteExpiryJobs.remove(key)
            pendingDeletes = pendingDeletes.filterNot { deleteKey(it) == key }
        }
    }

    private fun scheduleAttachmentExpiry(
        item: PendingTodoAttachmentDelete,
        remainingMillis: Long,
    ) {
        val attachmentId = item.token.attachmentId
        attachmentExpiryJobs.remove(attachmentId)?.cancel()
        attachmentExpiryJobs[attachmentId] = expiryScheduler(remainingMillis) {
            attachmentExpiryJobs.remove(attachmentId)
            pendingAttachmentDeletes = pendingAttachmentDeletes.filterNot {
                it.token.attachmentId == attachmentId
            }
        }
    }

    private fun isActive(editorKey: String, attempt: Long): Boolean =
        activeSaveEditorKey == editorKey && activeSaveAttempt == attempt

    private fun clearAttachmentCopyRequest(editorKey: String) {
        if (attachmentRequestEditorKey != editorKey) return
        clearAttachmentCopyRequest()
    }

    private fun clearAttachmentCopyRequest() {
        attachmentRequestEditorKey = null
        attachmentRequestDigest = null
        attachmentCopyAttemptId = null
        savedStateHandle.remove<String>(COPY_EDITOR_KEY)
        savedStateHandle.remove<String>(COPY_REQUEST_DIGEST_KEY)
        savedStateHandle.remove<String>(COPY_ATTEMPT_ID_KEY)
    }

    private fun clearOwnerRecovery() {
        ownerSessionKey = null
        ownerRequestDigest = null
        clientOperationToken = null
        savedOwnerId = null
        savedStateHandle.remove<String>(OWNER_SESSION_KEY)
        savedStateHandle.remove<String>(OWNER_REQUEST_DIGEST_KEY)
        savedStateHandle.remove<String>(CLIENT_OPERATION_TOKEN_KEY)
        savedStateHandle.remove<Long>(OWNER_ID_KEY)
    }

    private fun updateOwnerRequestDigest(editorKey: String, requestDigest: String) {
        if (ownerSessionKey != editorKey || ownerRequestDigest == requestDigest) return
        ownerRequestDigest = requestDigest
        savedStateHandle[OWNER_REQUEST_DIGEST_KEY] = requestDigest
        clearAttachmentCopyRequest(editorKey)
    }

    private fun deleteKey(item: PendingTodoDelete): String =
        "${item.receipt.itemId}:${item.receipt.deletedAt}"

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun deterministicUuid(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()

    override fun onCleared() {
        activeSaveJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val SAVE_TIMEOUT_MILLIS = 45_000L
        const val OWNER_SESSION_KEY = "todo_save_owner_session"
        const val OWNER_REQUEST_DIGEST_KEY = "todo_save_owner_request"
        const val CLIENT_OPERATION_TOKEN_KEY = "todo_save_client_operation_token"
        const val OWNER_ID_KEY = "todo_save_owner_id"
        const val COPY_EDITOR_KEY = "todo_attachment_copy_editor"
        const val COPY_REQUEST_DIGEST_KEY = "todo_attachment_copy_request"
        const val COPY_ATTEMPT_ID_KEY = "todo_attachment_copy_attempt"
    }
}

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
    val context = LocalContext.current
    val locale = uiLocale(settings.uiLanguage)
    val dateFormatter = remember(settings.dateFormat, locale) {
        UserFormatting.dateFormatter(settings.dateFormat, locale)
    }
    val use24HourTime = UserFormatting.uses24HourClock(
        option = settings.timeFormat,
        systemUses24Hour = DateFormat.is24HourFormat(context),
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val screenScope = rememberCoroutineScope()
    val uiLanguage = LocalUiLanguage.current
    val todoControlsListState = rememberLazyListState()
    val quickDescriptionFocusRequester = remember { FocusRequester() }
    val softwareKeyboardController = LocalSoftwareKeyboardController.current

    var quickDescription by rememberSaveable { mutableStateOf("") }
    var quickDeadlineText by rememberSaveable { mutableStateOf("") }
    var quickPriority by rememberSaveable { mutableStateOf(TodoPriority.NONE) }
    var quickCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var quickTagsText by rememberSaveable { mutableStateOf("") }
    var quickAddInFlight by rememberSaveable { mutableStateOf(false) }
    var quickValidationError by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedPriority by rememberSaveable { mutableStateOf<TodoPriority?>(null) }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategoryFilters by rememberSaveable(stateSaver = longSetSaver) {
        mutableStateOf(setOf(ALL_CATEGORIES_FILTER_KEY))
    }
    var observedCategoryIds by remember { mutableStateOf<Set<Long>?>(null) }
    var sort by rememberSaveable { mutableStateOf(TodoSort.DEADLINE) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    var showCategoryManager by rememberSaveable { mutableStateOf(false) }
    var showTagManager by rememberSaveable { mutableStateOf(false) }
    var editorDraftBundle by rememberSaveable { mutableStateOf<Bundle?>(null) }
    var editorRequestId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorSessionKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editorLoadGeneration by rememberSaveable { mutableIntStateOf(0) }
    var editorIsSeries by rememberSaveable { mutableStateOf(false) }
    var completionTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorFailureVersion by remember { mutableIntStateOf(0) }
    var editorFailureMessage by remember { mutableStateOf<String?>(null) }

    val editorDraft = remember(editorDraftBundle) { editorDraftBundle?.toTodoDraft() }
    val allTodos = activeTodos + completedTodos
    val todayEpochDay = LocalDate.now().toEpochDay()
    val dailyMomentum = remember(activeTodos, completedTodos, todayEpochDay) {
        dailyTodoMomentum(
            activeTodos = activeTodos,
            completedTodos = completedTodos,
            todayEpochDay = todayEpochDay,
        )
    }
    val completionTarget = allTodos.firstOrNull { it.id == completionTargetId }
    val deleteTarget = allTodos.firstOrNull { it.id == deleteTargetId }

    val quickAddBlockedByDialog = editorDraftBundle != null ||
        showCategoryManager ||
        showTagManager ||
        completionTarget != null ||
        deleteTarget != null
    LaunchedEffect(quickAddRequestToken, quickAddBlockedByDialog) {
        val requestToken = quickAddRequestToken ?: return@LaunchedEffect
        if (quickAddBlockedByDialog) return@LaunchedEffect
        todoControlsListState.scrollToItem(0)
        quickDescriptionFocusRequester.requestFocus()
        softwareKeyboardController?.show()
        onQuickAddRequestHandled(requestToken)
    }

    LaunchedEffect(viewModel, editorDraft != null, uiLanguage) {
        viewModel.errors.collect { message ->
            if (editorDraft != null) {
                editorFailureMessage = message
                editorFailureVersion += 1
            } else {
                snackbarHostState.showSnackbar(translateUiText(message, uiLanguage))
            }
        }
    }
    val pendingDelete = uiOperations.pendingDeletes.firstOrNull()
    LaunchedEffect(pendingDelete, uiLanguage) {
        val item = pendingDelete ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = translateUiText(if (item.includedFuture) {
                "Deleted ${item.title} and future occurrences"
            } else {
                "Deleted ${item.title}"
            }, uiLanguage),
            actionLabel = translateUiText("Undo", uiLanguage),
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            viewModel.undoDeleteTodo(item.receipt)
        }
        uiOperations.consumeDelete(item)
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

    val query = search.trim()
    val categoryParentIds = remember(categories) { categories.associate { it.id to it.parentId } }
    val categoryNames = remember(categories, categoryParentIds) {
        val namesById = categories.associate { it.id to it.name }
        categories.associate { category ->
            category.id to (categoryPathLabel(category.id, namesById, categoryParentIds) ?: category.name)
        }
    }
    val currentCategoryIds = remember(categories) { categories.mapTo(hashSetOf(), CategoryEntity::id) }
    LaunchedEffect(currentCategoryIds) {
        val previousCategoryIds = observedCategoryIds
        if (previousCategoryIds != null && previousCategoryIds != currentCategoryIds) {
            selectedCategoryFilters = sanitizeTodoCategoryFilters(
                current = selectedCategoryFilters,
                validCategoryIds = currentCategoryIds,
            )
        }
        observedCategoryIds = currentCategoryIds
    }
    val allCategoriesSelected = ALL_CATEGORIES_FILTER_KEY in selectedCategoryFilters
    val categoryFilterIds = remember(selectedCategoryFilters, categoryParentIds) {
        buildSet<Long?> {
            if (UNCATEGORIZED_FILTER_KEY in selectedCategoryFilters) add(null)
            selectedCategoryFilters
                .asSequence()
                .filter { it != ALL_CATEGORIES_FILTER_KEY && it != UNCATEGORIZED_FILTER_KEY }
                .forEach { categoryId ->
                    addAll(categoryIdsIncludedByTodoFilter(categoryId, categoryParentIds))
                }
        }
    }
    val availableTags = remember(activeTodos, completedTodos, todoSeries) {
        collectDistinctTodoTags(
            (activeTodos + completedTodos).map(TodoEntity::tagsCsv) + todoSeries.map { it.tagsCsv },
        )
    }
    val filteredActive = remember(
        activeTodos,
        query,
        selectedPriority,
        selectedTag,
        categoryFilterIds,
        allCategoriesSelected,
        sort,
    ) {
        activeTodos
            .asSequence()
            .filter { todo ->
                query.isEmpty() || listOf(todo.title, todo.description, todo.tagsCsv)
                    .any { it.contains(query, ignoreCase = true) }
            }
            .filter { selectedPriority == null || it.priority == selectedPriority }
            .filter { todo ->
                selectedTag == null || todo.tagsCsv.split(',').any { it.equals(selectedTag, ignoreCase = true) }
            }
            .filter { allCategoriesSelected || it.categoryId in categoryFilterIds }
            .sortedWith(
                when (sort) {
                    TodoSort.DEADLINE -> compareBy<TodoEntity> { it.deadlineEpochDay == null }
                        .thenBy { it.deadlineEpochDay ?: Long.MAX_VALUE }
                        .thenBy { it.deadlineMinute ?: -1 }
                        .thenByDescending { it.createdAt }
                    TodoSort.CREATED -> compareByDescending<TodoEntity> { it.createdAt }
                    TodoSort.CUSTOM -> compareByDescending<TodoEntity> { it.customOrder }
                        .thenByDescending { it.createdAt }
                        .thenByDescending { it.id }
                },
            )
            .toList()
    }
    val filteredCompleted = remember(
        completedTodos,
        query,
        selectedPriority,
        selectedTag,
        categoryFilterIds,
        allCategoriesSelected,
        sort,
    ) {
        completedTodos
            .asSequence()
            .filter { todo ->
                query.isEmpty() || listOf(todo.title, todo.description, todo.tagsCsv)
                    .any { it.contains(query, ignoreCase = true) }
            }
            .filter { selectedPriority == null || it.priority == selectedPriority }
            .filter { todo ->
                selectedTag == null || todo.tagsCsv.split(',').any { it.equals(selectedTag, ignoreCase = true) }
            }
            .filter { allCategoriesSelected || it.categoryId in categoryFilterIds }
            .sortedWith(
                when (sort) {
                    TodoSort.DEADLINE -> compareBy<TodoEntity> { it.deadlineEpochDay == null }
                        .thenBy { it.deadlineEpochDay ?: Long.MAX_VALUE }
                        .thenBy { it.deadlineMinute ?: -1 }
                        .thenByDescending { it.createdAt }
                    TodoSort.CREATED -> compareByDescending<TodoEntity> { it.createdAt }
                    TodoSort.CUSTOM -> compareByDescending<TodoEntity> { it.customOrder }
                        .thenByDescending { it.createdAt }
                        .thenByDescending { it.id }
                },
            )
            .toList()
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

    fun completeWithMomentum(todoId: Long, completeSubtasks: Boolean) {
        val completedCount = dailyMomentum.completedToday + 1
        viewModel.completeTodo(todoId, completeSubtasks) {
            screenScope.launch {
                snackbarHostState.showSnackbar(
                    translateUiText(completionCelebrationMessage(completedCount), uiLanguage),
                )
            }
        }
    }

    val controls: @Composable () -> Unit = {
        TodoControls(
            dailyMomentum = dailyMomentum,
            quickDescription = quickDescription,
            quickDescriptionFocusRequester = quickDescriptionFocusRequester,
            onQuickDescriptionChange = {
                quickDescription = it
                if (it.isNotBlank()) quickValidationError = null
            },
            quickAddFields = settings.todoQuickAddFields,
            quickDeadlineText = quickDeadlineText,
            onQuickDeadlineChange = {
                quickDeadlineText = it
                quickValidationError = null
            },
            quickPriority = quickPriority,
            onQuickPriorityChange = { quickPriority = it },
            quickCategoryId = quickCategoryId,
            onQuickCategoryChange = { quickCategoryId = it },
            quickTagsText = quickTagsText,
            onQuickTagsChange = { quickTagsText = it },
            quickAddInFlight = quickAddInFlight,
            quickValidationError = quickValidationError,
            onQuickAdd = {
                if (!quickAddInFlight) {
                    val result = buildQuickTodoDraft(
                        description = quickDescription,
                        enabledFields = settings.todoQuickAddFields,
                        deadlineText = quickDeadlineText,
                        priority = quickPriority,
                        categoryId = quickCategoryId,
                        tagsText = quickTagsText,
                        todayEpochDay = LocalDate.now().toEpochDay(),
                        defaultReminderOffsets = settings.defaultReminderOffsetsMinutes,
                    )
                    when (result.error) {
                        QuickTodoDraftError.DESCRIPTION_REQUIRED -> {
                            quickValidationError = "Description is required"
                        }
                        QuickTodoDraftError.INVALID_DEADLINE -> {
                            quickValidationError = "Enter a valid deadline"
                        }
                        null -> {
                            val draft = result.draft ?: return@TodoControls
                            quickAddInFlight = true
                            quickValidationError = null
                            viewModel.addQuickTodo(
                                draft = draft,
                                onSaved = {
                                    quickDescription = ""
                                    quickDeadlineText = ""
                                    quickPriority = TodoPriority.NONE
                                    quickCategoryId = null
                                    quickTagsText = ""
                                    quickAddInFlight = false
                                },
                                onFailure = { quickAddInFlight = false },
                            )
                        }
                    }
                }
            },
            search = search,
            onSearchChange = { search = it },
            priority = selectedPriority,
            onPriorityChange = { selectedPriority = it },
            selectedTag = selectedTag,
            availableTags = availableTags,
            onTagChange = { selectedTag = it },
            selectedCategoryFilters = selectedCategoryFilters,
            onCategoryFilterToggle = { key ->
                selectedCategoryFilters = toggleTodoCategoryFilter(selectedCategoryFilters, key)
            },
            categories = categories,
            categoryNames = categoryNames,
            sort = sort,
            onSortChange = { sort = it },
            filtersExpanded = filtersExpanded,
            onFiltersExpandedChange = { filtersExpanded = it },
            onNewTask = {
                editorRequestId = null
                editorSessionKey = UUID.randomUUID().toString()
                editorIsSeries = false
                editorDraftBundle = TodoDraft(description = "").toBundle()
            },
            onManageCategories = { showCategoryManager = true },
            onManageTags = { showTagManager = true },
        )
    }

    fun requestEdit(todoId: Long, isSeries: Boolean) {
        uiOperations.abandonEditor(editorSessionKey)
        editorDraftBundle = null
        editorRequestId = todoId
        editorSessionKey = UUID.randomUUID().toString()
        editorLoadGeneration += 1
        editorIsSeries = isSeries
    }

    fun edit(todo: TodoEntity) = requestEdit(todo.id, todo.seriesId != null)

    LaunchedEffect(requestedTodoId) {
        val todoId = requestedTodoId ?: return@LaunchedEffect
        requestEdit(
            todoId = todoId,
            isSeries = allTodos.firstOrNull { it.id == todoId }?.seriesId != null,
        )
        onRequestedTodoHandled(todoId)
    }

    fun closeEditor() {
        val closingSessionKey = editorSessionKey
        editorDraftBundle = null
        editorRequestId = null
        editorSessionKey = null
        editorIsSeries = false
        editorFailureMessage = null
        uiOperations.abandonEditor(closingSessionKey)
    }

    LaunchedEffect(uiOperations.savedEditorKey, editorSessionKey) {
        val savedKey = uiOperations.savedEditorKey ?: return@LaunchedEffect
        if (savedKey == editorSessionKey) {
            uiOperations.consumeSaved(savedKey)
            closeEditor()
        }
    }

    val addTaskItems: LazyListScope.() -> Unit = {
        todoListItems(
            viewModel = viewModel,
            activeTodos = filteredActive,
            completedTodos = filteredCompleted,
            categoryNames = categoryNames,
            completedExpanded = completedExpanded,
            dateFormatter = dateFormatter,
            use24HourTime = use24HourTime,
            onCompletedExpandedChange = { completedExpanded = it },
            onComplete = { todo, hasIncompleteSubtasks ->
                if (hasIncompleteSubtasks) {
                    completionTargetId = todo.id
                } else {
                    completeWithMomentum(todo.id, false)
                }
            },
            onRestore = viewModel::restoreTodo,
            onEdit = ::edit,
            onDelete = { todo ->
                if (todo.seriesId == null) {
                    deleteWithUndo(todo, SeriesEditScope.ONLY_THIS_OCCURRENCE)
                }
                else deleteTargetId = todo.id
            },
            customOrderingEnabled = sort == TodoSort.CUSTOM,
            onMoveTodo = viewModel::moveTodoRelativeToVisibleNeighbor,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(modifier = Modifier.weight(0.9f).fillMaxHeight()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = todoControlsListState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                    ) {
                        item(key = "todo-controls") { controls() }
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1.4f).fillMaxHeight(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = addTaskItems,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                state = todoControlsListState,
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "todo-controls") { controls() }
                addTaskItems()
            }
        }
    }

    if (showCategoryManager) {
        CategoryManagerDialog(
            categories = categories,
            onDismiss = { showCategoryManager = false },
            onAdd = viewModel::addCategory,
            onDelete = { categoryId ->
                selectedCategoryFilters = sanitizeTodoCategoryFilters(
                    current = selectedCategoryFilters,
                    validCategoryIds = categories
                        .asSequence()
                        .map(CategoryEntity::id)
                        .filterNot { it == categoryId }
                        .toSet(),
                )
                viewModel.deleteCategory(categoryId)
            },
        )
    }

    if (showTagManager) {
        TagManagerDialog(
            tags = availableTags,
            onDismiss = { showTagManager = false },
            onRename = { source, replacement ->
                viewModel.renameTodoTag(source, replacement) {
                    if (selectedTag.equals(source, ignoreCase = true)) selectedTag = replacement.trim()
                }
            },
            onDelete = { tag ->
                viewModel.deleteTodoTag(tag) {
                    if (selectedTag.equals(tag, ignoreCase = true)) selectedTag = null
                }
            },
        )
    }

    completionTarget?.let { todo ->
        CompletionDialog(
            title = todo.title,
            onDismiss = { completionTargetId = null },
            onCompleteTaskOnly = {
                completeWithMomentum(todo.id, false)
                completionTargetId = null
            },
            onCompleteWithSubtasks = {
                completeWithMomentum(todo.id, true)
                completionTargetId = null
            },
        )
    }

    deleteTarget?.let { todo ->
        RecurringDeleteDialog(
            title = todo.title,
            onDismiss = { deleteTargetId = null },
            onOnlyThisOccurrence = {
                deleteWithUndo(todo, SeriesEditScope.ONLY_THIS_OCCURRENCE)
                deleteTargetId = null
            },
            onThisAndFutureOccurrences = {
                deleteWithUndo(todo, SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES)
                deleteTargetId = null
            },
        )
    }

    editorDraft?.let { draft ->
        val sessionKey = editorSessionKey ?: return@let
        val todoCompleted = draft.id?.let { id -> completedTodos.any { it.id == id } }
        TodoEditorDialog(
            viewModel = viewModel,
            uiOperations = uiOperations,
            initialDraft = draft,
            categories = categories,
            availableTags = availableTags,
            editingSeriesOccurrence = editorIsSeries,
            use24HourTime = use24HourTime,
            defaultReminderOffsets = settings.defaultReminderOffsetsMinutes,
            operationFailureVersion = editorFailureVersion,
            operationFailureMessage = editorFailureMessage,
            isSaving = uiOperations.savingEditorKey == sessionKey,
            recordSavedAwaitingAttachments = uiOperations.hasSavedOwner(sessionKey),
            todoCompleted = todoCompleted,
            onCompletionChange = { completed ->
                draft.id?.let { id ->
                    if (completed) viewModel.completeTodo(id, completeSubtasks = false)
                    else viewModel.restoreTodo(id)
                }
            },
            onDismiss = ::closeEditor,
            onSave = { savedDraft, editScope, attachmentUris ->
                val saveAttempt = uiOperations.beginSave(sessionKey) ?: return@TodoEditorDialog
                val ownerRequest = "$savedDraft|scope=$editScope"
                if (savedDraft.id == null) {
                    uiOperations.clientOperationToken(sessionKey, ownerRequest)
                }
                val previouslySavedOwnerId = uiOperations.savedOwnerId(sessionKey, ownerRequest)

                fun copyAttachmentsAndFinish(todoId: Long) {
                    if (attachmentUris.isNotEmpty()) {
                        val copyAttemptId = uiOperations.attachmentCopyAttemptId(
                            sessionKey,
                            attachmentUris.map(Uri::toString),
                        )
                        val copyJob = viewModel.addAttachments(
                            ownerType = AttachmentOwnerType.TODO,
                            ownerId = todoId,
                            uris = attachmentUris,
                            copyAttemptId = copyAttemptId,
                            onCopied = {
                                uiOperations.markSaveSucceeded(sessionKey, saveAttempt)
                            },
                            onCopyFailed = {
                                uiOperations.markSaveFailed(sessionKey, saveAttempt)
                            },
                        )
                        uiOperations.trackSaveJob(sessionKey, saveAttempt, copyJob)
                    } else {
                        uiOperations.markSaveSucceeded(sessionKey, saveAttempt)
                    }
                }

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
                            uiOperations.markOwnerSaved(
                                sessionKey,
                                saveAttempt,
                                todoId,
                                ownerRequest,
                            )
                            copyAttachmentsAndFinish(todoId)
                        },
                        onFailure = {
                            uiOperations.markSaveFailed(sessionKey, saveAttempt)
                        },
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
                        onFailure = {
                            uiOperations.markSaveFailed(sessionKey, saveAttempt)
                        },
                    )
                    uiOperations.trackSaveJob(sessionKey, saveAttempt, validationJob)
                }
            },
        )
    }
}

@Composable
private fun TodoControls(
    dailyMomentum: DailyTodoMomentum,
    quickDescription: String,
    quickDescriptionFocusRequester: FocusRequester,
    onQuickDescriptionChange: (String) -> Unit,
    quickAddFields: Set<TodoQuickAddField>,
    quickDeadlineText: String,
    onQuickDeadlineChange: (String) -> Unit,
    quickPriority: TodoPriority,
    onQuickPriorityChange: (TodoPriority) -> Unit,
    quickCategoryId: Long?,
    onQuickCategoryChange: (Long?) -> Unit,
    quickTagsText: String,
    onQuickTagsChange: (String) -> Unit,
    quickAddInFlight: Boolean,
    quickValidationError: String?,
    onQuickAdd: () -> Unit,
    search: String,
    onSearchChange: (String) -> Unit,
    priority: TodoPriority?,
    onPriorityChange: (TodoPriority?) -> Unit,
    selectedTag: String?,
    availableTags: List<String>,
    onTagChange: (String?) -> Unit,
    onManageTags: () -> Unit,
    selectedCategoryFilters: Set<Long>,
    onCategoryFilterToggle: (Long) -> Unit,
    categories: List<CategoryEntity>,
    categoryNames: Map<Long, String>,
    sort: TodoSort,
    onSortChange: (TodoSort) -> Unit,
    filtersExpanded: Boolean,
    onFiltersExpandedChange: (Boolean) -> Unit,
    onNewTask: () -> Unit,
    onManageCategories: () -> Unit,
) {
    val allCategoriesSelected = ALL_CATEGORIES_FILTER_KEY in selectedCategoryFilters
    val categoryLabel = when {
        allCategoriesSelected -> localizedText("All categories")
        selectedCategoryFilters.size == 1 -> {
            val key = selectedCategoryFilters.single()
            if (key == UNCATEGORIZED_FILTER_KEY) localizedText("Uncategorized")
            else categoryNames[key] ?: localizedText("1 category selected")
        }
        else -> localizedText("${selectedCategoryFilters.size} categories selected")
    }
    val activeFilterSummaries = buildList {
        search.trim().takeIf(String::isNotEmpty)?.let { query ->
            add("${localizedText("Search")}: ${query.take(24)}")
        }
        priority?.let { add("${localizedText("Priority")}: ${localizedText(it.displayName())}") }
        selectedTag?.let { add("${localizedText("Tag")}: #$it") }
        if (!allCategoriesSelected) add("${localizedText("Category")}: $categoryLabel")
        if (sort != TodoSort.DEADLINE) add("${localizedText("Sort")}: ${localizedText(sort.label)}")
    }
    val filterSummary = if (activeFilterSummaries.isEmpty()) {
        "No active filters"
    } else {
        "${localizedText("${activeFilterSummaries.size} active")} • ${activeFilterSummaries.joinToString(" • ")}"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DailyMomentumCard(dailyMomentum)
        OutlinedTextField(
            value = quickDescription,
            onValueChange = onQuickDescriptionChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(quickDescriptionFocusRequester),
            label = { Text(localizedText("Quick add description")) },
            placeholder = { Text(localizedText("What needs to be done?")) },
            enabled = !quickAddInFlight,
            isError = quickValidationError == "Description is required",
            supportingText = if (quickValidationError == "Description is required") {
                { Text(localizedText("Description is required")) }
            } else null,
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = onQuickAdd,
                    enabled = quickDescription.isNotBlank() && !quickAddInFlight,
                ) {
                    Icon(Icons.Default.Add, contentDescription = localizedText("Add todo"))
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onQuickAdd() }),
        )
        QuickAddOptionalFields(
            fields = quickAddFields,
            deadlineText = quickDeadlineText,
            onDeadlineChange = onQuickDeadlineChange,
            priority = quickPriority,
            onPriorityChange = onQuickPriorityChange,
            categoryId = quickCategoryId,
            onCategoryChange = onQuickCategoryChange,
            tagsText = quickTagsText,
            onTagsChange = onQuickTagsChange,
            categories = categories,
            categoryNames = categoryNames,
            enabled = !quickAddInFlight,
            deadlineIsError = quickValidationError == "Enter a valid deadline",
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth < 480.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onNewTask, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(localizedText("New task"))
                    }
                    OutlinedButton(onClick = onManageCategories, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Category, null)
                        Spacer(Modifier.width(8.dp))
                        Text(localizedText("Categories"), maxLines = 1)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onNewTask, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(localizedText("New task"))
                    }
                    OutlinedButton(onClick = onManageCategories, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Category, null)
                        Spacer(Modifier.width(8.dp))
                        Text(localizedText("Categories"), maxLines = 1)
                    }
                }
            }
        }
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFiltersExpandedChange(!filtersExpanded) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.FilterList, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(localizedText("Filters"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = filterSummary,
                        color = if (activeFilterSummaries.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = if (filtersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (filtersExpanded) "Collapse filters" else "Expand filters",
                )
            }

            if (filtersExpanded) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (activeFilterSummaries.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    onSearchChange("")
                                    onPriorityChange(null)
                                    onTagChange(null)
                                    onCategoryFilterToggle(ALL_CATEGORIES_FILTER_KEY)
                                    onSortChange(TodoSort.DEADLINE)
                                },
                            ) { Text(localizedText("Clear filters")) }
                        }
                    }
                    OutlinedTextField(
                        value = search,
                        onValueChange = onSearchChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(localizedText("Search")) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = if (search.isNotEmpty()) {
                            {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Default.Close, "Clear search")
                                }
                            }
                        } else null,
                    )
                    Text(localizedText("Priority"), style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = priority == null,
                                onClick = { onPriorityChange(null) },
                                label = { Text(localizedText("All")) },
                            )
                        }
                        items(TodoPriority.entries) { option ->
                            FilterChip(
                                selected = priority == option,
                                onClick = { onPriorityChange(option) },
                                label = { Text(localizedText(option.displayName())) },
                            )
                        }
                    }
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val narrow = maxWidth < 480.dp
                        val menuModifier = if (narrow) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier.fillMaxWidth(0.31f).widthIn(min = 140.dp)
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = if (narrow) 1 else 3,
                        ) {
                            ChoiceMenu(
                                label = selectedTag?.let { "${localizedText("Tag")}: #$it" }
                                    ?: localizedText("All tags"),
                                modifier = menuModifier,
                            ) { close ->
                                DropdownMenuItem(
                                    text = { Text(localizedText("All tags")) },
                                    onClick = { onTagChange(null); close() },
                                )
                                availableTags.forEach { tag ->
                                    DropdownMenuItem(
                                        text = { Text(localizedText("#$tag")) },
                                        onClick = { onTagChange(tag); close() },
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(localizedText("Manage tags...")) },
                                    onClick = { close(); onManageTags() },
                                )
                            }
                            ChoiceMenu(label = categoryLabel, modifier = menuModifier) { close ->
                                DropdownMenuItem(
                                    text = { Text(localizedText("All categories")) },
                                    onClick = {
                                        onCategoryFilterToggle(ALL_CATEGORIES_FILTER_KEY)
                                        close()
                                    },
                                    leadingIcon = {
                                        Checkbox(checked = allCategoriesSelected, onCheckedChange = null)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(localizedText("Uncategorized")) },
                                    onClick = { onCategoryFilterToggle(UNCATEGORIZED_FILTER_KEY) },
                                    leadingIcon = {
                                        Checkbox(
                                            checked = UNCATEGORIZED_FILTER_KEY in selectedCategoryFilters,
                                            onCheckedChange = null,
                                        )
                                    },
                                )
                                categories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(categoryNames[category.id] ?: category.name) },
                                        onClick = { onCategoryFilterToggle(category.id) },
                                        leadingIcon = {
                                            Checkbox(
                                                checked = category.id in selectedCategoryFilters,
                                                onCheckedChange = null,
                                            )
                                        },
                                    )
                                }
                            }
                            ChoiceMenu(
                                label = "${localizedText("Sort")}: ${localizedText(sort.label)}",
                                modifier = menuModifier,
                            ) { close ->
                                TodoSort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(localizedText(option.label)) },
                                        onClick = { onSortChange(option); close() },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyMomentumCard(momentum: DailyTodoMomentum) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = localizedText("Daily momentum"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = localizedText(momentum.summary),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (momentum.total > 0) {
                LinearProgressIndicator(
                    progress = { momentum.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuickAddOptionalFields(
    fields: Set<TodoQuickAddField>,
    deadlineText: String,
    onDeadlineChange: (String) -> Unit,
    priority: TodoPriority,
    onPriorityChange: (TodoPriority) -> Unit,
    categoryId: Long?,
    onCategoryChange: (Long?) -> Unit,
    tagsText: String,
    onTagsChange: (String) -> Unit,
    categories: List<CategoryEntity>,
    categoryNames: Map<Long, String>,
    enabled: Boolean,
    deadlineIsError: Boolean,
) {
    if (fields.isEmpty()) return

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val fieldModifier = if (maxWidth < 480.dp) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.fillMaxWidth(0.48f).widthIn(min = 220.dp)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = if (maxWidth < 480.dp) 1 else 2,
        ) {
            if (TodoQuickAddField.DEADLINE in fields) {
                OutlinedTextField(
                    value = deadlineText,
                    onValueChange = onDeadlineChange,
                    modifier = fieldModifier,
                    enabled = enabled,
                    label = { Text(localizedText("Deadline")) },
                    placeholder = { Text(localizedText("Day, MM/DD, or MM/DD/YYYY")) },
                    isError = deadlineIsError,
                    supportingText = if (deadlineIsError) {
                        { Text(localizedText("Enter a valid deadline")) }
                    } else null,
                    singleLine = true,
                )
            }
            if (TodoQuickAddField.PRIORITY in fields) {
                ChoiceMenu(
                    label = "${localizedText("Priority")}: ${localizedText(priority.displayName())}",
                    modifier = fieldModifier,
                    enabled = enabled,
                ) { close ->
                    TodoPriority.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(localizedText(option.displayName())) },
                            onClick = { onPriorityChange(option); close() },
                        )
                    }
                }
            }
            if (TodoQuickAddField.CATEGORY in fields) {
                ChoiceMenu(
                    label = categoryId?.let(categoryNames::get) ?: localizedText("Uncategorized"),
                    modifier = fieldModifier,
                    enabled = enabled,
                ) { close ->
                    DropdownMenuItem(
                        text = { Text(localizedText("Uncategorized")) },
                        onClick = { onCategoryChange(null); close() },
                    )
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(categoryNames[category.id] ?: category.name) },
                            onClick = { onCategoryChange(category.id); close() },
                        )
                    }
                }
            }
            if (TodoQuickAddField.TAGS in fields) {
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = onTagsChange,
                    modifier = fieldModifier,
                    enabled = enabled,
                    label = { Text(localizedText("Tags")) },
                    placeholder = { Text(localizedText("Comma-separated")) },
                    singleLine = true,
                )
            }
        }
    }
}

private fun LazyListScope.todoListItems(
    viewModel: TaskLedgerViewModel,
    activeTodos: List<TodoEntity>,
    completedTodos: List<TodoEntity>,
    categoryNames: Map<Long, String>,
    completedExpanded: Boolean,
    dateFormatter: DateTimeFormatter,
    use24HourTime: Boolean,
    onCompletedExpandedChange: (Boolean) -> Unit,
    onComplete: (TodoEntity, Boolean) -> Unit,
    onRestore: (Long) -> Unit,
    onEdit: (TodoEntity) -> Unit,
    onDelete: (TodoEntity) -> Unit,
    customOrderingEnabled: Boolean,
    onMoveTodo: (Long, Long) -> Unit,
) {
    item(key = "active-header", contentType = "section-header") {
        Text(
            text = localizedText("Active (${activeTodos.size})"),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
    if (customOrderingEnabled) {
        item(key = "custom-order-help", contentType = "supporting-text") {
            Text(
                localizedText("Moves follow the visible filtered list; hidden tasks keep their relative order."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (activeTodos.isEmpty()) {
        item(key = "active-empty", contentType = "empty-message") {
            EmptyCard("No active tasks match these filters.")
        }
    } else {
        itemsIndexed(
            items = activeTodos,
            key = { _, todo -> "active-${todo.id}" },
            contentType = { _, _ -> "todo-card" },
        ) { index, todo ->
            TodoCard(
                viewModel = viewModel,
                todo = todo,
                categoryName = todo.categoryId?.let(categoryNames::get),
                completed = false,
                dateFormatter = dateFormatter,
                use24HourTime = use24HourTime,
                onComplete = { hasIncomplete -> onComplete(todo, hasIncomplete) },
                onRestore = {},
                onEdit = { onEdit(todo) },
                onDelete = { onDelete(todo) },
                showMoveControls = customOrderingEnabled,
                canMoveUp = index > 0,
                canMoveDown = index < activeTodos.lastIndex,
                onMoveUp = {
                    if (index > 0) onMoveTodo(todo.id, activeTodos[index - 1].id)
                },
                onMoveDown = {
                    if (index < activeTodos.lastIndex) onMoveTodo(todo.id, activeTodos[index + 1].id)
                },
            )
        }
    }
    item(key = "completed-toggle", contentType = "section-header") {
        OutlinedButton(
            onClick = { onCompletedExpandedChange(!completedExpanded) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(if (completedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            Spacer(Modifier.width(8.dp))
            Text(localizedText("Completed (${completedTodos.size})"))
        }
    }
    if (completedExpanded) {
        if (completedTodos.isEmpty()) {
            item(key = "completed-empty", contentType = "empty-message") {
                EmptyCard("No completed tasks match these filters.")
            }
        } else {
            items(
                items = completedTodos,
                key = { "completed-${it.id}" },
                contentType = { "todo-card" },
            ) { todo ->
                TodoCard(
                    viewModel = viewModel,
                    todo = todo,
                    categoryName = todo.categoryId?.let(categoryNames::get),
                    completed = true,
                    dateFormatter = dateFormatter,
                    use24HourTime = use24HourTime,
                    onComplete = {},
                    onRestore = { onRestore(todo.id) },
                    onEdit = { onEdit(todo) },
                    onDelete = { onDelete(todo) },
                )
            }
        }
    }
}

@Composable
private fun TodoCard(
    viewModel: TaskLedgerViewModel,
    todo: TodoEntity,
    categoryName: String?,
    completed: Boolean,
    dateFormatter: DateTimeFormatter,
    use24HourTime: Boolean,
    onComplete: (hasIncompleteSubtasks: Boolean) -> Unit,
    onRestore: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showMoveControls: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
) {
    val loadedSubtasks by produceState<List<SubtaskEntity>?>(null, todo.id, viewModel) {
        viewModel.subtasks(todo.id).collect { value = it }
    }
    val subtasks = loadedSubtasks.orEmpty()
    var reorderMenuExpanded by remember(todo.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (completed) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = completed,
                enabled = completed || loadedSubtasks != null,
                onCheckedChange = {
                    if (completed) onRestore()
                    else onComplete(subtasks.any { subtask -> !subtask.isCompleted })
                },
                modifier = Modifier.semantics {
                    contentDescription = if (completed) {
                        "Restore ${todo.title}"
                    } else {
                        "Complete ${todo.title}"
                    }
                },
            )
            Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                Text(
                    text = todo.title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (todo.description != todo.title) {
                    Text(
                        todo.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val metadata = buildList {
                    todo.deadlineEpochDay?.let {
                        add(formatDeadline(it, todo.deadlineMinute, dateFormatter, use24HourTime))
                    }
                    categoryName?.let(::add)
                    if (todo.priority != TodoPriority.NONE) add(localizedText(todo.priority.displayName()))
                    if (todo.seriesId != null) add(localizedText("Repeating"))
                }
                if (metadata.isNotEmpty()) {
                    Text(
                        metadata.joinToString(" • "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (todo.tagsCsv.isNotBlank()) {
                    Text(
                        todo.tagsCsv.split(',').joinToString("  ") { "#$it" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (subtasks.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    subtasks.forEach { subtask ->
                        val subtaskContentDescription = localizedText(
                            "Subtask: ${subtask.description}",
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = subtask.isCompleted,
                                onCheckedChange = { checked ->
                                    viewModel.setSubtaskCompleted(subtask, checked)
                                },
                                enabled = !completed,
                                modifier = Modifier.semantics {
                                    contentDescription = subtaskContentDescription
                                },
                            )
                            Text(
                                text = subtask.description,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Column {
                if (showMoveControls) {
                    Box {
                        IconButton(onClick = { reorderMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "Reorder ${todo.title}")
                        }
                        DropdownMenu(
                            expanded = reorderMenuExpanded,
                            onDismissRequest = { reorderMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(localizedText("Move up")) },
                                leadingIcon = { Icon(Icons.Default.ArrowUpward, null) },
                                enabled = canMoveUp,
                                onClick = { reorderMenuExpanded = false; onMoveUp() },
                            )
                            DropdownMenuItem(
                                text = { Text(localizedText("Move down")) },
                                leadingIcon = { Icon(Icons.Default.ArrowDownward, null) },
                                enabled = canMoveDown,
                                onClick = { reorderMenuExpanded = false; onMoveDown() },
                            )
                        }
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit ${todo.title}")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete ${todo.title}")
                }
                if (completed) {
                    IconButton(onClick = onRestore) {
                        Icon(Icons.Default.Restore, "Restore ${todo.title}")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            localizedText(message),
            modifier = Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryManagerDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onAdd: (String, Long?) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var parentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryToDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val categoryToDelete = categories.firstOrNull { it.id == categoryToDeleteId }

    AppDialog(onDismiss = onDismiss) {
        Text(localizedText("Manage categories"), style = MaterialTheme.typography.headlineSmall)
        Text(
            localizedText("Deleting a category keeps its tasks in Uncategorized and moves child categories up one level."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(localizedText("Category name")) },
            singleLine = true,
        )
        ChoiceMenu(
            label = parentId?.let { id -> categories.firstOrNull { it.id == id }?.displayName(categories) }
                ?: localizedText("No parent"),
            modifier = Modifier.fillMaxWidth(),
        ) { close ->
            DropdownMenuItem(text = { Text(localizedText("No parent")) }, onClick = { parentId = null; close() })
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.displayName(categories)) },
                    onClick = { parentId = category.id; close() },
                )
            }
        }
        Button(
            onClick = { onAdd(name, parentId); name = "" },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(localizedText("Add category"))
        }
        HorizontalDivider()
        if (categories.isEmpty()) {
            Text(localizedText("No categories yet."))
        } else {
            categories.sortedBy { it.displayName(categories) }.forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(category.displayName(categories), modifier = Modifier.weight(1f))
                    IconButton(onClick = { categoryToDeleteId = category.id }) {
                        Icon(Icons.Default.Delete, "Delete ${category.name}")
                    }
                }
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(localizedText("Done")) }
    }

    categoryToDelete?.let { category ->
        HingeSafeAlertDialog(
            onDismissRequest = { categoryToDeleteId = null },
            title = { Text(localizedText("Delete ${category.name}?")) },
            text = {
                Text(localizedText("Tasks will become Uncategorized. Child categories will move to this category's parent."))
            },
            confirmButton = {
                Button(onClick = { onDelete(category.id); categoryToDeleteId = null }) { Text(localizedText("Delete")) }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDeleteId = null }) { Text(localizedText("Cancel")) }
            },
        )
    }
}

@Composable
private fun TagManagerDialog(
    tags: List<String>,
    onDismiss: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var tagToRename by rememberSaveable { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var tagToDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val renameIsValid = renameText.trim().isNotEmpty() && ',' !in renameText

    AppDialog(onDismiss = onDismiss) {
        Text(localizedText("Manage tags"), style = MaterialTheme.typography.headlineSmall)
        Text(
            localizedText("Changes apply to existing tasks and repeating rules, including future occurrences."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tags.isEmpty()) {
            Text(localizedText("No tags yet."))
        } else {
            tags.forEach { tag ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(localizedText("#$tag"), modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { tagToRename = tag; renameText = tag },
                    ) {
                        Icon(Icons.Default.Edit, "Rename $tag tag")
                    }
                    IconButton(onClick = { tagToDelete = tag }) {
                        Icon(Icons.Default.Delete, "Delete $tag tag")
                    }
                }
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(localizedText("Done")) }
    }

    tagToRename?.let { sourceTag ->
        HingeSafeAlertDialog(
            onDismissRequest = { tagToRename = null },
            title = { Text(localizedText("Rename #$sourceTag")) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(localizedText("Tag name")) },
                    supportingText = if (',' in renameText) {
                        { Text(localizedText("A tag name cannot contain commas.")) }
                    } else null,
                    isError = ',' in renameText,
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    enabled = renameIsValid,
                    onClick = {
                        onRename(sourceTag, renameText)
                        tagToRename = null
                    },
                ) { Text(localizedText("Rename")) }
            },
            dismissButton = {
                TextButton(onClick = { tagToRename = null }) { Text(localizedText("Cancel")) }
            },
        )
    }

    tagToDelete?.let { tag ->
        HingeSafeAlertDialog(
            onDismissRequest = { tagToDelete = null },
            title = { Text(localizedText("Delete #$tag?")) },
            text = { Text(localizedText("This removes the tag from all tasks and repeating rules.")) },
            confirmButton = {
                Button(onClick = { onDelete(tag); tagToDelete = null }) { Text(localizedText("Delete")) }
            },
            dismissButton = {
                TextButton(onClick = { tagToDelete = null }) { Text(localizedText("Cancel")) }
            },
        )
    }
}

@Composable
private fun CompletionDialog(
    title: String,
    onDismiss: () -> Unit,
    onCompleteTaskOnly: () -> Unit,
    onCompleteWithSubtasks: () -> Unit,
) {
    HingeSafeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("Complete task?")) },
        text = { Text(localizedText("Choose whether to also complete any unfinished subtasks in “$title”.")) },
        confirmButton = { Button(onClick = onCompleteWithSubtasks) { Text(localizedText("Task + subtasks")) } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(localizedText("Cancel")) }
                TextButton(onClick = onCompleteTaskOnly) { Text(localizedText("Task only")) }
            }
        },
    )
}

@Composable
private fun RecurringDeleteDialog(
    title: String,
    onDismiss: () -> Unit,
    onOnlyThisOccurrence: () -> Unit,
    onThisAndFutureOccurrences: () -> Unit,
) {
    AppDialog(onDismiss = onDismiss) {
        Text(localizedText("Delete repeating task?"), style = MaterialTheme.typography.headlineSmall)
        Text(
            localizedText("Choose how much of “$title” to delete. Past occurrences are not changed."),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onOnlyThisOccurrence,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(localizedText("Only this occurrence"))
        }
        Button(
            onClick = onThisAndFutureOccurrences,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(localizedText("This and future occurrences"))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(localizedText("Cancel"))
        }
    }
}

@Composable
private fun TodoEditorDialog(
    viewModel: TaskLedgerViewModel,
    uiOperations: TodoUiOperationsViewModel,
    initialDraft: TodoDraft,
    categories: List<CategoryEntity>,
    availableTags: List<String>,
    editingSeriesOccurrence: Boolean,
    use24HourTime: Boolean,
    defaultReminderOffsets: Set<Long>,
    operationFailureVersion: Int,
    operationFailureMessage: String?,
    isSaving: Boolean,
    recordSavedAwaitingAttachments: Boolean,
    todoCompleted: Boolean?,
    onCompletionChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (TodoDraft, SeriesEditScope, List<Uri>) -> Unit,
) {
    val context = LocalContext.current
    val platformDialogLauncher = rememberHingeSafePlatformDialogLauncher()
    val editorSnackbarHostState = remember { SnackbarHostState() }
    val editorScope = rememberCoroutineScope()
    val uiLanguage = LocalUiLanguage.current
    val openAttachment = rememberAttachmentOpener { message ->
        editorScope.launch {
            editorSnackbarHostState.showSnackbar(translateUiText(message, uiLanguage))
        }
    }
    var title by rememberSaveable(initialDraft.id) { mutableStateOf(initialDraft.title) }
    var description by rememberSaveable(initialDraft.id) { mutableStateOf(initialDraft.description) }
    var categoryId by rememberSaveable(initialDraft.id) { mutableStateOf(initialDraft.categoryId) }
    var deadlineText by rememberSaveable(initialDraft.id) {
        mutableStateOf(initialDraft.deadlineEpochDay?.let(::formatUsDate).orEmpty())
    }
    var hasTime by rememberSaveable(initialDraft.id) { mutableStateOf(initialDraft.deadlineMinute != null) }
    var timeText by rememberSaveable(initialDraft.id) {
        mutableStateOf(initialDraft.deadlineMinute?.let { formatTime(it, use24HourTime) }.orEmpty())
    }
    var priority by rememberSaveable(initialDraft.id) { mutableStateOf(initialDraft.priority) }
    var tagsText by rememberSaveable(initialDraft.id) { mutableStateOf(initialDraft.tags.joinToString(", ")) }
    val tagSuggestions = remember(tagsText, availableTags) {
        todoTagSuggestions(tagsText, availableTags)
    }
    var reminderOffsets by rememberSaveable(initialDraft.id, stateSaver = longListSaver) {
        mutableStateOf(initialDraft.reminderOffsetsMinutes.distinct())
    }
    var appliedDefaultReminders by rememberSaveable(initialDraft.id) {
        mutableStateOf(initialDraft.id != null || initialDraft.deadlineEpochDay != null)
    }
    var recurrenceEnabled by rememberSaveable(initialDraft.id) { mutableStateOf(initialDraft.recurrence != null) }
    var recurrenceUnit by rememberSaveable(initialDraft.id) {
        mutableStateOf(initialDraft.recurrence?.unit ?: RecurrenceUnit.WEEK)
    }
    var recurrenceInterval by rememberSaveable(initialDraft.id) {
        mutableStateOf((initialDraft.recurrence?.interval ?: 1).toString())
    }
    var recurrenceEndText by rememberSaveable(initialDraft.id) {
        mutableStateOf(initialDraft.recurrence?.endEpochDay?.let(::formatUsDate).orEmpty())
    }
    var subtasksText by rememberSaveable(initialDraft.id) {
        mutableStateOf(initialDraft.subtasks.joinToString("\n"))
    }
    var editScope by rememberSaveable(initialDraft.id) {
        mutableStateOf(SeriesEditScope.ONLY_THIS_OCCURRENCE)
    }
    var pendingUriStrings by rememberSaveable(initialDraft.id, stateSaver = stringListSaver) {
        mutableStateOf(emptyList())
    }
    var validationError by rememberSaveable(initialDraft.id) { mutableStateOf<String?>(null) }
    val attachmentFlow = remember(initialDraft.id) {
        initialDraft.id?.let { viewModel.attachments(AttachmentOwnerType.TODO, it) } ?: flowOf(emptyList())
    }
    val existingAttachments by attachmentFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingAttachmentDelete = uiOperations.pendingAttachmentDeletes.firstOrNull()

    LaunchedEffect(operationFailureVersion, uiLanguage) {
        if (operationFailureVersion > 0) {
            operationFailureMessage?.let {
                editorSnackbarHostState.showSnackbar(translateUiText(it, uiLanguage))
            }
        }
    }

    LaunchedEffect(pendingAttachmentDelete, uiLanguage) {
        val item = pendingAttachmentDelete ?: return@LaunchedEffect
        val result = editorSnackbarHostState.showSnackbar(
            message = translateUiText("Removed ${item.originalName}", uiLanguage),
            actionLabel = translateUiText("Undo", uiLanguage),
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            viewModel.undoRemoveAttachment(item.token)
        }
        uiOperations.consumeAttachmentDelete(item)
    }

    fun updateDeadline(value: String) {
        val deadlineWasAdded = deadlineText.isBlank() && value.isNotBlank()
        deadlineText = value
        val dependentState = clearTodoDeadlineDependentsIfBlank(
            deadlineText = value,
            current = TodoDeadlineDependentState(
                hasTime = hasTime,
                timeText = timeText,
                reminderOffsets = reminderOffsets,
                appliedDefaultReminders = appliedDefaultReminders,
                recurrenceEnabled = recurrenceEnabled,
                recurrenceUnit = recurrenceUnit,
                recurrenceInterval = recurrenceInterval,
                recurrenceEndText = recurrenceEndText,
            ),
        )
        hasTime = dependentState.hasTime
        timeText = dependentState.timeText
        reminderOffsets = dependentState.reminderOffsets
        appliedDefaultReminders = dependentState.appliedDefaultReminders
        recurrenceEnabled = dependentState.recurrenceEnabled
        recurrenceUnit = dependentState.recurrenceUnit
        recurrenceInterval = dependentState.recurrenceInterval
        recurrenceEndText = dependentState.recurrenceEndText
        if (
            initialDraft.id == null &&
            deadlineWasAdded &&
            !appliedDefaultReminders &&
            defaultReminderOffsets.isNotEmpty()
        ) {
            reminderOffsets = defaultReminderOffsets.sorted()
            appliedDefaultReminders = true
        }
    }

    fun selectOnlyThisOccurrence() {
        editScope = SeriesEditScope.ONLY_THIS_OCCURRENCE
        if (deadlineText.isBlank()) return

        // Rule edits belong to the series tail. Revert only recurrence controls when returning to
        // occurrence scope; ordinary occurrence edits stay untouched.
        recurrenceEnabled = initialDraft.recurrence != null
        recurrenceUnit = initialDraft.recurrence?.unit ?: RecurrenceUnit.WEEK
        recurrenceInterval = (initialDraft.recurrence?.interval ?: 1).toString()
        recurrenceEndText = initialDraft.recurrence?.endEpochDay?.let(::formatUsDate).orEmpty()
    }

    LaunchedEffect(
        initialDraft.id,
        deadlineText,
        appliedDefaultReminders,
        defaultReminderOffsets,
    ) {
        if (
            initialDraft.id == null &&
            deadlineText.isNotBlank() &&
            !appliedDefaultReminders &&
            defaultReminderOffsets.isNotEmpty()
        ) {
            reminderOffsets = defaultReminderOffsets.sorted()
            appliedDefaultReminders = true
        }
    }

    fun showDatePicker(currentText: String, onSelected: (String) -> Unit) {
        val today = LocalDate.now()
        val initial = SmartDateParser.parse(currentText, today) ?: today
        platformDialogLauncher(
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    onSelected(formatUsDate(LocalDate.of(year, month + 1, day).toEpochDay()))
                },
                initial.year,
                initial.monthValue - 1,
                initial.dayOfMonth,
            ),
        )
    }

    val attachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val remaining = (MAX_ATTACHMENTS - existingAttachments.size - pendingUriStrings.size).coerceAtLeast(0)
        val acceptedUris = uris.take(remaining)
        pendingUriStrings = (pendingUriStrings + acceptedUris.map(Uri::toString)).distinct()
    }

    fun buildDraft(): TodoDraft? {
        if (description.isBlank()) {
            validationError = "Description is required."
            return null
        }
        val today = LocalDate.now().toEpochDay()
        val deadline = if (deadlineText.isBlank()) null else SmartDateParser.parse(deadlineText, today)
        if (deadlineText.isNotBlank() && deadline == null) {
            validationError = "Enter a valid US date: day, M/d, or M/d/yyyy."
            return null
        }
        val minute = if (hasTime) parseMinuteOfDay(timeText) else null
        if (hasTime && minute == null) {
            validationError = "Enter a valid time such as 9:30 AM or 21:30."
            return null
        }
        if (hasTime && deadline == null) {
            validationError = "A time requires a deadline date."
            return null
        }
        if (recurrenceEnabled && deadline == null) {
            validationError = "Repeating tasks require a deadline."
            return null
        }
        val interval = recurrenceInterval.toIntOrNull()
        if (recurrenceEnabled && (interval == null || interval < 1)) {
            validationError = "Repeat interval must be at least 1."
            return null
        }
        val recurrenceEnd = if (recurrenceEndText.isBlank()) null
        else SmartDateParser.parse(recurrenceEndText, today)
        if (recurrenceEnabled && recurrenceEndText.isNotBlank() && recurrenceEnd == null) {
            validationError = "Enter a valid repeat end date."
            return null
        }
        val validateRecurrenceRule = shouldValidateTodoRecurrenceRule(
            recurrenceEnabled = recurrenceEnabled,
            editingSeriesOccurrence = editingSeriesOccurrence,
            editScope = editScope,
        )
        if (
            validateRecurrenceRule &&
            recurrenceEnd != null &&
            deadline != null &&
            recurrenceEnd < deadline
        ) {
            validationError = "Repeat end date cannot be before the deadline."
            return null
        }
        validationError = null
        return TodoDraft(
            id = initialDraft.id,
            title = title.trim(),
            description = description.trim(),
            categoryId = categoryId,
            deadlineEpochDay = deadline,
            deadlineMinute = minute,
            priority = priority,
            tags = tagsText.split(',').map(String::trim).filter(String::isNotEmpty),
            reminderOffsetsMinutes = if (deadline == null) emptyList() else reminderOffsets.toList(),
            subtasks = subtasksText.lineSequence().map(String::trim).filter(String::isNotEmpty).toList(),
            recurrence = if (recurrenceEnabled) {
                RecurrenceRule(recurrenceUnit, requireNotNull(interval), recurrenceEnd)
            } else null,
        )
    }

    if (recordSavedAwaitingAttachments) {
        AppDialog(
            onDismiss = { if (!isSaving) onDismiss() },
            snackbarHostState = editorSnackbarHostState,
        ) {
            Text(localizedText("Task saved"), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = localizedText(if (isSaving) {
                    "The task details are saved. Files are being copied now."
                } else {
                    "The task details are already saved, but one or more files were not copied. " +
                        "Details are locked so later edits cannot be silently skipped. Remove any " +
                        "unavailable file, then retry."
                }),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pendingUriStrings.isEmpty()) {
                Text(localizedText("No files remain to copy."))
            } else {
                pendingUriStrings.forEachIndexed { index, uriString ->
                    val uri = uriString.toUri()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "• ${uri.lastPathSegment ?: "Selected file"}",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            enabled = !isSaving,
                            onClick = {
                                pendingUriStrings = pendingUriStrings.filterIndexed { i, _ -> i != index }
                            },
                        ) {
                            Icon(Icons.Default.Close, "Remove ${uri.lastPathSegment ?: "selected file"}")
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = { attachmentLauncher.launch(arrayOf("*/*")) },
                enabled = !isSaving && existingAttachments.size + pendingUriStrings.size < MAX_ATTACHMENTS,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.AttachFile, null)
                Spacer(Modifier.width(8.dp))
                Text(localizedText("Add files (${existingAttachments.size + pendingUriStrings.size}/$MAX_ATTACHMENTS)"))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !isSaving) { Text(localizedText("Done")) }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = !isSaving,
                    onClick = {
                        buildDraft()?.let { draft ->
                            onSave(
                                draft,
                                editScope,
                                pendingUriStrings.map { it.toUri() },
                            )
                        }
                    },
                ) {
                    Text(
                        localizedText(when {
                            isSaving -> "Copying…"
                            pendingUriStrings.isEmpty() -> "Finish"
                            else -> "Retry files"
                        }),
                    )
                }
            }
        }
        return
    }

    AppDialog(
        onDismiss = { if (!isSaving) onDismiss() },
        snackbarHostState = editorSnackbarHostState,
    ) {
        Text(
            localizedText(if (initialDraft.id == null) "New task" else "Edit task"),
            style = MaterialTheme.typography.headlineSmall,
        )
        todoCompleted?.let { completed ->
            OutlinedButton(
                onClick = { onCompletionChange(!completed) },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (completed) Icons.Default.Restore else Icons.Default.CheckCircle,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(localizedText(if (completed) "Mark as not done" else "Mark as done"))
            }
        }
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(localizedText("Description *")) },
            minLines = 2,
            maxLines = 5,
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(localizedText("Title (optional)")) },
            supportingText = { Text(localizedText("If blank, it is derived from the description.")) },
            singleLine = true,
        )
        ChoiceMenu(
            label = categoryId?.let { id -> categories.firstOrNull { it.id == id }?.displayName(categories) }
                ?: localizedText("Uncategorized"),
            modifier = Modifier.fillMaxWidth(),
        ) { close ->
            DropdownMenuItem(
                text = { Text(localizedText("Uncategorized")) },
                onClick = { categoryId = null; close() },
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.displayName(categories)) },
                    onClick = { categoryId = category.id; close() },
                )
            }
        }
        OutlinedTextField(
            value = deadlineText,
            onValueChange = ::updateDeadline,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(localizedText("Deadline (US date)")) },
            placeholder = { Text(localizedText("15, 8/15, or 8/15/2026")) },
            supportingText = { Text(localizedText("A passed day or month/day rolls forward automatically.")) },
            trailingIcon = {
                IconButton(onClick = { showDatePicker(deadlineText, ::updateDeadline) }) {
                    Icon(Icons.Default.CalendarMonth, localizedText("Choose deadline date"))
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                AssistChip(
                    onClick = { updateDeadline(formatUsDate(LocalDate.now().toEpochDay())) },
                    label = { Text(localizedText("Today")) },
                )
            }
            item {
                AssistChip(
                    onClick = { updateDeadline(formatUsDate(LocalDate.now().plusDays(1).toEpochDay())) },
                    label = { Text(localizedText("Tomorrow")) },
                )
            }
            item {
                AssistChip(
                    onClick = { updateDeadline(formatUsDate(LocalDate.now().plusWeeks(1).toEpochDay())) },
                    label = { Text(localizedText("Next week")) },
                )
            }
            if (deadlineText.isNotBlank()) {
                item {
                    AssistChip(onClick = { updateDeadline("") }, label = { Text(localizedText("Clear")) })
                }
            }
        }
        FilterChip(
            selected = hasTime,
            onClick = {
                hasTime = !hasTime
                if (hasTime && timeText.isBlank()) timeText = formatTime(0, use24HourTime)
            },
            enabled = canEnableTodoDeadlineTime(deadlineText),
            label = { Text(localizedText(if (hasTime) "Deadline has a time" else "Add deadline time")) },
        )
        if (hasTime) {
            OutlinedTextField(
                value = timeText,
                onValueChange = { timeText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(localizedText("Time")) },
                placeholder = { Text(localizedText("9:30 AM or 21:30")) },
                singleLine = true,
            )
        }
        Text(localizedText("Priority"), style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TodoPriority.entries) { option ->
                FilterChip(
                    selected = priority == option,
                    onClick = { priority = option },
                    label = { Text(localizedText(option.displayName())) },
                )
            }
        }
        OutlinedTextField(
            value = tagsText,
            onValueChange = { tagsText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(localizedText("Tags")) },
            placeholder = { Text(localizedText("work, errands")) },
            supportingText = { Text(localizedText("Separate tags with commas. Type to find existing tags.")) },
        )
        if (tagSuggestions.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tagSuggestions, key = { it }) { tag ->
                    val addTagContentDescription = localizedText("Add $tag tag")
                    AssistChip(
                        onClick = { tagsText = acceptTodoTagSuggestion(tagsText, tag) },
                        label = { Text(localizedText("#$tag")) },
                        modifier = Modifier.semantics {
                            contentDescription = addTagContentDescription
                        },
                    )
                }
            }
        }
        Text(localizedText("Reminders"), style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(reminderPresets) { preset ->
                FilterChip(
                    selected = preset.offset in reminderOffsets,
                    onClick = {
                        appliedDefaultReminders = true
                        reminderOffsets = if (preset.offset in reminderOffsets) {
                            reminderOffsets - preset.offset
                        } else {
                            reminderOffsets + preset.offset
                        }
                    },
                    enabled = deadlineText.isNotBlank(),
                    label = { Text(localizedText(preset.label)) },
                )
            }
            items(
                items = reminderOffsets
                    .filterNot { offset -> reminderPresets.any { it.offset == offset } }
                    .sorted(),
                key = { offset -> offset },
            ) { offset ->
                FilterChip(
                    selected = true,
                    onClick = {
                        appliedDefaultReminders = true
                        reminderOffsets = reminderOffsets - offset
                    },
                    enabled = deadlineText.isNotBlank(),
                    label = { Text(localizedText(formatReminderOffset(offset))) },
                )
            }
        }
        CustomReminderOffsetInput(
            existingOffsets = reminderOffsets,
            enabled = deadlineText.isNotBlank(),
            onAdd = { offset ->
                appliedDefaultReminders = true
                reminderOffsets = (reminderOffsets + offset).distinct().sorted()
            },
        )
        Text(localizedText("Subtasks"), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = subtasksText,
            onValueChange = { subtasksText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(localizedText("One subtask per line")) },
            minLines = 2,
            maxLines = 6,
        )
        if (editingSeriesOccurrence) {
            Text(localizedText("Apply changes to"), style = MaterialTheme.typography.labelLarge)
            ScopeChoice(
                selected = editScope == SeriesEditScope.ONLY_THIS_OCCURRENCE,
                label = "Only this occurrence",
                onClick = ::selectOnlyThisOccurrence,
            )
            ScopeChoice(
                selected = editScope == SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES,
                label = "This and future occurrences",
                onClick = { editScope = SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES },
            )
        }
        val recurrenceRuleEditable = canEditTodoRecurrenceRule(editingSeriesOccurrence, editScope)
        if (!recurrenceRuleEditable) {
            Text(
                localizedText(
                    "Choose This and future occurrences to change the repeat rule. " +
                        "Other task fields still apply only to this occurrence.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilterChip(
            selected = recurrenceEnabled,
            onClick = { recurrenceEnabled = !recurrenceEnabled },
            enabled = canEnableTodoRecurrence(deadlineText, editingSeriesOccurrence, editScope),
            label = { Text(localizedText(if (recurrenceEnabled) "Repeats" else "Add recurrence")) },
        )
        if (recurrenceEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = recurrenceInterval,
                    onValueChange = { recurrenceInterval = it.filter(Char::isDigit) },
                    modifier = Modifier.weight(0.7f),
                    label = { Text(localizedText("Every")) },
                    enabled = recurrenceRuleEditable,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                ChoiceMenu(
                    label = localizedText(recurrenceUnit.displayName(recurrenceInterval.toIntOrNull() ?: 1)),
                    modifier = Modifier.weight(1.3f),
                    enabled = recurrenceRuleEditable,
                ) { close ->
                    RecurrenceUnit.entries.forEach { unit ->
                        DropdownMenuItem(
                            text = { Text(localizedText(unit.displayName(recurrenceInterval.toIntOrNull() ?: 1))) },
                            onClick = { recurrenceUnit = unit; close() },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = recurrenceEndText,
                onValueChange = { recurrenceEndText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(localizedText("Repeat end date (optional)")) },
                enabled = recurrenceRuleEditable,
                placeholder = { Text(localizedText("M/d/yyyy")) },
                trailingIcon = {
                    IconButton(
                        enabled = recurrenceRuleEditable,
                        onClick = {
                            showDatePicker(recurrenceEndText) { selected -> recurrenceEndText = selected }
                        },
                    ) {
                        Icon(Icons.Default.CalendarMonth, localizedText("Choose repeat end date"))
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        }
        Text(localizedText("Attachments"), style = MaterialTheme.typography.labelLarge)
        if (existingAttachments.isNotEmpty()) {
            existingAttachments.forEach { attachment ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { openAttachment(attachment) },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            attachment.originalName,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        enabled = !isSaving,
                        onClick = {
                            viewModel.removeAttachment(attachment.id) { token ->
                                uiOperations.publishAttachmentDelete(
                                    PendingTodoAttachmentDelete(attachment.originalName, token),
                                )
                            }
                        },
                    ) {
                        Icon(Icons.Default.Delete, "Remove ${attachment.originalName}")
                    }
                }
            }
        }
        pendingUriStrings.forEachIndexed { index, uriString ->
            val uri = uriString.toUri()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "• ${uri.lastPathSegment ?: "Selected file"}",
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    enabled = !isSaving,
                    onClick = {
                        pendingUriStrings = pendingUriStrings.filterIndexed { i, _ -> i != index }
                    },
                ) {
                    Icon(Icons.Default.Close, "Remove ${uri.lastPathSegment ?: "selected attachment"}")
                }
            }
        }
        OutlinedButton(
            onClick = { attachmentLauncher.launch(arrayOf("*/*")) },
            enabled = !isSaving && existingAttachments.size + pendingUriStrings.size < MAX_ATTACHMENTS,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.AttachFile, null)
            Spacer(Modifier.width(8.dp))
            Text(localizedText("Add files (${existingAttachments.size + pendingUriStrings.size}/$MAX_ATTACHMENTS)"))
        }
        Text(
            localizedText("Up to 10 files per task, 25 MB each, and 128 MB total. New files are copied after the task is saved."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        validationError?.let {
            Text(localizedText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text(localizedText("Cancel")) }
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = !isSaving,
                onClick = {
                    buildDraft()?.let { draft ->
                        onSave(
                            draft,
                            editScope,
                            pendingUriStrings.map { it.toUri() },
                        )
                    }
                },
            ) {
                Text(localizedText(if (isSaving) "Saving…" else "Save"))
            }
        }
    }
}

@Composable
private fun ScopeChoice(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(localizedText(label))
    }
}

@Composable
private fun AppDialog(
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    HingeSafeDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.hingeSafeDialogSurface(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
                }
                snackbarHostState?.let { state ->
                    SnackbarHost(
                        hostState = state,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceMenu(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable (close: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ExpandMore, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            content { expanded = false }
        }
    }
}

private data class ReminderPreset(val label: String, val offset: Long)

private val reminderPresets = listOf(
    ReminderPreset("At due time", 0),
    ReminderPreset("1 hour before", 60),
    ReminderPreset("1 day before", 1_440),
    ReminderPreset("3 days before", 4_320),
    ReminderPreset("1 week before", 10_080),
)

private fun TodoPriority.displayName(): String = name.lowercase().replaceFirstChar(Char::uppercase)

private fun RecurrenceUnit.displayName(interval: Int): String {
    val singular = name.lowercase().replaceFirstChar(Char::uppercase)
    return if (interval == 1) singular else "${singular}s"
}

private fun CategoryEntity.displayName(all: List<CategoryEntity>): String {
    val namesById = all.associate { it.id to it.name }
    val parentIdsById = all.associate { it.id to it.parentId }
    return categoryPathLabel(id, namesById, parentIdsById) ?: name
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

private fun Bundle.nullableLong(key: String): Long? =
    if (containsKey(key)) getLong(key) else null

private fun formatUsDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US))

private fun formatDeadline(
    epochDay: Long,
    minute: Int?,
    dateFormatter: DateTimeFormatter,
    use24HourTime: Boolean,
): String {
    val date = LocalDate.ofEpochDay(epochDay).format(dateFormatter)
    return if (minute == null) date else "$date ${formatTime(minute, use24HourTime)}"
}

private fun formatTime(minute: Int, use24HourTime: Boolean): String =
    LocalTime.of(minute / 60, minute % 60).format(
        DateTimeFormatter.ofPattern(if (use24HourTime) "HH:mm" else "h:mm a", Locale.US),
    )

private fun parseMinuteOfDay(input: String): Int? {
    val value = input.trim().uppercase(Locale.US)
    val formatters = listOf(
        DateTimeFormatter.ofPattern("h:mm a", Locale.US),
        DateTimeFormatter.ofPattern("h a", Locale.US),
        DateTimeFormatter.ofPattern("H:mm", Locale.US),
    )
    return formatters.firstNotNullOfOrNull { formatter ->
        try {
            LocalTime.parse(value, formatter).let { it.hour * 60 + it.minute }
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
