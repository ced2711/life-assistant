package com.ced2711.lifetracker.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteUndoWindowTest {
    @Test
    fun windowIsOpenOnlyBeforeItsDeadline() {
        assertTrue(isUndoWindowOpen(expiresAtMillis = 6_000L, nowMillis = 5_999L))
        assertFalse(isUndoWindowOpen(expiresAtMillis = 6_000L, nowMillis = 6_000L))
        assertFalse(isUndoWindowOpen(expiresAtMillis = 6_000L, nowMillis = 6_001L))
    }
}
