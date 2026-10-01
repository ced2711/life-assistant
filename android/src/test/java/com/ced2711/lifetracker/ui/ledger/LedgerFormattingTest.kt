package com.ced2711.lifetracker.ui.ledger

import com.ced2711.lifetracker.domain.model.MAX_LEDGER_AMOUNT_CENTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerFormattingTest {
    @Test
    fun parsesManual12And24HourTimes() {
        assertEquals(570, parseLedgerTimeInput("9:30 AM"))
        assertEquals(1_290, parseLedgerTimeInput("9:30 pm"))
        assertEquals(1_290, parseLedgerTimeInput("21:30"))
        assertEquals(540, parseLedgerTimeInput("9 AM"))
    }

    @Test
    fun rejectsInvalidManualTimes() {
        assertNull(parseLedgerTimeInput(""))
        assertNull(parseLedgerTimeInput("25:00"))
        assertNull(parseLedgerTimeInput("9:99 PM"))
    }

    @Test
    fun formatsEditableTimeUsingSelectedClockConvention() {
        assertEquals("00:05", formatLedgerTimeInput(5, uses24HourTime = true))
        assertEquals("21:30", formatLedgerTimeInput(1_290, uses24HourTime = true))
        assertEquals("12:05 AM", formatLedgerTimeInput(5, uses24HourTime = false))
        assertEquals("9:30 PM", formatLedgerTimeInput(1_290, uses24HourTime = false))
    }

    @Test
    fun recurrenceIntervalHasNoSilentThreeDigitLimit() {
        assertEquals("1000", sanitizeRecurrenceIntervalInput("1000"))
        assertEquals(Int.MAX_VALUE.toString(), sanitizeRecurrenceIntervalInput(Int.MAX_VALUE.toString()))
        assertNull(recurrenceIntervalError(Int.MAX_VALUE.toString()))
    }

    @Test
    fun recurrenceIntervalExplainsZeroAndIntegerOverflow() {
        val expected = "Enter a whole number from 1 to 2,147,483,647"
        assertEquals(expected, recurrenceIntervalError("0"))
        assertEquals(expected, recurrenceIntervalError("2147483648"))
        assertNull(sanitizeRecurrenceIntervalInput("12 days"))
    }

    @Test
    fun acceptsMaximumLedgerAmount() {
        assertEquals(MAX_LEDGER_AMOUNT_CENTS, parseAmountCents("999999999.99"))
        assertEquals("999999999.99", sanitizeAmountInput("999999999.99"))
    }

    @Test
    fun rejectsAmountsAboveUiLimitOrTooLongForInput() {
        assertNull(parseAmountCents("1000000000.00"))
        assertNull(sanitizeAmountInput("1000000000"))
        assertNull(sanitizeAmountInput("0000000001"))
    }

    @Test
    fun rejectsNonFiniteScientificAndOverflowingAmounts() {
        assertNull(parseAmountCents("NaN"))
        assertNull(parseAmountCents("Infinity"))
        assertNull(parseAmountCents("1e9"))
        assertNull(parseAmountCents("999999999999999999999999999999999999"))
    }

    @Test
    fun attachmentPickerRequestsEveryFileType() {
        assertArrayEquals(arrayOf("*/*"), ledgerAttachmentMimeTypes())
    }
}
