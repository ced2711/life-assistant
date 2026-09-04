package com.ced2711.lifetracker.ui.ledger

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.translateUiText
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.domain.date.SmartDateParser
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.ui.adaptive.HingeSafeDialog
import com.ced2711.lifetracker.ui.adaptive.hingeSafeDialogSurface
import com.ced2711.lifetracker.ui.adaptive.rememberHingeSafePlatformDialogLauncher
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
internal fun LedgerEditorDialog(
    initialDraft: LedgerDraft,
    isWide: Boolean,
    formatting: LedgerDisplayFormatting,
    attachmentsForEntry: (Long) -> Flow<List<AttachmentEntity>>,
    onOpenAttachment: (AttachmentEntity) -> Unit,
    onRemoveAttachment: (AttachmentEntity) -> Unit,
    pendingAttachmentDelete: PendingLedgerAttachmentDelete?,
    onUndoAttachmentDelete: (PendingLedgerAttachmentDelete) -> Unit,
    onConsumeAttachmentDelete: (PendingLedgerAttachmentDelete) -> Unit,
    isSaving: Boolean,
    failureMessage: String?,
    onDismiss: () -> Unit,
    onSave: (LedgerDraft, List<Uri>) -> Unit,
) {
    val context = LocalContext.current
    val platformDialogLauncher = rememberHingeSafePlatformDialogLauncher()
    val editorKey = initialDraft.id?.toString() ?: "new"
    var typeName by rememberSaveable(editorKey) { mutableStateOf(initialDraft.type.name) }
    val type = LedgerType.entries.firstOrNull { it.name == typeName } ?: initialDraft.type
    var amount by rememberSaveable(editorKey) {
        mutableStateOf(if (initialDraft.amountCents > 0) initialDraft.amountCents.let(::formatMoney).removePrefix("$").replace(",", "") else "")
    }
    val today = LocalDate.now()
    var dateInput by rememberSaveable(editorKey) {
        mutableStateOf(LocalDate.ofEpochDay(initialDraft.epochDay).format(LEDGER_SHORTCUT_DATE_FORMATTER))
    }
    val date = SmartDateParser.parse(dateInput, today)
    var timeInput by rememberSaveable(editorKey) {
        mutableStateOf(
            formatLedgerTimeInput(
                initialDraft.minuteOfDay,
                uses24HourTime = formatting.uses24HourTime,
            ),
        )
    }
    val minuteOfDay = parseLedgerTimeInput(timeInput)
    var merchant by rememberSaveable(editorKey) { mutableStateOf(initialDraft.merchant) }
    var note by rememberSaveable(editorKey) { mutableStateOf(initialDraft.note) }
    var tags by rememberSaveable(editorKey) { mutableStateOf(initialDraft.tags.joinToString(", ")) }
    var recurring by rememberSaveable(editorKey) { mutableStateOf(initialDraft.recurrence != null) }
    var recurrenceUnitName by rememberSaveable(editorKey) {
        mutableStateOf((initialDraft.recurrence?.unit ?: RecurrenceUnit.MONTH).name)
    }
    val recurrenceUnit = RecurrenceUnit.entries.firstOrNull { it.name == recurrenceUnitName }
        ?: RecurrenceUnit.MONTH
    var interval by rememberSaveable(editorKey) {
        mutableStateOf((initialDraft.recurrence?.interval ?: 1).toString())
    }
    var hasEndDate by rememberSaveable(editorKey) {
        mutableStateOf(initialDraft.recurrence?.endEpochDay != null)
    }
    var endDateEpochDay by rememberSaveable(editorKey) {
        mutableStateOf(
            initialDraft.recurrence?.endEpochDay
                ?: defaultLedgerRecurrenceEndEpochDay(initialDraft.epochDay),
        )
    }
    val endDate = LocalDate.ofEpochDay(endDateEpochDay)
    var attachmentUriStrings by rememberSaveable(editorKey) { mutableStateOf(emptyList<String>()) }
    val attachments = attachmentUriStrings.map(Uri::parse)
    val existingAttachmentFlow = remember(initialDraft.id) {
        initialDraft.id?.let(attachmentsForEntry) ?: flowOf(emptyList())
    }
    val existingAttachments by existingAttachmentFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val uiLanguage = LocalUiLanguage.current
    val attachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { selected ->
        val remaining = (10 - existingAttachments.size - attachments.size).coerceAtLeast(0)
        attachmentUriStrings = (attachmentUriStrings + selected.take(remaining).map(Uri::toString))
            .distinct()
    }
    val amountCents = parseAmountCents(amount)
    val intervalValue = interval.toIntOrNull()
    val recurrenceValid = !recurring || (
        intervalValue != null && intervalValue > 0 && date != null &&
            (!hasEndDate || !endDate.isBefore(date))
        )
    val futureRecurrenceConversionBlocked = isFutureRecurrenceConversionBlocked(
        initialDraft = initialDraft,
        recurringEnabled = recurring,
        selectedEpochDay = date?.toEpochDay(),
        todayEpochDay = today.toEpochDay(),
    )
    val futureRecurringWithoutEntry = initialDraft.id == null && recurring && date?.isAfter(today) == true
    val valid = amountCents != null && date != null && minuteOfDay != null && recurrenceValid &&
        !futureRecurrenceConversionBlocked &&
        (!futureRecurringWithoutEntry || attachments.isEmpty())

    LaunchedEffect(pendingAttachmentDelete, uiLanguage) {
        val item = pendingAttachmentDelete ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = translateUiText("Removed ${item.originalName}", uiLanguage),
            actionLabel = translateUiText("Undo", uiLanguage),
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onUndoAttachmentDelete(item)
        }
        onConsumeAttachmentDelete(item)
    }

    fun openDatePicker(current: LocalDate, update: (LocalDate) -> Unit) {
        platformDialogLauncher(
            DatePickerDialog(
                context,
                { _, year, month, day -> update(LocalDate.of(year, month + 1, day)) },
                current.year,
                current.monthValue - 1,
                current.dayOfMonth,
            ),
        )
    }

    HingeSafeDialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(
            modifier = Modifier
                .hingeSafeDialogSurface(
                    maxWidth = 900.dp,
                    widthFraction = 1f,
                    heightFraction = 1f,
                ),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(Icons.Outlined.Close, contentDescription = localizedText("Close"))
                    }
                    Text(
                        text = localizedText(if (initialDraft.id == null) "New entry" else "Edit entry"),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = valid && !isSaving,
                        onClick = {
                            val cents = amountCents ?: return@Button
                            val selectedDate = date ?: return@Button
                            val selectedMinute = minuteOfDay ?: return@Button
                            val rule = if (recurring) {
                                RecurrenceRule(
                                    unit = recurrenceUnit,
                                    interval = intervalValue ?: 1,
                                    endEpochDay = if (hasEndDate) endDate.toEpochDay() else null,
                                )
                            } else {
                                null
                            }
                            onSave(
                                initialDraft.copy(
                                    type = type,
                                    amountCents = cents,
                                    epochDay = selectedDate.toEpochDay(),
                                    minuteOfDay = selectedMinute,
                                    merchant = merchant,
                                    note = note,
                                    tags = tags.split(',').map(String::trim).filter(String::isNotEmpty),
                                    recurrence = rule,
                                ),
                                attachments,
                            )
                        },
                    ) { Text(localizedText(if (isSaving) "Saving…" else "Save")) }
                }
                failureMessage?.let { message ->
                    Text(
                        text = localizedText(message),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val useColumns = isWide && maxWidth >= 650.dp
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 32.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 900.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                LedgerTypeChooser(type = type, onTypeChanged = { typeName = it.name })
                                OutlinedTextField(
                                    value = amount,
                                    onValueChange = { candidate ->
                                        sanitizeAmountInput(candidate)?.let { amount = it }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(localizedText("Amount")) },
                                    prefix = { Text(localizedText("$")) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    isError = amount.isNotBlank() && amountCents == null,
                                    supportingText = {
                                        Text(localizedText("Required · up to $999,999,999.99 · max 2 decimal places"))
                                    },
                                )
                                AdaptiveFieldPair(
                                    useColumns = useColumns,
                                    first = {
                                        OutlinedTextField(
                                            value = dateInput,
                                            onValueChange = { dateInput = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(localizedText("Date")) },
                                            placeholder = { Text(localizedText("15, 8/15, or 8/15/2026")) },
                                            singleLine = true,
                                            isError = date == null,
                                            trailingIcon = {
                                                IconButton(
                                                    onClick = {
                                                        openDatePicker(
                                                            date ?: LocalDate.ofEpochDay(initialDraft.epochDay),
                                                        ) { selected ->
                                                            dateInput = selected.format(LEDGER_SHORTCUT_DATE_FORMATTER)
                                                        }
                                                    },
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.Event,
                                                        contentDescription = localizedText("Choose date"),
                                                    )
                                                }
                                            },
                                            supportingText = {
                                                Text(
                                                    localizedText(
                                                        date?.let { selected ->
                                                            "Selected: ${formatting.date(selected.toEpochDay())}"
                                                        } ?: "Enter a valid day, month/day, or month/day/year",
                                                    ),
                                                )
                                            },
                                        )
                                    },
                                    second = {
                                        OutlinedTextField(
                                            value = timeInput,
                                            onValueChange = { timeInput = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(localizedText("Time")) },
                                            placeholder = { Text(localizedText("9:30 AM or 21:30")) },
                                            singleLine = true,
                                            isError = minuteOfDay == null,
                                            trailingIcon = {
                                                IconButton(
                                                    onClick = {
                                                        val initialMinute = minuteOfDay
                                                            ?: initialDraft.minuteOfDay
                                                        platformDialogLauncher(
                                                            TimePickerDialog(
                                                                context,
                                                                { _, hour, minute ->
                                                                    timeInput = formatLedgerTimeInput(
                                                                        hour * 60 + minute,
                                                                        uses24HourTime =
                                                                            formatting.uses24HourTime,
                                                                    )
                                                                },
                                                                initialMinute / 60,
                                                                initialMinute % 60,
                                                                formatting.uses24HourTime,
                                                            ),
                                                        )
                                                    },
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.Schedule,
                                                        contentDescription = localizedText("Choose time"),
                                                    )
                                                }
                                            },
                                            supportingText = {
                                                Text(
                                                    localizedText(
                                                        minuteOfDay?.let(formatting::time)
                                                            ?: "Enter a valid time such as 9:30 AM or 21:30",
                                                    ),
                                                )
                                            },
                                        )
                                    },
                                )
                                AdaptiveFieldPair(
                                    useColumns = useColumns,
                                    first = {
                                        OutlinedTextField(
                                        value = merchant,
                                        onValueChange = { merchant = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(localizedText("Merchant")) },
                                        singleLine = true,
                                    )
                                    },
                                    second = {
                                        OutlinedTextField(
                                        value = tags,
                                        onValueChange = { tags = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(localizedText("Tags")) },
                                        placeholder = { Text(localizedText("travel, work")) },
                                        singleLine = true,
                                    )
                                    },
                                )
                                OutlinedTextField(
                                    value = note,
                                    onValueChange = { note = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(localizedText("Note")) },
                                    minLines = 2,
                                    maxLines = 5,
                                )
                                RecurrenceEditor(
                                    enabled = recurring,
                                    onEnabledChanged = { recurring = it },
                                    scheduleLocked = initialDraft.id != null && initialDraft.recurrence != null,
                                    unit = recurrenceUnit,
                                    onUnitChanged = { recurrenceUnitName = it.name },
                                    interval = interval,
                                    onIntervalChanged = { candidate ->
                                        sanitizeRecurrenceIntervalInput(candidate)?.let { interval = it }
                                    },
                                    hasEndDate = hasEndDate,
                                    onHasEndDateChanged = { hasEndDate = it },
                                    endDate = endDate,
                                    onPickEndDate = {
                                        openDatePicker(endDate) { endDateEpochDay = it.toEpochDay() }
                                    },
                                    startDate = date ?: LocalDate.ofEpochDay(initialDraft.epochDay),
                                    formatting = formatting,
                                    conversionError = if (futureRecurrenceConversionBlocked) {
                                        "An existing entry can only start repeating today or earlier. " +
                                            "Create a new recurring entry for a future start."
                                    } else {
                                        null
                                    },
                                )
                                AttachmentPicker(
                                    existingAttachments = existingAttachments,
                                    pendingAttachments = attachments,
                                    allowNewAttachments = !futureRecurringWithoutEntry,
                                    enabled = !isSaving,
                                    disabledReason = if (futureRecurringWithoutEntry) {
                                        "Receipts can be added after the first scheduled entry is generated."
                                    } else {
                                        null
                                    },
                                    onPick = { attachmentLauncher.launch(ledgerAttachmentMimeTypes()) },
                                    onOpenExisting = onOpenAttachment,
                                    onRemoveExisting = onRemoveAttachment,
                                    onRemovePending = { uri ->
                                        attachmentUriStrings = attachmentUriStrings - uri.toString()
                                    },
                                )
                            }
                        }
                    }
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
}

internal fun defaultLedgerRecurrenceEndEpochDay(startEpochDay: Long): Long {
    val latestUnclampedStartEpochDay = LocalDate
        .ofEpochDay(SmartDateParser.MAX_SUPPORTED_EPOCH_DAY)
        .minusMonths(1)
        .toEpochDay()
    if (startEpochDay > latestUnclampedStartEpochDay) {
        return SmartDateParser.MAX_SUPPORTED_EPOCH_DAY
    }
    return LocalDate.ofEpochDay(startEpochDay).plusMonths(1).toEpochDay()
}

internal fun ledgerAttachmentMimeTypes(): Array<String> = arrayOf("*/*")

@Composable
private fun AdaptiveFieldPair(
    useColumns: Boolean,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    if (useColumns) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) { first() }
            Box(modifier = Modifier.weight(1f)) { second() }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            first()
            second()
        }
    }
}

@Composable
private fun RecurrenceEditor(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    scheduleLocked: Boolean,
    unit: RecurrenceUnit,
    onUnitChanged: (RecurrenceUnit) -> Unit,
    interval: String,
    onIntervalChanged: (String) -> Unit,
    hasEndDate: Boolean,
    onHasEndDateChanged: (Boolean) -> Unit,
    endDate: LocalDate,
    onPickEndDate: () -> Unit,
    startDate: LocalDate,
    formatting: LedgerDisplayFormatting,
    conversionError: String?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(localizedText("Repeat"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        localizedText("Create independent entries on schedule"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChanged,
                    enabled = !scheduleLocked,
                )
            }
            if (scheduleLocked) {
                Text(
                    localizedText("This occurrence is independent. Stop its schedule from the Recurring page."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            conversionError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (enabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RecurrenceUnit.entries.forEach { option ->
                        FilterChip(
                            selected = option == unit,
                            onClick = { onUnitChanged(option) },
                            enabled = !scheduleLocked,
                            label = {
                                Text(localizedText(option.name.lowercase().replaceFirstChar { it.titlecase(Locale.US) }))
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = interval,
                    onValueChange = onIntervalChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(localizedText("Every N ${unit.name.lowercase()}(s)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = recurrenceIntervalError(interval) != null,
                    enabled = !scheduleLocked,
                    supportingText = recurrenceIntervalError(interval)?.let { message ->
                        { Text(localizedText(message)) }
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hasEndDate,
                        onCheckedChange = onHasEndDateChanged,
                        enabled = !scheduleLocked,
                    )
                    Text(localizedText("End date"))
                }
                if (hasEndDate) {
                    OutlinedButton(
                        onClick = onPickEndDate,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !scheduleLocked,
                    ) {
                        Icon(Icons.Outlined.Event, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(formatting.date(endDate.toEpochDay()))
                    }
                    if (endDate.isBefore(startDate)) {
                        Text(
                            localizedText("End date cannot be before the entry date"),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

internal fun isFutureRecurrenceConversionBlocked(
    initialDraft: LedgerDraft,
    recurringEnabled: Boolean,
    selectedEpochDay: Long?,
    todayEpochDay: Long,
): Boolean = initialDraft.id != null &&
    initialDraft.recurrence == null &&
    recurringEnabled &&
    selectedEpochDay != null &&
    selectedEpochDay > todayEpochDay

@Composable
private fun AttachmentPicker(
    existingAttachments: List<AttachmentEntity>,
    pendingAttachments: List<Uri>,
    allowNewAttachments: Boolean,
    enabled: Boolean,
    disabledReason: String?,
    onPick: () -> Unit,
    onOpenExisting: (AttachmentEntity) -> Unit,
    onRemoveExisting: (AttachmentEntity) -> Unit,
    onRemovePending: (Uri) -> Unit,
) {
    val attachmentCount = existingAttachments.size + pendingAttachments.size
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(localizedText("Attachments"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        localizedText("$attachmentCount of 10 files"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        localizedText("25 MB each, 128 MB total"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onPick,
                    enabled = enabled && allowNewAttachments && attachmentCount < 10,
                ) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(localizedText("Choose"))
                }
            }
            disabledReason?.let { reason ->
                Text(
                    localizedText(reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            existingAttachments.forEach { attachment ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = attachment.originalName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { onOpenExisting(attachment) },
                        enabled = enabled,
                    ) { Text(localizedText("Open")) }
                    TextButton(
                        onClick = { onRemoveExisting(attachment) },
                        enabled = enabled,
                    ) { Text(localizedText("Remove")) }
                }
            }
            pendingAttachments.forEach { uri ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = uri.lastPathSegment ?: localizedText("Selected file"),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { onRemovePending(uri) },
                        enabled = enabled,
                    ) { Text(localizedText("Remove")) }
                }
            }
        }
    }
}
