package com.ced2711.lifetracker.domain.format

import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.WeekStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFormattingTest {
    @Test
    fun `explicit date formats are fixed width and unambiguous`() {
        val date = LocalDate.of(2026, 8, 3)

        assertEquals(
            "08/03/2026",
            UserFormatting.formatDate(date, DateFormatOption.MONTH_DAY_YEAR, Locale.US),
        )
        assertEquals(
            "03/08/2026",
            UserFormatting.formatDate(date, DateFormatOption.DAY_MONTH_YEAR, Locale.US),
        )
        assertEquals(
            "2026-08-03",
            UserFormatting.formatDate(date, DateFormatOption.YEAR_MONTH_DAY, Locale.US),
        )
    }

    @Test
    fun `time format honors explicit and system choices`() {
        val time = LocalTime.of(13, 5)

        assertEquals(
            "1:05 PM",
            UserFormatting.formatTime(time, TimeFormatOption.HOUR_12, true, Locale.US),
        )
        assertEquals(
            "13:05",
            UserFormatting.formatTime(time, TimeFormatOption.HOUR_24, false, Locale.US),
        )
        assertTrue(UserFormatting.uses24HourClock(TimeFormatOption.SYSTEM, true))
        assertFalse(UserFormatting.uses24HourClock(TimeFormatOption.SYSTEM, false))
    }

    @Test
    fun `week ordering starts on the selected day`() {
        assertEquals(
            listOf(
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
            ),
            UserFormatting.orderedDaysOfWeek(WeekStart.SUNDAY, Locale.US),
        )
        assertEquals(
            DayOfWeek.MONDAY,
            UserFormatting.orderedDaysOfWeek(WeekStart.MONDAY, Locale.US).first(),
        )
    }
}
