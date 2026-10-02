package com.ced2711.lifetracker.ui.calendar

import android.text.format.DateFormat
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ced2711.lifetracker.data.local.DiaryEntryEntity
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.SubtaskEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.TodoDraft
import com.ced2711.lifetracker.ui.TaskLedgerViewModel
import com.ced2711.lifetracker.ui.adaptive.HingeSafeAlertDialog
import com.ced2711.lifetracker.ui.design.ReadableWidth
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.LocalDate
import java.time.YearMonth

/** What the Calendar's parts draw from: every day's contents and how to word dates and times. */
internal class CalendarData(
    val contents: DayContents,
    val subtasksByTodo: Map<Long, List<SubtaskEntity>>,
    val settings: AppSettings,
    val showDiary: Boolean,
    val today: LocalDate,
    val systemUses24Hour: Boolean,
)

/** What the Calendar's rows can do. */
internal class CalendarActions(
    /** Ticks a todo off or back on. */
    val onToggleTodo: (TodoEntity, Boolean) -> Unit,
    /** Adds a todo with this text, due on this epoch day. */
    val onAddTodo: (String, Long) -> Unit,
    val onOpenTodo: (Long) -> Unit,
    val onOpenLedgerEntry: (Long) -> Unit,
    val onOpenDiary: (Long) -> Unit,
)

/**
 * The Calendar: the month, week, day or the coming 30 days with each day's todos and money, and
 * the selected day's details to tick todos off, add one, and open ledger entries and the diary.
 */
@Composable
fun CalendarScreen(
    viewModel: TaskLedgerViewModel,
    onOpenTodo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
    onOpenDiary: (Long) -> Unit = {},
    showDiary: Boolean = true,
    onOpenLedgerEntry: (Long) -> Unit = {},
) {
    val activeTodos by viewModel.activeTodos.collectAsStateWithLifecycle()
    val completedTodos by viewModel.completedTodos.collectAsStateWithLifecycle()
    val subtasksByTodo by viewModel.subtasksByTodo.collectAsStateWithLifecycle()
    val entries by viewModel.ledgerEntries.collectAsStateWithLifecycle()
    val diaryEntries by viewModel.diaryEntries.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    CalendarContent(
        todos = activeTodos + completedTodos,
        subtasksByTodo = subtasksByTodo,
        ledger = entries,
        diary = diaryEntries,
        settings = settings,
        showDiary = showDiary,
        isWide = isWide,
        modifier = modifier,
        onVisibleEndEpochDayChanged = viewModel::materializeCalendarTodoOccurrencesThrough,
        onToggleTodo = { todo, done, withSubtasks ->
            if (done) viewModel.completeTodo(todo.id, withSubtasks) else viewModel.restoreTodo(todo.id)
        },
        onAddTodo = { text, epochDay ->
            viewModel.addQuickTodo(
                TodoDraft(
                    description = text,
                    deadlineEpochDay = epochDay,
                    reminderOffsetsMinutes = settings.defaultReminderOffsetsMinutes.toList(),
                ),
            )
        },
        onOpenTodo = onOpenTodo,
        onOpenLedgerEntry = onOpenLedgerEntry,
        onOpenDiary = onOpenDiary,
    )
}

/**
 * The Calendar without a view model. It keeps the selected day and view itself; [initialView]
 * and [initialSelected] say where it starts. [onToggleTodo] gets the todo, whether it is now
 * done, and whether its open subtasks are finished with it.
 */
@Composable
internal fun CalendarContent(
    todos: List<TodoEntity>,
    subtasksByTodo: Map<Long, List<SubtaskEntity>>,
    ledger: List<LedgerEntryEntity>,
    diary: List<DiaryEntryEntity>,
    settings: AppSettings,
    showDiary: Boolean,
    isWide: Boolean,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    initialView: CalendarView = CalendarView.MONTH,
    initialSelected: LocalDate = today,
    onVisibleEndEpochDayChanged: (Long) -> Unit,
    onToggleTodo: (TodoEntity, Boolean, Boolean) -> Unit,
    onAddTodo: (String, Long) -> Unit,
    onOpenTodo: (Long) -> Unit,
    onOpenLedgerEntry: (Long) -> Unit,
    onOpenDiary: (Long) -> Unit,
) {
    val language = LocalUiLanguage.current
    val locale = uiLocale(language)
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(initialSelected.toEpochDay()) }
    var viewName by rememberSaveable { mutableStateOf(initialView.name) }
    var completing by remember { mutableStateOf<TodoEntity?>(null) }
    val selected = LocalDate.ofEpochDay(selectedEpochDay)
    val view = CalendarView.entries.firstOrNull { it.name == viewName } ?: CalendarView.MONTH
    val firstDayOfWeek = UserFormatting.firstDayOfWeek(settings.weekStart, locale)
    val systemUses24Hour = DateFormat.is24HourFormat(LocalContext.current)

    // Upcoming repeating todos are created for the days on screen.
    val visibleEnd = calendarVisibleEndEpochDay(selected, view, firstDayOfWeek, today)
    LaunchedEffect(visibleEnd) { onVisibleEndEpochDayChanged(visibleEnd) }

    val contents = remember(todos, ledger, diary) { DayContents(todos, ledger, diary) }
    val data = CalendarData(contents, subtasksByTodo, settings, showDiary, today, systemUses24Hour)
    val actions = CalendarActions(
        onToggleTodo = { todo, done ->
            if (done && subtasksByTodo[todo.id].orEmpty().any { !it.isCompleted }) completing = todo else onToggleTodo(todo, done, false)
        },
        onAddTodo = onAddTodo,
        onOpenTodo = onOpenTodo,
        onOpenLedgerEntry = onOpenLedgerEntry,
        onOpenDiary = onOpenDiary,
    )

    val page: (Int) -> Unit = { direction ->
        selectedEpochDay = when (view) {
            CalendarView.MONTH -> selected.moveMonth(direction.toLong())
            CalendarView.WEEK -> selected.plusWeeks(direction.toLong())
            CalendarView.DAY, CalendarView.AGENDA -> selected.plusDays(direction.toLong())
        }.toEpochDay()
    }
    val twoPanes = isWide && usesWideCalendarMasterDetail(view)
    val header: @Composable () -> Unit = {
        CalendarHeader(
            title = calendarHeaderLabel(selected, view, firstDayOfWeek, UserFormatting.dateFormatter(settings.dateFormat, locale), language),
            view = view,
            showToday = selected != today,
            onPrevious = { page(-1) },
            onNext = { page(1) },
            onToday = { selectedEpochDay = today.toEpochDay() },
            onView = { viewName = it.name },
        )
    }
    val weekStart = selected.previousOrSame(firstDayOfWeek)

    if (twoPanes) {
        // The calendar on the left, the selected day on the right.
        Row(modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = Space.xl)) {
                header()
                Spacer(Modifier.height(Space.xs))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val room = maxHeight - Space.lg
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Space.lg)) {
                        if (view == CalendarView.MONTH) {
                            MonthGrid(YearMonth.from(selected), selected, data, firstDayOfWeek, onSelect = { selectedEpochDay = it.toEpochDay() }, onPage = page, minHeight = room)
                        } else {
                            WeekList(weekStart, selected, data, onSelect = { selectedEpochDay = it.toEpochDay() }, onPage = page)
                        }
                    }
                }
            }
            Box(Modifier.fillMaxHeight().width(1.dp).background(LifeTheme.colors.divider))
            Column(
                Modifier.width(360.dp).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.md, vertical = Space.lg),
            ) {
                DayDetails(selected, data, actions)
                Spacer(Modifier.height(Space.xxxl))
            }
        }
    } else {
        // One column that scrolls as a whole; on a phone the selected day sits under the month.
        ReadableWidth(modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Space.lg, end = Space.lg, bottom = 32.dp),
            ) {
                item(key = "header") {
                    Column {
                        header()
                        Spacer(Modifier.height(Space.sm))
                    }
                }
                when (view) {
                    CalendarView.MONTH -> {
                        item(key = "month") {
                            MonthGrid(YearMonth.from(selected), selected, data, firstDayOfWeek, onSelect = { selectedEpochDay = it.toEpochDay() }, onPage = page)
                        }
                        item(key = "details") { DayDetails(selected, data, actions, Modifier.padding(top = Space.lg)) }
                    }
                    CalendarView.WEEK -> item(key = "week") {
                        // Without room for the day beside the week, choosing a day opens it.
                        WeekList(
                            weekStart, selected, data,
                            onSelect = {
                                selectedEpochDay = it.toEpochDay()
                                viewName = CalendarView.DAY.name
                            },
                            onPage = page,
                        )
                    }
                    CalendarView.DAY -> item(key = "day") { DayDetails(selected, data, actions, Modifier.padding(top = Space.sm), showDate = false) }
                    CalendarView.AGENDA -> agendaItems(data, actions)
                }
            }
        }
    }

    completing?.let { todo ->
        HingeSafeAlertDialog(
            onDismissRequest = { completing = null },
            title = { Text(localizedText("Complete task?")) },
            text = { Text(localizedText("It still has open subtasks.")) },
            dismissButton = { TextButton(onClick = { completing = null; onToggleTodo(todo, true, false) }) { Text(localizedText("Task only")) } },
            confirmButton = { Button(onClick = { completing = null; onToggleTodo(todo, true, true) }) { Text(localizedText("Task + subtasks")) } },
        )
    }
}
