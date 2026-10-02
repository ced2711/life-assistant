package com.ced2711.lifetracker.ui.calendar

import com.ced2711.lifetracker.domain.model.UiLanguage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarVisibleRangeTest {
    private val selectedDate = LocalDate.of(2026, 8, 18)
    private val today = LocalDate.of(2026, 8, 3)

    @Test
    fun monthMaterializesThroughLastCellOfSixWeekGrid() {
        assertEquals(
            LocalDate.of(2026, 9, 5).toEpochDay(),
            calendarVisibleEndEpochDay(selectedDate, CalendarView.MONTH, DayOfWeek.SUNDAY, today),
        )
    }

    @Test
    fun weekMaterializesThroughConfiguredWeekEnd() {
        assertEquals(
            LocalDate.of(2026, 8, 22).toEpochDay(),
            calendarVisibleEndEpochDay(selectedDate, CalendarView.WEEK, DayOfWeek.SUNDAY, today),
        )
        assertEquals(
            LocalDate.of(2026, 8, 23).toEpochDay(),
            calendarVisibleEndEpochDay(selectedDate, CalendarView.WEEK, DayOfWeek.MONDAY, today),
        )
    }

    @Test
    fun dayUsesTheSelectedDayAndAgendaTheComingThirtyDays() {
        assertEquals(
            selectedDate.toEpochDay(),
            calendarVisibleEndEpochDay(selectedDate, CalendarView.DAY, DayOfWeek.SUNDAY, today),
        )
        assertEquals(
            LocalDate.of(2026, 9, 2).toEpochDay(),
            calendarVisibleEndEpochDay(selectedDate, CalendarView.AGENDA, DayOfWeek.SUNDAY, today),
        )
    }

    @Test
    fun headersNameTheMonthTheWeekTheDayAndTheAgendaRange() {
        val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)

        assertEquals(
            "August 2026",
            calendarHeaderLabel(selectedDate, CalendarView.MONTH, DayOfWeek.SUNDAY, formatter, UiLanguage.ENGLISH),
        )
        assertEquals(
            "2026年8月",
            calendarHeaderLabel(selectedDate, CalendarView.MONTH, DayOfWeek.SUNDAY, formatter, UiLanguage.SIMPLIFIED_CHINESE),
        )
        assertEquals(
            "08/16/2026 – 08/22/2026",
            calendarHeaderLabel(selectedDate, CalendarView.WEEK, DayOfWeek.SUNDAY, formatter, UiLanguage.ENGLISH),
        )
        assertEquals(
            "08/18/2026",
            calendarHeaderLabel(selectedDate, CalendarView.DAY, DayOfWeek.SUNDAY, formatter, UiLanguage.ENGLISH),
        )
        assertEquals(
            "Next 30 days",
            calendarHeaderLabel(selectedDate, CalendarView.AGENDA, DayOfWeek.SUNDAY, formatter, UiLanguage.ENGLISH),
        )
        assertEquals(
            "未来 30 天",
            calendarHeaderLabel(selectedDate, CalendarView.AGENDA, DayOfWeek.SUNDAY, formatter, UiLanguage.SIMPLIFIED_CHINESE),
        )
    }
}
