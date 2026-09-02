package com.ced2711.lifetracker.ui.ledger

import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerSaveResult
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.util.UUID

@Composable
internal fun LedgerEntriesPage(
    entries: List<LedgerEntryEntity>,
    isWide: Boolean,
    contentPadding: PaddingValues,
    onSave: (
        draft: LedgerDraft,
        clientOperationToken: String?,
        onSaved: (LedgerSaveResult) -> Unit,
        onFailure: (String) -> Unit,
    ) -> Unit,
    onLoad: (Long, (LedgerDraft) -> Unit) -> Unit,
    onDelete: (Long) -> Unit,
    onOwnerExists: (
        ownerId: Long,
        onResult: (Boolean) -> Unit,
        onFailure: (String) -> Unit,
    ) -> Unit,
    onAddAttachments: (
        ownerId: Long,
        uris: List<Uri>,
        copyAttemptId: String,
        onCopied: () -> Unit,
        onFailure: (String) -> Unit,
    ) -> Unit,
    attachmentsForEntry: (Long) -> Flow<List<AttachmentEntity>>,
    onOpenAttachment: (AttachmentEntity) -> Unit,
    onRemoveAttachment: (AttachmentEntity) -> Unit,
    onUndoAttachmentDelete: (PendingLedgerAttachmentDelete) -> Unit,
    formatting: LedgerDisplayFormatting,
    uiOperations: LedgerUiOperationsViewModel,
    quickAddRequestToken: String? = null,
    onQuickAddRequestHandled: (String) -> Unit = {},
) {
    var editorState by rememberSaveable { mutableStateOf<Bundle?>(null) }
    var pendingEditId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorSessionKey by rememberSaveable { mutableStateOf<String?>(null) }
    var quickSessionKey by rememberSaveable {
        mutableStateOf("$QUICK_LEDGER_SESSION_KEY:${UUID.randomUUID()}")
    }
    val entryListState = rememberLazyListState()
    val editorDraft = editorState?.toLedgerDraft()

    LaunchedEffect(quickAddRequestToken) {
        if (quickAddRequestToken != null) entryListState.scrollToItem(0)
    }

    fun openEditor(draft: LedgerDraft) {
        if (uiOperations.savingSessionKey != null) return
        editorSessionKey = UUID.randomUUID().toString()
        editorState = draft.toEditorState()
    }

    fun requestEdit(id: Long) {
        if (uiOperations.savingSessionKey != null) return
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

    fun saveQuickEntry(draft: LedgerDraft) {
        val sessionKey = quickSessionKey
        val operationToken = uiOperations.clientOperationToken(sessionKey, draft.toString())
        val attempt = uiOperations.beginSave(sessionKey) ?: return
        onSave(
            draft,
            operationToken,
            { uiOperations.markSaveSucceeded(sessionKey, attempt) },
            { message -> uiOperations.markSaveFailed(sessionKey, attempt, message) },
        )
    }

    fun consumeQuickSuccess() {
        val completedSession = quickSessionKey
        uiOperations.consumeSaved(completedSession)
        quickSessionKey = "$QUICK_LEDGER_SESSION_KEY:${UUID.randomUUID()}"
    }

    LaunchedEffect(pendingEditId) {
        pendingEditId?.let { id ->
            onLoad(id) { loaded ->
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

    if (editorDraft != null && editorSessionKey != null) {
        val sessionKey = requireNotNull(editorSessionKey)
        LedgerEditorDialog(
            initialDraft = editorDraft,
            isWide = isWide,
            formatting = formatting,
            attachmentsForEntry = attachmentsForEntry,
            onOpenAttachment = onOpenAttachment,
            onRemoveAttachment = onRemoveAttachment,
            pendingAttachmentDelete = uiOperations.pendingAttachmentDeletes.firstOrNull(),
            onUndoAttachmentDelete = onUndoAttachmentDelete,
            onConsumeAttachmentDelete = uiOperations::consumeAttachmentDelete,
            isSaving = uiOperations.savingSessionKey == sessionKey,
            failureMessage = uiOperations.failureFor(sessionKey),
            onDismiss = ::closeEditor,
            onSave = { draft, attachments ->
                val attempt = uiOperations.beginSave(sessionKey) ?: return@LedgerEditorDialog
                val ownerRequest = draft.toString()
                if (draft.id == null) {
                    uiOperations.clientOperationToken(sessionKey, ownerRequest)
                }
                val previouslySavedOwnerId = uiOperations.savedOwnerId(sessionKey, ownerRequest)

                fun copyAttachmentsAndFinish(ownerId: Long?) {
                    if (attachments.isEmpty()) {
                        uiOperations.markSaveSucceeded(sessionKey, attempt)
                    } else if (ownerId == null) {
                        uiOperations.markSaveFailed(
                            sessionKey,
                            attempt,
                            "Attachments require a generated ledger entry.",
                        )
                    } else {
                        val copyAttemptId = uiOperations.attachmentCopyAttemptId(
                            sessionKey,
                            attachments.map(Uri::toString),
                        )
                        onAddAttachments(
                            ownerId,
                            attachments,
                            copyAttemptId,
                            { uiOperations.markSaveSucceeded(sessionKey, attempt) },
                            { message ->
                                uiOperations.markSaveFailed(
                                    sessionKey,
                                    attempt,
                                    receiptCopyFailureMessage(message),
                                )
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
                    onSave(
                        retrySafeDraft,
                        operationToken,
                        { result ->
                            result.entryId?.let { savedOwnerId ->
                                uiOperations.markOwnerSaved(
                                    sessionKey,
                                    attempt,
                                    savedOwnerId,
                                    ownerRequest,
                                )
                            }
                            copyAttachmentsAndFinish(result.entryId ?: ownerId)
                        },
                        { message -> uiOperations.markSaveFailed(sessionKey, attempt, message) },
                    )
                }

                if (previouslySavedOwnerId == null) {
                    saveOwnerAndCopy(null)
                } else {
                    onOwnerExists(
                        previouslySavedOwnerId,
                        { ownerExists ->
                            if (ownerExists) {
                                saveOwnerAndCopy(previouslySavedOwnerId)
                            } else {
                                uiOperations.rejectSavedOwner(sessionKey, previouslySavedOwnerId)
                                saveOwnerAndCopy(null)
                            }
                        },
                        { message -> uiOperations.markSaveFailed(sessionKey, attempt, message) },
                    )
                }
            },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useTwoPanes = isWide && maxWidth >= 720.dp
        if (useTwoPanes) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 360.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                ) {
                    QuickEntryCard(
                        isSaving = uiOperations.savingSessionKey == quickSessionKey,
                        saveSucceeded = quickSessionKey in uiOperations.savedSessionKeys,
                        failureMessage = uiOperations.failureFor(quickSessionKey),
                        onConsumeSuccess = ::consumeQuickSuccess,
                        onSave = ::saveQuickEntry,
                        onOpenDetails = ::openEditor,
                        quickAddRequestToken = quickAddRequestToken.takeIf { editorDraft == null },
                        onQuickAddRequestHandled = onQuickAddRequestHandled,
                    )
                }
                EntryList(
                    entries = entries,
                    contentPadding = contentPadding,
                    formatting = formatting,
                    modifier = Modifier.weight(1f),
                    onEdit = ::requestEdit,
                    onDelete = onDelete,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = entryListState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = contentPadding.calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    QuickEntryCard(
                        isSaving = uiOperations.savingSessionKey == quickSessionKey,
                        saveSucceeded = quickSessionKey in uiOperations.savedSessionKeys,
                        failureMessage = uiOperations.failureFor(quickSessionKey),
                        onConsumeSuccess = ::consumeQuickSuccess,
                        onSave = ::saveQuickEntry,
                        onOpenDetails = ::openEditor,
                        quickAddRequestToken = quickAddRequestToken.takeIf { editorDraft == null },
                        onQuickAddRequestHandled = onQuickAddRequestHandled,
                    )
                }
                item {
                    Text(
                        text = "Recent entries",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (entries.isEmpty()) {
                    item { EmptyEntries() }
                } else {
                    items(entries, key = { it.id }) { entry ->
                        LedgerEntryCard(
                            entry = entry,
                            formatting = formatting,
                            onEdit = { requestEdit(entry.id) },
                            onDelete = { onDelete(entry.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryList(
    entries: List<LedgerEntryEntity>,
    contentPadding: PaddingValues,
    formatting: LedgerDisplayFormatting,
    modifier: Modifier = Modifier,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxHeight(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Recent entries", style = MaterialTheme.typography.titleMedium) }
        if (entries.isEmpty()) {
            item { EmptyEntries() }
        } else {
            items(entries, key = { it.id }) { entry ->
                LedgerEntryCard(
                    entry = entry,
                    formatting = formatting,
                    onEdit = { onEdit(entry.id) },
                    onDelete = { onDelete(entry.id) },
                )
            }
        }
    }
}

@Composable
private fun QuickEntryCard(
    isSaving: Boolean,
    saveSucceeded: Boolean,
    failureMessage: String?,
    onConsumeSuccess: () -> Unit,
    onSave: (LedgerDraft) -> Unit,
    onOpenDetails: (LedgerDraft) -> Unit,
    quickAddRequestToken: String?,
    onQuickAddRequestHandled: (String) -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var typeName by rememberSaveable { mutableStateOf(LedgerType.EXPENSE.name) }
    val type = LedgerType.entries.firstOrNull { it.name == typeName } ?: LedgerType.EXPENSE
    val amountCents = parseAmountCents(amount)
    val amountFocusRequester = remember { FocusRequester() }
    val softwareKeyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(quickAddRequestToken) {
        val requestToken = quickAddRequestToken ?: return@LaunchedEffect
        amountFocusRequester.requestFocus()
        softwareKeyboardController?.show()
        onQuickAddRequestHandled(requestToken)
    }

    LaunchedEffect(saveSucceeded) {
        if (saveSucceeded) {
            amount = ""
            typeName = LedgerType.EXPENSE.name
            onConsumeSuccess()
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Quick entry", style = MaterialTheme.typography.titleMedium)
            LedgerTypeChooser(type = type, onTypeChanged = { typeName = it.name })
            OutlinedTextField(
                value = amount,
                onValueChange = { candidate ->
                    sanitizeAmountInput(candidate)?.let { amount = it }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocusRequester),
                label = { Text("Amount") },
                prefix = { Text("$") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                supportingText = {
                    Text(failureMessage ?: "Up to $999,999,999.99 · max 2 decimal places")
                },
                isError = (amount.isNotEmpty() && amountCents == null) || failureMessage != null,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    enabled = !isSaving,
                    onClick = {
                        onOpenDetails(
                            newLedgerDraft().copy(
                                type = type,
                                amountCents = amountCents ?: 0,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Details")
                }
                Button(
                    enabled = amountCents != null && !isSaving,
                    onClick = {
                        val cents = amountCents ?: return@Button
                        onSave(newLedgerDraft().copy(type = type, amountCents = cents))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save") }
            }
        }
    }
}

@Composable
internal fun LedgerTypeChooser(
    type: LedgerType,
    onTypeChanged: (LedgerType) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 320.dp) {
            Column(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LedgerType.entries.forEach { option ->
                    LedgerTypeOption(
                        option = option,
                        selected = option == type,
                        onSelected = { onTypeChanged(option) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LedgerType.entries.forEach { option ->
                    LedgerTypeOption(
                        option = option,
                        selected = option == type,
                        onSelected = { onTypeChanged(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerTypeOption(
    option: LedgerType,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.selectable(
            selected = selected,
            onClick = onSelected,
            role = Role.RadioButton,
        ),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            if (option == LedgerType.INCOME) incomeColor().copy(alpha = 0.20f)
            else expenseColor().copy(alpha = 0.20f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(option.displayName(), maxLines = 1)
        }
    }
}

@Composable
private fun LedgerEntryCard(
    entry: LedgerEntryEntity,
    formatting: LedgerDisplayFormatting,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = if (entry.type == LedgerType.INCOME) incomeColor() else expenseColor()
    val formattedDate = formatting.date(entry.epochDay)
    Card(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth < 420.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 12.dp, bottom = 6.dp, end = 6.dp),
                ) {
                    EntrySummary(entry = entry, formatting = formatting)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = (if (entry.type == LedgerType.INCOME) "+" else "−") +
                                formatMoney(entry.amountCents),
                            color = accent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onEdit) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = ledgerEntryActionDescription(
                                    "Edit",
                                    entry,
                                    formattedDate,
                                ),
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = ledgerEntryActionDescription(
                                    "Delete",
                                    entry,
                                    formattedDate,
                                ),
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EntrySummary(
                        entry = entry,
                        formatting = formatting,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = (if (entry.type == LedgerType.INCOME) "+" else "−") +
                            formatMoney(entry.amountCents),
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = ledgerEntryActionDescription(
                                "Edit",
                                entry,
                                formattedDate,
                            ),
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = ledgerEntryActionDescription(
                                "Delete",
                                entry,
                                formattedDate,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntrySummary(
    entry: LedgerEntryEntity,
    formatting: LedgerDisplayFormatting,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = entry.merchant.ifBlank {
                entry.note.ifBlank { entry.type.displayName() }
            },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
        )
        Text(
            text = "${formatting.date(entry.epochDay)} · ${formatting.time(entry.minuteOfDay)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entry.tagsCsv.isNotBlank()) {
            Text(
                text = entry.tagsCsv.split(',').joinToString("  ") { "#$it" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyEntries() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No entries yet",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun incomeColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
    Color(0xFF77D89B)
} else {
    Color(0xFF147A42)
}

@Composable
internal fun expenseColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
    Color(0xFFFF8A80)
} else {
    Color(0xFFB3261E)
}

internal fun newLedgerDraft(now: LocalDateTime = LocalDateTime.now()): LedgerDraft {
    return LedgerDraft(
        amountCents = 0,
        epochDay = now.toLocalDate().toEpochDay(),
        minuteOfDay = now.hour * 60 + now.minute,
    )
}

internal fun ledgerEntryActionDescription(
    action: String,
    entry: LedgerEntryEntity,
    formattedDate: String,
): String {
    val label = entry.merchant.ifBlank { entry.note.ifBlank { entry.type.displayName() } }
    val signedAmount = if (entry.type == LedgerType.INCOME) entry.amountCents else -entry.amountCents
    return "$action $label, ${entry.type.displayName().lowercase()} " +
        "${formatSignedMoney(signedAmount)}, $formattedDate"
}

internal fun receiptCopyFailureMessage(reason: String): String {
    val detail = reason.ifBlank { "Unknown receipt copy error" }
    return "Entry saved, but receipts failed to copy: $detail. " +
        "Retry Save to copy them; the entry will not be duplicated."
}

/** Bundle-backed editor state keeps an open editor and its seed data through rotation/process restore. */
private fun LedgerDraft.toEditorState(): Bundle = Bundle().apply {
    putBoolean("has_id", id != null)
    id?.let { putLong("id", it) }
    putString("type", type.name)
    putLong("amount", amountCents)
    putLong("date", epochDay)
    putInt("time", minuteOfDay)
    putString("note", note)
    putString("merchant", merchant)
    putStringArrayList("tags", ArrayList(tags))
    putBoolean("has_recurrence", recurrence != null)
    recurrence?.let { rule ->
        putString("recurrence_unit", rule.unit.name)
        putInt("recurrence_interval", rule.interval)
        putBoolean("has_recurrence_end", rule.endEpochDay != null)
        rule.endEpochDay?.let { putLong("recurrence_end", it) }
    }
}

private fun Bundle.toLedgerDraft(): LedgerDraft? = runCatching {
    val recurrence = if (getBoolean("has_recurrence")) {
        RecurrenceRule(
            unit = RecurrenceUnit.valueOf(getString("recurrence_unit") ?: return null),
            interval = getInt("recurrence_interval", 1),
            endEpochDay = if (getBoolean("has_recurrence_end")) getLong("recurrence_end") else null,
        )
    } else {
        null
    }
    LedgerDraft(
        id = if (getBoolean("has_id")) getLong("id") else null,
        type = LedgerType.valueOf(getString("type") ?: LedgerType.EXPENSE.name),
        amountCents = getLong("amount"),
        epochDay = getLong("date"),
        minuteOfDay = getInt("time"),
        note = getString("note").orEmpty(),
        merchant = getString("merchant").orEmpty(),
        tags = getStringArrayList("tags").orEmpty(),
        recurrence = recurrence,
    )
}.getOrNull()
