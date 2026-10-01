package com.ced2711.lifetracker.ui.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerStatisticsInteractionTest {
    @Test
    fun hitTestingIncludesFirstAndLastBuckets() {
        assertEquals(0, trendPointIndexAtX(54f, 54f, 300f, 3))
        assertEquals(0, trendPointIndexAtX(153.99f, 54f, 300f, 3))
        assertEquals(1, trendPointIndexAtX(154f, 54f, 300f, 3))
        assertEquals(2, trendPointIndexAtX(354f, 54f, 300f, 3))
    }

    @Test
    fun hitTestingRejectsOutOfBoundsAndInvalidCharts() {
        assertNull(trendPointIndexAtX(53.99f, 54f, 300f, 3))
        assertNull(trendPointIndexAtX(354.01f, 54f, 300f, 3))
        assertNull(trendPointIndexAtX(Float.NaN, 54f, 300f, 3))
        assertNull(trendPointIndexAtX(54f, 54f, 0f, 3))
        assertNull(trendPointIndexAtX(54f, 54f, 300f, 0))
    }

    @Test
    fun singleBucketOwnsTheWholeChartWidth() {
        assertEquals(0, trendPointIndexAtX(10f, 10f, 120f, 1))
        assertEquals(0, trendPointIndexAtX(75f, 10f, 120f, 1))
        assertEquals(0, trendPointIndexAtX(130f, 10f, 120f, 1))
    }

    @Test
    fun hitTestingRemainsPreciseWithOneHundredTwentyBuckets() {
        assertEquals(0, trendPointIndexAtX(15f, 10f, 1_200f, 120))
        assertEquals(73, trendPointIndexAtX(745f, 10f, 1_200f, 120))
        assertEquals(119, trendPointIndexAtX(1_205f, 10f, 1_200f, 120))
        assertEquals(119, trendPointIndexAtX(1_210f, 10f, 1_200f, 120))
    }

    @Test
    fun summaryUsesExactMoneyFormattingIncludingZeroValues() {
        val points = listOf(
            TrendPoint(incomeCents = 0L, expenseCents = 0L, axisLabel = "Aug 20"),
            TrendPoint(incomeCents = 123_456L, expenseCents = 78_901L, axisLabel = "Aug 21"),
        )

        assertEquals(
            TrendSelectionSummary(
                label = "Aug 20",
                incomeText = "Income $0.00",
                expensesText = "Expenses $0.00",
            ),
            trendSelectionSummary(points, 0),
        )
        assertEquals(
            "Aug 21. Income $1,234.56. Expenses $789.01.",
            trendSelectionSummary(points, 1)?.accessibilityDescription,
        )
        assertNull(trendSelectionSummary(points, -1))
        assertNull(trendSelectionSummary(points, points.size))
    }
}
