package com.ced2711.lifetracker.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import com.ced2711.lifetracker.domain.model.TodayOverview
import com.ced2711.lifetracker.domain.model.TodoDraft
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.ui.design.CheckCircle
import com.ced2711.lifetracker.ui.design.EmptyState
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.ListRow
import com.ced2711.lifetracker.ui.design.MoneyText
import com.ced2711.lifetracker.ui.design.PageTitle
import com.ced2711.lifetracker.ui.design.Panel
import com.ced2711.lifetracker.ui.design.ProgressLine
import com.ced2711.lifetracker.ui.design.SectionLabel
import com.ced2711.lifetracker.ui.design.Segmented
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.design.Stat
import com.ced2711.lifetracker.ui.design.priorityColor
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import kotlinx.coroutines.launch

private const val NEW_TODO = -1L

/**
 * The day at a glance: what is overdue and due today (tick them off here), what comes this week,
 * money today and this month with a quick way to note an expense, today's diary page and pinned
 * notes. Everything opens where it lives.
 */
@Composable
internal fun TodayPage(
    snapshot: BackupSnapshot,
    store: DesktopDataStore,
    showDiary: Boolean,
    showNotes: Boolean,
    showLedger: Boolean,
    onOpenDiary: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenLedger: () -> Unit,
) {
    val language = LocalUiLanguage.current
    val scope = rememberSafeCoroutineScope()
    val today = LocalDate.now()
    val overview = remember(snapshot, today) { TodayOverview.of(snapshot.todos, snapshot.ledgerEntries, today) }
    var editing by remember { mutableStateOf<Long?>(null) }
    var completing by remember { mutableStateOf<TodoEntity?>(null) }
    var showDone by remember { mutableStateOf(false) }

    fun toggle(todo: TodoEntity, done: Boolean) {
        val openSubtasks = snapshot.subtasks.count { it.todoId == todo.id && !it.isCompleted }
        if (done && openSubtasks > 0) completing = todo else scope.launch { store.setTodoCompleted(todo.id, done) }
    }
    RegisterPageShortcuts(onNew = { editing = NEW_TODO })

    @Composable
    fun todoRow(todo: TodoEntity, showDate: Boolean) {
        val subtasks = snapshot.subtasks.filter { it.todoId == todo.id }
        val overdue = todo.completedAt == null && (todo.deadlineEpochDay ?: Long.MAX_VALUE) < today.toEpochDay()
        val details = buildList {
            todo.deadlineEpochDay?.let { day ->
                if (showDate || overdue) add(formatDeadline(day, todo.deadlineMinute, snapshot, language))
                else todo.deadlineMinute?.let { add(UserFormatting.formatMinuteOfDay(it, snapshot.settings.timeFormat, false, uiLocale(language))) }
            }
            if (subtasks.isNotEmpty()) add("${subtasks.count { it.isCompleted }}/${subtasks.size}")
            todo.categoryId?.let { add(categoryPath(it, snapshot)) }
        }.joinToString(" · ")
        ListRow(
            title = todo.title,
            supporting = details.ifBlank { null },
            supportingColor = if (overdue) LifeTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
            struck = todo.completedAt != null,
            leading = {
                CheckCircle(
                    checked = todo.completedAt != null,
                    onCheckedChange = { toggle(todo, it) },
                    color = priorityColor(todo.priority) ?: MaterialTheme.colorScheme.primary,
                    contentDescription = todo.title,
                )
            },
            trailing = if (todo.seriesId != null) {
                { Icon(Icons.Rounded.Repeat, desktopText("Repeats"), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                null
            },
            onClick = { editing = todo.id },
        )
    }

    @Composable
    fun todoColumn() {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            QuickTodoField(onAdd = { text ->
                scope.launch {
                    store.saveTodo(
                        TodoDraft(
                            description = text,
                            deadlineEpochDay = today.toEpochDay(),
                            reminderOffsetsMinutes = snapshot.settings.defaultReminderOffsetsMinutes.toList(),
                        ),
                        SeriesEditScope.ONLY_THIS_OCCURRENCE,
                    )
                }
            })
            if (overview.overdue.isNotEmpty()) {
                Column {
                    SectionLabel(desktopText("Overdue"), count = overview.overdue.size, color = LifeTheme.colors.danger)
                    overview.overdue.forEach { todoRow(it, showDate = true) }
                }
            }
            Column {
                SectionLabel(desktopText("Today"), count = overview.dueToday.size)
                if (overview.dueToday.isEmpty()) {
                    Text(
                        desktopText(if (overview.completedToday.isEmpty()) "Nothing due today. Add something above, or enjoy the space." else "Everything due today is done."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                    )
                }
                overview.dueToday.forEach { todoRow(it, showDate = false) }
            }
            if (overview.upcoming.isNotEmpty()) {
                Column {
                    SectionLabel(desktopText("Next 7 days"), count = overview.upcoming.size)
                    overview.upcoming.take(8).forEach { todoRow(it, showDate = true) }
                    if (overview.upcoming.size > 8) {
                        Text(
                            desktopMoreCount(overview.upcoming.size - 8, language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs),
                        )
                    }
                }
            }
            if (overview.completedToday.isNotEmpty()) {
                Column {
                    SectionLabel(
                        desktopText("Done today"),
                        count = overview.completedToday.size,
                        trailing = {
                            TextButton(onClick = { showDone = !showDone }) { Text(desktopText(if (showDone) "Hide" else "Show")) }
                        },
                    )
                    if (showDone) overview.completedToday.forEach { todoRow(it, showDate = false) }
                }
            }
        }
    }

    @Composable
    fun sideColumn() {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            val dueTotal = overview.completedToday.size + overview.dueToday.size + overview.overdue.size
            Panel(padding = PaddingValues(Space.xl)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EventAvailable, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(desktopText("Progress"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Space.sm).weight(1f))
                    Text(
                        "${overview.completedToday.size} / $dueTotal",
                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Space.md))
                ProgressLine(overview.progress)
            }
            if (showLedger) MoneyPanel(snapshot, overview, store, onOpenLedger)
            if (showDiary) {
                val page = snapshot.diaryEntries.firstOrNull { it.epochDay == today.toEpochDay() }
                Panel(padding = PaddingValues(Space.xl)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoStories, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(desktopText("Diary"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Space.sm).weight(1f))
                        TextButton(onClick = { onOpenDiary(today.toEpochDay()) }) {
                            Text(desktopText(if (page == null) "Write" else "Open"))
                        }
                    }
                    Text(
                        page?.body?.trim()?.take(220) ?: desktopText("How was your day? A few lines are enough."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (page == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showNotes) {
                val pinned = snapshot.notes.filter { it.pinned }.sortedByDescending { it.updatedAt }.take(4)
                if (pinned.isNotEmpty()) {
                    Panel(padding = PaddingValues(vertical = Space.md, horizontal = Space.xs)) {
                        Row(Modifier.padding(horizontal = Space.lg, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PushPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(desktopText("Pinned notes"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Space.sm))
                        }
                        pinned.forEach { note ->
                            ListRow(
                                title = note.title,
                                supporting = note.body.replace('\n', ' ').take(90).ifBlank { null },
                                maxTitleLines = 1,
                                onClick = { onOpenNote(note.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= 1_000.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(Space.xxl),
        ) {
            PageTitle(title = desktopText("Today"), subtitle = todaySubtitle(today, overview, language))
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Column(Modifier.weight(1.45f)) { todoColumn() }
                    Column(Modifier.weight(1f).widthIn(max = 460.dp)) { sideColumn() }
                }
            } else {
                todoColumn()
                sideColumn()
            }
        }
    }

    editing?.let { id ->
        TodoEditorWindow(
            snapshot = snapshot,
            store = store,
            todoId = id.takeIf { it != NEW_TODO },
            initialDay = today.toEpochDay(),
            onClose = { editing = null },
        )
    }
    completing?.let { todo ->
        CompleteWithSubtasksDialog(
            onDismiss = { completing = null },
            onChoose = { withSubtasks ->
                completing = null
                scope.launch { store.setTodoCompleted(todo.id, true, completeSubtasks = withSubtasks) }
            },
        )
    }
}

/** "Add a todo for today" — type and press Enter. */
@Composable
private fun QuickTodoField(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    LifeTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = desktopText("Add a todo for today, then press Enter"),
        leadingIcon = Icons.Rounded.Add,
        modifier = Modifier.fillMaxWidth().onEnter {
            text.trim().takeIf(String::isNotEmpty)?.let(onAdd)
            text = ""
        },
    )
}

@Composable
private fun MoneyPanel(snapshot: BackupSnapshot, overview: TodayOverview, store: DesktopDataStore, onOpenLedger: () -> Unit) {
    val scope = rememberSafeCoroutineScope()
    var type by remember { mutableStateOf(LedgerType.EXPENSE) }
    var amount by remember { mutableStateOf("") }
    var what by remember { mutableStateOf("") }
    val cents = parseAmountCents(amount.trim())
    fun save() {
        val value = cents ?: return
        val now = LocalTime.now()
        val label = what.trim()
        amount = ""
        what = ""
        scope.launch {
            store.saveLedger(
                LedgerDraft(type = type, amountCents = value, epochDay = LocalDate.now().toEpochDay(), minuteOfDay = now.hour * 60 + now.minute, merchant = label),
                SeriesEditScope.ONLY_THIS_OCCURRENCE,
            )
        }
    }
    Panel(padding = PaddingValues(Space.xl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(desktopText("Money"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenLedger) { Text(desktopText("Open ledger")) }
        }
        Row(Modifier.fillMaxWidth().padding(top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
            Stat(desktopText("Spent today"), formatMoney(overview.moneyToday.expenseCents), Modifier.weight(1f), valueColor = if (overview.moneyToday.expenseCents > 0) LifeTheme.colors.expense else MaterialTheme.colorScheme.onSurface)
            Stat(
                desktopText("This month"),
                (if (overview.moneyThisMonth.netCents < 0) "−" else "+") + formatMoney(kotlin.math.abs(overview.moneyThisMonth.netCents)),
                Modifier.weight(1f),
                valueColor = if (overview.moneyThisMonth.netCents < 0) LifeTheme.colors.expense else LifeTheme.colors.income,
                caption = desktopText("net"),
            )
        }
        Spacer(Modifier.height(Space.lg))
        Segmented(
            options = listOf(LedgerType.EXPENSE, LedgerType.INCOME),
            selected = type,
            onSelect = { type = it },
            label = { desktopText(if (it == LedgerType.EXPENSE) "Expense" else "Income") },
            fill = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            LifeTextField(
                value = amount,
                onValueChange = { if (isValidDesktopAmountInput(it)) amount = it },
                placeholder = "0.00",
                prefix = "$",
                modifier = Modifier.width(120.dp).onEnter(::save),
            )
            LifeTextField(
                value = what,
                onValueChange = { what = it },
                placeholder = desktopText("What for? Enter saves"),
                modifier = Modifier.weight(1f).onEnter(::save),
            )
        }
        if (overview.entriesToday.isNotEmpty()) {
            Spacer(Modifier.height(Space.md))
            overview.entriesToday.take(4).forEach { entry -> TodayEntryRow(entry, snapshot) }
        }
    }
}

@Composable
private fun TodayEntryRow(entry: LedgerEntryEntity, snapshot: BackupSnapshot) {
    val language = LocalUiLanguage.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            UserFormatting.formatMinuteOfDay(entry.minuteOfDay, snapshot.settings.timeFormat, false, uiLocale(language)),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Text(
            entry.merchant.ifBlank { entry.note }.ifBlank { desktopText(if (entry.type == LedgerType.INCOME) "Income" else "Expense") },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MoneyText(entry.amountCents, entry.type, formatMoney(entry.amountCents), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun CompleteWithSubtasksDialog(onDismiss: () -> Unit, onChoose: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(desktopText("Complete this todo?")) },
        text = { Text(desktopText("It still has open subtasks.")) },
        dismissButton = { TextButton(onClick = { onChoose(false) }) { Text(desktopText("Only the todo")) } },
        confirmButton = { Button(onClick = { onChoose(true) }) { Text(desktopText("Todo and subtasks")) } },
    )
}

private fun todaySubtitle(today: LocalDate, overview: TodayOverview, language: UiLanguage): String {
    val locale = uiLocale(language)
    val date = when (language) {
        UiLanguage.SIMPLIFIED_CHINESE -> "${today.monthValue}月${today.dayOfMonth}日 " + today.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        UiLanguage.ENGLISH -> today.dayOfWeek.getDisplayName(TextStyle.FULL, locale) + ", " +
            today.month.getDisplayName(TextStyle.FULL, locale) + " " + today.dayOfMonth
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
