package com.ced2711.lifetracker.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentCapacityPolicyTest {
    @Test
    fun pendingRowsStillConsumeOwnerCapacity() {
        // The policy intentionally receives the DAO's total row count, not just active rows.
        assertFalse(isWithinAttachmentLimit(existingCount = 10, incomingCount = 1, maxAttachments = 10))
    }

    @Test
    fun freedCapacityAcceptsOnlyTheAvailableNumberOfFiles() {
        assertTrue(isWithinAttachmentLimit(existingCount = 7, incomingCount = 3, maxAttachments = 10))
        assertFalse(isWithinAttachmentLimit(existingCount = 7, incomingCount = 4, maxAttachments = 10))
    }

    @Test
    fun arithmeticDoesNotOverflowAtLargeCounts() {
        assertFalse(
            isWithinAttachmentLimit(
                existingCount = Int.MAX_VALUE,
                incomingCount = Int.MAX_VALUE,
                maxAttachments = Int.MAX_VALUE,
            ),
        )
    }

    @Test
    fun byteCapacityAllowsAnExactFillButRejectsOneExtraByte() {
        assertTrue(
            isWithinAttachmentByteLimit(
                existingBytes = 127,
                incomingBytes = 1,
                maxBytes = 128,
            ),
        )
        assertFalse(
            isWithinAttachmentByteLimit(
                existingBytes = 127,
                incomingBytes = 2,
                maxBytes = 128,
            ),
        )
    }

    @Test
    fun reachedOrLegacyOverLimitCapacityBlocksNewRows() {
        assertFalse(isWithinAttachmentByteLimit(128, 0, 128))
        assertEquals(128L, attachmentBytesTowardLimit(listOf(100, 50), 128))
        assertFalse(isWithinAttachmentByteLimit(attachmentBytesTowardLimit(listOf(100, 50), 128), 1, 128))
    }

    @Test
    fun byteSummationFailsClosedWithoutOverflowing() {
        assertNull(sumAttachmentBytesOrNull(listOf(Long.MAX_VALUE, 1)))
        assertNull(sumAttachmentBytesOrNull(listOf(-1)))
        assertEquals(30L, sumAttachmentBytesOrNull(listOf(10, 20)))
    }
}
