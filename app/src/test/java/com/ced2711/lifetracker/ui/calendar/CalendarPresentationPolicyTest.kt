package com.ced2711.lifetracker.ui.calendar

import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarPresentationPolicyTest {
    @Test
    fun `no todo counts render no status row`() {
        assertEquals(TodoStatusPlacement.NONE, todoStatusPlacement(0, 0))
        assertEquals(TodoStatusPlacement.NONE, todoStatusPlacement(-1, -1))
    }

    @Test
    fun `one todo status is centered while mixed statuses split left and right`() {
        assertEquals(TodoStatusPlacement.CENTER, todoStatusPlacement(3, 0))
        assertEquals(TodoStatusPlacement.CENTER, todoStatusPlacement(0, 4))
        assertEquals(TodoStatusPlacement.SPLIT, todoStatusPlacement(2, 5))
    }

    @Test
    fun `day cells keep a symbol-free amount without a positive plus`() {
        assertEquals("67", formatAmount(6_700))
        assertEquals("-325", formatAmount(-32_500))
        assertEquals("12.34", formatAmount(1_234))
        assertFalse(formatAmount(6_700).contains('$'))
        assertFalse(formatAmount(6_700).startsWith('+'))
    }

    @Test
    fun `day details use a fixed dollar symbol and preserve the expense sign`() {
        assertEquals("\$67", formatLedgerDetailAmount(6_700))
        assertEquals("-\$325", formatLedgerDetailAmount(-32_500))
        assertEquals("\$12.34", formatLedgerDetailAmount(1_234))
        assertEquals("\$0", formatLedgerDetailAmount(0))
    }

    @Test
    fun `wide day is single pane while browsable views stay master detail`() {
        assertFalse(usesWideCalendarMasterDetail(CalendarView.DAY))
        assertTrue(usesWideCalendarMasterDetail(CalendarView.MONTH))
        assertTrue(usesWideCalendarMasterDetail(CalendarView.WEEK))
        assertTrue(usesWideCalendarMasterDetail(CalendarView.AGENDA))
    }

    @Test
    fun `calendar date labels use the supplied user formatter`() {
        val date = LocalDate.of(2026, 8, 18)

        assertEquals(
            "08/18/2026",
            calendarDateLabel(date, DateTimeFormatter.ofPattern("MM/dd/yyyy")),
        )
        assertEquals(
            "18/08/2026",
            calendarDateLabel(date, DateTimeFormatter.ofPattern("dd/MM/yyyy")),
        )
        assertEquals(
            "2026-08-18",
            calendarDateLabel(date, DateTimeFormatter.ofPattern("yyyy-MM-dd")),
        )
    }

    @Test
    fun `month grid keeps its compact baseline height at normal font scale`() {
        assertEquals(76f, monthGridRowHeight(1f).value, 0f)
    }

    @Test
    fun `month grid row grows enough for both labels at two times font scale`() {
        val normalHeight = monthGridRowHeight(1f)
        val largeFontHeight = monthGridRowHeight(2f)

        assertTrue(largeFontHeight > normalHeight)
        assertTrue(largeFontHeight.value >= 89f)
    }

    @Test
    fun `month grid preserves a scrollable minimum width on narrow screens`() {
        assertEquals(360.dp, monthGridWidth(280.dp))
        assertEquals(720.dp, monthGridWidth(720.dp))
    }
}
