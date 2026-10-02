package com.ced2711.lifetracker.ui.ledger

import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.LedgerSeriesEntity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.MoneyTotals
import com.ced2711.lifetracker.domain.model.parseTags
import com.ced2711.lifetracker.ui.TaskLedgerViewModel
import com.ced2711.lifetracker.ui.attachment.rememberAttachmentOpener
import com.ced2711.lifetracker.ui.design.ReadableWidth
import com.ced2711.lifetracker.ui.design.Segmented
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.translateUiText
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Money in and out. Entries: a period you can step through, its totals, quick add, search and the
 * entries by day. Statistics: the period as a picture. Recurring: the schedules.
 *
 * This part talks to the view models: it owns saving (including the retry that copies receipts
 * without saving the entry twice), deleting with Undo, and which editor is open. What is on
 * screen is [LedgerContent].
 */
@Composable
fun LedgerScreen(
    viewModel: TaskLedgerViewModel,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
    quickAddRequestToken: String? = null,
    onQuickAddRequestHandled: (String) -> Unit = {},
    // An entry opened from elsewhere (the Calendar) is shown in the Entries page editor.
    requestedEntryId: Long? = null,
    onRequestedEntryHandled: (Long) -> Unit = {},
) {
    val uiOperations: LedgerUiOperationsViewModel = composeViewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, uiOperations) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) uiOperations.reconcileUndoWindows()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val entries by viewModel.ledgerEntries.collectAsStateWithLifecycle()
    val series by viewModel.ledgerSeries.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val attachmentOwnerIds by viewModel.ledgerAttachmentOwnerIds.collectAsStateWithLifecycle(initialValue = emptyList())
    val entriesWithAttachments = remember(attachmentOwnerIds) { attachmentOwnerIds.toSet() }
    val snackbarHostState = remember { SnackbarHostState() }
    val editorSnackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uiLanguage = LocalUiLanguage.current
    val formatting = rememberLedgerDisplayFormatting(settings)
    val openAttachment = rememberAttachmentOpener { message ->
        scope.launch { snackbarHostState.showSnackbar(translateUiText(message, uiLanguage)) }
    }

    var tabName by rememberSaveable { mutableStateOf(LedgerTab.ENTRIES.name) }
    val tab = LedgerTab.entries.firstOrNull { it.name == tabName } ?: LedgerTab.ENTRIES
    var editorState by rememberSaveable { mutableStateOf<Bundle?>(null) }
    var pendingEditId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorSessionKey by rememberSaveable { mutableStateOf<String?>(null) }
    var quickSessionKey by rememberSaveable { mutableStateOf("$QUICK_LEDGER_SESSION_KEY:${UUID.randomUUID()}") }
    var ruleSeriesId by rememberSaveable { mutableStateOf<Long?>(null) }
    var ruleSessionKey by rememberSaveable { mutableStateOf<String?>(null) }
    val editorDraft = editorState?.toLedgerDraft()

    fun openEditor(draft: LedgerDraft) {
        if (uiOperations.savingSessionKey != null) return
        uiOperations.abandonSession(editorSessionKey)
        editorSessionKey = UUID.randomUUID().toString()
        pendingEditId = null
        editorState = draft.toEditorState()
    }

    fun requestEdit(id: Long) {
        if (uiOperations.savingSessionKey != null) return
        uiOperations.abandonSession(editorSessionKey)
        editorState = null
        editorSessionKey = UUID.randomUUID().toString()
        pendingEditId = id
    }

    fun closeEditor() {
        val closingSession = editorSessionKey
        if (uiOperations.savingSessionKey == closingSession) return
        editorState = null
        pendingEditId = null
        editorSessionKey = null
        uiOperations.abandonSession(closingSession)
    }

    fun closeRuleEditor() {
        val closingSession = ruleSessionKey
        if (uiOperations.savingSessionKey == closingSession) return
        ruleSeriesId = null
        ruleSessionKey = null
        uiOperations.abandonSession(closingSession)
    }

    // The widget and the Calendar always land on Entries.
    LaunchedEffect(quickAddRequestToken) {
        if (quickAddRequestToken != null) tabName = LedgerTab.ENTRIES.name
    }
    LaunchedEffect(requestedEntryId) {
        val id = requestedEntryId ?: return@LaunchedEffect
        tabName = LedgerTab.ENTRIES.name
        requestEdit(id)
        onRequestedEntryHandled(id)
    }
    LaunchedEffect(pendingEditId) {
        pendingEditId?.let { id ->
            viewModel.loadLedgerDraft(id) { loaded ->
                editorState = loaded.toEditorState()
                pendingEditId = null
            }
        }
    }
    LaunchedEffect(uiOperations.savedSessionKeys, editorSessionKey) {
        val sessionKey = editorSessionKey ?: return@LaunchedEffect
        if (sessionKey in uiOperations.savedSessionKeys) {
            uiOperations.consumeSaved(sessionKey)
            closeEditor()
        }
    }
    LaunchedEffect(uiOperations.savedSessionKeys, ruleSessionKey) {
        val sessionKey = ruleSessionKey ?: return@LaunchedEffect
        if (sessionKey in uiOperations.savedSessionKeys) {
            uiOperations.consumeSaved(sessionKey)
            closeRuleEditor()
        }
    }
    LaunchedEffect(viewModel, uiLanguage) {
        viewModel.errors.collect { message ->
            snackbarHostState.showSnackbar(translateUiText(message, uiLanguage))
        }
    }

    val pendingDelete = uiOperations.pendingDeletes.firstOrNull()
    LaunchedEffect(pendingDelete) {
        val item = pendingDelete ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = translateUiText("Deleted ${item.title}", uiLanguage),
            actionLabel = translateUiText("Undo", uiLanguage),
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDeleteLedgerEntry(item.receipt)
        }
        uiOperations.consumeDelete(item)
    }
    val pendingAttachmentDelete = uiOperations.pendingAttachmentDeletes.firstOrNull()
    LaunchedEffect(pendingAttachmentDelete, uiLanguage) {
        val item = pendingAttachmentDelete ?: return@LaunchedEffect
        val result = editorSnackbarHostState.showSnackbar(
            message = translateUiText("Removed ${item.originalName}", uiLanguage),
            actionLabel = translateUiText("Undo", uiLanguage),
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoRemoveAttachment(item.token)
        }
        uiOperations.consumeAttachmentDelete(item)
    }

    fun deleteEntry(id: Long) {
        if (!uiOperations.beginDelete(id)) return
        val entry = entries.firstOrNull { it.id == id }
        val title = entry?.merchant?.ifBlank { entry.note.ifBlank { entry.type.displayName() } }
            ?: "entry"
        viewModel.deleteLedgerEntry(
            id = id,
            onDeleted = { receipt ->
                uiOperations.publishDelete(id, PendingLedgerDelete(title, receipt))
            },
            onFailure = { uiOperations.markDeleteFailed(id) },
        )
    }

    fun saveQuickEntry(draft: LedgerDraft) {
        val sessionKey = quickSessionKey
        val operationToken = uiOperations.clientOperationToken(sessionKey, draft.toString())
        val attempt = uiOperations.beginSave(sessionKey) ?: return
        viewModel.saveLedgerWithResult(
            draft = draft,
            clientOperationToken = operationToken,
            onSaved = { uiOperations.markSaveSucceeded(sessionKey, attempt) },
            onFailure = { message -> uiOperations.markSaveFailed(sessionKey, attempt, message) },
        )
    }

    /**
     * Saves the entry, then copies its new files. When the copy fails the entry is already
     * stored, so a retry reuses that entry instead of adding it again.
     */
    fun saveEditorEntry(sessionKey: String, draft: LedgerDraft, attachments: List<Uri>) {
        val attempt = uiOperations.beginSave(sessionKey) ?: return
        val ownerRequest = draft.toString()
        if (draft.id == null) {
            uiOperations.clientOperationToken(sessionKey, ownerRequest)
        }
        val previouslySavedOwnerId = uiOperations.savedOwnerId(sessionKey, ownerRequest)

        fun copyAttachmentsAndFinish(ownerId: Long?) {
            if (attachments.isEmpty()) {
                uiOperations.markSaveSucceeded(sessionKey, attempt)
            } else if (ownerId == null) {
                uiOperations.markSaveFailed(sessionKey, attempt, "Attachments require a generated ledger entry.")
            } else {
                val copyAttemptId = uiOperations.attachmentCopyAttemptId(sessionKey, attachments.map(Uri::toString))
                viewModel.addAttachments(
                    ownerType = AttachmentOwnerType.LEDGER,
                    ownerId = ownerId,
                    uris = attachments,
                    copyAttemptId = copyAttemptId,
                    onCopied = { uiOperations.markSaveSucceeded(sessionKey, attempt) },
                    onCopyFailed = { message ->
                        uiOperations.markSaveFailed(sessionKey, attempt, receiptCopyFailureMessage(message))
                    },
                )
            }
        }

        fun saveOwnerAndCopy(ownerId: Long?) {
            val retrySafeDraft = ownerId?.let { draft.copy(id = it) } ?: draft
            val operationToken = if (draft.id == null && ownerId == null) {
                uiOperations.clientOperationToken(sessionKey, ownerRequest)
            } else {
                null
            }
            viewModel.saveLedgerWithResult(
                draft = retrySafeDraft,
                clientOperationToken = operationToken,
                onSaved = { result ->
                    result.entryId?.let { savedOwnerId ->
                        uiOperations.markOwnerSaved(sessionKey, attempt, savedOwnerId, ownerRequest)
                    }
                    copyAttachmentsAndFinish(result.entryId ?: ownerId)
                },
                onFailure = { message -> uiOperations.markSaveFailed(sessionKey, attempt, message) },
            )
        }

        if (previouslySavedOwnerId == null) {
            saveOwnerAndCopy(null)
        } else {
            viewModel.attachmentOwnerExists(
                ownerType = AttachmentOwnerType.LEDGER,
                ownerId = previouslySavedOwnerId,
                onResult = { ownerExists ->
                    if (ownerExists) {
                        saveOwnerAndCopy(previouslySavedOwnerId)
                    } else {
                        uiOperations.rejectSavedOwner(sessionKey, previouslySavedOwnerId)
                        saveOwnerAndCopy(null)
                    }
                },
                onFailure = { message -> uiOperations.markSaveFailed(sessionKey, attempt, message) },
            )
        }
    }

    val editorSession = editorSessionKey
    val ruleSession = ruleSessionKey
    LedgerContent(
        entries = entries,
        series = series,
        formatting = formatting,
        isWide = isWide,
        tab = tab,
        onTabChange = { tabName = it.name },
        modifier = modifier,
        entriesWithAttachments = entriesWithAttachments,
        quickAdd = LedgerQuickAddUi(
            isSaving = uiOperations.savingSessionKey == quickSessionKey,
            saveSucceeded = quickSessionKey in uiOperations.savedSessionKeys,
            failureMessage = uiOperations.failureFor(quickSessionKey),
        ),
        onQuickSave = ::saveQuickEntry,
        onQuickSaveConsumed = {
            uiOperations.consumeSaved(quickSessionKey)
            quickSessionKey = "$QUICK_LEDGER_SESSION_KEY:${UUID.randomUUID()}"
        },
        // The keyboard belongs to an open editor first.
        quickAddRequestToken = quickAddRequestToken.takeIf { editorDraft == null },
        onQuickAddRequestHandled = onQuickAddRequestHandled,
        editor = if (editorDraft != null && editorSession != null) {
            LedgerEditorUi(
                draft = editorDraft,
                sessionKey = editorSession,
                isSaving = uiOperations.savingSessionKey == editorSession,
                failureMessage = uiOperations.failureFor(editorSession),
            )
        } else {
            null
        },
        onNewEntry = ::openEditor,
        onOpenEntry = ::requestEdit,
        onCloseEditor = ::closeEditor,
        onSaveEntry = { draft, attachments -> editorSession?.let { saveEditorEntry(it, draft, attachments) } },
        onDeleteEntry = { id ->
            if (editorDraft?.id == id) closeEditor()
            deleteEntry(id)
        },
        attachmentsForEntry = { ownerId -> viewModel.attachments(AttachmentOwnerType.LEDGER, ownerId) },
        onOpenAttachment = openAttachment,
        onRemoveAttachment = { attachment ->
            viewModel.removeAttachment(attachment.id) { token ->
                uiOperations.publishAttachmentDelete(PendingLedgerAttachmentDelete(attachment.originalName, token))
            }
        },
        ruleEditor = ruleSeriesId?.let { id ->
            ruleSession?.let { session ->
                LedgerRuleEditorUi(
                    seriesId = id,
                    isSaving = uiOperations.savingSessionKey == session,
                    failureMessage = uiOperations.failureFor(session),
                )
            }
        },
        onEditRule = { id ->
            if (uiOperations.savingSessionKey == null) {
                uiOperations.abandonSession(ruleSessionKey)
                ruleSeriesId = id
                ruleSessionKey = UUID.randomUUID().toString()
            }
        },
        onCloseRuleEditor = ::closeRuleEditor,
        onSaveRule = { seriesId, effectiveEpochDay, draft ->
            ruleSession?.let { session ->
                uiOperations.beginSave(session)?.let { attempt ->
                    viewModel.editLedgerSeriesForFuture(
                        seriesId = seriesId,
                        effectiveEpochDay = effectiveEpochDay,
                        draft = draft,
                        onSaved = { uiOperations.markSaveSucceeded(session, attempt) },
                        onFailure = { message -> uiOperations.markSaveFailed(session, attempt, message) },
                    )
                }
            }
        },
        onStopSeries = viewModel::stopLedgerSeries,
        onDeleteSeries = viewModel::deleteStoppedLedgerSeries,
        editorSnackbar = editorSnackbarHostState,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    )
}

/**
 * Everything the Ledger shows, without a view model: the three pages, the period they share, and
 * the open editor (a sheet on phones, the right-hand pane on wide screens).
 */
@Composable
internal fun LedgerContent(
    entries: List<LedgerEntryEntity>,
    series: List<LedgerSeriesEntity>,
    formatting: LedgerDisplayFormatting,
    isWide: Boolean,
    tab: LedgerTab,
    onTabChange: (LedgerTab) -> Unit,
    modifier: Modifier = Modifier,
    entriesWithAttachments: Set<Long> = emptySet(),
    quickAdd: LedgerQuickAddUi = LedgerQuickAddUi(),
    onQuickSave: (LedgerDraft) -> Unit = {},
    onQuickSaveConsumed: () -> Unit = {},
    quickAddRequestToken: String? = null,
    onQuickAddRequestHandled: (String) -> Unit = {},
    editor: LedgerEditorUi? = null,
    onNewEntry: (LedgerDraft) -> Unit = {},
    onOpenEntry: (Long) -> Unit = {},
    onCloseEditor: () -> Unit = {},
    onSaveEntry: (LedgerDraft, List<Uri>) -> Unit = { _, _ -> },
    onDeleteEntry: (Long) -> Unit = {},
    attachmentsForEntry: (Long) -> Flow<List<AttachmentEntity>> = { flowOf(emptyList()) },
    onOpenAttachment: (AttachmentEntity) -> Unit = {},
    onRemoveAttachment: (AttachmentEntity) -> Unit = {},
    ruleEditor: LedgerRuleEditorUi? = null,
    onEditRule: (Long) -> Unit = {},
    onCloseRuleEditor: () -> Unit = {},
    onSaveRule: (seriesId: Long, effectiveEpochDay: Long, draft: LedgerDraft) -> Unit = { _, _, _ -> },
    onStopSeries: (Long) -> Unit = {},
    onDeleteSeries: (Long) -> Unit = {},
    editorSnackbar: SnackbarHostState? = null,
    snackbarHost: @Composable () -> Unit = {},
    initialPeriod: LedgerPeriod = LedgerPeriod.MONTH,
    initialStopRequest: Long? = null,
) {
    val language = LocalUiLanguage.current
    val today = LocalDate.now()
    var periodName by rememberSaveable { mutableStateOf(initialPeriod.name) }
    val period = LedgerPeriod.entries.firstOrNull { it.name == periodName } ?: LedgerPeriod.MONTH
    var anchorEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val anchor = LocalDate.ofEpochDay(anchorEpochDay)
    var query by rememberSaveable { mutableStateOf("") }
    // The custom period keeps its last valid days while a date is being typed.
    var customStartEpochDay by rememberSaveable { mutableStateOf(today.withDayOfMonth(1).toEpochDay()) }
    var customEndEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var customStartInput by rememberSaveable { mutableStateOf(formatting.dateInput(customStartEpochDay)) }
    var customEndInput by rememberSaveable { mutableStateOf(formatting.dateInput(customEndEpochDay)) }
    val listState = rememberLazyListState()

    val live = remember(entries) {
        entries.filter { it.deletedAt == null }
            .sortedWith(compareByDescending<LedgerEntryEntity> { it.epochDay }.thenByDescending { it.minuteOfDay }.thenByDescending { it.id })
    }
    val range = remember(period, anchor, formatting.firstDayOfWeek, live, customStartEpochDay, customEndEpochDay) {
        period.range(
            anchor = anchor,
            firstDayOfWeek = formatting.firstDayOfWeek,
            entries = live,
            custom = LedgerRange(minOf(customStartEpochDay, customEndEpochDay), maxOf(customStartEpochDay, customEndEpochDay)),
        )
    }
    val periodEntries = remember(live, range) { live.filter { it.epochDay in range } }
    val shown = remember(periodEntries, query) { periodEntries.filter { ledgerEntryMatches(it, query) } }
    val totals = remember(periodEntries) { MoneyTotals.of(periodEntries) }
    val knownTags = remember(entries) {
        entries.flatMap { parseTags(it.tagsCsv) }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    }

    LaunchedEffect(quickAddRequestToken) {
        if (quickAddRequestToken != null) listState.scrollToItem(0)
    }

    val periodHeader: @Composable (List<LedgerPeriod>) -> Unit = { periods ->
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            LedgerPeriodSwitch(
                period = period,
                periods = periods,
                onPeriod = { periodName = it.name },
                showToday = period.canStep && today.toEpochDay() !in range,
                onToday = { anchorEpochDay = today.toEpochDay() },
            )
            if (period == LedgerPeriod.CUSTOM) {
                LedgerCustomRangeFields(
                    startText = customStartInput,
                    endText = customEndInput,
                    startDate = formatting.parseDate(customStartInput, today),
                    endDate = formatting.parseDate(customEndInput, today),
                    onStart = { text ->
                        customStartInput = text
                        formatting.parseDate(text, today)?.let { customStartEpochDay = it.toEpochDay() }
                    },
                    onEnd = { text ->
                        customEndInput = text
                        formatting.parseDate(text, today)?.let { customEndEpochDay = it.toEpochDay() }
                    },
                    onStartPicked = { picked ->
                        customStartEpochDay = picked.toEpochDay()
                        customStartInput = formatting.dateInput(picked.toEpochDay())
                    },
                    onEndPicked = { picked ->
                        customEndEpochDay = picked.toEpochDay()
                        customEndInput = formatting.dateInput(picked.toEpochDay())
                    },
                )
            }
            LedgerTotalsPanel(
                title = ledgerPeriodTitle(period, range, formatting, language),
                canStep = period.canStep,
                onShift = { steps -> anchorEpochDay = period.shift(anchor, steps).toEpochDay() },
                totals = totals,
            )
        }
    }
    val standardPeriods = listOf(LedgerPeriod.WEEK, LedgerPeriod.MONTH, LedgerPeriod.YEAR, LedgerPeriod.ALL)

    // On short screens the floating button would sit on top of quick add and search; it waits
    // until they have scrolled out of its corner.
    val fabZone = with(LocalDensity.current) { 96.dp.roundToPx() }
    val fabClear by remember(listState, fabZone) {
        derivedStateOf {
            val info = listState.layoutInfo
            val search = info.visibleItemsInfo.firstOrNull { it.key == "search" }
            search == null || search.offset + search.size <= info.viewportEndOffset - fabZone
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val twoPane = isWide && maxWidth >= 700.dp
        val sideWidth: Dp = (maxWidth * 0.42f).coerceIn(340.dp, 480.dp)
        val newEntry = { onNewEntry(newLedgerDraft()) }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = snackbarHost,
            floatingActionButton = {
                // Wide screens have the button next to the tabs instead.
                if (!twoPane && (tab == LedgerTab.RECURRING || (tab == LedgerTab.ENTRIES && fabClear))) {
                    FloatingActionButton(onClick = newEntry, containerColor = MaterialTheme.colorScheme.primary) {
                        Icon(Icons.Rounded.Add, localizedText("New entry"))
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                ReadableWidth(maxWidth = if (twoPane) Dp.Infinity else 760.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Space.lg).padding(top = Space.xs, bottom = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (twoPane) {
                            Segmented(LedgerTab.entries, tab, onTabChange, { localizedText(it.label) })
                            Spacer(Modifier.weight(1f))
                            Button(onClick = newEntry) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(localizedText("New entry"))
                            }
                        } else {
                            Segmented(LedgerTab.entries, tab, onTabChange, { localizedText(it.label) }, Modifier.fillMaxWidth(), fill = true)
                        }
                    }
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    ReadableWidth(Modifier.weight(1f).fillMaxHeight(), maxWidth = if (tab == LedgerTab.STATISTICS) 1_080.dp else 760.dp) {
                        when (tab) {
                            LedgerTab.ENTRIES -> LedgerEntriesList(
                                shown = shown,
                                query = query,
                                onQuery = { query = it },
                                formatting = formatting,
                                entriesWithAttachments = entriesWithAttachments,
                                selectedEntryId = editor?.draft?.id.takeIf { twoPane },
                                listState = listState,
                                onOpen = onOpenEntry,
                                onDelete = onDeleteEntry,
                                // Custom is chosen on Statistics; it stays visible here while it is in use.
                                header = { periodHeader(if (period == LedgerPeriod.CUSTOM) LedgerPeriod.entries else standardPeriods) },
                                quickAdd = {
                                    LedgerQuickAdd(
                                        state = quickAdd,
                                        onSave = onQuickSave,
                                        onConsumeSuccess = onQuickSaveConsumed,
                                        onDetails = onNewEntry,
                                        requestToken = quickAddRequestToken,
                                        onRequestHandled = onQuickAddRequestHandled,
                                    )
                                },
                            )
                            LedgerTab.STATISTICS -> LedgerStatisticsPage(
                                periodEntries = periodEntries,
                                range = range,
                                totals = totals,
                                formatting = formatting,
                                modifier = Modifier.fillMaxSize(),
                                header = { periodHeader(LedgerPeriod.entries) },
                            )
                            LedgerTab.RECURRING -> LedgerRecurringPage(
                                series = series,
                                formatting = formatting,
                                selectedSeriesId = ruleEditor?.seriesId.takeIf { twoPane },
                                onEditRule = onEditRule,
                                onStop = onStopSeries,
                                onDelete = onDeleteSeries,
                                initialStopRequest = initialStopRequest,
                            )
                        }
                    }
                    val ruleSeries = ruleEditor?.let { open -> series.firstOrNull { it.id == open.seriesId } }
                    val editors: @Composable () -> Unit = {
                        if (editor != null) {
                            key(editor.sessionKey) {
                                LedgerEditor(
                                    initialDraft = editor.draft,
                                    inPane = twoPane,
                                    formatting = formatting,
                                    knownTags = knownTags,
                                    attachmentsForEntry = attachmentsForEntry,
                                    onOpenAttachment = onOpenAttachment,
                                    onRemoveAttachment = onRemoveAttachment,
                                    isSaving = editor.isSaving,
                                    failureMessage = editor.failureMessage,
                                    onDismiss = onCloseEditor,
                                    onSave = onSaveEntry,
                                    onDelete = onDeleteEntry,
                                    snackbar = editorSnackbar,
                                )
                            }
                        } else if (ruleEditor != null && ruleSeries != null) {
                            LedgerRuleEditor(
                                series = ruleSeries,
                                inPane = twoPane,
                                formatting = formatting,
                                knownTags = knownTags,
                                isSaving = ruleEditor.isSaving,
                                failureMessage = ruleEditor.failureMessage,
                                onDismiss = onCloseRuleEditor,
                                onSave = { effectiveEpochDay, draft -> onSaveRule(ruleSeries.id, effectiveEpochDay, draft) },
                            )
                        }
                    }
                    // Wide screens: the open editor, or on Entries the period as a picture, beside the list.
                    val showSide = twoPane && (editor != null || ruleSeries != null || tab == LedgerTab.ENTRIES)
                    if (showSide) {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(LifeTheme.colors.divider))
                        Box(Modifier.width(sideWidth).fillMaxHeight()) {
                            if (editor == null && ruleSeries == null) {
                                Column(
                                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                        .padding(start = Space.lg, end = Space.lg, top = Space.xs, bottom = 32.dp),
                                    verticalArrangement = Arrangement.spacedBy(Space.md),
                                ) {
                                    LedgerTrendPanel(periodEntries, range, formatting)
                                    LedgerRatioPanel(totals)
                                    LedgerTagPanel(periodEntries)
                                }
                            } else {
                                editors()
                            }
                        }
                    }
                    // Phones: the editor is a sheet over the page.
                    if (!twoPane) editors()
                }
            }
        }
    }
}
