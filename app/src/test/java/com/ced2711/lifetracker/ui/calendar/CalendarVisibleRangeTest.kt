package com.ced2711.lifetracker.ui.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarVisibleRangeTest {
    private val selectedDate = LocalDate.of(2026, 8, 18)

    @Test
    fun monthMaterializesThroughLastCellOfSixWeekGrid() {
        assertEquals(
            LocalDate.of(2026, 9, 5).toEpochDay(),
            calendarVisibleEndEpochDay(selectedDate, CalendarView.MONTH, DayOfWeek.SUNDAY),
        )
    }

    @Test
    fun weekMaterializesThroughConfiguredWeekEnd() {
        assertEquals(
            LocalDate.of(2026, 8, 22).toEpochDay(),
            calendarVisibleEndEpochDay(selectedDate, CalendarView.WEEK, DayOfWeek.SUNDAY),
        )
        assertEquals(
            LocalDate.of(2026, 8, 23).toEpochDay(),
            calendarVisibleEndEpochDay(selectedDate, CalendarView.WEEK, DayOfWeek.MONDAY),
        )
    }

    @Test
    fun dayAndAgendaUseTheirActualVisibleEnds() {
        assertEquals(
            selectedDate.toEpochDay(),
            calendarVisibleEndEpochDay(selectedDate, CalendarView.DAY, DayOfWeek.SUNDAY),
        )
        assertEquals(
            LocalDate.of(2026, 8, 31).toEpochDay(),
            calendarVisibleEndEpochDay(selectedDate, CalendarView.AGENDA, DayOfWeek.SUNDAY),
        )
    }

    @Test
    fun monthAndAgendaHeadersShowMonthAndYearWhileDayKeepsFullDate() {
        val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)

        assertEquals(
            "August 2026",
            calendarHeaderLabel(
                selectedDate,
                CalendarView.MONTH,
                DayOfWeek.SUNDAY,
                formatter,
                Locale.US,
            ),
        )
        assertEquals(
            "August 2026",
            calendarHeaderLabel(
                selectedDate,
                CalendarView.AGENDA,
                DayOfWeek.SUNDAY,
                formatter,
                Locale.US,
            ),
        )
        assertEquals(
            "08/18/2026",
            calendarHeaderLabel(
                selectedDate,
                CalendarView.DAY,
                DayOfWeek.SUNDAY,
                formatter,
                Locale.US,
            ),
        )
    }
}
