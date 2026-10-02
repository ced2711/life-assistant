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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.ced2711.lifetracker.domain.model.diaryPreview
import com.ced2711.lifetracker.ui.design.CheckCircle
import com.ced2711.lifetracker.ui.design.Dot
import com.ced2711.lifetracker.ui.design.EmptyState
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.ListRow
import com.ced2711.lifetracker.ui.design.MoneyText
import com.ced2711.lifetracker.ui.design.SectionLabel
import com.ced2711.lifetracker.ui.design.Segmented
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.design.priorityColor
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.launch

private enum class CalendarView(val label: String) { MONTH("Month"), WEEK("Week"), DAY("Day"), AGENDA("Agenda") }

/** Everything that happens on one day, gathered once per render. */
private class DayContents(snapshot: BackupSnapshot) {
    val todos: Map<Long, List<TodoEntity>> = snapshot.todos.filter { it.deletedAt == null && it.deadlineEpochDay != null }
        .groupBy { it.deadlineEpochDay!! }
        .mapValues { (_, rows) -> rows.sortedWith(compareBy<TodoEntity> { it.completedAt != null }.thenBy { it.deadlineMinute ?: Int.MAX_VALUE }.thenByDescending { it.priority.ordinal }) }
    val ledger: Map<Long, List<LedgerEntryEntity>> = snapshot.ledgerEntries.filter { it.deletedAt == null }.groupBy { it.epochDay }
    val diary = snapshot.diaryEntries.associateBy { it.epochDay }

    fun net(day: Long): Long = ledger[day].orEmpty().sumOf { if (it.type == LedgerType.INCOME) it.amountCents else -it.amountCents }
}

/**
 * The month (or week, day, or the coming 30 days) with each day's todos and money, and the
 * selected day beside it to tick todos off, add things and open the diary. Arrow keys move the
 * selected day, Page Up/Down the page, T jumps to today.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    RegisterPageShortcuts(onNew = { editingTodo = -1L })

    // Like the phone's planning views, upcoming repeating todos are created for the shown days.
    val visibleEnd = when (view) {
        CalendarView.MONTH -> month.atEndOfMonth().plusDays(7)
        CalendarView.WEEK -> weekStart.plusDays(6)
        CalendarView.DAY -> selected
        CalendarView.AGENDA -> LocalDate.now().plusDays(30)
    }.toEpochDay()
    LaunchedEffect(visibleEnd) { runCatching { store.runMaintenance(planningThroughEpochDay = visibleEnd) } }

    fun move(days: Long) { selected = selected.plusDays(days) }
    fun page(forward: Boolean) {
        val step = if (forward) 1L else -1L
        selected = when (view) {
            CalendarView.MONTH -> selected.plusMonths(step)
            CalendarView.WEEK, CalendarView.AGENDA -> selected.plusWeeks(step)
            CalendarView.DAY -> selected.plusDays(step)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sidePanel = maxWidth >= 980.dp && view != CalendarView.DAY
        // Without room for the day beside the grid, choosing a day opens it as the Day view.
        val select: (LocalDate) -> Unit = { date ->
            selected = date
            if (maxWidth < 980.dp && view != CalendarView.DAY) view = CalendarView.DAY
        }
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .focusRequester(focus)
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
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
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth().padding(start = PagePadding, end = PagePadding, top = 28.dp, bottom = Space.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (view) {
                            CalendarView.MONTH -> monthTitle(month, language)
                            CalendarView.WEEK -> UserFormatting.formatDate(weekStart, snapshot.settings.dateFormat, locale) + " – " +
                                UserFormatting.formatDate(weekStart.plusDays(6), snapshot.settings.dateFormat, locale)
                            CalendarView.DAY -> formatDeadline(selected.toEpochDay(), null, snapshot, language)
                            CalendarView.AGENDA -> desktopText("Next 30 days")
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (view != CalendarView.AGENDA) {
                        Spacer(Modifier.width(Space.sm))
                        IconButton(onClick = { page(false) }) { Icon(Icons.Rounded.ChevronLeft, desktopText("Previous")) }
                        IconButton(onClick = { page(true) }) { Icon(Icons.Rounded.ChevronRight, desktopText("Next")) }
                        if (selected != LocalDate.now()) TextButton(onClick = { selected = LocalDate.now() }) { Text(desktopText("Today")) }
                    }
                  }
                    Segmented(CalendarView.entries, view, { view = it }, { desktopText(it.label) })
                }
                when (view) {
                    CalendarView.MONTH -> MonthGrid(contents, month, weekDays, selected, showDiary, onSelect = select, onOpenTodo = { editingTodo = it })
                    CalendarView.WEEK -> WeekColumns(snapshot, contents, weekStart, selected, onSelect = select, onOpenTodo = { editingTodo = it })
                    CalendarView.DAY -> DayPanel(
                        snapshot, store, contents, selected, showDiary,
                        onOpenTodo = { editingTodo = it },
                        onNewTodo = { editingTodo = -1L },
                        onOpenLedger = { editingLedger = it },
                        onNewLedger = { addingLedgerOn = selected.toEpochDay() },
                        onOpenDiary = onOpenDiary,
                        showDate = false,
                        modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
                    )
                    CalendarView.AGENDA -> Agenda(snapshot, contents, onOpenTodo = { editingTodo = it }, onOpenLedger = { editingLedger = it })
                }
            }
            if (sidePanel) {
                ColumnDivider()
                DayPanel(
                    snapshot, store, contents, selected, showDiary,
                    onOpenTodo = { editingTodo = it },
                    onNewTodo = { editingTodo = -1L },
                    onOpenLedger = { editingLedger = it },
                    onNewLedger = { addingLedgerOn = selected.toEpochDay() },
                    onOpenDiary = onOpenDiary,
                    showDate = true,
                    modifier = Modifier.width(360.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow),
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
    val line = LifeTheme.colors.divider
    Column(Modifier.fillMaxSize().padding(start = PagePadding, end = PagePadding, bottom = PagePadding)) {
        Row(Modifier.fillMaxWidth().padding(bottom = Space.sm)) {
            weekDays.forEach { day ->
                Text(
                    UserFormatting.formatWeekday(day, uiLocale(language)),
                    Modifier.weight(1f).padding(start = Space.sm),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).border(1.dp, line, RoundedCornerShape(16.dp))) {
            repeat(weeks) { week ->
                if (week > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(line))
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    repeat(7) { index ->
                        if (index > 0) Box(Modifier.fillMaxHeight().width(1.dp).background(line))
                        val date = first.plusDays((week * 7 + index).toLong())
                        val day = date.toEpochDay()
                        val inMonth = YearMonth.from(date) == month
                        val isSelected = date == selected
                        val todos = contents.todos[day].orEmpty()
                        val net = contents.net(day)
                        val open = todos.count { it.completedAt == null }
                        val summary = buildString {
                            append(date.toString())
                            if (todos.isNotEmpty()) append(", ${todos.size - open} done, $open open")
                            if (net != 0L) append(", net ${signedMoney(net)}")
                            if (showDiary && day in contents.diary) append(", diary")
                        }
                        Column(
                            Modifier.weight(1f).fillMaxHeight()
                                .background(if (isSelected) LifeTheme.colors.accentSoft else Color.Transparent)
                                .clickable { onSelect(date) }
                                .semantics { contentDescription = summary; this.selected = isSelected }
                                .padding(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(24.dp)) {
                                Box(
                                    Modifier.size(24.dp).clip(CircleShape).background(if (date == today) MaterialTheme.colorScheme.primary else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = if (date == today) FontWeight.Bold else FontWeight.Medium,
                                        color = when {
                                            date == today -> MaterialTheme.colorScheme.onPrimary
                                            inMonth -> MaterialTheme.colorScheme.onSurface
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        },
                                    )
                                }
                                if (showDiary && day in contents.diary) Dot(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.padding(start = 5.dp), size = 5.dp)
                                Spacer(Modifier.weight(1f))
                                if (net != 0L) Text(
                                    (if (net > 0) "+" else "−") + compactAmount(kotlin.math.abs(net)),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                    color = (if (net > 0) LifeTheme.colors.income else LifeTheme.colors.expense).copy(alpha = if (inMonth) 1f else 0.5f),
                                    maxLines = 1,
                                )
                            }
                            // As many todo titles as fit, then "+N more".
                            BoxWithConstraints(Modifier.fillMaxSize().padding(top = 3.dp)) {
                                val fits = ((maxHeight.value + 2) / 21).toInt().coerceAtLeast(0)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val shown = if (todos.size > fits) todos.take((fits - 1).coerceAtLeast(0)) else todos
                                    shown.forEach { todo -> CellTodo(todo, dimmed = !inMonth) { onOpenTodo(todo.id) } }
                                    if (todos.size > shown.size) Text(desktopMoreCount(todos.size - shown.size, language), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A todo inside a day cell: a bar in its priority colour and the title. */
@Composable
private fun CellTodo(todo: TodoEntity, dimmed: Boolean = false, onClick: () -> Unit) {
    val done = todo.completedAt != null
    val color = priorityColor(todo.priority) ?: MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().height(19.dp).clip(RoundedCornerShape(5.dp))
            .background(if (done) Color.Transparent else color.copy(alpha = if (dimmed) 0.08f else 0.16f))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (done) Color.Transparent else color.copy(alpha = if (dimmed) 0.5f else 1f)))
        Text(
            todo.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            color = if (done || dimmed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 5.dp),
        )
    }
}

private fun compactAmount(cents: Long): String = when {
    cents >= 100_000_000 -> "%.1fM".format(cents / 100_000_000.0)
    cents >= 100_000 -> "%.1fk".format(cents / 100_000.0)
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
    val line = LifeTheme.colors.divider
    Row(Modifier.fillMaxSize().padding(start = PagePadding, end = PagePadding, bottom = PagePadding).clip(RoundedCornerShape(16.dp)).border(1.dp, line, RoundedCornerShape(16.dp))) {
        repeat(7) { offset ->
            if (offset > 0) Box(Modifier.fillMaxHeight().width(1.dp).background(line))
            val date = weekStart.plusDays(offset.toLong())
            val day = date.toEpochDay()
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .background(if (date == selected) LifeTheme.colors.accentSoft else Color.Transparent)
                    .clickable { onSelect(date) }
                    .padding(Space.sm)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, uiLocale(language)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                contents.net(day).takeIf { it != 0L }?.let { net ->
                    Text(signedMoney(net), style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = if (net > 0) LifeTheme.colors.income else LifeTheme.colors.expense)
                }
                Spacer(Modifier.height(2.dp))
                contents.todos[day].orEmpty().forEach { todo ->
                    Column {
                        todo.deadlineMinute?.let {
                            Text(UserFormatting.formatMinuteOfDay(it, snapshot.settings.timeFormat, false, uiLocale(language)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        CellTodo(todo) { onOpenTodo(todo.id) }
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
    onOpenTodo: (Long) -> Unit,
    onOpenLedger: (LedgerEntryEntity) -> Unit,
) {
    val language = LocalUiLanguage.current
    val today = LocalDate.now().toEpochDay()
    val overdue = contents.todos.filterKeys { it < today }.values.flatten().filter { it.completedAt == null }.sortedBy { it.deadlineEpochDay }
    val days = (today..today + 30).filter { contents.todos[it].orEmpty().isNotEmpty() || contents.ledger[it].orEmpty().isNotEmpty() }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = PagePadding - Space.md).widthIn(max = 820.dp)) {
        if (overdue.isNotEmpty()) {
            item { SectionLabel(desktopText("Overdue"), count = overdue.size, color = LifeTheme.colors.danger, modifier = Modifier.padding(start = Space.md)) }
            items(overdue, key = { "overdue-${it.id}" }) { todo -> AgendaTodo(todo, snapshot, showDate = true) { onOpenTodo(todo.id) } }
        }
        if (days.isEmpty() && overdue.isEmpty()) item {
            EmptyState(title = desktopText("Nothing planned for the next 30 days"), icon = Icons.Rounded.EventAvailable, body = desktopText("Todos with a date and ledger entries show up here."))
        }
        days.forEach { day ->
            item(key = "day-$day") {
                SectionLabel(formatDeadline(day, null, snapshot, language), modifier = Modifier.padding(start = Space.md, top = Space.md)) {
                    contents.net(day).takeIf { it != 0L }?.let { net ->
                        Text(signedMoney(net), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = Space.md))
                    }
                }
            }
            items(contents.todos[day].orEmpty(), key = { "todo-${it.id}" }) { todo -> AgendaTodo(todo, snapshot, showDate = false) { onOpenTodo(todo.id) } }
            items(contents.ledger[day].orEmpty().sortedBy { it.minuteOfDay }, key = { "ledger-${it.id}" }) { entry ->
                ListRow(
                    title = entry.merchant.ifBlank { entry.note.ifBlank { desktopText(if (entry.type == LedgerType.INCOME) "Income" else "Expense") } },
                    leading = { Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Dot(if (entry.type == LedgerType.INCOME) LifeTheme.colors.income else LifeTheme.colors.expense) } },
                    trailing = { MoneyText(entry.amountCents, entry.type, formatMoney(entry.amountCents), style = MaterialTheme.typography.bodyMedium) },
                    maxTitleLines = 1,
                    onClick = { onOpenLedger(entry) },
                )
            }
        }
        item { Spacer(Modifier.height(48.dp)) }
    }
}

@Composable
private fun AgendaTodo(todo: TodoEntity, snapshot: BackupSnapshot, showDate: Boolean, onClick: () -> Unit) {
    val language = LocalUiLanguage.current
    val overdue = todo.completedAt == null && todo.deadlineEpochDay!! < LocalDate.now().toEpochDay()
    ListRow(
        title = todo.title,
        struck = todo.completedAt != null,
        supporting = when {
            showDate -> formatDeadline(todo.deadlineEpochDay!!, todo.deadlineMinute, snapshot, language)
            else -> todo.deadlineMinute?.let { UserFormatting.formatMinuteOfDay(it, snapshot.settings.timeFormat, false, uiLocale(language)) }
        },
        supportingColor = if (overdue) LifeTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
        leading = { CheckCircle(todo.completedAt != null, null, color = priorityColor(todo.priority) ?: MaterialTheme.colorScheme.primary) },
        maxTitleLines = 1,
        onClick = onClick,
    )
}

/** The selected day: todos to tick off, money and the diary page, with quick add. */
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
    showDate: Boolean,
    modifier: Modifier,
) {
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    val locale = uiLocale(language)
    val day = date.toEpochDay()
    var quickTodo by remember(day) { mutableStateOf("") }
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = if (showDate) Space.lg else PagePadding - Space.md, vertical = if (showDate) 28.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (showDate) {
            Column(Modifier.padding(horizontal = Space.md).padding(bottom = Space.md)) {
                Text(date.dayOfWeek.getDisplayName(TextStyle.FULL, locale), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(UserFormatting.formatDate(date, snapshot.settings.dateFormat, locale), style = MaterialTheme.typography.headlineSmall)
            }
        }
        val todos = contents.todos[day].orEmpty()
        SectionLabel(desktopText("Todos"), count = todos.size.takeIf { it > 0 }, modifier = Modifier.padding(start = Space.md)) {
            IconButton(onClick = onNewTodo, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Add, desktopText("New todo"), Modifier.size(18.dp)) }
        }
        todos.forEach { todo ->
            ListRow(
                title = todo.title,
                struck = todo.completedAt != null,
                supporting = todo.deadlineMinute?.let { UserFormatting.formatMinuteOfDay(it, snapshot.settings.timeFormat, false, locale) },
                leading = {
                    CheckCircle(
                        checked = todo.completedAt != null,
                        onCheckedChange = { checked -> scope.launch { store.setTodoCompleted(todo.id, checked) } },
                        color = priorityColor(todo.priority) ?: MaterialTheme.colorScheme.primary,
                        contentDescription = todo.title,
                    )
                },
                onClick = { onOpenTodo(todo.id) },
            )
        }
        LifeTextField(
            quickTodo, { quickTodo = it },
            placeholder = desktopText("Add a todo for this day"),
            leadingIcon = Icons.Rounded.Add,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = Space.xs).onEnter {
                val text = quickTodo.trim()
                if (text.isNotEmpty()) {
                    quickTodo = ""
                    scope.launch {
                        store.saveTodo(TodoDraft(description = text, deadlineEpochDay = day, reminderOffsetsMinutes = snapshot.settings.defaultReminderOffsetsMinutes.toList()), SeriesEditScope.ONLY_THIS_OCCURRENCE)
                    }
                }
            },
        )

        Spacer(Modifier.height(Space.md))
        val entries = contents.ledger[day].orEmpty().sortedBy { it.minuteOfDay }
        SectionLabel(desktopText("Ledger"), modifier = Modifier.padding(start = Space.md)) {
            contents.net(day).takeIf { it != 0L }?.let { net ->
                Text(signedMoney(net), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = if (net > 0) LifeTheme.colors.income else LifeTheme.colors.expense)
            }
            IconButton(onClick = onNewLedger, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Add, desktopText("New entry"), Modifier.size(18.dp)) }
        }
        if (entries.isEmpty()) {
            Text(desktopText("No ledger entries"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs))
        }
        entries.forEach { entry ->
            ListRow(
                title = entry.merchant.ifBlank { entry.note.ifBlank { desktopText(if (entry.type == LedgerType.INCOME) "Income" else "Expense") } },
                supporting = UserFormatting.formatMinuteOfDay(entry.minuteOfDay, snapshot.settings.timeFormat, false, locale),
                trailing = { MoneyText(entry.amountCents, entry.type, formatMoney(entry.amountCents), style = MaterialTheme.typography.bodyMedium) },
                maxTitleLines = 1,
                onClick = { onOpenLedger(entry) },
            )
        }

        if (showDiary) {
            Spacer(Modifier.height(Space.md))
            val page = contents.diary[day]
            SectionLabel(desktopText("Diary"), modifier = Modifier.padding(start = Space.md)) {
                TextButton(onClick = { onOpenDiary(day) }) { Text(desktopText(if (page != null) "Open diary" else "Write diary")) }
            }
            Text(
                page?.let { diaryPreview(it.body, 240) } ?: desktopText("No diary entry"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.md),
            )
        }
        Spacer(Modifier.height(48.dp))
    }
}
