package com.ced2711.lifetracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MonotonicTimestampsTest {
    @Test
    fun mutation_usesWallClockWhenItIsNewerThanEveryPredecessor() {
        assertEquals(30L, monotonicMutationTimestamp(30L, 10L, 20L, null))
    }

    @Test
    fun mutation_advancesPastMaximumPredecessorAfterClockRollback() {
        assertEquals(21L, monotonicMutationTimestamp(5L, 10L, 20L, null))
        assertEquals(21L, monotonicMutationTimestamp(-2_600_000L, 10L, 20L, null))
    }

    @Test
    fun mutation_rejectsOverflowInsteadOfWrapping() {
        assertThrows(IllegalStateException::class.java) {
            monotonicMutationTimestamp(0L, Long.MAX_VALUE)
        }
    }

    @Test
    fun mutation_advancesToPersistedLimitButNeverBeyondIt() {
        assertEquals(
            MAX_PERSISTED_TIMESTAMP_MILLIS,
            monotonicMutationTimestamp(
                0L,
                MAX_PERSISTED_TIMESTAMP_MILLIS - 1L,
            ),
        )
        assertThrows(IllegalStateException::class.java) {
            monotonicMutationTimestamp(0L, MAX_PERSISTED_TIMESTAMP_MILLIS)
        }
        assertThrows(IllegalStateException::class.java) {
            monotonicMutationTimestamp(MAX_PERSISTED_TIMESTAMP_MILLIS + 1L)
        }
        assertThrows(IllegalStateException::class.java) {
            monotonicMutationTimestamp(-1L)
        }
    }

    @Test
    fun deadline_isFlooredAtAffectedRecordCreationTime() {
        assertEquals(50L, persistedDeadlineTimestamp(5L, 6L, 20L, 50L))
    }

    @Test
    fun deadline_rejectsOverflowInsteadOfWrapping() {
        assertThrows(IllegalArgumentException::class.java) {
            persistedDeadlineTimestamp(Long.MAX_VALUE, 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            persistedDeadlineTimestamp(MAX_PERSISTED_TIMESTAMP_MILLIS, 1L)
        }
    }
}
