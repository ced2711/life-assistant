package com.ced2711.lifetracker.ui.ledger

import com.ced2711.lifetracker.domain.model.MAX_LEDGER_AMOUNT_CENTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerStatisticsAxisTest {
    @Test
    fun addsTightHeadroomInsteadOfJumpingToTheNextWideScale() {
        assertEquals(100L, chartAxisMaximum(0L))
        assertEquals(110L, chartAxisMaximum(100L))
        assertEquals(354L, chartAxisMaximum(321L))
        assertEquals(13_580L, chartAxisMaximum(12_345L))

        val justOverTwoThousandDollars = 200_001L
        val axisMaximum = chartAxisMaximum(justOverTwoThousandDollars)
        assertEquals(220_002L, axisMaximum)
        assertTrue(axisMaximum < 500_000L)
    }

    @Test
    fun preservesHeadroomForExactAndFractionalDollarPeaks() {
        assertEquals(220_000L, chartAxisMaximum(200_000L))
        assertEquals(220_002L, chartAxisMaximum(200_000L + 1L))
        assertEquals(2L, chartAxisMaximum(1L))
    }

    @Test
    fun saturatesSafelyAtLongMaximum() {
        assertEquals(109_999_999_999L, chartAxisMaximum(MAX_LEDGER_AMOUNT_CENTS))
        assertEquals(Long.MAX_VALUE, chartAxisMaximum(Long.MAX_VALUE - 1L))
        assertEquals(Long.MAX_VALUE, chartAxisMaximum(Long.MAX_VALUE))
    }

    @Test
    fun formatsCompactDollarAxisLabels() {
        assertEquals("$0", formatCompactAxisMoney(0L))
        assertEquals("$12.34", formatCompactAxisMoney(1_234L))
        assertEquals("$1.5K", formatCompactAxisMoney(150_000L))
        assertEquals("$2.0M", formatCompactAxisMoney(200_000_000L))
        assertEquals("$1.0B", formatCompactAxisMoney(100_000_000_000L))
    }
}
