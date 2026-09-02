package com.ced2711.lifetracker.ui.calendar

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.ui.TaskLedgerViewModel
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

internal enum class CalendarView(val label: String) {
    MONTH("Month"),
    WEEK("Week"),
    DAY("Day"),
    AGENDA("Agenda"),
}

internal fun usesWideCalendarMasterDetail(view: CalendarView): Boolean = view != CalendarView.DAY

internal fun calendarVisibleEndEpochDay(
    selectedDate: LocalDate,
    view: CalendarView,
    firstDayOfWeek: DayOfWeek,
): Long = when (view) {
    CalendarView.MONTH -> YearMonth.from(selectedDate)
        .atDay(1)
        .previousOrSame(firstDayOfWeek)
        .plusDays(41)
        .toEpochDay()
    CalendarView.WEEK -> selectedDate
        .previousOrSame(firstDayOfWeek)
        .plusDays(6)
        .toEpochDay()
    CalendarView.DAY -> selectedDate.toEpochDay()
    CalendarView.AGENDA -> YearMonth.from(selectedDate).atEndOfMonth().toEpochDay()
}

/** Calendar destination shared by compact, landscape, tablet, and foldable layouts. */
@Composable
fun CalendarScreen(
    viewModel: TaskLedgerViewModel,
    onOpenTodo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
) {
    val activeTodos by viewModel.activeTodos.collectAsStateWithLifecycle()
    val completedTodos by viewModel.completedTodos.collectAsStateWithLifecycle()
    val entries by viewModel.ledgerEntries.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    CalendarContent(
        todos = (activeTodos + completedTodos).filter { it.deadlineEpochDay != null },
        entries = entries,
        settings = settings,
        isWide = isWide,
        onVisibleEndEpochDayChanged = viewModel::materializeCalendarTodoOccurrencesThrough,
        onOpenTodo = onOpenTodo,
        modifier = modifier,
    )
}

@Composable
private fun CalendarContent(
    todos: List<TodoEntity>,
    entries: List<LedgerEntryEntity>,
    settings: AppSettings,
    isWide: Boolean,
    onVisibleEndEpochDayChanged: (Long) -> Unit,
    onOpenTodo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var viewName by rememberSaveable { mutableStateOf(CalendarView.MONTH.name) }
    val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)
    val view = CalendarView.entries.firstOrNull { it.name == viewName } ?: CalendarView.MONTH
    val locale = Locale.getDefault()
    val systemUses24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val firstDayOfWeek = UserFormatting.firstDayOfWeek(settings.weekStart, locale)
    val use24HourTime = UserFormatting.uses24HourClock(settings.timeFormat, systemUses24Hour)
    val visibleEndEpochDay = remember(selectedDate, view, firstDayOfWeek) {
        calendarVisibleEndEpochDay(selectedDate, view, firstDayOfWeek)
    }
    LaunchedEffect(visibleEndEpochDay) {
        onVisibleEndEpochDayChanged(visibleEndEpochDay)
    }
    val dateFormatter = remember(settings.dateFormat, locale) {
        UserFormatting.dateFormatter(settings.dateFormat, locale)
    }
    val todosByDay = remember(todos) {
        todos.mapNotNull { todo -> todo.deadlineEpochDay?.let { it to todo } }.groupByPair()
    }
    val entriesByDay = remember(entries) { entries.map { it.epochDay to it }.groupByPair() }

    val move: (Int) -> Unit = { direction ->
        val next = when (view) {
            CalendarView.MONTH, CalendarView.AGENDA -> selectedDate.moveMonth(direction.toLong())
            CalendarView.WEEK -> selectedDate.plusWeeks(direction.toLong())
            CalendarView.DAY -> selectedDate.plusDays(direction.toLong())
        }
        selectedEpochDay = next.toEpochDay()
    }

    if (isWide && !usesWideCalendarMasterDetail(view)) {
        Box(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CalendarHeader(
                    selectedDate = selectedDate,
                    view = view,
                    firstDayOfWeek = firstDayOfWeek,
                    dateFormatter = dateFormatter,
                    locale = locale,
                    onPrevious = { move(-1) },
                    onNext = { move(1) },
                    onToday = { selectedEpochDay = today.toEpochDay() },
                    onViewChanged = { viewName = it.name },
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    DayDetails(
                        date = selectedDate,
                        todos = todosByDay[selectedEpochDay].orEmpty(),
                        entries = entriesByDay[selectedEpochDay].orEmpty(),
                        dateFormatter = dateFormatter,
                        use24HourTime = use24HourTime,
                        onOpenTodo = onOpenTodo,
                        contentPadding = PaddingValues(20.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    } else if (isWide) {
        Row(
            modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1.35f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CalendarHeader(
                    selectedDate = selectedDate,
                    view = view,
                    firstDayOfWeek = firstDayOfWeek,
                    dateFormatter = dateFormatter,
                    locale = locale,
                    onPrevious = { move(-1) },
                    onNext = { move(1) },
                    onToday = { selectedEpochDay = today.toEpochDay() },
                    onViewChanged = { viewName = it.name },
                )
                CalendarViewBody(
                    view = view,
                    selectedDate = selectedDate,
                    firstDayOfWeek = firstDayOfWeek,
                    todosByDay = todosByDay,
                    entriesByDay = entriesByDay,
                    dateFormatter = dateFormatter,
                    use24HourTime = use24HourTime,
                    detailed = false,
                    onDateSelected = { selectedEpochDay = it.toEpochDay() },
                    onOpenTodo = onOpenTodo,
                )
                Spacer(Modifier.height(4.dp))
            }

            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                DayDetails(
                    date = selectedDate,
                    todos = todosByDay[selectedEpochDay].orEmpty(),
                    entries = entriesByDay[selectedEpochDay].orEmpty(),
                    dateFormatter = dateFormatter,
                    use24HourTime = use24HourTime,
                    onOpenTodo = onOpenTodo,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    contentPadding = PaddingValues(20.dp),
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CalendarHeader(
                selectedDate = selectedDate,
                view = view,
                firstDayOfWeek = firstDayOfWeek,
                dateFormatter = dateFormatter,
                locale = locale,
                onPrevious = { move(-1) },
                onNext = { move(1) },
                onToday = { selectedEpochDay = today.toEpochDay() },
                onViewChanged = { viewName = it.name },
            )
            CalendarViewBody(
                view = view,
                selectedDate = selectedDate,
                firstDayOfWeek = firstDayOfWeek,
                todosByDay = todosByDay,
                entriesByDay = entriesByDay,
                dateFormatter = dateFormatter,
                use24HourTime = use24HourTime,
                detailed = true,
                onDateSelected = { selectedEpochDay = it.toEpochDay() },
                onOpenTodo = onOpenTodo,
            )
            if (view == CalendarView.MONTH) {
                HorizontalDivider()
                DayDetails(
                    date = selectedDate,
                    todos = todosByDay[selectedEpochDay].orEmpty(),
                    entries = entriesByDay[selectedEpochDay].orEmpty(),
                    dateFormatter = dateFormatter,
                    use24HourTime = use24HourTime,
                    onOpenTodo = onOpenTodo,
                    contentPadding = PaddingValues(bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    selectedDate: LocalDate,
    view: CalendarView,
    firstDayOfWeek: DayOfWeek,
    dateFormatter: DateTimeFormatter,
    locale: Locale,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onViewChanged: (CalendarView) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous")
            }
            Text(
                text = calendarHeaderLabel(
                    date = selectedDate,
                    view = view,
                    firstDayOfWeek = firstDayOfWeek,
                    dateFormatter = dateFormatter,
                    locale = locale,
                ),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onToday) {
                Icon(Icons.Rounded.Today, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Today")
            }
            CalendarView.entries.forEach { option ->
                FilterChip(
                    selected = option == view,
                    onClick = { onViewChanged(option) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

@Composable
private fun CalendarViewBody(
    view: CalendarView,
    selectedDate: LocalDate,
    firstDayOfWeek: DayOfWeek,
    todosByDay: Map<Long, List<TodoEntity>>,
    entriesByDay: Map<Long, List<LedgerEntryEntity>>,
    dateFormatter: DateTimeFormatter,
    use24HourTime: Boolean,
    detailed: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    onOpenTodo: (Long) -> Unit,
) {
    when (view) {
        CalendarView.MONTH -> MonthView(
            selectedDate = selectedDate,
            firstDayOfWeek = firstDayOfWeek,
            todosByDay = todosByDay,
            entriesByDay = entriesByDay,
            dateFormatter = dateFormatter,
            onDateSelected = onDateSelected,
        )
        CalendarView.WEEK -> WeekView(
            selectedDate = selectedDate,
            firstDayOfWeek = firstDayOfWeek,
            todosByDay = todosByDay,
            entriesByDay = entriesByDay,
            dateFormatter = dateFormatter,
            use24HourTime = use24HourTime,
            detailed = detailed,
            onDateSelected = onDateSelected,
            onOpenTodo = onOpenTodo,
        )
        CalendarView.DAY -> DayDetails(
            date = selectedDate,
            todos = todosByDay[selectedDate.toEpochDay()].orEmpty(),
            entries = entriesByDay[selectedDate.toEpochDay()].orEmpty(),
            dateFormatter = dateFormatter,
            use24HourTime = use24HourTime,
            contentPadding = PaddingValues(vertical = 4.dp),
            onOpenTodo = onOpenTodo,
        )
        CalendarView.AGENDA -> AgendaView(
            selectedDate = selectedDate,
            todosByDay = todosByDay,
            entriesByDay = entriesByDay,
            dateFormatter = dateFormatter,
            use24HourTime = use24HourTime,
            detailed = detailed,
            onDateSelected = onDateSelected,
            onOpenTodo = onOpenTodo,
        )
    }
}

@Composable
private fun MonthView(
    selectedDate: LocalDate,
    firstDayOfWeek: DayOfWeek,
    todosByDay: Map<Long, List<TodoEntity>>,
    entriesByDay: Map<Long, List<LedgerEntryEntity>>,
    dateFormatter: DateTimeFormatter,
    onDateSelected: (LocalDate) -> Unit,
) {
    val month = YearMonth.from(selectedDate)
    val gridStart = month.atDay(1).previousOrSame(firstDayOfWeek)
    val dates = remember(month, firstDayOfWeek) { List(42) { gridStart.plusDays(it.toLong()) } }
    val daysOfWeek = remember(firstDayOfWeek) { List(7) { firstDayOfWeek.plus(it.toLong()) } }
    val rowHeight = monthGridRowHeight(LocalDensity.current.fontScale)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gridWidth = monthGridWidth(maxWidth)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Column(
                    modifier = Modifier.width(gridWidth),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        daysOfWeek.forEach { day ->
                            Text(
                                text = UserFormatting.formatWeekday(day, Locale.ENGLISH),
                                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    dates.chunked(7).forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(rowHeight),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            week.forEach { date ->
                                val dayTodos = todosByDay[date.toEpochDay()].orEmpty()
                                val completedTodoCount = dayTodos.count { it.completedAt != null }
                                val incompleteTodoCount = dayTodos.size - completedTodoCount
                                val dayEntries = entriesByDay[date.toEpochDay()].orEmpty()
                                val net = dayEntries.netCents()
                                MonthDayCell(
                                    date = date,
                                    isCurrentMonth = YearMonth.from(date) == month,
                                    isSelected = date == selectedDate,
                                    completedTodoCount = completedTodoCount,
                                    incompleteTodoCount = incompleteTodoCount,
                                    hasEntries = dayEntries.isNotEmpty(),
                                    netCents = net,
                                    dateFormatter = dateFormatter,
                                    onClick = { onDateSelected(date) },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    completedTodoCount: Int,
    incompleteTodoCount: Int,
    hasEntries: Boolean,
    netCents: Long,
    dateFormatter: DateTimeFormatter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val dayColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        isCurrentMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
    }
    val border = if (isSelected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    val accessibilityLabel = buildString {
        append(calendarDateLabel(date, dateFormatter))
        append(if (isCurrentMonth) ", current month" else ", outside the current month")
        append(", $completedTodoCount completed")
        append(", $incompleteTodoCount incomplete")
        if (hasEntries) append(", net ${formatAmount(netCents)}") else append(", no ledger entries")
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(border, shape)
            .clickable(
                onClickLabel = "Select date",
                role = Role.Button,
                onClick = onClick,
            )
            .clearAndSetSemantics {
                contentDescription = accessibilityLabel
                selected = isSelected
            }
            .padding(horizontal = 3.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                color = dayColor,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (hasEntries) {
                Text(
                    text = formatAmount(netCents),
                    color = amountColor(netCents),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (completedTodoCount > 0 || incompleteTodoCount > 0) {
                TodoStatusCounts(
                    completedCount = completedTodoCount,
                    incompleteCount = incompleteTodoCount,
                )
            }
        }
    }
}

@Composable
private fun TodoStatusCounts(
    completedCount: Int,
    incompleteCount: Int,
) {
    val placement = todoStatusPlacement(completedCount, incompleteCount)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (placement == TodoStatusPlacement.SPLIT) {
            Arrangement.SpaceBetween
        } else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (completedCount > 0) {
            Text(
                text = completedCount.toString(),
                color = incomeColor(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        if (incompleteCount > 0) {
            Text(
                text = incompleteCount.toString(),
                color = expenseColor(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WeekView(
    selectedDate: LocalDate,
    firstDayOfWeek: DayOfWeek,
    todosByDay: Map<Long, List<TodoEntity>>,
    entriesByDay: Map<Long, List<LedgerEntryEntity>>,
    dateFormatter: DateTimeFormatter,
    use24HourTime: Boolean,
    detailed: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    onOpenTodo: (Long) -> Unit,
) {
    val weekStart = selectedDate.previousOrSame(firstDayOfWeek)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(7) { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val todos = todosByDay[date.toEpochDay()].orEmpty()
            val entries = entriesByDay[date.toEpochDay()].orEmpty()
            if (detailed) {
                DayCard(
                    date = date,
                    todos = todos,
                    entries = entries,
                    dateFormatter = dateFormatter,
                    use24HourTime = use24HourTime,
                    selected = date == selectedDate,
                    onClick = { onDateSelected(date) },
                    onOpenTodo = onOpenTodo,
                )
            } else {
                DaySummaryCard(
                    date = date,
                    todos = todos,
                    entries = entries,
                    dateFormatter = dateFormatter,
                    selected = date == selectedDate,
                    onClick = { onDateSelected(date) },
                )
            }
        }
    }
}

@Composable
private fun AgendaView(
    selectedDate: LocalDate,
    todosByDay: Map<Long, List<TodoEntity>>,
    entriesByDay: Map<Long, List<LedgerEntryEntity>>,
    dateFormatter: DateTimeFormatter,
    use24HourTime: Boolean,
    detailed: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    onOpenTodo: (Long) -> Unit,
) {
    val month = YearMonth.from(selectedDate)
    val activeDates = remember(month, todosByDay, entriesByDay) {
        (1..month.lengthOfMonth())
            .map(month::atDay)
            .filter { date ->
                val key = date.toEpochDay()
                !todosByDay[key].isNullOrEmpty() || !entriesByDay[key].isNullOrEmpty()
            }
    }

    if (activeDates.isEmpty()) {
        EmptyMessage("Nothing scheduled this month")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        activeDates.forEach { date ->
            val todos = todosByDay[date.toEpochDay()].orEmpty()
            val entries = entriesByDay[date.toEpochDay()].orEmpty()
            if (detailed) {
                DayCard(
                    date = date,
                    todos = todos,
                    entries = entries,
                    dateFormatter = dateFormatter,
                    use24HourTime = use24HourTime,
                    selected = date == selectedDate,
                    onClick = { onDateSelected(date) },
                    onOpenTodo = onOpenTodo,
                )
            } else {
                DaySummaryCard(
                    date = date,
                    todos = todos,
                    entries = entries,
                    dateFormatter = dateFormatter,
                    selected = date == selectedDate,
                    onClick = { onDateSelected(date) },
                )
            }
        }
    }
}

@Composable
private fun DaySummaryCard(
    date: LocalDate,
    todos: List<TodoEntity>,
    entries: List<LedgerEntryEntity>,
    dateFormatter: DateTimeFormatter,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = calendarDateLabel(date, dateFormatter),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${todos.size} ${countLabel(todos.size, "todo")}  |  " +
                        "${entries.size} ${countLabel(entries.size, "ledger entry")}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (entries.isNotEmpty()) {
                Text(
                    text = formatAmount(entries.netCents()),
                    color = amountColor(entries.netCents()),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun DayCard(
    date: LocalDate,
    todos: List<TodoEntity>,
    entries: List<LedgerEntryEntity>,
    dateFormatter: DateTimeFormatter,
    use24HourTime: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onOpenTodo: (Long) -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        DayDetails(
            date = date,
            todos = todos,
            entries = entries,
            dateFormatter = dateFormatter,
            use24HourTime = use24HourTime,
            contentPadding = PaddingValues(14.dp),
            onOpenTodo = onOpenTodo,
        )
    }
}

@Composable
private fun DayDetails(
    date: LocalDate,
    todos: List<TodoEntity>,
    entries: List<LedgerEntryEntity>,
    dateFormatter: DateTimeFormatter,
    use24HourTime: Boolean,
    onOpenTodo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Column(
        modifier = modifier.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = UserFormatting.formatWeekday(
                        date.dayOfWeek,
                        Locale.ENGLISH,
                        TextStyle.FULL,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = calendarDateLabel(date, dateFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entries.isNotEmpty()) {
                Text(
                    text = formatLedgerDetailAmount(entries.netCents()),
                    color = amountColor(entries.netCents()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        DetailSection(title = "Todos", emptyText = "No todos", isEmpty = todos.isEmpty()) {
            todos.sortedWith(compareBy<TodoEntity> { it.deadlineMinute ?: -1 }.thenBy { it.title })
                .forEach { todo ->
                    TodoRow(
                        todo = todo,
                        use24HourTime = use24HourTime,
                        onClick = { onOpenTodo(todo.id) },
                    )
                }
        }
        DetailSection(
            title = "Ledger entries",
            emptyText = "No ledger entries",
            isEmpty = entries.isEmpty(),
        ) {
            entries.sortedWith(compareBy<LedgerEntryEntity> { it.minuteOfDay }.thenBy { it.createdAt })
                .forEach { entry ->
                    LedgerRow(entry = entry, use24HourTime = use24HourTime)
                }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    emptyText: String,
    isEmpty: Boolean,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (isEmpty) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            content()
        }
    }
}

@Composable
private fun TodoRow(
    todo: TodoEntity,
    use24HourTime: Boolean,
    onClick: () -> Unit,
) {
    val completed = todo.completedAt != null
    val textColor = if (completed) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                onClickLabel = "View or edit todo",
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (completed) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.primary,
                ),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = todo.title.ifBlank { todo.description },
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            todo.deadlineMinute?.let { minute ->
                Text(
                    text = formatTime(minute, use24HourTime),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (completed) {
                Text(
                    text = "Completed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                )
            }
        }
    }
}

@Composable
private fun LedgerRow(entry: LedgerEntryEntity, use24HourTime: Boolean) {
    val signedCents = if (entry.type == LedgerType.EXPENSE) -entry.amountCents else entry.amountCents
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatTime(entry.minuteOfDay, use24HourTime),
            modifier = Modifier.widthIn(min = 58.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = entry.merchant.ifBlank { entry.note.ifBlank { entry.type.displayName() } },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatLedgerDetailAmount(signedCents),
            color = if (entry.type == LedgerType.INCOME) incomeColor() else expenseColor(),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun calendarHeaderLabel(
    date: LocalDate,
    view: CalendarView,
    firstDayOfWeek: DayOfWeek,
    dateFormatter: DateTimeFormatter,
    locale: Locale,
): String = when (view) {
    CalendarView.MONTH, CalendarView.AGENDA -> date.format(
        DateTimeFormatter.ofPattern("MMMM yyyy", locale),
    )
    CalendarView.WEEK -> {
        val start = date.previousOrSame(firstDayOfWeek)
        "${calendarDateLabel(start, dateFormatter)} to " +
            calendarDateLabel(start.plusDays(6), dateFormatter)
    }
    CalendarView.DAY -> calendarDateLabel(date, dateFormatter)
}

private fun LocalDate.moveMonth(months: Long): LocalDate {
    val target = YearMonth.from(this).plusMonths(months)
    return target.atDay(dayOfMonth.coerceAtMost(target.lengthOfMonth()))
}

private fun LocalDate.previousOrSame(dayOfWeek: DayOfWeek): LocalDate =
    with(TemporalAdjusters.previousOrSame(dayOfWeek))

private fun <T> List<Pair<Long, T>>.groupByPair(): Map<Long, List<T>> =
    groupBy(keySelector = { it.first }, valueTransform = { it.second })

internal fun calendarDateLabel(date: LocalDate, dateFormatter: DateTimeFormatter): String =
    dateFormatter.format(date)

internal fun monthGridRowHeight(fontScale: Float): Dp {
    val scaledTextHeight = MONTH_CELL_TEXT_LINE_HEIGHT_DP * fontScale.coerceAtLeast(0f)
    return maxOf(MONTH_GRID_BASE_ROW_HEIGHT_DP, scaledTextHeight + MONTH_CELL_FIXED_HEIGHT_DP).dp
}

internal fun monthGridWidth(availableWidth: Dp): Dp =
    availableWidth.coerceAtLeast(MONTH_GRID_MIN_WIDTH_DP.dp)

internal enum class TodoStatusPlacement { NONE, CENTER, SPLIT }

internal fun todoStatusPlacement(
    completedCount: Int,
    incompleteCount: Int,
): TodoStatusPlacement = when {
    completedCount <= 0 && incompleteCount <= 0 -> TodoStatusPlacement.NONE
    completedCount > 0 && incompleteCount > 0 -> TodoStatusPlacement.SPLIT
    else -> TodoStatusPlacement.CENTER
}

private fun List<LedgerEntryEntity>.netCents(): Long = fold(0L) { total, entry ->
    if (entry.type == LedgerType.INCOME) total + entry.amountCents else total - entry.amountCents
}

internal fun formatAmount(cents: Long): String =
    BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString()

internal fun formatLedgerDetailAmount(cents: Long): String {
    val amount = formatAmount(cents)
    return if (amount.startsWith('-')) {
        "-\$${amount.drop(1)}"
    } else {
        "\$$amount"
    }
}

private fun formatTime(minuteOfDay: Int, use24HourTime: Boolean): String =
    UserFormatting.formatMinuteOfDay(
        minuteOfDay = minuteOfDay,
        option = if (use24HourTime) TimeFormatOption.HOUR_24 else TimeFormatOption.HOUR_12,
        systemUses24Hour = use24HourTime,
        locale = Locale.ENGLISH,
    )

private fun LedgerType.displayName(): String = when (this) {
    LedgerType.INCOME -> "Income"
    LedgerType.EXPENSE -> "Expense"
}

private fun countLabel(count: Int, singular: String): String =
    if (count == 1) singular else if (singular.endsWith("y")) singular.dropLast(1) + "ies" else singular + "s"

@Composable
private fun amountColor(cents: Long): Color = when {
    cents > 0 -> incomeColor()
    cents < 0 -> expenseColor()
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun incomeColor(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
    Color(0xFF66BB6A)
} else {
    Color(0xFF2E7D32)
}

@Composable
private fun expenseColor(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
    Color(0xFFEF5350)
} else {
    Color(0xFFC62828)
}

private const val MONTH_GRID_BASE_ROW_HEIGHT_DP = 76f
private const val MONTH_GRID_MIN_WIDTH_DP = 360f
private const val MONTH_CELL_TEXT_LINE_HEIGHT_DP = 36f
private const val MONTH_CELL_FIXED_HEIGHT_DP = 17f
