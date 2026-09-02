package com.ced2711.lifetracker.ui.ledger

import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.date.SmartDateParser
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerEditorValidationTest {
    private val today = LocalDate.of(2026, 8, 18).toEpochDay()

    @Test
    fun `blocks existing standalone entry converted to future recurrence`() {
        assertTrue(
            isFutureRecurrenceConversionBlocked(
                initialDraft = draft(id = 7L),
                recurringEnabled = true,
                selectedEpochDay = today + 1,
                todayEpochDay = today,
            ),
        )
    }

    @Test
    fun `allows current day conversion and new future recurring rules`() {
        assertFalse(
            isFutureRecurrenceConversionBlocked(
                initialDraft = draft(id = 7L),
                recurringEnabled = true,
                selectedEpochDay = today,
                todayEpochDay = today,
            ),
        )
        assertFalse(
            isFutureRecurrenceConversionBlocked(
                initialDraft = draft(id = null),
                recurringEnabled = true,
                selectedEpochDay = today + 1,
                todayEpochDay = today,
            ),
        )
    }

    @Test
    fun `allows an existing generated occurrence to move independently`() {
        assertFalse(
            isFutureRecurrenceConversionBlocked(
                initialDraft = draft(id = 7L, recurring = true),
                recurringEnabled = true,
                selectedEpochDay = today + 1,
                todayEpochDay = today,
            ),
        )
    }

    @Test
    fun `default recurrence end stays inside four digit editor range`() {
        assertEquals(
            SmartDateParser.MAX_SUPPORTED_EPOCH_DAY,
            defaultLedgerRecurrenceEndEpochDay(SmartDateParser.MAX_SUPPORTED_EPOCH_DAY),
        )
        assertEquals(
            SmartDateParser.MAX_SUPPORTED_EPOCH_DAY,
            defaultLedgerRecurrenceEndEpochDay(LocalDate.of(9999, 12, 1).toEpochDay()),
        )
        assertEquals(
            LocalDate.of(2026, 9, 18).toEpochDay(),
            defaultLedgerRecurrenceEndEpochDay(today),
        )
    }

    private fun draft(id: Long?, recurring: Boolean = false) = LedgerDraft(
        id = id,
        amountCents = 500L,
        epochDay = today,
        minuteOfDay = 0,
        recurrence = if (recurring) RecurrenceRule(RecurrenceUnit.DAY) else null,
    )
}
