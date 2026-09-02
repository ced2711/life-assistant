package com.ced2711.lifetracker.data

internal const val MAX_PERSISTED_TIMESTAMP_MILLIS = 253_402_300_799_999L

/**
 * Returns a wall-clock timestamp that is strictly newer than every persisted predecessor.
 *
 * Wall time can move backwards when the user changes the device clock. Persisted mutation order
 * must not. Failing on [Long.MAX_VALUE] is preferable to wrapping into a timestamp that predates
 * the record it is meant to update.
 */
internal fun monotonicMutationTimestamp(
    wallClockMillis: Long,
    vararg predecessors: Long?,
): Long {
    val latest = predecessors.filterNotNull().maxOrNull()
    val timestamp = when {
        latest == null || wallClockMillis > latest -> wallClockMillis
        else -> {
            check(latest < MAX_PERSISTED_TIMESTAMP_MILLIS) {
                "No mutation timestamp is available"
            }
            latest + 1L
        }
    }
    check(timestamp in 0..MAX_PERSISTED_TIMESTAMP_MILLIS) {
        "Mutation timestamp is outside the supported range"
    }
    return timestamp
}

/** Adds an Undo duration without overflow, then floors the persisted deadline at every record. */
internal fun persistedDeadlineTimestamp(
    wallClockMillis: Long,
    durationMillis: Long,
    vararg floors: Long?,
): Long {
    require(durationMillis >= 0) { "Undo window must not be negative" }
    val deadline = try {
        Math.addExact(wallClockMillis, durationMillis)
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("Undo window is too large")
    }
    return maxOf(deadline, floors.filterNotNull().maxOrNull() ?: Long.MIN_VALUE).also {
        require(it in 0..MAX_PERSISTED_TIMESTAMP_MILLIS) {
            "Persisted deadline is outside the supported range"
        }
    }
}
