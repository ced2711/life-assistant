package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.data.backup.validate
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLedgerOpsTest {
    private val empty = DesktopDataStore.defaultSnapshot(now = 1_000)
    private val today = 20_100L

    private fun draft(day: Long, cents: Long = 1_500, recurrence: RecurrenceRule? = null, id: Long? = null) =
        LedgerDraft(id = id, type = LedgerType.EXPENSE, amountCents = cents, epochDay = day, minuteOfDay = 12 * 60, merchant = "Gym", recurrence = recurrence)

    private fun save(snapshot: BackupSnapshot, draft: LedgerDraft, scope: SeriesEditScope = SeriesEditScope.ONLY_THIS_OCCURRENCE, now: Long = 2_000) =
        DesktopLedgerOps.save(snapshot, draft, scope, today, now).let { (s, id) -> s.validate() to id }

    @Test
    fun aSingleEntryKeepsItsTime() {
        val (snapshot, id) = save(empty, draft(today))
        assertEquals(720, snapshot.ledgerEntries.single { it.id == id }.minuteOfDay)
    }

    @Test
    fun aScheduleCreatesItsPastAndTodaysEntries() {
        val (snapshot, id) = save(empty, draft(today - 14, recurrence = RecurrenceRule(RecurrenceUnit.WEEK)))
        assertEquals(listOf(today - 14, today - 7, today), snapshot.ledgerEntries.sortedBy { it.epochDay }.map { it.epochDay })
        assertEquals(today - 14, snapshot.ledgerEntries.single { it.id == id }.occurrenceEpochDay)
    }

    @Test
    fun aScheduleStartingLaterCreatesNothingYet() {
        val (snapshot, id) = save(empty, draft(today + 3, recurrence = RecurrenceRule(RecurrenceUnit.MONTH)))
        assertNull(id)
        assertTrue(snapshot.ledgerEntries.isEmpty())
        assertEquals(1, snapshot.ledgerSeries.size)
    }

    @Test
    fun deletingAnOccurrenceKeepsItFromComingBack() {
        val (snapshot, _) = save(empty, draft(today - 2, recurrence = RecurrenceRule(RecurrenceUnit.DAY)))
        val last = snapshot.ledgerEntries.single { it.epochDay == today }
        val (deleted, deletion) = DesktopLedgerOps.delete(snapshot, last.id, SeriesEditScope.ONLY_THIS_OCCURRENCE, 3_000)
        assertTrue(deletion.exceptionCreated)
        val again = DesktopTodoOps.materialize(DesktopTodoOps.purgeDeleted(deleted, 100_000), today, 200_000)
        assertTrue(again.ledgerEntries.none { it.epochDay == today })
    }

    @Test
    fun editingThisAndLaterReplacesTheSchedule() {
        val (snapshot, _) = save(empty, draft(today - 2, recurrence = RecurrenceRule(RecurrenceUnit.DAY)))
        val middle = snapshot.ledgerEntries.single { it.epochDay == today - 1 }
        val (edited, _) = save(snapshot, draft(today - 1, cents = 2_000, recurrence = RecurrenceRule(RecurrenceUnit.DAY), id = middle.id), SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES, 3_000)
        val live = edited.ledgerEntries.filter { it.deletedAt == null }.sortedBy { it.epochDay }
        assertEquals(listOf(1_500L, 2_000L, 2_000L), live.map { it.amountCents })
        assertEquals(1, edited.ledgerSeries.count { it.active })
    }

    @Test
    fun editingOnlyOneOccurrenceWithoutRepeatDetachesIt() {
        val (snapshot, id) = save(empty, draft(today, recurrence = RecurrenceRule(RecurrenceUnit.MONTH)))
        val (edited, _) = save(snapshot, draft(today, cents = 999, id = id), now = 3_000)
        assertNull(edited.ledgerEntries.single { it.id == id }.seriesId)
        assertEquals(listOf(today), edited.ledgerOccurrenceExceptions.map { it.occurrenceEpochDay })
    }

    @Test
    fun undoRestoresAWholeDeletedTail() {
        val (snapshot, _) = save(empty, draft(today - 3, recurrence = RecurrenceRule(RecurrenceUnit.DAY)))
        val second = snapshot.ledgerEntries.single { it.epochDay == today - 2 }
        val (deleted, deletion) = DesktopLedgerOps.delete(snapshot, second.id, SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES, 3_000)
        assertEquals(3, deletion.entryIds.size)
        val undone = DesktopLedgerOps.undoDelete(deleted, deletion, 4_000).validate()
        assertTrue(undone.ledgerEntries.all { it.deletedAt == null })
        assertTrue(undone.ledgerSeries.single().active)
    }

    @Test
    fun aStoppedScheduleCanBeRemovedAndItsEntriesStay() {
        val (snapshot, _) = save(empty, draft(today - 1, recurrence = RecurrenceRule(RecurrenceUnit.DAY)))
        val seriesId = snapshot.ledgerSeries.single().id
        val removed = DesktopLedgerOps.deleteStoppedSeries(DesktopLedgerOps.stopSeries(snapshot, seriesId, 3_000), seriesId, 4_000).validate()
        assertTrue(removed.ledgerSeries.isEmpty())
        assertEquals(2, removed.ledgerEntries.size)
        assertTrue(removed.ledgerEntries.all { it.seriesId == null })
    }

    @Test
    fun aScheduleCanBeChangedFromAFutureDay() {
        val (snapshot, _) = save(empty, draft(today - 1, recurrence = RecurrenceRule(RecurrenceUnit.MONTH)))
        val seriesId = snapshot.ledgerSeries.single().id
        val changed = DesktopLedgerOps.editSeriesForFuture(snapshot, seriesId, draft(today + 30, cents = 4_000, recurrence = RecurrenceRule(RecurrenceUnit.MONTH)), today, 3_000).validate()
        assertEquals(listOf(false, true), changed.ledgerSeries.sortedBy { it.id }.map { it.active })
        assertEquals(1, changed.ledgerEntries.size)
    }
}
