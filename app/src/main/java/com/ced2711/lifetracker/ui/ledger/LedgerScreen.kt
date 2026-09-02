package com.ced2711.lifetracker.ui.ledger

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.ui.TaskLedgerViewModel
import com.ced2711.lifetracker.ui.attachment.rememberAttachmentOpener
import kotlinx.coroutines.launch

private enum class LedgerPage(val label: String) {
    ENTRIES("Entries"),
    STATISTICS("Statistics"),
    RECURRING("Recurring"),
}

@Composable
fun LedgerScreen(
    viewModel: TaskLedgerViewModel,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
    quickAddRequestToken: String? = null,
    onQuickAddRequestHandled: (String) -> Unit = {},
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pageName by rememberSaveable { mutableStateOf(LedgerPage.ENTRIES.name) }
    val page = LedgerPage.entries.firstOrNull { it.name == pageName } ?: LedgerPage.ENTRIES
    val formatting = rememberLedgerDisplayFormatting(settings)
    val openAttachment = rememberAttachmentOpener { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(quickAddRequestToken) {
        if (quickAddRequestToken != null) pageName = LedgerPage.ENTRIES.name
    }

    LaunchedEffect(viewModel) {
        viewModel.errors.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val pendingDelete = uiOperations.pendingDeletes.firstOrNull()
    LaunchedEffect(pendingDelete) {
        val item = pendingDelete ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Deleted ${item.title}",
            actionLabel = "Undo",
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDeleteLedgerEntry(item.receipt)
        }
        uiOperations.consumeDelete(item)
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                LedgerPage.entries.forEach { destination ->
                    FilterChip(
                        selected = page == destination,
                        onClick = { pageName = destination.name },
                        label = { Text(destination.label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            when (page) {
                LedgerPage.ENTRIES -> LedgerEntriesPage(
                    entries = entries,
                    isWide = isWide,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    onSave = { draft, operationToken, saved, failed ->
                        viewModel.saveLedgerWithResult(
                            draft = draft,
                            clientOperationToken = operationToken,
                            onSaved = saved,
                            onFailure = failed,
                        )
                    },
                    onLoad = { id, loaded -> viewModel.loadLedgerDraft(id, loaded) },
                    onDelete = ::deleteEntry,
                    onOwnerExists = { ownerId, result, failed ->
                        viewModel.attachmentOwnerExists(
                            ownerType = AttachmentOwnerType.LEDGER,
                            ownerId = ownerId,
                            onResult = result,
                            onFailure = failed,
                        )
                    },
                    onAddAttachments = { ownerId, uris, copyAttemptId, copied, failed ->
                        viewModel.addAttachments(
                            ownerType = AttachmentOwnerType.LEDGER,
                            ownerId = ownerId,
                            uris = uris,
                            copyAttemptId = copyAttemptId,
                            onCopied = copied,
                            onCopyFailed = failed,
                        )
                    },
                    attachmentsForEntry = { ownerId ->
                        viewModel.attachments(AttachmentOwnerType.LEDGER, ownerId)
                    },
                    onOpenAttachment = openAttachment,
                    onRemoveAttachment = { attachment ->
                        viewModel.removeAttachment(attachment.id) { token ->
                            uiOperations.publishAttachmentDelete(
                                PendingLedgerAttachmentDelete(attachment.originalName, token),
                            )
                        }
                    },
                    onUndoAttachmentDelete = { item ->
                        viewModel.undoRemoveAttachment(item.token)
                    },
                    formatting = formatting,
                    uiOperations = uiOperations,
                    quickAddRequestToken = quickAddRequestToken,
                    onQuickAddRequestHandled = onQuickAddRequestHandled,
                )

                LedgerPage.STATISTICS -> LedgerStatisticsPage(
                    entries = entries,
                    isWide = isWide,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    formatting = formatting,
                )

                LedgerPage.RECURRING -> LedgerRecurringPage(
                    series = series,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    onStop = viewModel::stopLedgerSeries,
                    onEditRule = { seriesId, effectiveEpochDay, draft, onSaved, onFailure ->
                        viewModel.editLedgerSeriesForFuture(
                            seriesId = seriesId,
                            effectiveEpochDay = effectiveEpochDay,
                            draft = draft,
                            onSaved = { onSaved() },
                            onFailure = onFailure,
                        )
                    },
                    formatting = formatting,
                    uiOperations = uiOperations,
                )
            }
        }
    }
}
