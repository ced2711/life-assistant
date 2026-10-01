package com.ced2711.lifetracker.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import com.ced2711.lifetracker.domain.model.TodoDraft
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.diaryPreview
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.uiLocale
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.launch

private enum class CalendarView(val label: String) { MONTH("Month"), WEEK("Week"), AGENDA("Agenda") }

/** Everything that happens on one day, gathered once per render. */
private class DayContents(snapshot: BackupSnapshot) {
    val todos: Map<Long, List<TodoEntity>> = snapshot.todos.filter { it.deletedAt == null && it.deadlineEpochDay != null }
        .groupBy { it.deadlineEpochDay!! }
        .mapValues { (_, rows) -> rows.sortedWith(compareBy<TodoEntity> { it.completedAt != null }.thenBy { it.deadlineMinute ?: Int.MAX_VALUE }.thenByDescending { it.priority.ordinal }) }
    val ledger: Map<Long, List<LedgerEntryEntity>> = snapshot.ledgerEntries.filter { it.deletedAt == null }.groupBy { it.epochDay }
    val diary = snapshot.diaryEntries.associateBy { it.epochDay }

    fun net(day: Long): Long = ledger[day].orEmpty().sumOf { if (it.type == LedgerType.INCOME) it.amountCents else -it.amountCents }
}

@Composable
internal fun CalendarPage(
    snapshot: BackupSnapshot,
    store: DesktopDataStore,
    showDiary: Boolean,
    onOpenDiary: (Long) -> Unit,
) {
    val language = LocalUiLanguage.current
    val locale = uiLocale(language)
    var view by remember { mutableStateOf(CalendarView.MONTH) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var editingTodo by remember { mutableStateOf<Long?>(null) }
    var editingLedger by remember { mutableStateOf<LedgerEntryEntity?>(null) }
    var addingLedgerOn by remember { mutableStateOf<Long?>(null) }
    val contents = DayContents(snapshot)
    val weekDays = UserFormatting.orderedDaysOfWeek(snapshot.settings.weekStart, locale)
    val month = YearMonth.from(selected)
    val weekStart = selected.with(TemporalAdjusters.previousOrSame(weekDays.first()))

    // Like the phone's planning views, upcoming repeating todos are created for the shown days.
    val visibleEnd = when (view) {
        CalendarView.MONTH -> month.atEndOfMonth()
        CalendarView.WEEK -> weekStart.plusDays(6)
        CalendarView.AGENDA -> LocalDate.now().plusDays(30)
    }.toEpochDay()
    LaunchedEffect(visibleEnd) { runCatching { store.runMaintenance(planningThroughEpochDay = visibleEnd) } }

    fun move(days: Long) { selected = selected.plusDays(days) }
    fun page(forward: Boolean) {
        selected = when (view) {
            CalendarView.MONTH -> if (forward) selected.plusMonths(1) else selected.minusMonths(1)
            CalendarView.WEEK, CalendarView.AGENDA -> if (forward) selected.plusWeeks(1) else selected.minusWeeks(1)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sidePanel = maxWidth >= 980.dp
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> { move(-1); true }
                            Key.DirectionRight -> { move(1); true }
                            Key.DirectionUp -> { move(-7); true }
                            Key.DirectionDown -> { move(7); true }
                            Key.PageUp -> { page(false); true }
                            Key.PageDown -> { page(true); true }
                            Key.T -> { selected = LocalDate.now(); true }
                            else -> false
                        }
                    }
                    .focusable(),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { page(false) }) { Icon(Icons.Default.ChevronLeft, desktopText("Previous")) }
                    Text(
                        when (view) {
                            CalendarView.MONTH -> monthTitle(month, language)
                            CalendarView.WEEK -> UserFormatting.formatDate(weekStart, snapshot.settings.dateFormat, locale) + " – " +
                                UserFormatting.formatDate(weekStart.plusDays(6), snapshot.settings.dateFormat, locale)
                            CalendarView.AGENDA -> desktopText("Next 30 days")
                        },
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = { page(true) }) { Icon(Icons.Default.ChevronRight, desktopText("Next")) }
                    TextButton(onClick = { selected = LocalDate.now() }) { Text(desktopText("Today")) }
                    Spacer(Modifier.weight(1f))
                    CalendarView.entries.forEach { item ->
                        FilterChipSimple(desktopText(item.label), view == item) { view = item }
                        Spacer(Modifier.width(6.dp))
                    }
                }
                when (view) {
                    CalendarView.MONTH -> MonthGrid(snapshot, contents, month, weekDays, selected, showDiary, onSelect = { selected = it }, onOpenTodo = { editingTodo = it })
                    CalendarView.WEEK -> WeekColumns(snapshot, contents, weekStart, selected, onSelect = { selected = it }, onOpenTodo = { editingTodo = it })
                    CalendarView.AGENDA -> Agenda(snapshot, contents, onSelect = { selected = it }, onOpenTodo = { editingTodo = it }, onOpenLedger = { editingLedger = it })
                }
            }
            if (sidePanel) {
                VerticalDivider()
                DayPanel(
                    snapshot, store, contents, selected, showDiary,
                    onOpenTodo = { editingTodo = it },
                    onNewTodo = { editingTodo = -1L },
                    onOpenLedger = { editingLedger = it },
                    onNewLedger = { addingLedgerOn = selected.toEpochDay() },
                    onOpenDiary = onOpenDiary,
                    modifier = Modifier.width(380.dp).fillMaxHeight(),
                )
            }
        }
    }
    editingTodo?.let { id ->
        TodoEditorWindow(snapshot, store, id.takeIf { it > 0 }, initialDay = selected.toEpochDay()) { editingTodo = null }
    }
    editingLedger?.let { entry -> LedgerEditorWindow(snapshot, store, entry.id, entry.epochDay) { editingLedger = null } }
    addingLedgerOn?.let { day -> LedgerEditorWindow(snapshot, store, null, day) { addingLedgerOn = null } }
}

@Composable
private fun MonthGrid(
    snapshot: BackupSnapshot,
    contents: DayContents,
    month: YearMonth,
    weekDays: List<java.time.DayOfWeek>,
    selected: LocalDate,
    showDiary: Boolean,
    onSelect: (LocalDate) -> Unit,
    onOpenTodo: (Long) -> Unit,
) {
    val language = LocalUiLanguage.current
    val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(weekDays.first()))
    val weeks = ((month.atEndOfMonth().toEpochDay() - first.toEpochDay()) / 7 + 1).toInt()
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            weekDays.forEach { day ->
                Text(
                    UserFormatting.formatWeekday(day, uiLocale(language)), Modifier.weight(1f).padding(bottom = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        repeat(weeks) { week ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                repeat(7) { index ->
                    val date = first.plusDays((week * 7 + index).toLong())
                    val day = date.toEpochDay()
                    val inMonth = YearMonth.from(date) == month
                    val isSelected = date == selected
                    val todos = contents.todos[day].orEmpty()
                    val net = contents.net(day)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.secondaryContainer
                            inMonth -> MaterialTheme.colorScheme.surfaceContainerLow
                            else -> Color.Transparent
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp)
                            .then(if (date == today) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
                            .clickable { onSelect(date) },
                    ) {
                        Column(Modifier.padding(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    date.dayOfMonth.toString(),
                                    fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                    color = if (inMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                if (showDiary && day in contents.diary) {
                                    Spacer(Modifier.width(4.dp))
                                    Box(Modifier.size(6.dp).background(IncomeColor, CircleShape))
                                }
                                Spacer(Modifier.weight(1f))
                                if (net != 0L) Text(
                                    (if (net > 0) "+" else "−") + compactAmount(kotlin.math.abs(net)),
                                    style = MaterialTheme.typography.labelSmall, color = if (net > 0) IncomeColor else ExpenseColor, maxLines = 1,
                                )
                            }
                            // As many todo titles as fit, then "+N more".
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val fits = ((maxHeight.value - 2) / 19).toInt().coerceAtLeast(0)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val shown = if (todos.size > fits) todos.take((fits - 1).coerceAtLeast(0)) else todos
                                    shown.forEach { todo -> CellTodo(todo) { onOpenTodo(todo.id) } }
                                    if (todos.size > shown.size) Text(desktopMoreCount(todos.size - shown.size, language), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun CellTodo(todo: TodoEntity, onClick: () -> Unit) {
    val done = todo.completedAt != null
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (done) Color.Transparent else priorityTint(todo.priority),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Text(
            todo.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun priorityTint(priority: TodoPriority): Color = when (priority) {
    TodoPriority.URGENT -> MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
    TodoPriority.HIGH -> Color(0xFFFFB673).copy(alpha = 0.25f)
    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
}

private fun compactAmount(cents: Long): String = when {
    cents >= 100_000_00 -> "${cents / 100_000_00}M"
    cents >= 1_000_00 -> "%.1fk".format(cents / 100_000.0)
    else -> "%.0f".format(cents / 100.0)
}

@Composable
private fun WeekColumns(
    snapshot: BackupSnapshot,
    contents: DayContents,
    weekStart: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onOpenTodo: (Long) -> Unit,
) {
    val language = LocalUiLanguage.current
    val today = LocalDate.now()
    Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(7) { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val day = date.toEpochDay()
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (date == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.weight(1f).fillMaxHeight().clickable { onSelect(date) },
            ) {
                Column(Modifier.padding(8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, uiLocale(language)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal, color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    contents.net(day).takeIf { it != 0L }?.let { net ->
                        Text((if (net > 0) "+" else "−") + formatMoney(kotlin.math.abs(net)), style = MaterialTheme.typography.labelSmall, color = if (net > 0) IncomeColor else ExpenseColor)
                    }
                    contents.todos[day].orEmpty().forEach { todo ->
                        Column(Modifier.fillMaxWidth().clickable { onOpenTodo(todo.id) }) {
                            todo.deadlineMinute?.let { Text(UserFormatting.formatMinuteOfDay(it, snapshot.settings.timeFormat, false, uiLocale(language)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            CellTodo(todo) { onOpenTodo(todo.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Agenda(
    snapshot: BackupSnapshot,
    contents: DayContents,
    onSelect: (LocalDate) -> Unit,
    onOpenTodo: (Long) -> Unit,
    onOpenLedger: (LedgerEntryEntity) -> Unit,
) {
    val language = LocalUiLanguage.current
    val today = LocalDate.now().toEpochDay()
    val overdue = contents.todos.filterKeys { it < today }.values.flatten().filter { it.completedAt == null }
    val days = (today..today + 30).filter { contents.todos[it].orEmpty().isNotEmpty() || contents.ledger[it].orEmpty().isNotEmpty() }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (overdue.isNotEmpty()) {
            item { Text(desktopText("Overdue"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
            items(overdue, key = { "overdue-${it.id}" }) { todo -> AgendaTodo(todo, snapshot) { onOpenTodo(todo.id) } }
        }
        if (days.isEmpty() && overdue.isEmpty()) item { Text(desktopText("Nothing planned for the next 30 days."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp)) }
        days.forEach { day ->
            item(key = "day-$day") {
                Text(
                    formatDeadline(day, null, snapshot, language),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 14.dp).clickable { onSelect(LocalDate.ofEpochDay(day)) },
                )
            }
            items(contents.todos[day].orEmpty(), key = { "todo-${it.id}" }) { todo -> AgendaTodo(todo, snapshot) { onOpenTodo(todo.id) } }
            items(contents.ledger[day].orEmpty(), key = { "ledger-${it.id}" }) { entry ->
                Row(Modifier.fillMaxWidth().clickable { onOpenLedger(entry) }.padding(vertical = 6.dp, horizontal = 8.dp)) {
                    Text(entry.merchant.ifBlank { entry.note.ifBlank { desktopText(if (entry.type == LedgerType.INCOME) "Income" else "Expense") } }, modifier = Modifier.weight(1f))
                    Text((if (entry.type == LedgerType.INCOME) "+" else "−") + formatMoney(entry.amountCents), color = if (entry.type == LedgerType.INCOME) IncomeColor else ExpenseColor)
                }
            }
        }
    }
}

@Composable
private fun AgendaTodo(todo: TodoEntity, snapshot: BackupSnapshot, onClick: () -> Unit) {
    val language = LocalUiLanguage.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            todo.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            textDecoration = if (todo.completedAt != null) TextDecoration.LineThrough else null,
        )
        Text(formatDeadline(todo.deadlineEpochDay!!, todo.deadlineMinute, snapshot, language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Right column: the selected day's todos, money and diary page, with quick add. */
@Composable
private fun DayPanel(
    snapshot: BackupSnapshot,
    store: DesktopDataStore,
    contents: DayContents,
    date: LocalDate,
    showDiary: Boolean,
    onOpenTodo: (Long) -> Unit,
    onNewTodo: () -> Unit,
    onOpenLedger: (LedgerEntryEntity) -> Unit,
    onNewLedger: () -> Unit,
    onOpenDiary: (Long) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    val locale = uiLocale(language)
    val day = date.toEpochDay()
    var quickTodo by remember(day) { mutableStateOf("") }
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(date.dayOfWeek.getDisplayName(TextStyle.FULL, locale), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(UserFormatting.formatDate(date, snapshot.settings.dateFormat, locale), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(desktopText("Todos"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onNewTodo) { Icon(Icons.Default.Add, desktopText("New todo")) }
        }
        contents.todos[day].orEmpty().forEach { todo ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onOpenTodo(todo.id) }) {
                Checkbox(todo.completedAt != null, { checked -> scope.launch { store.setTodoCompleted(todo.id, checked) } })
                Column(Modifier.weight(1f)) {
                    Text(todo.title, maxLines = 2, overflow = TextOverflow.Ellipsis, textDecoration = if (todo.completedAt != null) TextDecoration.LineThrough else null)
                    todo.deadlineMinute?.let { Text(UserFormatting.formatMinuteOfDay(it, snapshot.settings.timeFormat, false, locale), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        OutlinedTextField(
            quickTodo, { quickTodo = it },
            placeholder = { Text(desktopText("Add a todo for this day")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().onEnter {
                val text = quickTodo.trim()
                if (text.isNotEmpty()) {
                    quickTodo = ""
                    scope.launch {
                        store.saveTodo(TodoDraft(description = text, deadlineEpochDay = day, reminderOffsetsMinutes = snapshot.settings.defaultReminderOffsetsMinutes.toList()), SeriesEditScope.ONLY_THIS_OCCURRENCE)
                    }
                }
            },
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(desktopText("Ledger"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            contents.net(day).takeIf { it != 0L }?.let { net ->
                Text((if (net > 0) "+" else "−") + formatMoney(kotlin.math.abs(net)), color = if (net > 0) IncomeColor else ExpenseColor, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onNewLedger) { Icon(Icons.Default.Add, desktopText("New entry")) }
        }
        val entries = contents.ledger[day].orEmpty()
        if (entries.isEmpty()) Text(desktopText("No ledger entries"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        entries.sortedBy { it.minuteOfDay }.forEach { entry ->
            Row(Modifier.fillMaxWidth().clickable { onOpenLedger(entry) }.padding(vertical = 6.dp)) {
                Text(entry.merchant.ifBlank { entry.note.ifBlank { desktopText(if (entry.type == LedgerType.INCOME) "Income" else "Expense") } }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text((if (entry.type == LedgerType.INCOME) "+" else "−") + formatMoney(entry.amountCents), color = if (entry.type == LedgerType.INCOME) IncomeColor else ExpenseColor)
            }
        }

        if (showDiary) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(desktopText("Diary"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                val page = contents.diary[day]
                TextButton(onClick = { onOpenDiary(day) }) { Text(desktopText(if (page != null) "Open diary" else "Write diary")) }
            }
            contents.diary[day]?.let { Text(diaryPreview(it.body, 240), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
