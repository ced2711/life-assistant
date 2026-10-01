package com.ced2711.lifetracker.ui.ledger

import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.LedgerType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerStatisticsRangeTest {
    @Test
    fun maximumCustomDateRangeProducesBoundedChartData() {
        val start = LocalDate.of(1, 1, 1)
        val end = LocalDate.of(9999, 12, 31)
        val entries = listOf(
            entry(LedgerType.INCOME, 250, start),
            entry(LedgerType.EXPENSE, 100, end),
        )

        val result = trendPoints(entries, start to end, DateFormatOption.MONTH_DAY_YEAR, Locale.US)

        assertTrue(result.points.size <= MAX_LEDGER_TREND_POINTS)
        assertNotNull(result.notice)
        assertEquals(250L, result.points.first().incomeCents)
        assertEquals(100L, result.points.last().expenseCents)
    }

    @Test
    fun oneYearRangeUsesReadableMonthlyPointsWithoutNotice() {
        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 12, 31)

        val result = trendPoints(
            emptyList(),
            start to end,
            DateFormatOption.MONTH_DAY_YEAR,
            Locale.US,
        )

        assertEquals(12, result.points.size)
        assertEquals(null, result.notice)
        assertEquals("Jan", result.points.first().axisLabel)
        assertEquals("Dec", result.points.last().axisLabel)
    }

    @Test
    fun dailyAxisLabelsFollowEveryConfiguredDateOrder() {
        val start = LocalDate.of(2026, 8, 15)
        val end = start.plusDays(1)

        val expectedByFormat = mapOf(
            DateFormatOption.SYSTEM to "15/8",
            DateFormatOption.MONTH_DAY_YEAR to "8/15",
            DateFormatOption.DAY_MONTH_YEAR to "15/8",
            DateFormatOption.YEAR_MONTH_DAY to "8-15",
        )

        expectedByFormat.forEach { (option, expected) ->
            val locale = if (option == DateFormatOption.SYSTEM) Locale.UK else Locale.US
            val result = trendPoints(emptyList(), start to end, option, locale)

            assertEquals(option.name, expected, result.points.first().axisLabel)
        }
    }

    @Test
    fun multiYearMonthLabelsFollowConfiguredYearOrderAndLocale() {
        val start = LocalDate.of(2025, 12, 1)
        val end = LocalDate.of(2026, 1, 31)
        val localizedDecember = start.format(DateTimeFormatter.ofPattern("MMM", Locale.GERMAN))

        val monthFirst = trendPoints(
            emptyList(),
            start to end,
            DateFormatOption.MONTH_DAY_YEAR,
            Locale.GERMAN,
        )
        val yearFirst = trendPoints(
            emptyList(),
            start to end,
            DateFormatOption.YEAR_MONTH_DAY,
            Locale.GERMAN,
        )

        assertEquals("$localizedDecember 25", monthFirst.points.first().axisLabel)
        assertEquals("25 $localizedDecember", yearFirst.points.first().axisLabel)
    }

    private fun entry(type: LedgerType, amountCents: Long, date: LocalDate) = LedgerEntryEntity(
        type = type,
        amountCents = amountCents,
        epochDay = date.toEpochDay(),
        minuteOfDay = 0,
    )
}
