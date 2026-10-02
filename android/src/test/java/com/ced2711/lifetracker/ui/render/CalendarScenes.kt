package com.ced2711.lifetracker.ui.render

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ced2711.lifetracker.data.local.DiaryEntryEntity
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.ui.calendar.CalendarContent
import com.ced2711.lifetracker.ui.calendar.CalendarView

private val day = RenderSamples.today.toEpochDay()

/** A fuller month than the shared samples: busy days, a day with more todos than marks, and some money. */
private val calendarTodos: List<TodoEntity> = RenderSamples.todos + listOf(
    TodoEntity(id = 101, title = "Team meeting", description = "Team meeting", deadlineEpochDay = day + 1, deadlineMinute = 10 * 60, priority = TodoPriority.HIGH),
    TodoEntity(id = 102, title = "Pay the electricity bill", description = "Pay the electricity bill", deadlineEpochDay = day + 2, priority = TodoPriority.URGENT),
    TodoEntity(id = 103, title = "Gym", description = "Gym", deadlineEpochDay = day + 2, deadlineMinute = 18 * 60 + 30, seriesId = 2),
    TodoEntity(id = 104, title = "Pick up the parcel", description = "Pick up the parcel", deadlineEpochDay = day + 4, priority = TodoPriority.MEDIUM),
    TodoEntity(id = 105, title = "Dinner with Sam", description = "Dinner with Sam", deadlineEpochDay = day + 4, deadlineMinute = 19 * 60),
    TodoEntity(id = 106, title = "Review the budget", description = "Review the budget", deadlineEpochDay = day + 4, priority = TodoPriority.LOW),
    TodoEntity(id = 107, title = "Clean the bike", description = "Clean the bike", deadlineEpochDay = day + 4),
    TodoEntity(id = 108, title = "Send birthday card", description = "Send birthday card", deadlineEpochDay = day + 4, priority = TodoPriority.HIGH),
    TodoEntity(id = 109, title = "Car inspection", description = "Car inspection", deadlineEpochDay = day + 9, deadlineMinute = 8 * 60),
    TodoEntity(id = 110, title = "Library books back", description = "Library books back", deadlineEpochDay = day - 6, completedAt = 1),
    TodoEntity(id = 111, title = "Gym", description = "Gym", deadlineEpochDay = day - 5, completedAt = 1, seriesId = 2),
)

private val calendarLedger: List<LedgerEntryEntity> = RenderSamples.ledger + listOf(
    LedgerEntryEntity(id = 101, type = LedgerType.EXPENSE, amountCents = 4_800, epochDay = day + 2, minuteOfDay = 0, merchant = "Electricity", seriesId = 2),
    LedgerEntryEntity(id = 102, type = LedgerType.INCOME, amountCents = 12_000, epochDay = day + 4, minuteOfDay = 9 * 60, merchant = "Refund"),
)

private val calendarDiary: List<DiaryEntryEntity> = RenderSamples.diary +
    DiaryEntryEntity(id = 101, epochDay = day, body = "Sunny morning. Walked to work and had coffee with Ana.")

@Composable
private fun Calendar(isWide: Boolean, view: CalendarView, empty: Boolean = false) {
    CalendarContent(
        todos = if (empty) emptyList() else calendarTodos,
        subtasksByTodo = if (empty) emptyMap() else RenderSamples.subtasksByTodo,
        ledger = if (empty) emptyList() else calendarLedger,
        diary = if (empty) emptyList() else calendarDiary,
        settings = RenderSamples.settings,
        showDiary = true,
        isWide = isWide,
        modifier = Modifier.fillMaxSize(),
        today = RenderSamples.today,
        initialView = view,
        onVisibleEndEpochDayChanged = {},
        onToggleTodo = { _, _, _ -> },
        onAddTodo = { _, _ -> },
        onOpenTodo = {},
        onOpenLedgerEntry = {},
        onOpenDiary = {},
    )
}

/** Scenes of the Calendar screens for ScreenRenderTest; see RenderScene. */
internal val calendarScenes: List<RenderScene> = listOf(
    RenderScene("calendar-month", TopLevelDestination.CALENDAR) { isWide -> Calendar(isWide, CalendarView.MONTH) },
    RenderScene("calendar-week", TopLevelDestination.CALENDAR) { isWide -> Calendar(isWide, CalendarView.WEEK) },
    RenderScene("calendar-day", TopLevelDestination.CALENDAR) { isWide -> Calendar(isWide, CalendarView.DAY) },
    RenderScene("calendar-agenda", TopLevelDestination.CALENDAR) { isWide -> Calendar(isWide, CalendarView.AGENDA) },
    RenderScene("calendar-month-empty", TopLevelDestination.CALENDAR) { isWide -> Calendar(isWide, CalendarView.MONTH, empty = true) },
    RenderScene("calendar-agenda-empty", TopLevelDestination.CALENDAR) { isWide -> Calendar(isWide, CalendarView.AGENDA, empty = true) },
)
