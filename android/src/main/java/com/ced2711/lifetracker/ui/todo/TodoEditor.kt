package com.ced2711.lifetracker.ui.todo

import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.domain.date.SmartDateParser
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import com.ced2711.lifetracker.domain.model.TodoDraft
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.acceptTodoTagSuggestion
import com.ced2711.lifetracker.domain.model.todoTagSuggestions
import com.ced2711.lifetracker.ui.components.CustomReminderOffsetInput
import com.ced2711.lifetracker.ui.components.DatePickerButton
import com.ced2711.lifetracker.ui.components.EditorPane
import com.ced2711.lifetracker.ui.components.EditorSheet
import com.ced2711.lifetracker.ui.components.FieldLabel
import com.ced2711.lifetracker.ui.components.TimePickerButton
import com.ced2711.lifetracker.ui.components.formatReminderOffset
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.ListRow
import com.ced2711.lifetracker.ui.design.Pill
import com.ced2711.lifetracker.ui.design.Segmented
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.translateUiText
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

internal const val MAX_TODO_ATTACHMENTS = 10

private val stringListSaver = listSaver<List<String>, String>(save = { it }, restore = { it.toList() })
private val longListSaver = listSaver<List<Long>, Long>(save = { it }, restore = { it.toList() })

private data class ReminderPreset(val label: String, val offset: Long)

private val reminderPresets = listOf(
    ReminderPreset("At due time", 0),
    ReminderPreset("1 hour before", 60),
    ReminderPreset("1 day before", 1_440),
    ReminderPreset("3 days before", 4_320),
    ReminderPreset("1 week before", 10_080),
)

/**
 * The editor of one todo. On phones it is a full-height sheet; with [inline] it fills the right
 * pane of a wide screen. It keeps what is typed across rotation and folding, and only [onSave]
 * changes data.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodoEditor(
    initialDraft: TodoDraft,
    inline: Boolean,
    categories: List<CategoryEntity>,
    availableTags: List<String>,
    settings: AppSettings,
    existingAttachments: List<AttachmentEntity>,
    editingSeriesOccurrence: Boolean,
    isSaving: Boolean,
    recordSavedAwaitingAttachments: Boolean,
    todoCompleted: Boolean?,
    snackbarHostState: SnackbarHostState,
    onCompletionChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (TodoDraft, SeriesEditScope, List<Uri>) -> Unit,
    onDelete: (() -> Unit)?,
    onOpenAttachment: (AttachmentEntity) -> Unit,
    onRemoveAttachment: (AttachmentEntity) -> Unit,
) {
    val context = LocalContext.current
    val language = LocalUiLanguage.current
    val locale = uiLocale(language)
    val scope = rememberCoroutineScope()
    val use24Hour = UserFormatting.uses24HourClock(settings.timeFormat, DateFormat.is24HourFormat(context))
    fun formatDay(epochDay: Long) = UserFormatting.formatDate(LocalDate.ofEpochDay(epochDay), settings.dateFormat, locale)
    fun parseDay(text: String): LocalDate? = SmartDateParser.parse(text, LocalDate.now(), settings.dateFormat, locale)

    val key = initialDraft.id
    var title by rememberSaveable(key) { mutableStateOf(initialDraft.title.takeIf { it != initialDraft.description }.orEmpty()) }
    var description by rememberSaveable(key) { mutableStateOf(initialDraft.description) }
    var categoryId by rememberSaveable(key) { mutableStateOf(initialDraft.categoryId) }
    var deadlineText by rememberSaveable(key) { mutableStateOf(initialDraft.deadlineEpochDay?.let(::formatDay).orEmpty()) }
    var hasTime by rememberSaveable(key) { mutableStateOf(initialDraft.deadlineMinute != null) }
    var timeText by rememberSaveable(key) { mutableStateOf(initialDraft.deadlineMinute?.let { formatEditorTime(it, use24Hour) }.orEmpty()) }
    var priority by rememberSaveable(key) { mutableStateOf(initialDraft.priority) }
    var tagsText by rememberSaveable(key) { mutableStateOf(initialDraft.tags.joinToString(", ")) }
    var reminderOffsets by rememberSaveable(key, stateSaver = longListSaver) { mutableStateOf(initialDraft.reminderOffsetsMinutes.distinct()) }
    var appliedDefaultReminders by rememberSaveable(key) { mutableStateOf(initialDraft.id != null || initialDraft.deadlineEpochDay != null) }
    var recurrenceEnabled by rememberSaveable(key) { mutableStateOf(initialDraft.recurrence != null) }
    var recurrenceUnit by rememberSaveable(key) { mutableStateOf(initialDraft.recurrence?.unit ?: RecurrenceUnit.WEEK) }
    var recurrenceInterval by rememberSaveable(key) { mutableStateOf((initialDraft.recurrence?.interval ?: 1).toString()) }
    var recurrenceEndText by rememberSaveable(key) { mutableStateOf(initialDraft.recurrence?.endEpochDay?.let(::formatDay).orEmpty()) }
    var subtasksText by rememberSaveable(key) { mutableStateOf(initialDraft.subtasks.joinToString("\n")) }
    var editScope by rememberSaveable(key) { mutableStateOf(SeriesEditScope.ONLY_THIS_OCCURRENCE) }
    var pendingUriStrings by rememberSaveable(key, stateSaver = stringListSaver) { mutableStateOf(emptyList()) }
    var errorField by rememberSaveable(key) { mutableStateOf<String?>(null) }
    val tagSuggestions = remember(tagsText, availableTags) {
        val entered = tagsText.split(',').map { it.trim().lowercase() }.toSet()
        todoTagSuggestions(tagsText, availableTags).filterNot { it.lowercase() in entered }
    }
    val defaultReminderOffsets = settings.defaultReminderOffsetsMinutes

    fun fail(field: String, message: String) {
        errorField = field
        scope.launch { snackbarHostState.showSnackbar(translateUiText(message, language)) }
    }

    fun updateDeadline(value: String) {
        val deadlineWasAdded = deadlineText.isBlank() && value.isNotBlank()
        deadlineText = value
        if (errorField == "deadline") errorField = null
        val dependents = clearTodoDeadlineDependentsIfBlank(
            deadlineText = value,
            current = TodoDeadlineDependentState(hasTime, timeText, reminderOffsets, appliedDefaultReminders, recurrenceEnabled, recurrenceUnit, recurrenceInterval, recurrenceEndText),
        )
        hasTime = dependents.hasTime
        timeText = dependents.timeText
        reminderOffsets = dependents.reminderOffsets
        appliedDefaultReminders = dependents.appliedDefaultReminders
        recurrenceEnabled = dependents.recurrenceEnabled
        recurrenceUnit = dependents.recurrenceUnit
        recurrenceInterval = dependents.recurrenceInterval
        recurrenceEndText = dependents.recurrenceEndText
        if (initialDraft.id == null && deadlineWasAdded && !appliedDefaultReminders && defaultReminderOffsets.isNotEmpty()) {
            reminderOffsets = defaultReminderOffsets.sorted()
            appliedDefaultReminders = true
        }
    }

    fun selectScope(scopeChoice: SeriesEditScope) {
        editScope = scopeChoice
        if (scopeChoice != SeriesEditScope.ONLY_THIS_OCCURRENCE || deadlineText.isBlank()) return
        // The repeat rule belongs to the rest of the series: going back to one occurrence puts the
        // rule fields back, and leaves the other edits alone.
        recurrenceEnabled = initialDraft.recurrence != null
        recurrenceUnit = initialDraft.recurrence?.unit ?: RecurrenceUnit.WEEK
        recurrenceInterval = (initialDraft.recurrence?.interval ?: 1).toString()
        recurrenceEndText = initialDraft.recurrence?.endEpochDay?.let(::formatDay).orEmpty()
    }

    LaunchedEffect(initialDraft.id, deadlineText, appliedDefaultReminders, defaultReminderOffsets) {
        if (initialDraft.id == null && deadlineText.isNotBlank() && !appliedDefaultReminders && defaultReminderOffsets.isNotEmpty()) {
            reminderOffsets = defaultReminderOffsets.sorted()
            appliedDefaultReminders = true
        }
    }

    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val remaining = (MAX_TODO_ATTACHMENTS - existingAttachments.size - pendingUriStrings.size).coerceAtLeast(0)
        pendingUriStrings = (pendingUriStrings + uris.take(remaining).map(Uri::toString)).distinct()
    }

    fun buildDraft(): TodoDraft? {
        if (description.isBlank()) {
            fail("description", "Description is required.")
            return null
        }
        val deadline = if (deadlineText.isBlank()) null else parseDay(deadlineText)?.toEpochDay()
        if (deadlineText.isNotBlank() && deadline == null) {
            fail("deadline", "Enter a valid deadline")
            return null
        }
        val minute = if (hasTime) parseMinuteOfDay(timeText) else null
        if (hasTime && minute == null) {
            fail("time", "Enter a valid time such as 9:30 AM or 21:30.")
            return null
        }
        if (hasTime && deadline == null) {
            fail("deadline", "A time requires a deadline date.")
            return null
        }
        if (recurrenceEnabled && deadline == null) {
            fail("deadline", "Repeating tasks require a deadline.")
            return null
        }
        val interval = recurrenceInterval.toIntOrNull()
        if (recurrenceEnabled && (interval == null || interval < 1)) {
            fail("interval", "Repeat interval must be at least 1.")
            return null
        }
        val recurrenceEnd = if (recurrenceEndText.isBlank()) null else parseDay(recurrenceEndText)?.toEpochDay()
        if (recurrenceEnabled && recurrenceEndText.isNotBlank() && recurrenceEnd == null) {
            fail("end", "Enter a valid repeat end date.")
            return null
        }
        val validateRule = shouldValidateTodoRecurrenceRule(recurrenceEnabled, editingSeriesOccurrence, editScope)
        if (validateRule && recurrenceEnd != null && deadline != null && recurrenceEnd < deadline) {
            fail("end", "Repeat end date cannot be before the deadline.")
            return null
        }
        errorField = null
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
            recurrence = if (recurrenceEnabled) RecurrenceRule(recurrenceUnit, requireNotNull(interval), recurrenceEnd) else null,
        )
    }

    fun save() {
        buildDraft()?.let { draft -> onSave(draft, editScope, pendingUriStrings.map { it.toUri() }) }
    }

    val fileCount = existingAttachments.size + pendingUriStrings.size
    val pendingFiles: @Composable ColumnScope.() -> Unit = {
        pendingUriStrings.forEachIndexed { index, uriString ->
            val name = uriString.toUri().lastPathSegment ?: localizedText("Selected file")
            ListRow(
                title = name,
                supporting = localizedText("Copied when you save"),
                maxTitleLines = 1,
                trailing = {
                    IconButton(enabled = !isSaving, onClick = { pendingUriStrings = pendingUriStrings.filterIndexed { i, _ -> i != index } }) {
                        Icon(Icons.Rounded.Close, removeLabel(name, language), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        }
        TextButton(onClick = { attachmentLauncher.launch(arrayOf("*/*")) }, enabled = !isSaving && fileCount < MAX_TODO_ATTACHMENTS) {
            Icon(Icons.Rounded.AttachFile, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Space.sm))
            Text(addFilesLabel(fileCount, language))
        }
    }

    if (recordSavedAwaitingAttachments) {
        // The todo itself is saved; only its files are left. Details stay locked so that later
        // edits cannot be skipped silently.
        EditorFrame(
            inline = inline,
            title = localizedText("Task saved"),
            onClose = onDismiss,
            actionLabel = localizedText(if (pendingUriStrings.isEmpty()) "Finish" else "Retry files"),
            onAction = ::save,
            working = isSaving,
            snackbarHostState = snackbarHostState,
            footer = null,
        ) {
            Text(
                localizedText(
                    if (isSaving) {
                        "The task details are saved. Files are being copied now."
                    } else {
                        "The task details are already saved, but one or more files were not copied. " +
                            "Details are locked so later edits cannot be silently skipped. Remove any " +
                            "unavailable file, then retry."
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pendingUriStrings.isEmpty()) Text(localizedText("No files remain to copy."), style = MaterialTheme.typography.bodyMedium)
            pendingFiles()
        }
        return
    }

    val ruleEditable = canEditTodoRecurrenceRule(editingSeriesOccurrence, editScope)
    val hasDeadline = deadlineText.isNotBlank()
    EditorFrame(
        inline = inline,
        title = localizedText(if (initialDraft.id == null) "New task" else "Edit task"),
        onClose = onDismiss,
        actionLabel = localizedText("Save"),
        onAction = ::save,
        working = isSaving,
        snackbarHostState = snackbarHostState,
        footer = if (onDelete != null || todoCompleted != null) {
            {
                if (onDelete != null) {
                    TextButton(onClick = onDelete, enabled = !isSaving) {
                        Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp), tint = LifeTheme.colors.danger)
                        Spacer(Modifier.width(Space.xs))
                        Text(localizedText("Delete"), color = LifeTheme.colors.danger)
                    }
                }
                Spacer(Modifier.weight(1f))
                todoCompleted?.let { completed ->
                    TextButton(onClick = { onCompletionChange(!completed) }, enabled = !isSaving) {
                        Text(localizedText(if (completed) "Mark as not done" else "Mark as done"))
                    }
                }
            }
        } else {
            null
        },
    ) {
        if (editingSeriesOccurrence) {
            FieldLabel(localizedText("Apply changes to"))
            Segmented(
                options = SeriesEditScope.entries,
                selected = editScope,
                onSelect = ::selectScope,
                label = { localizedText(if (it == SeriesEditScope.ONLY_THIS_OCCURRENCE) "Only this one" else "This and future") },
                fill = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val descriptionLabel = localizedText("Description")
        LifeTextField(
            value = description,
            onValueChange = { description = it; if (errorField == "description") errorField = null },
            placeholder = localizedText("What needs to be done?"),
            minLines = 2,
            maxLines = 5,
            isError = errorField == "description",
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = descriptionLabel },
        )

        FieldLabel(localizedText("Due"))
        LifeTextField(
            value = deadlineText,
            onValueChange = ::updateDeadline,
            placeholder = dueExample(settings, locale, language),
            isError = errorField == "deadline",
            modifier = Modifier.fillMaxWidth(),
            trailing = { DatePickerButton(parseDay(deadlineText), onPicked = { picked -> updateDeadline(formatDay(picked.toEpochDay())) }) },
        )
        parseDay(deadlineText)?.let { parsed ->
            if (formatDay(parsed.toEpochDay()) != deadlineText.trim()) {
                Text(
                    parsed.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) + " " + formatDay(parsed.toEpochDay()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            val today = LocalDate.now()
            listOf("Today" to today, "Tomorrow" to today.plusDays(1), "Next week" to today.plusWeeks(1)).forEach { (label, date) ->
                Pill(localizedText(label), parseDay(deadlineText) == date, { updateDeadline(formatDay(date.toEpochDay())) }, exclusive = true)
            }
            if (hasDeadline) {
                Pill(
                    localizedText("Time"),
                    hasTime,
                    {
                        hasTime = !hasTime
                        if (hasTime && timeText.isBlank()) timeText = formatEditorTime(9 * 60, use24Hour)
                    },
                )
                Pill(localizedText("No date"), false, { updateDeadline("") }, exclusive = true)
            }
        }
        if (hasTime) {
            LifeTextField(
                value = timeText,
                onValueChange = { timeText = it; if (errorField == "time") errorField = null },
                placeholder = localizedText("9:30 AM or 21:30"),
                isError = errorField == "time",
                modifier = Modifier.fillMaxWidth(),
                trailing = { TimePickerButton(parseMinuteOfDay(timeText), use24Hour, { picked -> timeText = formatEditorTime(picked, use24Hour) }) },
            )
        }

        FieldLabel(localizedText("Priority"))
        PriorityPills(priority, { priority = it ?: priority }, allowAny = false)

        FieldLabel(localizedText("Category"))
        val paths = remember(categories) { categoryPaths(categories) }
        ChoiceField(categoryId?.let(paths::get) ?: localizedText("Uncategorized"), Modifier.fillMaxWidth()) { close ->
            DropdownMenuItem(text = { Text(localizedText("Uncategorized")) }, onClick = { categoryId = null; close() })
            categories.sortedBy { paths[it.id] }.forEach { category ->
                DropdownMenuItem(text = { Text(paths[category.id] ?: category.name) }, onClick = { categoryId = category.id; close() })
            }
        }

        FieldLabel(localizedText("Tags"))
        LifeTextField(tagsText, { tagsText = it }, Modifier.fillMaxWidth(), placeholder = localizedText("work, errands"))
        if (tagSuggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                tagSuggestions.forEach { tag -> Pill("#$tag", false, { tagsText = acceptTodoTagSuggestion(tagsText, tag) }) }
            }
        }

        FieldLabel(localizedText("Subtasks"))
        LifeTextField(subtasksText, { subtasksText = it }, Modifier.fillMaxWidth(), placeholder = localizedText("One subtask per line"), minLines = 2, maxLines = 8)

        FieldLabel(localizedText("Reminders"))
        if (!hasDeadline) {
            Hint("Set a due date to add reminders or repeat the task.")
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                reminderPresets.forEach { preset ->
                    Pill(localizedText(preset.label), preset.offset in reminderOffsets, {
                        appliedDefaultReminders = true
                        reminderOffsets = if (preset.offset in reminderOffsets) reminderOffsets - preset.offset else reminderOffsets + preset.offset
                    })
                }
                reminderOffsets.filterNot { offset -> reminderPresets.any { it.offset == offset } }.sorted().forEach { offset ->
                    Pill(localizedText(formatReminderOffset(offset)), true, {
                        appliedDefaultReminders = true
                        reminderOffsets = reminderOffsets - offset
                    })
                }
            }
            CustomReminderOffsetInput(existingOffsets = reminderOffsets, onAdd = { offset ->
                appliedDefaultReminders = true
                reminderOffsets = (reminderOffsets + offset).distinct().sorted()
            })
        }

        FieldLabel(localizedText("Repeat"))
        if (!ruleEditable) {
            Hint("Choose This and future to change how the task repeats.")
        }
        val canRepeat = canEnableTodoRecurrence(deadlineText, editingSeriesOccurrence, editScope)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Pill(localizedText("Never"), !recurrenceEnabled, { recurrenceEnabled = false }, exclusive = true, enabled = canRepeat)
            RecurrenceUnit.entries.forEach { unit ->
                Pill(
                    localizedText(unit.everyLabel()),
                    recurrenceEnabled && recurrenceUnit == unit,
                    { recurrenceEnabled = true; recurrenceUnit = unit },
                    exclusive = true,
                    enabled = canRepeat,
                )
            }
        }
        if (recurrenceEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                LabelledField(localizedText("Every"), Modifier.weight(1f)) {
                    LifeTextField(
                        value = recurrenceInterval,
                        onValueChange = { recurrenceInterval = it.filter(Char::isDigit).take(3) },
                        enabled = ruleEditable,
                        isError = errorField == "interval",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailing = {
                            Text(
                                localizedText(recurrenceUnit.unitWord(recurrenceInterval.toIntOrNull() ?: 1)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
                LabelledField(localizedText("Until (optional)"), Modifier.weight(1.4f)) {
                    LifeTextField(
                        value = recurrenceEndText,
                        onValueChange = { recurrenceEndText = it; if (errorField == "end") errorField = null },
                        enabled = ruleEditable,
                        isError = errorField == "end",
                        placeholder = localizedText("No end"),
                        modifier = Modifier.fillMaxWidth(),
                        trailing = {
                            if (ruleEditable) DatePickerButton(parseDay(recurrenceEndText), onPicked = { picked -> recurrenceEndText = formatDay(picked.toEpochDay()) })
                        },
                    )
                }
            }
        }

        FieldLabel(localizedText("Title (optional)"))
        LifeTextField(title, { title = it }, Modifier.fillMaxWidth(), placeholder = localizedText("Taken from the description when empty"))

        FieldLabel(localizedText("Attachments"))
        existingAttachments.forEach { attachment ->
            ListRow(
                title = attachment.originalName,
                maxTitleLines = 1,
                leading = { Icon(Icons.Rounded.AttachFile, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailing = {
                    IconButton(enabled = !isSaving, onClick = { onRemoveAttachment(attachment) }) {
                        Icon(Icons.Rounded.Close, removeLabel(attachment.originalName, language), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                onClick = { if (!isSaving) onOpenAttachment(attachment) },
            )
        }
        pendingFiles()
        Hint("Up to 10 files per task, 25 MB each, and 128 MB total. New files are copied after the task is saved.")
        Spacer(Modifier.size(Space.lg))
    }
}

@Composable
private fun EditorFrame(
    inline: Boolean,
    title: String,
    onClose: () -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
    working: Boolean,
    snackbarHostState: SnackbarHostState,
    footer: (@Composable RowScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (inline) {
        EditorPane(title, onClose, actionLabel, onAction, Modifier.fillMaxSize(), working = working, snackbarHostState = snackbarHostState, footer = footer, content = content)
    } else {
        EditorSheet(title, onClose, actionLabel, onAction, working = working, snackbarHostState = snackbarHostState, footer = footer, content = content)
    }
}

@Composable
private fun Hint(text: String) {
    Text(localizedText(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LabelledField(label: String, modifier: Modifier, field: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        field()
    }
}

private fun RecurrenceUnit.everyLabel(): String = when (this) {
    RecurrenceUnit.DAY -> "Daily"
    RecurrenceUnit.WEEK -> "Weekly"
    RecurrenceUnit.MONTH -> "Monthly"
    RecurrenceUnit.YEAR -> "Yearly"
}

private fun RecurrenceUnit.unitWord(interval: Int): String {
    val singular = name.lowercase().replaceFirstChar(Char::uppercase)
    return if (interval == 1) singular else "${singular}s"
}

private fun addFilesLabel(count: Int, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> "添加文件（$count/$MAX_TODO_ATTACHMENTS）"
    UiLanguage.ENGLISH -> "Add files ($count/$MAX_TODO_ATTACHMENTS)"
}

private fun removeLabel(name: String, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> "移除 $name"
    UiLanguage.ENGLISH -> "Remove $name"
}

/** A placeholder that shows what can be typed: a word, or a date in the user's own format. */
private fun dueExample(settings: AppSettings, locale: Locale, language: UiLanguage): String {
    val example = UserFormatting.formatDate(LocalDate.now().plusDays(3), settings.dateFormat, locale)
    return when (language) {
        UiLanguage.SIMPLIFIED_CHINESE -> "明天、周五 或 $example"
        UiLanguage.ENGLISH -> "tomorrow, fri or $example"
    }
}

private fun formatEditorTime(minute: Int, use24Hour: Boolean): String =
    LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", Locale.US))

/** Reads "9:30", "21:30", "9pm", "9:30 PM", "下午3点" style input as minutes after midnight. */
internal fun parseMinuteOfDay(input: String): Int? {
    val value = input.trim().lowercase(Locale.ROOT).replace(" ", "").replace("：", ":")
    if (value.isEmpty()) return null
    val match = Regex("^(上午|下午)?(\\d{1,2})(?:[:点](\\d{2}))?点?(am|pm)?$").matchEntire(value) ?: return null
    var hour = match.groupValues[2].toInt()
    val minute = match.groupValues[3].ifEmpty { "0" }.toInt()
    when (match.groupValues[1].ifEmpty { match.groupValues[4] }) {
        "am", "上午" -> if (hour == 12) hour = 0
        "pm", "下午" -> if (hour < 12) hour += 12
    }
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}
