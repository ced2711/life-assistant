package com.ced2711.lifetracker.data.repository

import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerSaveValidationTest {
    private val today = LocalDate.of(2026, 8, 18).toEpochDay()

    @Test
    fun `standalone entry cannot become a future recurring rule`() {
        val failure = runCatching {
            validateExistingLedgerUpdate(
                existing = entry(),
                draft = draft(today + 1, recurring = true),
                todayEpochDay = today,
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `standalone entry can start recurring today`() {
        validateExistingLedgerUpdate(
            existing = entry(),
            draft = draft(today, recurring = true),
            todayEpochDay = today,
        )
    }

    @Test
    fun `generated occurrence remains independently editable on a future date`() {
        validateExistingLedgerUpdate(
            existing = entry(seriesId = 9L),
            draft = draft(today + 1, recurring = true),
            todayEpochDay = today,
        )
    }

    @Test
    fun `soft deleted entry cannot be updated`() {
        val failure = runCatching {
            validateExistingLedgerUpdate(
                existing = entry(deletedAt = 1_000L),
                draft = draft(today, recurring = false),
                todayEpochDay = today,
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
    }

    private fun entry(
        seriesId: Long? = null,
        deletedAt: Long? = null,
    ) = LedgerEntryEntity(
        id = 1L,
        seriesId = seriesId,
        occurrenceEpochDay = seriesId?.let { today },
        type = LedgerType.EXPENSE,
        amountCents = 500L,
        epochDay = today,
        minuteOfDay = 0,
        deletedAt = deletedAt,
    )

    private fun draft(epochDay: Long, recurring: Boolean) = LedgerDraft(
        id = 1L,
        amountCents = 500L,
        epochDay = epochDay,
        minuteOfDay = 0,
        recurrence = if (recurring) RecurrenceRule(RecurrenceUnit.DAY) else null,
    )
}
