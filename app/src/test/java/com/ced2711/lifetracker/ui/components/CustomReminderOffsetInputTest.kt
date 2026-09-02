package com.ced2711.lifetracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomReminderOffsetInputTest {
    @Test
    fun convertsSupportedUnitsToMinutes() {
        assertEquals(120L, customReminderOffsetMinutes("2", ReminderOffsetUnit.HOURS))
        assertEquals(4_320L, customReminderOffsetMinutes("3", ReminderOffsetUnit.DAYS))
        assertEquals(20_160L, customReminderOffsetMinutes("2", ReminderOffsetUnit.WEEKS))
    }

    @Test
    fun rejectsZeroNegativeMalformedAndBeyondLookahead() {
        assertNull(customReminderOffsetMinutes("0", ReminderOffsetUnit.HOURS))
        assertNull(customReminderOffsetMinutes("-2", ReminderOffsetUnit.DAYS))
        assertNull(customReminderOffsetMinutes("two", ReminderOffsetUnit.WEEKS))
        assertNull(customReminderOffsetMinutes("367", ReminderOffsetUnit.DAYS))
        assertNull(customReminderOffsetMinutes("53", ReminderOffsetUnit.WEEKS))
    }

    @Test
    fun acceptsExactMaximum() {
        assertEquals(
            MAX_REMINDER_OFFSET_MINUTES,
            customReminderOffsetMinutes("366", ReminderOffsetUnit.DAYS),
        )
    }

    @Test
    fun formatsCustomOffsetsReadably() {
        assertEquals("2 hours before", formatReminderOffset(120L))
        assertEquals("2 days before", formatReminderOffset(2_880L))
        assertEquals("2 weeks before", formatReminderOffset(20_160L))
    }
}
