package com.ced2711.lifetracker.ui.ledger

import androidx.lifecycle.SavedStateHandle
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerUiOperationsViewModelTest {
    @Test
    fun activeSaveRemainsLockedUntilRetainedCallbackCompletes() {
        val operations = LedgerUiOperationsViewModel(SavedStateHandle())
        val attempt = requireNotNull(operations.beginSave("editor-a"))

        assertEquals("editor-a", operations.savingSessionKey)
        assertNull(operations.beginSave("editor-a"))

        operations.markSaveSucceeded("editor-a", attempt)

        assertNull(operations.savingSessionKey)
        assertTrue("editor-a" in operations.savedSessionKeys)
        operations.consumeSaved("editor-a")
        assertFalse("editor-a" in operations.savedSessionKeys)
    }

    @Test
    fun attachmentFailureRetainsOwnerForSameRecordRetry() {
        val operations = LedgerUiOperationsViewModel(SavedStateHandle())
        val firstAttempt = requireNotNull(operations.beginSave("editor-b"))
        operations.clientOperationToken("editor-b", "draft-b")
        operations.markOwnerSaved("editor-b", firstAttempt, ownerId = 42L, ownerRequest = "draft-b")
        operations.markSaveFailed("editor-b", firstAttempt, "Copy failed")

        assertNull(operations.savingSessionKey)
        assertEquals(42L, operations.savedOwnerId("editor-b", "draft-b"))
        assertEquals("Copy failed", operations.failureFor("editor-b"))

        val retryAttempt = requireNotNull(operations.beginSave("editor-b"))
        assertEquals(42L, operations.savedOwnerId("editor-b", "draft-b"))
        operations.markSaveSucceeded("editor-b", retryAttempt)

        assertFalse(operations.hasSavedOwner("editor-b"))
        assertTrue("editor-b" in operations.savedSessionKeys)
    }

    @Test
    fun attachmentRetryReusesCopyAttemptUntilRequestSucceeds() {
        val operations = LedgerUiOperationsViewModel(SavedStateHandle())
        val firstSave = requireNotNull(operations.beginSave("editor-copy"))
        val uriSignature = listOf("content://receipts/one", "content://receipts/two")
        val firstCopyAttempt = operations.attachmentCopyAttemptId("editor-copy", uriSignature)
        assertEquals(firstCopyAttempt, UUID.fromString(firstCopyAttempt).toString())
        operations.markSaveFailed("editor-copy", firstSave, "Copy failed")

        val retrySave = requireNotNull(operations.beginSave("editor-copy"))
        val retryCopyAttempt = operations.attachmentCopyAttemptId("editor-copy", uriSignature)

        assertEquals(firstCopyAttempt, retryCopyAttempt)
        operations.markSaveSucceeded("editor-copy", retrySave)

        val laterSave = requireNotNull(operations.beginSave("editor-copy-new"))
        val laterCopyAttempt = operations.attachmentCopyAttemptId("editor-copy-new", uriSignature)
        assertFalse(firstCopyAttempt == laterCopyAttempt)
        operations.markSaveSucceeded("editor-copy-new", laterSave)
    }

    @Test
    fun duplicateDeleteRequestIsRejectedUntilReceiptOrFailure() {
        val operations = LedgerUiOperationsViewModel(SavedStateHandle())

        assertTrue(operations.beginDelete(7L))
        assertFalse(operations.beginDelete(7L))

        operations.markDeleteFailed(7L)
        assertTrue(operations.beginDelete(7L))
    }

    @Test
    fun processRecreationRestoresOwnerAndCopyAttemptForSameRequest() {
        val handle = SavedStateHandle()
        val first = LedgerUiOperationsViewModel(handle)
        val attempt = requireNotNull(first.beginSave("editor-restored"))
        val uris = listOf("content://receipts/one", "content://receipts/two")
        first.clientOperationToken("editor-restored", "same-draft")
        val copyAttempt = first.attachmentCopyAttemptId("editor-restored", uris)
        first.markOwnerSaved("editor-restored", attempt, 88L, "same-draft")
        first.markSaveFailed("editor-restored", attempt, "Process stopped")

        val restored = LedgerUiOperationsViewModel(restoredHandle(handle))

        assertEquals(88L, restored.savedOwnerId("editor-restored", "same-draft"))
        assertEquals(copyAttempt, restored.attachmentCopyAttemptId("editor-restored", uris))
        assertNull(restored.beginSave("editor-restored")?.let { restored.beginSave("editor-restored") })
    }

    @Test
    fun processRecreationBeforeOwnerCallbackRestoresClientOperationToken() {
        val handle = SavedStateHandle()
        val first = LedgerUiOperationsViewModel(handle)
        val token = first.clientOperationToken("editor-pre-callback", "same-draft")

        val restored = LedgerUiOperationsViewModel(restoredHandle(handle))

        assertEquals(token, restored.clientOperationToken("editor-pre-callback", "same-draft"))
        assertFalse(restored.hasSavedOwner("editor-pre-callback"))
        assertEquals(
            token,
            restored.clientOperationToken("editor-pre-callback", "changed-draft"),
        )

        val staleSavedState = LedgerUiOperationsViewModel(SavedStateHandle())
        assertEquals(
            token,
            staleSavedState.clientOperationToken("editor-pre-callback", "same-draft"),
        )
    }

    @Test
    fun changedDraftKeepsRecoveredOwnerAndCreationTokenButRotatesCopyRequest() {
        val handle = SavedStateHandle()
        val first = LedgerUiOperationsViewModel(handle)
        val attempt = requireNotNull(first.beginSave("editor-changed"))
        val uris = listOf("content://receipts/one")
        val creationToken = first.clientOperationToken("editor-changed", "old-draft")
        val oldCopyAttempt = first.attachmentCopyAttemptId("editor-changed", uris)
        first.markOwnerSaved("editor-changed", attempt, 91L, "old-draft")

        val restored = LedgerUiOperationsViewModel(restoredHandle(handle))

        assertEquals(91L, restored.savedOwnerId("editor-changed", "new-draft"))
        assertTrue(restored.hasSavedOwner("editor-changed"))
        assertEquals(creationToken, restored.clientOperationToken("editor-changed", "new-draft"))
        assertFalse(oldCopyAttempt == restored.attachmentCopyAttemptId("editor-changed", uris))
    }

    @Test
    fun quickEntryCreationTokenSurvivesAmountChangeAndNewSessionRotatesIt() {
        val operations = LedgerUiOperationsViewModel(SavedStateHandle())
        val session = "$QUICK_LEDGER_SESSION_KEY:stable"
        val first = operations.clientOperationToken(session, "amount=100")

        assertEquals(first, operations.clientOperationToken(session, "amount=250"))
        assertNotEquals(
            first,
            operations.clientOperationToken("$QUICK_LEDGER_SESSION_KEY:new", "amount=250"),
        )
    }

    @Test
    fun changedUrisUseNewCopyAttemptWithoutDuplicatingOwner() {
        val handle = SavedStateHandle()
        val first = LedgerUiOperationsViewModel(handle)
        val attempt = requireNotNull(first.beginSave("editor-uris"))
        first.clientOperationToken("editor-uris", "same-draft")
        val oldCopyAttempt = first.attachmentCopyAttemptId(
            "editor-uris",
            listOf("content://receipts/old"),
        )
        first.markOwnerSaved("editor-uris", attempt, 92L, "same-draft")

        val restored = LedgerUiOperationsViewModel(restoredHandle(handle))
        val newCopyAttempt = restored.attachmentCopyAttemptId(
            "editor-uris",
            listOf("content://receipts/new"),
        )

        assertFalse(oldCopyAttempt == newCopyAttempt)
        assertEquals(92L, restored.savedOwnerId("editor-uris", "same-draft"))
    }

    @Test
    fun deletedOwnerRejectionClearsRecovery() {
        val handle = SavedStateHandle()
        val first = LedgerUiOperationsViewModel(handle)
        val attempt = requireNotNull(first.beginSave("editor-deleted"))
        first.clientOperationToken("editor-deleted", "same-draft")
        first.attachmentCopyAttemptId("editor-deleted", listOf("content://receipts/one"))
        first.markOwnerSaved("editor-deleted", attempt, 93L, "same-draft")

        val restoredState = restoredHandle(handle)
        val restored = LedgerUiOperationsViewModel(restoredState)
        restored.rejectSavedOwner("editor-deleted", 93L)

        assertFalse(restored.hasSavedOwner("editor-deleted"))
        assertNull(
            LedgerUiOperationsViewModel(restoredHandle(restoredState))
                .savedOwnerId("editor-deleted", "same-draft"),
        )
    }

    @Test
    fun abandoningRestoredEditorClearsOwnerAndCopyIdentity() {
        val state = SavedStateHandle()
        val first = LedgerUiOperationsViewModel(state)
        val attempt = requireNotNull(first.beginSave("editor-abandoned"))
        first.clientOperationToken("editor-abandoned", "same-draft")
        val oldCopyAttempt = first.attachmentCopyAttemptId(
            "editor-abandoned",
            listOf("content://receipts/one"),
        )
        first.markOwnerSaved("editor-abandoned", attempt, 94L, "same-draft")
        first.markSaveFailed("editor-abandoned", attempt, "Copy failed")

        val restoredState = restoredHandle(state)
        val restored = LedgerUiOperationsViewModel(restoredState)
        restored.abandonSession("editor-abandoned")

        val reopened = LedgerUiOperationsViewModel(restoredHandle(restoredState))
        assertFalse(reopened.hasSavedOwner("editor-abandoned"))
        assertFalse(
            oldCopyAttempt == reopened.attachmentCopyAttemptId(
                "editor-reopened",
                listOf("content://receipts/one"),
            ),
        )
    }

    private fun restoredHandle(source: SavedStateHandle): SavedStateHandle = SavedStateHandle(
        source.keys().associateWith { key -> source.get<Any?>(key) },
    )
}
