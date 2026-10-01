package com.ced2711.lifetracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UndoWindowTest {
    @Test
    fun remainingTimeUsesTheReceiptDeadline() {
        assertEquals(250L, remainingUndoMillis(expiresAtMillis = 1_000L, nowMillis = 750L))
    }

    @Test
    fun deadlineAndLaterHaveNoRemainingTime() {
        assertEquals(0L, remainingUndoMillis(expiresAtMillis = 1_000L, nowMillis = 1_000L))
        assertEquals(0L, remainingUndoMillis(expiresAtMillis = 1_000L, nowMillis = 1_001L))
    }

    @Test
    fun positiveOverflowSaturatesInsteadOfWrapping() {
        assertEquals(
            Long.MAX_VALUE,
            remainingUndoMillis(expiresAtMillis = Long.MAX_VALUE, nowMillis = Long.MIN_VALUE),
        )
    }
}
