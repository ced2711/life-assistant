package com.ced2711.lifetracker.domain.date

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartDateParserTest {
    private val today = LocalDate.of(2026, 8, 18)

    @Test
    fun `day only uses this month when day has not passed`() {
        assertEquals(LocalDate.of(2026, 8, 31), SmartDateParser.parse("31", today))
        assertEquals(today, SmartDateParser.parse("18", today))
    }

    @Test
    fun `day only uses next month when day has passed`() {
        assertEquals(LocalDate.of(2026, 9, 15), SmartDateParser.parse("15", today))
    }

    @Test
    fun `day only rolls into next year from December`() {
        val december = LocalDate.of(2026, 12, 20)

        assertEquals(LocalDate.of(2027, 1, 15), SmartDateParser.parse("15", december))
    }

    @Test
    fun `day only rejects a day absent from the selected month`() {
        val january31 = LocalDate.of(2026, 1, 31)

        assertNull(SmartDateParser.parse("30", january31))
        assertNull(SmartDateParser.parse("0", today))
        assertNull(SmartDateParser.parse("32", today))
    }

    @Test
    fun `month and day uses this year unless date has passed`() {
        assertEquals(LocalDate.of(2026, 8, 20), SmartDateParser.parse("8/20", today))
        assertEquals(today, SmartDateParser.parse("8/18", today))
        assertEquals(LocalDate.of(2027, 8, 17), SmartDateParser.parse("8/17", today))
    }

    @Test
    fun `month and day rejects invalid dates instead of correcting them`() {
        assertNull(SmartDateParser.parse("2/29", today))
        assertNull(SmartDateParser.parse("4/31", today))
        assertNull(SmartDateParser.parse("13/1", today))
    }

    @Test
    fun `full date parses regardless of whether it is in the past`() {
        assertEquals(LocalDate.of(2025, 1, 2), SmartDateParser.parse("1/2/2025", today))
        assertEquals(LocalDate.of(2028, 2, 29), SmartDateParser.parse("02/29/2028", today))
    }

    @Test
    fun `full date strictly rejects invalid or unsupported input`() {
        assertNull(SmartDateParser.parse("2/29/2026", today))
        assertNull(SmartDateParser.parse("4/31/2026", today))
        assertNull(SmartDateParser.parse("8-20-2026", today))
        assertNull(SmartDateParser.parse("8/20/26", today))
        assertNull(SmartDateParser.parse("8 / 20 / 2026", today))
        assertNull(SmartDateParser.parse("", today))
        assertNull(SmartDateParser.parse("1/1/0000", today))
    }

    @Test
    fun `epoch day overload returns epoch day`() {
        val parsed = SmartDateParser.parse("15", today.toEpochDay())

        assertEquals(LocalDate.of(2026, 9, 15).toEpochDay(), parsed)
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals(LocalDate.of(2026, 8, 20), SmartDateParser.parse("  8/20  ", today))
    }

    @Test
    fun `shortcuts do not roll beyond the four digit year range`() {
        val finalSupportedDay = LocalDate.of(9999, 12, 31)

        assertNull(SmartDateParser.parse("1", finalSupportedDay))
        assertNull(SmartDateParser.parse("1/1", finalSupportedDay))
    }
}
