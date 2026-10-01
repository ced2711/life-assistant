package com.ced2711.lifetracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ced2711.lifetracker.data.local.LedgerSeriesEntity
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.MAX_LEDGER_AMOUNT_CENTS
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerAmountValidationIntegrationTest {
    private lateinit var database: TaskLedgerDatabase
    private lateinit var repository: TaskLedgerRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TaskLedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TaskLedgerRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun standaloneCreateAcceptsMaximumAndUpdateRejectsOverLimit() = runBlocking {
        val today = LocalDate.of(2026, 8, 18).toEpochDay()
        val entryId = requireNotNull(
            repository.saveLedgerWithResult(
                LedgerDraft(
                    amountCents = MAX_LEDGER_AMOUNT_CENTS,
                    epochDay = today,
                    minuteOfDay = 0,
                ),
                throughEpochDay = today,
            ).entryId,
        )

        val failure = runCatching {
            repository.saveLedgerWithResult(
                LedgerDraft(
                    id = entryId,
                    amountCents = MAX_LEDGER_AMOUNT_CENTS + 1L,
                    epochDay = today,
                    minuteOfDay = 0,
                ),
                throughEpochDay = today,
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(MAX_LEDGER_AMOUNT_CENTS, database.dao().getLedgerEntry(entryId)?.amountCents)
    }

    @Test
    fun recurringRuleCreateAndFutureEditRejectOverLimitBeforePersistence() = runBlocking {
        val dao = database.dao()
        val today = LocalDate.of(2026, 8, 18).toEpochDay()
        val invalidDraft = LedgerDraft(
            amountCents = MAX_LEDGER_AMOUNT_CENTS + 1L,
            epochDay = today,
            minuteOfDay = 0,
            recurrence = RecurrenceRule(RecurrenceUnit.DAY),
        )

        val createFailure = runCatching {
            repository.saveLedgerWithResult(invalidDraft, throughEpochDay = today)
        }.exceptionOrNull()

        assertTrue(createFailure is IllegalArgumentException)
        assertTrue(dao.observeLedgerSeries().first().isEmpty())

        val originalSeriesId = dao.insertLedgerSeries(
            LedgerSeriesEntity(
                type = LedgerType.EXPENSE,
                amountCents = 500L,
                startEpochDay = today,
                recurrenceUnit = RecurrenceUnit.DAY,
            ),
        )
        val editFailure = runCatching {
            repository.editLedgerSeriesForFuture(
                seriesId = originalSeriesId,
                effectiveEpochDay = today + 1L,
                draft = invalidDraft.copy(epochDay = today + 1L),
                throughEpochDay = today,
            )
        }.exceptionOrNull()

        assertTrue(editFailure is IllegalArgumentException)
        assertTrue(requireNotNull(dao.getLedgerSeries(originalSeriesId)).active)
        assertEquals(1, dao.observeLedgerSeries().first().size)
    }

    @Test
    fun recurringGenerationRejectsInvalidPersistedAmountWithoutCreatingEntries() = runBlocking {
        val dao = database.dao()
        val today = LocalDate.of(2026, 8, 18).toEpochDay()
        val seriesId = dao.insertLedgerSeries(
            LedgerSeriesEntity(
                type = LedgerType.EXPENSE,
                amountCents = MAX_LEDGER_AMOUNT_CENTS + 1L,
                startEpochDay = today,
                recurrenceUnit = RecurrenceUnit.DAY,
            ),
        )

        val failure = runCatching { repository.catchUpRecurring(today) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(dao.observeLedgerEntries().first().isEmpty())
        assertNotNull(dao.getLedgerSeries(seriesId))
    }

    @Test
    fun recurringGenerationAcceptsMaximumAmount() = runBlocking {
        val dao = database.dao()
        val today = LocalDate.of(2026, 8, 18).toEpochDay()
        val seriesId = dao.insertLedgerSeries(
            LedgerSeriesEntity(
                type = LedgerType.INCOME,
                amountCents = MAX_LEDGER_AMOUNT_CENTS,
                startEpochDay = today,
                recurrenceUnit = RecurrenceUnit.DAY,
            ),
        )

        repository.catchUpRecurring(today)

        assertEquals(
            MAX_LEDGER_AMOUNT_CENTS,
            requireNotNull(dao.getLedgerOccurrence(seriesId, today)).amountCents,
        )
    }
}
