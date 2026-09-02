package com.ced2711.lifetracker.ui.ledger

import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.domain.model.LedgerType
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerEntriesHelpersTest {
    @Test
    fun newDraftUsesOneDateTimeSnapshot() {
        val now = LocalDateTime.of(2026, 8, 18, 23, 59)

        val draft = newLedgerDraft(now)

        assertEquals(now.toLocalDate().toEpochDay(), draft.epochDay)
        assertEquals(23 * 60 + 59, draft.minuteOfDay)
    }

    @Test
    fun entryActionsDescribeMerchantTypeSignedAmountAndDate() {
        val entry = LedgerEntryEntity(
            type = LedgerType.EXPENSE,
            amountCents = 1_234,
            epochDay = 0,
            minuteOfDay = 0,
            merchant = "Café 世界",
        )

        assertEquals(
            "Delete Café 世界, expense -$12.34, 08/18/2026",
            ledgerEntryActionDescription("Delete", entry, "08/18/2026"),
        )
    }

    @Test
    fun entryActionsFallBackToNoteWhenMerchantIsBlank() {
        val entry = LedgerEntryEntity(
            type = LedgerType.INCOME,
            amountCents = 500,
            epochDay = 0,
            minuteOfDay = 0,
            note = "Refund",
        )

        assertEquals(
            "Edit Refund, income +$5.00, 08/18/2026",
            ledgerEntryActionDescription("Edit", entry, "08/18/2026"),
        )
    }

    @Test
    fun receiptFailureExplainsSavedStateAndSafeRetry() {
        val message = receiptCopyFailureMessage("Storage unavailable")

        assertTrue(message.contains("Entry saved"))
        assertTrue(message.contains("receipts failed to copy"))
        assertTrue(message.contains("Retry Save"))
        assertTrue(message.contains("will not be duplicated"))
        assertTrue(message.contains("Storage unavailable"))
    }
}
