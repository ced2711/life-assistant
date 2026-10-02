package com.ced2711.lifetracker.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.local.DiaryEntryEntity
import com.ced2711.lifetracker.data.local.NoteEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.TodayOverview
import com.ced2711.lifetracker.domain.model.TodoDraft
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.ui.design.CheckCircle
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.ListRow
import com.ced2711.lifetracker.ui.design.PageTitle
import com.ced2711.lifetracker.ui.design.Panel
import com.ced2711.lifetracker.ui.design.ProgressLine
import com.ced2711.lifetracker.ui.design.SectionLabel
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.design.Stat
import com.ced2711.lifetracker.ui.design.priorityColor
import com.ced2711.lifetracker.ui.TaskLedgerViewModel
import com.ced2711.lifetracker.ui.ledger.formatMoney
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.theme.LifeTheme
import com.ced2711.lifetracker.ui.todo.CompleteWithSubtasksDialog
import java.time.LocalDate
import java.time.format.TextStyle

/** The phone's home: today's todos to tick off, money today and this month, diary and pinned notes. */
@Composable
fun TodayScreen(
    viewModel: TaskLedgerViewModel,
    modifier: Modifier = Modifier,
    showLedger: Boolean,
    showDiary: Boolean,
    showNotes: Boolean,
    onOpenTodo: (Long) -> Unit,
    onOpenLedger: () -> Unit,
    onOpenDiary: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val active by viewModel.activeTodos.collectAsState()
    val completed by viewModel.completedTodos.collectAsState()
    val ledger by viewModel.ledgerEntries.collectAsState()
    val diary by viewModel.diaryEntries.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val subtasksByTodo by viewModel.subtasksByTodo.collectAsState()
    val today = LocalDate.now()
    val overview = remember(active, completed, ledger, today) { TodayOverview.of(active + completed, ledger, today) }
    TodayContent(
        overview = overview,
        settings = settings,
        diaryToday = diary.firstOrNull { it.epochDay == today.toEpochDay() },
        pinnedNotes = notes.filter { it.pinned }.sortedByDescending { it.updatedAt }.take(3),
        showLedger = showLedger,
        showDiary = showDiary,
        showNotes = showNotes,
        modifier = modifier,
        onToggle = { todo, done, withSubtasks ->
            if (done) viewModel.completeTodo(todo.id, withSubtasks) else viewModel.restoreTodo(todo.id)
        },
        subtasksByTodo = subtasksByTodo,
        onAdd = { text ->
            viewModel.addQuickTodo(
                TodoDraft(
                    description = text,
                    deadlineEpochDay = today.toEpochDay(),
                    reminderOffsetsMinutes = settings.defaultReminderOffsetsMinutes.toList(),
                ),
            )
        },
        onOpenTodo = onOpenTodo,
        onOpenLedger = onOpenLedger,
        onOpenDiary = onOpenDiary,
        onOpenNote = onOpenNote,
    )
}

@Composable
internal fun TodayContent(
    overview: TodayOverview,
    settings: AppSettings,
    diaryToday: DiaryEntryEntity?,
    pinnedNotes: List<NoteEntity>,
    showLedger: Boolean,
    showDiary: Boolean,
    showNotes: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (TodoEntity, Boolean, Boolean) -> Unit,
    subtasksByTodo: Map<Long, List<com.ced2711.lifetracker.data.local.SubtaskEntity>>,
    onAdd: (String) -> Unit,
    onOpenTodo: (Long) -> Unit,
    onOpenLedger: () -> Unit,
    onOpenDiary: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val language = LocalUiLanguage.current
    var quick by rememberSaveable { mutableStateOf("") }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var completing by remember { mutableStateOf<TodoEntity?>(null) }

    @Composable
    fun todoRow(todo: TodoEntity, showDate: Boolean) {
        val subtasks = subtasksByTodo[todo.id].orEmpty()
        val overdue = todo.completedAt == null && (todo.deadlineEpochDay ?: Long.MAX_VALUE) < overview.date.toEpochDay()
        val details = buildList {
            todo.deadlineEpochDay?.let { day ->
                if (showDate || overdue) add(dayLabel(day, todo.deadlineMinute, settings, language))
                else todo.deadlineMinute?.let { add(UserFormatting.formatMinuteOfDay(it, settings.timeFormat, false, uiLocale(language))) }
            }
            if (subtasks.isNotEmpty()) add("${subtasks.count { it.isCompleted }}/${subtasks.size}")
        }.joinToString(" · ")
        ListRow(
            title = todo.title,
            supporting = details.ifBlank { null },
            supportingColor = if (overdue) LifeTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
            struck = todo.completedAt != null,
            leading = {
                CheckCircle(
                    checked = todo.completedAt != null,
                    onCheckedChange = { done ->
                        if (done && subtasks.any { !it.isCompleted }) completing = todo else onToggle(todo, done, false)
                    },
                    color = priorityColor(todo.priority) ?: MaterialTheme.colorScheme.primary,
                    contentDescription = todo.title,
                )
            },
            trailing = if (todo.seriesId != null) {
                { Icon(Icons.Rounded.Repeat, localizedText("Repeating"), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                null
            },
            onClick = { onOpenTodo(todo.id) },
        )
    }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = Space.xs, end = Space.xs, top = Space.xs, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        item {
            Column(Modifier.padding(horizontal = Space.md), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                // The top bar names the page; this line says what kind of day it is.
                Text(subtitle(overview, language), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val total = overview.completedToday.size + overview.dueToday.size + overview.overdue.size
                if (total > 0) ProgressLine(overview.progress)
                LifeTextField(
                    value = quick,
                    onValueChange = { quick = it },
                    placeholder = localizedText("Add a todo for today"),
                    leadingIcon = Icons.Rounded.Add,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        quick.trim().takeIf(String::isNotEmpty)?.let(onAdd)
                        quick = ""
                    }),
                )
            }
        }
        if (overview.overdue.isNotEmpty()) {
            item { SectionLabel(localizedText("Overdue"), Modifier.padding(horizontal = Space.md), count = overview.overdue.size, color = LifeTheme.colors.danger) }
            overview.overdue.forEach { todo -> item(key = "o${todo.id}") { todoRow(todo, showDate = true) } }
        }
        item { SectionLabel(localizedText("Today"), Modifier.padding(horizontal = Space.md), count = overview.dueToday.size) }
        if (overview.dueToday.isEmpty()) {
            item {
                Text(
                    localizedText(if (overview.completedToday.isEmpty()) "Nothing due today." else "Everything due today is done."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.md + Space.md, vertical = Space.sm),
                )
            }
        }
        overview.dueToday.forEach { todo -> item(key = "t${todo.id}") { todoRow(todo, showDate = false) } }
        if (overview.upcoming.isNotEmpty()) {
            item { SectionLabel(localizedText("Next 7 days"), Modifier.padding(horizontal = Space.md), count = overview.upcoming.size) }
            overview.upcoming.take(6).forEach { todo -> item(key = "u${todo.id}") { todoRow(todo, showDate = true) } }
        }
        if (overview.completedToday.isNotEmpty()) {
            item {
                SectionLabel(
                    localizedText("Done today"),
                    Modifier.padding(start = Space.md),
                    count = overview.completedToday.size,
                    trailing = { TextButton(onClick = { showDone = !showDone }) { Text(localizedText(if (showDone) "Hide" else "Show")) } },
                )
            }
            if (showDone) overview.completedToday.forEach { todo -> item(key = "d${todo.id}") { todoRow(todo, showDate = false) } }
        }
        if (showLedger) {
            item {
                Spacer(Modifier.height(Space.sm))
                Panel(Modifier.padding(horizontal = Space.md), padding = PaddingValues(Space.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(localizedText("Money"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = onOpenLedger) { Text(localizedText("Add entry")) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                        Stat(
                            localizedText("Spent today"),
                            formatMoney(overview.moneyToday.expenseCents),
                            Modifier.weight(1f),
                            valueColor = if (overview.moneyToday.expenseCents > 0) LifeTheme.colors.expense else MaterialTheme.colorScheme.onSurface,
                        )
                        val net = overview.moneyThisMonth.netCents
                        Stat(
                            localizedText("This month"),
                            (if (net < 0) "−" else "+") + formatMoney(kotlin.math.abs(net)),
                            Modifier.weight(1f),
                            valueColor = if (net < 0) LifeTheme.colors.expense else LifeTheme.colors.income,
                            caption = localizedText("net"),
                        )
                    }
                }
            }
        }
        if (showDiary) {
            item {
                Panel(Modifier.padding(horizontal = Space.md), padding = PaddingValues(Space.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoStories, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(localizedText("Diary"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Space.sm).weight(1f))
                        TextButton(onClick = { onOpenDiary(overview.date.toEpochDay()) }) { Text(localizedText(if (diaryToday == null) "Write" else "Open")) }
                    }
                    Text(
                        diaryToday?.body?.trim()?.take(200) ?: localizedText("How was your day? A few lines are enough."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (diaryToday == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (showNotes && pinnedNotes.isNotEmpty()) {
            item {
                Panel(Modifier.padding(horizontal = Space.md), padding = PaddingValues(vertical = Space.sm)) {
                    Row(Modifier.padding(horizontal = Space.lg, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PushPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(localizedText("Pinned notes"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Space.sm))
                    }
                    pinnedNotes.forEach { note ->
                        ListRow(title = note.title, supporting = note.body.replace('\n', ' ').take(80).ifBlank { null }, maxTitleLines = 1, onClick = { onOpenNote(note.id) })
                    }
                }
            }
        }
    }

    completing?.let { todo ->
        CompleteWithSubtasksDialog(
            onDismiss = { completing = null },
            onTaskOnly = { completing = null; onToggle(todo, true, false) },
            onWithSubtasks = { completing = null; onToggle(todo, true, true) },
        )
    }
}

/**
 * The day at a glance, without anything to tap: for the spare half of a folded screen. The date,
 * how the day stands, the next few things to do and, when the Ledger is shown, what was spent.
 */
@Composable
fun TodayGlance(viewModel: TaskLedgerViewModel, showLedger: Boolean, modifier: Modifier = Modifier) {
    val active by viewModel.activeTodos.collectAsState()
    val completed by viewModel.completedTodos.collectAsState()
    val ledger by viewModel.ledgerEntries.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val today = LocalDate.now()
    val overview = remember(active, completed, ledger, today) { TodayOverview.of(active + completed, ledger, today) }
    TodayGlanceContent(overview, settings, showLedger, modifier)
}

@Composable
internal fun TodayGlanceContent(overview: TodayOverview, settings: AppSettings, showLedger: Boolean, modifier: Modifier = Modifier) {
    val language = LocalUiLanguage.current
    val locale = uiLocale(language)
    val next = (overview.overdue + overview.dueToday).take(3)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(subtitle(overview, language), style = MaterialTheme.typography.titleMedium)
        if (overview.completedToday.size + overview.dueToday.size + overview.overdue.size > 0) ProgressLine(overview.progress)
        next.forEach { todo ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.ced2711.lifetracker.ui.design.Dot(priorityColor(todo.priority) ?: MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    todo.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Space.sm).weight(1f),
                )
                todo.deadlineMinute?.takeIf { todo.deadlineEpochDay == overview.date.toEpochDay() }?.let {
                    Text(
                        UserFormatting.formatMinuteOfDay(it, settings.timeFormat, false, locale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (showLedger && overview.moneyToday.expenseCents > 0) {
            Text(
                localizedText("Spent today") + "  " + formatMoney(overview.moneyToday.expenseCents),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun dayLabel(day: Long, minute: Int?, settings: AppSettings, language: UiLanguage): String {
    val locale = uiLocale(language)
    val date = LocalDate.ofEpochDay(day)
    val today = LocalDate.now()
    val text = when (date) {
        today -> translate("Today", language)
        today.plusDays(1) -> translate("Tomorrow", language)
        today.minusDays(1) -> translate("Yesterday", language)
        else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) + " " + UserFormatting.formatDate(date, settings.dateFormat, locale)
    }
    return text + (minute?.let { " " + UserFormatting.formatMinuteOfDay(it, settings.timeFormat, false, locale) } ?: "")
}

private fun translate(text: String, language: UiLanguage) = com.ced2711.lifetracker.ui.localization.translateUiText(text, language)

private fun subtitle(overview: TodayOverview, language: UiLanguage): String {
    val locale = uiLocale(language)
    val today = overview.date
    val date = when (language) {
        UiLanguage.SIMPLIFIED_CHINESE -> "${today.monthValue}月${today.dayOfMonth}日 " + today.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        UiLanguage.ENGLISH -> today.dayOfWeek.getDisplayName(TextStyle.FULL, locale) + ", " + today.month.getDisplayName(TextStyle.FULL, locale) + " " + today.dayOfMonth
    }
    val open = overview.dueToday.size
    val late = overview.overdue.size
    val status = when (language) {
        UiLanguage.SIMPLIFIED_CHINESE -> when {
            open == 0 && late == 0 && overview.completedToday.isNotEmpty() -> "今天的事都做完了"
            open == 0 && late == 0 -> "今天没有到期的待办"
            late == 0 -> "今天还有 $open 件待办"
            else -> "今天 $open 件 · 逾期 $late 件"
        }
        UiLanguage.ENGLISH -> when {
            open == 0 && late == 0 && overview.completedToday.isNotEmpty() -> "All done for today"
            open == 0 && late == 0 -> "Nothing due today"
            late == 0 -> "$open due today"
            else -> "$open due today · $late overdue"
        }
    }
    return "$date · $status"
}
