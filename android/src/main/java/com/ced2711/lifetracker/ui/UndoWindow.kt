package com.ced2711.lifetracker.ui

internal fun remainingUndoMillis(expiresAtMillis: Long, nowMillis: Long): Long {
    if (expiresAtMillis <= nowMillis) return 0L
    return try {
        Math.subtractExact(expiresAtMillis, nowMillis)
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }
}
