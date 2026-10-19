package com.ced2711.lifetracker.ui.calendar

import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.UiLanguage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarPresentationPolicyTest {
    @Test
    fun `day cells show a short amount with its sign and no currency symbol`() {
        assertEquals("67", compactAmount(6_700))
        assertEquals("12", compactAmount(1_234))
        assertEquals("1.5k", compactAmount(145_000))
        assertEquals("3k", compactAmount(300_000))
        assertEquals("145k", compactAmount(14_500_000))
        assertEquals("2.4M", compactAmount(240_000_000))
        assertEquals("+67", compactSignedAmount(6_700))
        assertEquals("−325", compactSignedAmount(-32_500))
        assertFalse(compactSignedAmount(6_700).contains('$'))
    }

    @Test
    fun `day details show money with a sign and the dollar symbol`() {
        assertEquals("+\$67.00", signedMoney(6_700))
        assertEquals("−\$325.00", signedMoney(-32_500))
    }

    @Test
    fun `wide month and week keep the day beside them while day and agenda are one column`() {
        assertTrue(usesWideCalendarMasterDetail(CalendarView.MONTH))
        assertTrue(usesWideCalendarMasterDetail(CalendarView.WEEK))
        assertFalse(usesWideCalendarMasterDetail(CalendarView.DAY))
        assertFalse(usesWideCalendarMasterDetail(CalendarView.AGENDA))
    }

    @Test
    fun `month grid rows grow with the font size`() {
        assertEquals(64f, monthGridRowHeight(1f).value, 0f)
        assertTrue(monthGridRowHeight(2f) > monthGridRowHeight(1f))
        assertTrue(monthGridRowHeight(1f, withTitles = true) > monthGridRowHeight(1f))
        // A smaller font never shrinks the touch target.
        assertEquals(monthGridRowHeight(1f), monthGridRowHeight(0.85f))
    }

    @Test
    fun `month grid has as many weeks as the month needs`() {
        assertEquals(4, monthGridWeeks(YearMonth.of(2026, 2), DayOfWeek.SUNDAY))
        assertEquals(5, monthGridWeeks(YearMonth.of(2026, 10), DayOfWeek.SUNDAY))
        assertEquals(6, monthGridWeeks(YearMonth.of(2026, 8), DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 9, 28), monthGridStart(YearMonth.of(2026, 10), DayOfWeek.MONDAY))
    }

    @Test
    fun `moving a month keeps the day or uses the last day of a shorter month`() {
        assertEquals(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 1, 31).moveMonth(1))
        assertEquals(LocalDate.of(2025, 12, 15), LocalDate.of(2026, 1, 15).moveMonth(-1))
    }

    @Test
    fun `day contents put open todos first and add up the net`() {
        val today = LocalDate.of(2026, 8, 18).toEpochDay()
        val contents = DayContents(
            todos = listOf(
                TodoEntity(id = 1, title = "Done", description = "", deadlineEpochDay = today, completedAt = 5),
                TodoEntity(id = 2, title = "Evening", description = "", deadlineEpochDay = today, deadlineMinute = 1_080),
                TodoEntity(id = 3, title = "Morning", description = "", deadlineEpochDay = today, deadlineMinute = 480, priority = TodoPriority.HIGH),
                TodoEntity(id = 4, title = "Late", description = "", deadlineEpochDay = today - 3),
                TodoEntity(id = 5, title = "No date", description = ""),
                TodoEntity(id = 6, title = "Later", description = "", deadlineEpochDay = today + 30),
                TodoEntity(id = 7, title = "Too far", description = "", deadlineEpochDay = today + 31),
            ),
            ledger = listOf(
                LedgerEntryEntity(id = 1, type = LedgerType.INCOME, amountCents = 1_000, epochDay = today, minuteOfDay = 0),
                LedgerEntryEntity(id = 2, type = LedgerType.EXPENSE, amountCents = 2_500, epochDay = today, minuteOfDay = 0),
                LedgerEntryEntity(id = 3, type = LedgerType.EXPENSE, amountCents = 100, epochDay = today + 2, minuteOfDay = 0),
            ),
            diary = emptyList(),
        )

        assertEquals(listOf(3L, 2L, 1L), contents.todos[today].orEmpty().map { it.id })
        assertEquals(-1_500L, contents.net(today))
        assertEquals(0L, contents.net(today + 1))
        assertEquals(listOf(4L), contents.overdue(today).map { it.id })
        assertEquals(listOf(today, today + 2, today + 30), contents.agendaDays(today))
    }

    @Test
    fun `day summaries are spoken in the app language`() {
        val settings = AppSettings(dateFormat = DateFormatOption.YEAR_MONTH_DAY)
        val date = LocalDate.of(2026, 8, 18)

        assertEquals(
            "Tuesday, 2026-08-18, today, 1 done, 2 open, net −\$15.00, diary",
            daySummary(date, 1, 2, -1_500, hasDiary = true, isToday = true, settings, UiLanguage.ENGLISH),
        )
        assertEquals("Tuesday, 2026-08-18", daySummary(date, 0, 0, 0, hasDiary = false, isToday = false, settings, UiLanguage.ENGLISH))
        assertEquals(
            "星期二，2026-08-18，已完成 1 项，未完成 2 项，净额 +\$15.00，有日记",
            daySummary(date, 1, 2, 1_500, hasDiary = true, isToday = false, settings, UiLanguage.SIMPLIFIED_CHINESE),
        )
    }

    @Test
    fun `days near today are named and others show the weekday and the date`() {
        val settings = AppSettings(dateFormat = DateFormatOption.YEAR_MONTH_DAY)
        val today = LocalDate.of(2026, 8, 18)

        assertEquals("Today", relativeDayLabel(today.toEpochDay(), today, settings, UiLanguage.ENGLISH))
        assertEquals("明天", relativeDayLabel(today.toEpochDay() + 1, today, settings, UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals("昨天", relativeDayLabel(today.toEpochDay() - 1, today, settings, UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals("Fri 2026-08-21", relativeDayLabel(today.toEpochDay() + 3, today, settings, UiLanguage.ENGLISH))
    }
}
