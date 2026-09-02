package com.ced2711.lifetracker.ui.ledger

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ced2711.lifetracker.data.attachment.AttachmentDeletionToken
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.RecurringDeleteResult
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerUiOperationsViewModelInstrumentedTest {
    @Test
    fun deleteReceiptKeepsOriginalElapsedDeadlineAcrossRotationAndConsumesOnce() {
        val operations = LedgerUiOperationsViewModel(SavedStateHandle())
        val item = PendingLedgerDelete(
            title = "Entry",
            receipt = deleteReceipt(undoExpiresAtElapsedRealtime = 20_000L),
        )

        onMain {
            assertTrue(operations.beginDelete(8L))
            operations.publishDelete(8L, item, nowElapsedRealtimeMillis = 12_000L)
            assertEquals(listOf(item), operations.pendingDeletes)

            // Activity recreation retains this ViewModel; the absolute elapsed deadline is unchanged.
            operations.publishDelete(8L, item, nowElapsedRealtimeMillis = 18_000L)
            assertEquals(20_000L, operations.pendingDeletes.single().receipt.undoExpiresAtElapsedRealtime)

            operations.consumeDelete(item)
            operations.consumeDelete(item)
            assertTrue(operations.pendingDeletes.isEmpty())
        }
    }

    @Test
    fun expiredDeleteReceiptIsNotPublishedOrRestoredAfterProcessDeath() {
        val state = SavedStateHandle()
        val first = LedgerUiOperationsViewModel(state)
        val item = PendingLedgerDelete(
            title = "Entry",
            receipt = deleteReceipt(undoExpiresAtElapsedRealtime = 20_000L),
        )

        onMain {
            first.publishDelete(8L, item, nowElapsedRealtimeMillis = 19_000L)
            assertEquals(listOf(item), first.pendingDeletes)
            assertTrue(LedgerUiOperationsViewModel(restoredHandle(state)).pendingDeletes.isEmpty())
            first.consumeDelete(item)

            first.publishDelete(8L, item, nowElapsedRealtimeMillis = 20_000L)
            assertTrue(first.pendingDeletes.isEmpty())
        }
    }

    @Test
    fun attachmentUndoAvailabilityUsesInjectedElapsedRealtimeInsteadOfWallClock() {
        val operations = LedgerUiOperationsViewModel(SavedStateHandle())
        val item = PendingLedgerAttachmentDelete(
            originalName = "receipt.jpg",
            token = attachmentDeleteToken(
                expiresAtMillis = 1L,
                undoExpiresAtElapsedRealtime = 20_000L,
            ),
        )

        onMain {
            operations.publishAttachmentDelete(item, nowElapsedRealtimeMillis = 19_000L)
            assertEquals(listOf(item), operations.pendingAttachmentDeletes)
            operations.consumeAttachmentDelete(item)

            operations.publishAttachmentDelete(item, nowElapsedRealtimeMillis = 20_000L)
            assertTrue(operations.pendingAttachmentDeletes.isEmpty())
        }
    }

    @Test
    fun resumePrunesExpiredAttachmentReceiptAndReschedulesTheNextAbsoluteDeadline() {
        var nowElapsedRealtime = 10_000L
        val scheduler = RecordingExpiryScheduler()
        val operations = LedgerUiOperationsViewModel(
            savedStateHandle = SavedStateHandle(),
            elapsedRealtimeMillis = { nowElapsedRealtime },
            expiryScheduler = scheduler::schedule,
        )
        val expiredAfterSleep = PendingLedgerAttachmentDelete(
            originalName = "old.jpg",
            token = attachmentDeleteToken(
                expiresAtMillis = Long.MAX_VALUE,
                undoExpiresAtElapsedRealtime = 11_000L,
                attachmentId = 12L,
            ),
        )
        val stillAvailable = PendingLedgerAttachmentDelete(
            originalName = "new.jpg",
            token = attachmentDeleteToken(
                expiresAtMillis = Long.MIN_VALUE,
                undoExpiresAtElapsedRealtime = 14_000L,
                attachmentId = 13L,
            ),
        )

        onMain {
            operations.publishAttachmentDelete(expiredAfterSleep)
            operations.publishAttachmentDelete(stillAvailable)
            assertEquals(listOf(expiredAfterSleep, stillAvailable), operations.pendingAttachmentDeletes)

            val jobsBeforeResume = scheduler.activeJobs()
            nowElapsedRealtime = 11_500L
            operations.reconcileUndoWindows()

            assertTrue(jobsBeforeResume.all(Job::isCancelled))
            assertEquals(listOf(stillAvailable), operations.pendingAttachmentDeletes)
            assertEquals(listOf(2_500L), scheduler.activeDelays())

            nowElapsedRealtime = 14_000L
            operations.reconcileUndoWindows()
            assertTrue(operations.pendingAttachmentDeletes.isEmpty())
            assertTrue(scheduler.activeDelays().isEmpty())
        }
    }

    private fun deleteReceipt(undoExpiresAtElapsedRealtime: Long) = RecurringDeleteResult(
        ownerType = AttachmentOwnerType.LEDGER,
        itemId = 8L,
        scope = SeriesEditScope.ONLY_THIS_OCCURRENCE,
        seriesId = null,
        boundaryEpochDay = null,
        seriesWasActive = false,
        seriesUpdatedAt = null,
        deletedAt = 456L,
        undoExpiresAtElapsedRealtime = undoExpiresAtElapsedRealtime,
        attachmentsPendingAt = Long.MAX_VALUE,
        exceptionCreated = false,
    )

    private fun attachmentDeleteToken(
        expiresAtMillis: Long,
        undoExpiresAtElapsedRealtime: Long,
        attachmentId: Long = 12L,
    ) = AttachmentDeletionToken(
        attachmentId = attachmentId,
        ownerType = AttachmentOwnerType.LEDGER,
        ownerId = 8L,
        expiresAtMillis = expiresAtMillis,
        pendingDeleteAtMillis = 2L,
        undoExpiresAtElapsedRealtime = undoExpiresAtElapsedRealtime,
    )

    private class RecordingExpiryScheduler {
        private data class ScheduledTask(
            val delayMillis: Long,
            val job: Job,
        )

        private val tasks = mutableListOf<ScheduledTask>()

        fun schedule(delayMillis: Long, onExpired: () -> Unit): Job = Job().also { job ->
            @Suppress("UNUSED_VARIABLE")
            val retainedCallback = onExpired
            tasks += ScheduledTask(delayMillis, job)
        }

        fun activeJobs(): List<Job> = tasks.map(ScheduledTask::job).filterNot(Job::isCancelled)

        fun activeDelays(): List<Long> = tasks
            .filterNot { it.job.isCancelled }
            .map(ScheduledTask::delayMillis)
    }

    private fun restoredHandle(source: SavedStateHandle): SavedStateHandle = SavedStateHandle(
        source.keys().associateWith { key -> source.get<Any?>(key) },
    )

    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }
}
