package com.ced2711.lifetracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoppedLedgerSeriesDeletionTest {
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
    fun closeDatabase() = database.close()

    @Test
    fun deletingAStoppedScheduleKeepsItsEntriesAsOrdinaryEntries() = runBlocking {
        val today = LocalDate.now().toEpochDay()
        val result = repository.saveLedgerWithResult(
            LedgerDraft(
                type = LedgerType.EXPENSE,
                amountCents = 1_250,
                epochDay = today - 3,
                minuteOfDay = 720,
                merchant = "Gym",
                recurrence = RecurrenceRule(RecurrenceUnit.DAY),
            ),
        )
        val seriesId = requireNotNull(result.seriesId)
        repository.catchUpRecurring(today)
        val created = database.dao().observeLedgerEntries().first().filter { it.seriesId == seriesId }
        assertEquals(4, created.size)

        // A running schedule cannot be deleted; it has to be stopped first.
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.deleteStoppedLedgerSeries(seriesId) }
        }
        repository.deactivateLedgerSeries(seriesId)
        repository.deleteStoppedLedgerSeries(seriesId)

        assertNull(database.dao().getLedgerSeries(seriesId))
        val kept = database.dao().observeLedgerEntries().first().filter { it.id in created.map { e -> e.id } }
        assertEquals(created.map { it.id }.toSet(), kept.map { it.id }.toSet())
        assertTrue(kept.all { it.seriesId == null && it.occurrenceEpochDay == null && it.merchant == "Gym" })
        assertTrue(kept.all { it.updatedAt >= it.createdAt })

        // Catching up again must not recreate anything for the deleted schedule.
        repository.catchUpRecurring(today + 5)
        assertEquals(4, database.dao().observeLedgerEntries().first().size)
    }
}
