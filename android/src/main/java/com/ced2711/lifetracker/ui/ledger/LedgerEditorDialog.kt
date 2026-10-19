package com.ced2711.lifetracker.ui.ledger

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.domain.date.SmartDateParser
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.parseTags
import com.ced2711.lifetracker.ui.components.DatePickerButton
import com.ced2711.lifetracker.ui.components.EditorSheet
import com.ced2711.lifetracker.ui.components.FieldLabel
import com.ced2711.lifetracker.ui.components.TimePickerButton
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.ListRow
import com.ced2711.lifetracker.ui.design.Panel
import com.ced2711.lifetracker.ui.design.Pill
import com.ced2711.lifetracker.ui.design.Segmented
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** An open entry editor: what it started from and how its last save went. */
internal data class LedgerEditorUi(
    val draft: LedgerDraft,
    /** Changes whenever another entry is opened, so typed text never leaks from one editor into the next. */
    val sessionKey: String = "",
    val isSaving: Boolean = false,
    val failureMessage: String? = null,
)

/**
 * Where an editor lives: the full-height sheet on phones, or the right-hand pane on wide screens
 * ([inPane]). Both have Close, the title and Save at the top, scrolling fields, and [footer] for
 * rare actions; [snackbar] shows Undo for a removed file above the footer.
 */
@Composable
internal fun LedgerEditorFrame(
    inPane: Boolean,
    title: String,
    onClose: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    working: Boolean,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bottom: (@Composable RowScope.() -> Unit)? = if (footer != null || snackbar?.currentSnackbarData != null) {
        {
            Column(Modifier.weight(1f)) {
                if (snackbar != null) SnackbarHost(snackbar)
                if (footer != null) Row(verticalAlignment = Alignment.CenterVertically, content = footer)
            }
        }
    } else {
        null
    }
    if (!inPane) {
        EditorSheet(
            title = title,
            onClose = { if (!working) onClose() },
            actionLabel = localizedText("Save"),
            onAction = onSave,
            modifier = modifier,
            actionEnabled = saveEnabled,
            working = working,
            footer = bottom,
            content = content,
        )
        return
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, enabled = !working) { Icon(Icons.Rounded.Close, localizedText("Close")) }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = Space.xs).semantics { heading() },
            )
            Button(onClick = onSave, enabled = saveEnabled && !working, modifier = Modifier.padding(end = Space.md)) {
                if (working) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(localizedText("Save"))
                }
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.lg, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            content = content,
        )
        if (bottom != null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(LifeTheme.colors.divider))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                content = bottom,
            )
        }
    }
}

/**
 * The editor of one entry: type, amount, when, where, tags, note, repeat and attached files.
 * A new entry can start a schedule; an entry that belongs to a schedule cannot change it here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LedgerEditor(
    initialDraft: LedgerDraft,
    inPane: Boolean,
    formatting: LedgerDisplayFormatting,
    knownTags: List<String>,
    attachmentsForEntry: (Long) -> Flow<List<AttachmentEntity>>,
    onOpenAttachment: (AttachmentEntity) -> Unit,
    onRemoveAttachment: (AttachmentEntity) -> Unit,
    isSaving: Boolean,
    failureMessage: String?,
    onDismiss: () -> Unit,
    onSave: (LedgerDraft, List<Uri>) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState? = null,
) {
    val language = LocalUiLanguage.current
    val editorKey = initialDraft.id?.toString() ?: "new"
    val today = LocalDate.now()
    var typeName by rememberSaveable(editorKey) { mutableStateOf(initialDraft.type.name) }
    val type = LedgerType.entries.firstOrNull { it.name == typeName } ?: initialDraft.type
    var amount by rememberSaveable(editorKey) { mutableStateOf(amountInputText(initialDraft.amountCents)) }
    var dateInput by rememberSaveable(editorKey) { mutableStateOf(formatting.dateInput(initialDraft.epochDay)) }
    val date = formatting.parseDate(dateInput, today)
    var timeInput by rememberSaveable(editorKey) {
        mutableStateOf(formatLedgerTimeInput(initialDraft.minuteOfDay, uses24HourTime = formatting.uses24HourTime))
    }
    val minuteOfDay = parseLedgerTimeInput(timeInput)
    var merchant by rememberSaveable(editorKey) { mutableStateOf(initialDraft.merchant) }
    var note by rememberSaveable(editorKey) { mutableStateOf(initialDraft.note) }
    var tags by rememberSaveable(editorKey) { mutableStateOf(initialDraft.tags.joinToString(", ")) }
    // Empty means the entry does not repeat.
    var repeatUnitName by rememberSaveable(editorKey) { mutableStateOf(initialDraft.recurrence?.unit?.name.orEmpty()) }
    val repeatUnit = RecurrenceUnit.entries.firstOrNull { it.name == repeatUnitName }
    val recurring = repeatUnit != null
    var interval by rememberSaveable(editorKey) { mutableStateOf((initialDraft.recurrence?.interval ?: 1).toString()) }
    var endInput by rememberSaveable(editorKey) {
        mutableStateOf(initialDraft.recurrence?.endEpochDay?.let(formatting::dateInput).orEmpty())
    }
    val endDate = endInput.takeIf(String::isNotBlank)?.let { formatting.parseDate(it, today) }
    var attachmentUriStrings by rememberSaveable(editorKey) { mutableStateOf(emptyList<String>()) }
    val pendingAttachments = attachmentUriStrings.map(Uri::parse)
    val existingAttachmentFlow = remember(initialDraft.id) {
        initialDraft.id?.let(attachmentsForEntry) ?: flowOf(emptyList())
    }
    val existingAttachments by existingAttachmentFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        val remaining = (10 - existingAttachments.size - pendingAttachments.size).coerceAtLeast(0)
        attachmentUriStrings = (attachmentUriStrings + selected.take(remaining).map(Uri::toString)).distinct()
    }

    val amountCents = parseAmountCents(amount)
    val intervalValue = interval.toIntOrNull()
    // An entry of an existing schedule is independent: its schedule is changed on the Recurring page.
    val scheduleLocked = initialDraft.id != null && initialDraft.recurrence != null
    val endInvalid = endInput.isNotBlank() && endDate == null
    val endBeforeStart = endDate != null && date != null && endDate.isBefore(date)
    val recurrenceValid = !recurring ||
        (intervalValue != null && intervalValue > 0 && date != null && !endInvalid && !endBeforeStart)
    val futureRecurrenceConversionBlocked = isFutureRecurrenceConversionBlocked(
        initialDraft = initialDraft,
        recurringEnabled = recurring,
        selectedEpochDay = date?.toEpochDay(),
        todayEpochDay = today.toEpochDay(),
    )
    // A new schedule that starts in the future has no entry yet to hold files.
    val futureRecurringWithoutEntry = initialDraft.id == null && recurring && date?.isAfter(today) == true
    val valid = amountCents != null && date != null && minuteOfDay != null && recurrenceValid &&
        !futureRecurrenceConversionBlocked &&
        (!futureRecurringWithoutEntry || pendingAttachments.isEmpty())

    fun save() {
        val cents = amountCents ?: return
        val selectedDate = date ?: return
        val selectedMinute = minuteOfDay ?: return
        if (!valid || isSaving) return
        onSave(
            initialDraft.copy(
                type = type,
                amountCents = cents,
                epochDay = selectedDate.toEpochDay(),
                minuteOfDay = selectedMinute,
                merchant = merchant,
                note = note,
                tags = parseTags(tags),
                recurrence = repeatUnit?.let { RecurrenceRule(unit = it, interval = intervalValue ?: 1, endEpochDay = endDate?.toEpochDay()) },
            ),
            pendingAttachments,
        )
    }

    LedgerEditorFrame(
        inPane = inPane,
        title = localizedText(if (initialDraft.id == null) "New entry" else "Edit entry"),
        onClose = onDismiss,
        onSave = ::save,
        saveEnabled = valid,
        working = isSaving,
        modifier = modifier,
        snackbar = snackbar,
        footer = initialDraft.id?.let { id ->
            {
                TextButton(onClick = { onDelete(id) }, enabled = !isSaving) {
                    Text(localizedText("Delete"), color = LifeTheme.colors.danger)
                }
            }
        },
    ) {
        failureMessage?.let { ErrorLine(localizedText(it)) }
        Segmented(
            listOf(LedgerType.EXPENSE, LedgerType.INCOME), type, { typeName = it.name },
            { localizedText(it.displayName()) },
            fill = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AmountField(amount, { amount = it }, amountCents, required = true)

        FieldLabel(localizedText("When"))
        AdaptivePair(
            firstWeight = 1.25f,
            first = {
                LifeTextField(
                    dateInput, { dateInput = it },
                    placeholder = localizedText("Date"),
                    isError = date == null,
                    trailing = { DatePickerButton(date ?: LocalDate.ofEpochDay(initialDraft.epochDay), { dateInput = formatting.dateInput(it.toEpochDay()) }) },
                    modifier = it,
                )
            },
            second = {
                LifeTextField(
                    timeInput, { timeInput = it },
                    placeholder = localizedText("Time"),
                    isError = minuteOfDay == null,
                    trailing = {
                        TimePickerButton(minuteOfDay ?: initialDraft.minuteOfDay, formatting.uses24HourTime, {
                            timeInput = formatLedgerTimeInput(it, uses24HourTime = formatting.uses24HourTime)
                        })
                    },
                    modifier = it,
                )
            },
        )
        when {
            date == null -> ErrorLine(localizedText("Enter a valid day, month/day, or month/day/year"))
            minuteOfDay == null -> ErrorLine(localizedText("Enter a valid time such as 9:30 AM or 21:30"))
            else -> Text(
                ledgerDayHeading(date.toEpochDay(), today, formatting, language) + " · " + formatting.time(minuteOfDay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        FieldLabel(localizedText("Merchant"))
        LifeTextField(merchant, { merchant = it }, placeholder = localizedText("Where or who"), modifier = Modifier.fillMaxWidth())

        FieldLabel(localizedText("Tags"))
        TagsField(tags, { tags = it }, knownTags)

        FieldLabel(localizedText("Note"))
        LifeTextField(note, { note = it }, placeholder = localizedText("Optional"), minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())

        FieldLabel(localizedText("Repeat"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(localizedText("Never"), !recurring, { repeatUnitName = "" }, exclusive = true, enabled = !scheduleLocked)
            RecurrenceUnit.entries.forEach { unit ->
                Pill(localizedText(ledgerRepeatLabel(unit)), repeatUnit == unit, { repeatUnitName = unit.name }, exclusive = true, enabled = !scheduleLocked)
            }
        }
        if (scheduleLocked) {
            QuietNote(localizedText("This occurrence is independent. Stop its schedule from the Recurring page."))
        }
        if (futureRecurrenceConversionBlocked) {
            ErrorLine(localizedText("An existing entry can only start repeating today or earlier. Create a new recurring entry for a future start."))
        }
        if (repeatUnit != null) {
            RepeatDetails(
                unit = repeatUnit,
                interval = interval,
                onInterval = { interval = it },
                endInput = endInput,
                onEndInput = { endInput = it },
                endDate = endDate,
                // The calendar opens a month after the entry, a likely first end date.
                endPickerStart = LocalDate.ofEpochDay(defaultLedgerRecurrenceEndEpochDay((date ?: LocalDate.ofEpochDay(initialDraft.epochDay)).toEpochDay())),
                endError = when {
                    endInvalid -> "Enter a valid day, month/day, or month/day/year"
                    endBeforeStart -> "End date cannot be before the entry date"
                    else -> null
                },
                formatting = formatting,
                enabled = !scheduleLocked,
            )
            if (!scheduleLocked) QuietNote(localizedText("Create independent entries on schedule"))
        }

        val attachmentCount = existingAttachments.size + pendingAttachments.size
        FieldLabel(localizedText("Attachments")) {
            TextButton(
                onClick = { attachmentLauncher.launch(ledgerAttachmentMimeTypes()) },
                enabled = !isSaving && !futureRecurringWithoutEntry && attachmentCount < 10,
            ) {
                Icon(Icons.Rounded.AttachFile, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(localizedText("Attach"))
            }
        }
        QuietNote(ledgerFileCount(attachmentCount, language))
        if (futureRecurringWithoutEntry) {
            QuietNote(localizedText("Receipts can be added after the first scheduled entry is generated."))
        }
        if (attachmentCount > 0) {
            Panel(Modifier.fillMaxWidth()) {
                existingAttachments.forEach { attachment ->
                    ListRow(
                        title = attachment.originalName,
                        maxTitleLines = 1,
                        onClick = if (isSaving) null else ({ onOpenAttachment(attachment) }),
                        onClickLabel = localizedText("Open"),
                        trailing = {
                            IconButton(onClick = { onRemoveAttachment(attachment) }, enabled = !isSaving) {
                                Icon(Icons.Rounded.Close, localizedText("Remove ${attachment.originalName}"), Modifier.size(18.dp))
                            }
                        },
                    )
                }
                pendingAttachments.forEach { uri ->
                    val name = uri.lastPathSegment ?: localizedText("Selected file")
                    ListRow(
                        title = name,
                        supporting = localizedText("Added when you save"),
                        maxTitleLines = 1,
                        trailing = {
                            IconButton(onClick = { attachmentUriStrings = attachmentUriStrings - uri.toString() }, enabled = !isSaving) {
                                Icon(Icons.Rounded.Close, localizedText("Remove $name"), Modifier.size(18.dp))
                            }
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.lg))
    }
}

@Composable
internal fun ErrorLine(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LifeTheme.colors.danger, modifier = modifier)
}

@Composable
internal fun QuietNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** The big amount field of an editor, with the limits spelled out while the amount is missing or wrong. */
@Composable
internal fun AmountField(value: String, onValueChange: (String) -> Unit, cents: Long?, required: Boolean) {
    LifeTextField(
        value, { candidate -> sanitizeAmountInput(candidate)?.let(onValueChange) },
        placeholder = "0.00",
        prefix = "$",
        textStyle = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
        isError = value.isNotBlank() && cents == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    if (cents == null) {
        val hint = localizedText(
            if (required) "Required · up to $999,999,999.99 · max 2 decimal places" else "Up to $999,999,999.99 · max 2 decimal places",
        )
        if (value.isBlank()) QuietNote(hint) else ErrorLine(hint)
    }
}

/** Tags typed with commas, with the ledger's existing tags offered as pills under the field. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagsField(tags: String, onTags: (String) -> Unit, knownTags: List<String>) {
    LifeTextField(tags, onTags, placeholder = localizedText("Comma separated, e.g. food, travel"), modifier = Modifier.fillMaxWidth())
    val typing = tags.substringAfterLast(',').trim().removePrefix("#")
    val chosen = parseTags(tags).map { it.lowercase() }.toSet()
    val suggestions = knownTags
        .filter { it.lowercase() !in chosen && (typing.isEmpty() || it.startsWith(typing, ignoreCase = true)) }
        .take(8)
    if (suggestions.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            suggestions.forEach { suggestion ->
                Pill("#$suggestion", false, {
                    val kept = tags.split(',').dropLast(1).map(String::trim).filter(String::isNotEmpty)
                    onTags((kept + suggestion).joinToString(", ") + ", ")
                })
            }
        }
    }
}

/** "Every [2] weeks" and the optional last day of a schedule. */
@Composable
internal fun RepeatDetails(
    unit: RecurrenceUnit,
    interval: String,
    onInterval: (String) -> Unit,
    endInput: String,
    onEndInput: (String) -> Unit,
    endDate: LocalDate?,
    endPickerStart: LocalDate,
    endError: String?,
    formatting: LedgerDisplayFormatting,
    enabled: Boolean,
) {
    val language = LocalUiLanguage.current
    val intervalError = recurrenceIntervalError(interval)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(localizedText("Every"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LifeTextField(
            interval, { candidate -> sanitizeRecurrenceIntervalInput(candidate)?.let(onInterval) },
            isError = intervalError != null,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(76.dp * LocalDensity.current.fontScale),
        )
        Text(
            ledgerRepeatUnitWord(unit, interval.toIntOrNull(), language),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    intervalError?.let { ErrorLine(localizedText(it)) }
    LifeTextField(
        endInput, onEndInput,
        placeholder = localizedText("Until (optional)"),
        isError = endError != null,
        enabled = enabled,
        trailing = if (enabled) {
            { DatePickerButton(endDate ?: endPickerStart, { onEndInput(formatting.dateInput(it.toEpochDay())) }) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
    endError?.let { ErrorLine(localizedText(it)) }
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
