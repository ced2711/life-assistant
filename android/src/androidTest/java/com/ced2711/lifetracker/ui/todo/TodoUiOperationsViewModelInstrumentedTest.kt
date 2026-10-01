package com.ced2711.lifetracker.ui.todo

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ced2711.lifetracker.data.attachment.AttachmentDeletionToken
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.RecurringDeleteResult
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoUiOperationsViewModelInstrumentedTest {
    @Test
    fun processRecreationBeforeOwnerCallbackRestoresClientOperationToken() {
        val state = SavedStateHandle()
        val first = TodoUiOperationsViewModel(state)
        val token = first.clientOperationToken("todo-pre-callback", "same-draft")

        val restored = TodoUiOperationsViewModel(restoredHandle(state))

        assertEquals(token, restored.clientOperationToken("todo-pre-callback", "same-draft"))
        assertFalse(restored.hasSavedOwner("todo-pre-callback"))
        assertEquals(token, restored.clientOperationToken("todo-pre-callback", "changed-draft"))
        assertEquals(
            token,
            TodoUiOperationsViewModel(SavedStateHandle())
                .clientOperationToken("todo-pre-callback", "same-draft"),
        )
    }

    @Test
    fun processRecreationRestoresOwnerAndCopyRetryForSameRequest() {
        val state = SavedStateHandle()
        lateinit var first: TodoUiOperationsViewModel
        var firstCopyAttempt = ""
        onMain {
            first = TodoUiOperationsViewModel(state)
            val attempt = requireNotNull(first.beginSave("todo-restored"))
            first.clientOperationToken("todo-restored", "same-draft")
            firstCopyAttempt = first.attachmentCopyAttemptId(
                "todo-restored",
                listOf("content://files/one"),
            )
            first.markOwnerSaved("todo-restored", attempt, 41L, "same-draft")
            first.markSaveFailed("todo-restored", attempt)
        }

        val restored = TodoUiOperationsViewModel(restoredHandle(state))

        assertEquals(41L, restored.savedOwnerId("todo-restored", "same-draft"))
        assertEquals(
            firstCopyAttempt,
            restored.attachmentCopyAttemptId("todo-restored", listOf("content://files/one")),
        )
    }

    @Test
    fun duplicateClickStaysLockedAndSuccessClearsPersistedRecovery() {
        val state = SavedStateHandle()
        lateinit var operations: TodoUiOperationsViewModel
        onMain {
            operations = TodoUiOperationsViewModel(state)
            val attempt = requireNotNull(operations.beginSave("todo-double"))
            assertNull(operations.beginSave("todo-double"))
            operations.clientOperationToken("todo-double", "same-draft")
            operations.markOwnerSaved("todo-double", attempt, 42L, "same-draft")
            operations.markSaveSucceeded("todo-double", attempt)
        }

        assertFalse(TodoUiOperationsViewModel(restoredHandle(state)).hasSavedOwner("todo-double"))
    }

    @Test
    fun changedDraftKeepsOwnerWhileDeletedOwnerClearsRecovery() {
        val draftChangedState = SavedStateHandle()
        lateinit var draftChanged: TodoUiOperationsViewModel
        onMain {
            draftChanged = TodoUiOperationsViewModel(draftChangedState)
            val attempt = requireNotNull(draftChanged.beginSave("todo-changed"))
            draftChanged.clientOperationToken("todo-changed", "old-draft")
            draftChanged.attachmentCopyAttemptId("todo-changed", listOf("content://files/one"))
            draftChanged.markOwnerSaved("todo-changed", attempt, 43L, "old-draft")
            draftChanged.markSaveFailed("todo-changed", attempt)
        }
        val restoredChanged = TodoUiOperationsViewModel(restoredHandle(draftChangedState))
        assertEquals(43L, restoredChanged.savedOwnerId("todo-changed", "new-draft"))
        assertTrue(restoredChanged.hasSavedOwner("todo-changed"))

        val deletedState = SavedStateHandle()
        lateinit var deleted: TodoUiOperationsViewModel
        onMain {
            deleted = TodoUiOperationsViewModel(deletedState)
            val attempt = requireNotNull(deleted.beginSave("todo-deleted"))
            deleted.clientOperationToken("todo-deleted", "same-draft")
            deleted.markOwnerSaved("todo-deleted", attempt, 44L, "same-draft")
            deleted.markSaveFailed("todo-deleted", attempt)
        }
        val restoredDeleted = TodoUiOperationsViewModel(restoredHandle(deletedState))
        assertTrue(restoredDeleted.hasSavedOwner("todo-deleted"))
        restoredDeleted.rejectSavedOwner("todo-deleted", 44L)
        assertFalse(restoredDeleted.hasSavedOwner("todo-deleted"))
    }

    @Test
    fun changedUrisUseNewCopyAttemptButKeepRecoveredOwner() {
        val state = SavedStateHandle()
        lateinit var first: TodoUiOperationsViewModel
        var oldCopyAttempt = ""
        onMain {
            first = TodoUiOperationsViewModel(state)
            val attempt = requireNotNull(first.beginSave("todo-uris"))
            first.clientOperationToken("todo-uris", "same-draft")
            oldCopyAttempt = first.attachmentCopyAttemptId(
                "todo-uris",
                listOf("content://files/old"),
            )
            first.markOwnerSaved("todo-uris", attempt, 45L, "same-draft")
            first.markSaveFailed("todo-uris", attempt)
        }

        val restored = TodoUiOperationsViewModel(restoredHandle(state))
        val newCopyAttempt = restored.attachmentCopyAttemptId(
            "todo-uris",
            listOf("content://files/new"),
        )

        assertNotEquals(oldCopyAttempt, newCopyAttempt)
        assertEquals(45L, restored.savedOwnerId("todo-uris", "same-draft"))
    }

    @Test
    fun deleteReceiptKeepsOriginalElapsedDeadlineAcrossRotationAndConsumesOnce() {
        val operations = TodoUiOperationsViewModel(SavedStateHandle())
        val item = PendingTodoDelete(
            title = "Todo",
            includedFuture = false,
            receipt = deleteReceipt(undoExpiresAtElapsedRealtime = 10_000L),
        )

        onMain {
            operations.publishDelete(item, nowElapsedRealtimeMillis = 4_000L)
            assertEquals(listOf(item), operations.pendingDeletes)

            // Activity recreation retains this ViewModel; the absolute elapsed deadline is unchanged.
            operations.publishDelete(item, nowElapsedRealtimeMillis = 8_000L)
            assertEquals(10_000L, operations.pendingDeletes.single().receipt.undoExpiresAtElapsedRealtime)

            operations.consumeDelete(item)
            operations.consumeDelete(item)
            assertTrue(operations.pendingDeletes.isEmpty())
        }
    }

    @Test
    fun expiredDeleteReceiptIsNotPublishedOrRestoredAfterProcessDeath() {
        val state = SavedStateHandle()
        val first = TodoUiOperationsViewModel(state)
        val item = PendingTodoDelete(
            title = "Todo",
            includedFuture = false,
            receipt = deleteReceipt(undoExpiresAtElapsedRealtime = 10_000L),
        )

        onMain {
            first.publishDelete(item, nowElapsedRealtimeMillis = 9_000L)
            assertEquals(listOf(item), first.pendingDeletes)
            assertTrue(TodoUiOperationsViewModel(restoredHandle(state)).pendingDeletes.isEmpty())
            first.consumeDelete(item)

            first.publishDelete(item, nowElapsedRealtimeMillis = 10_000L)
            assertTrue(first.pendingDeletes.isEmpty())
        }
    }

    @Test
    fun attachmentUndoAvailabilityUsesInjectedElapsedRealtimeInsteadOfWallClock() {
        val operations = TodoUiOperationsViewModel(SavedStateHandle())
        val item = PendingTodoAttachmentDelete(
            originalName = "receipt.jpg",
            token = attachmentDeleteToken(
                expiresAtMillis = 1L,
                undoExpiresAtElapsedRealtime = 10_000L,
            ),
        )

        onMain {
            operations.publishAttachmentDelete(item, nowElapsedRealtimeMillis = 9_000L)
            assertEquals(listOf(item), operations.pendingAttachmentDeletes)
            operations.consumeAttachmentDelete(item)

            operations.publishAttachmentDelete(item, nowElapsedRealtimeMillis = 10_000L)
            assertTrue(operations.pendingAttachmentDeletes.isEmpty())
        }
    }

    @Test
    fun resumePrunesExpiredAttachmentReceiptAndReschedulesTheNextAbsoluteDeadline() {
        var nowElapsedRealtime = 1_000L
        val scheduler = RecordingExpiryScheduler()
        val operations = TodoUiOperationsViewModel(
            savedStateHandle = SavedStateHandle(),
            elapsedRealtimeMillis = { nowElapsedRealtime },
            expiryScheduler = scheduler::schedule,
        )
        val expiredAfterSleep = PendingTodoAttachmentDelete(
            originalName = "old.jpg",
            token = attachmentDeleteToken(
                expiresAtMillis = Long.MAX_VALUE,
                undoExpiresAtElapsedRealtime = 2_000L,
                attachmentId = 11L,
            ),
        )
        val stillAvailable = PendingTodoAttachmentDelete(
            originalName = "new.jpg",
            token = attachmentDeleteToken(
                expiresAtMillis = Long.MIN_VALUE,
                undoExpiresAtElapsedRealtime = 4_000L,
                attachmentId = 12L,
            ),
        )

        onMain {
            operations.publishAttachmentDelete(expiredAfterSleep)
            operations.publishAttachmentDelete(stillAvailable)
            assertEquals(listOf(expiredAfterSleep, stillAvailable), operations.pendingAttachmentDeletes)

            val jobsBeforeResume = scheduler.activeJobs()
            nowElapsedRealtime = 2_500L
            operations.reconcileUndoWindows()

            assertTrue(jobsBeforeResume.all(Job::isCancelled))
            assertEquals(listOf(stillAvailable), operations.pendingAttachmentDeletes)
            assertEquals(listOf(1_500L), scheduler.activeDelays())

            nowElapsedRealtime = 4_000L
            operations.reconcileUndoWindows()
            assertTrue(operations.pendingAttachmentDeletes.isEmpty())
            assertTrue(scheduler.activeDelays().isEmpty())
        }
    }

    private fun deleteReceipt(undoExpiresAtElapsedRealtime: Long) = RecurringDeleteResult(
        ownerType = AttachmentOwnerType.TODO,
        itemId = 7L,
        scope = SeriesEditScope.ONLY_THIS_OCCURRENCE,
        seriesId = null,
        boundaryEpochDay = null,
        seriesWasActive = false,
        seriesUpdatedAt = null,
        deletedAt = 123L,
        undoExpiresAtElapsedRealtime = undoExpiresAtElapsedRealtime,
        attachmentsPendingAt = Long.MAX_VALUE,
        exceptionCreated = false,
    )

    private fun attachmentDeleteToken(
        expiresAtMillis: Long,
        undoExpiresAtElapsedRealtime: Long,
        attachmentId: Long = 11L,
    ) = AttachmentDeletionToken(
        attachmentId = attachmentId,
        ownerType = AttachmentOwnerType.TODO,
        ownerId = 7L,
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
