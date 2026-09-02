package com.ced2711.lifetracker.data.attachment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AttachmentCopyAttemptIdTest {
    @Test
    fun canonicalUuidIsNormalizedToLowercase() {
        assertEquals(
            "123e4567-e89b-12d3-a456-426614174000",
            normalizeCopyAttemptId("123E4567-E89B-12D3-A456-426614174000"),
        )
    }

    @Test
    fun malformedAndNonCanonicalIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeCopyAttemptId("retry-1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeCopyAttemptId("123e4567e89b12d3a456426614174000")
        }
    }
}
