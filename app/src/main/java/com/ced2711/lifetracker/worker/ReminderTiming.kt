package com.ced2711.lifetracker.worker

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal enum class ReminderWindowState {
    EARLY,
    DUE,
    EXPIRED,
}

enum class ReminderReschedulePolicy {
    FUTURE_ONLY,
    INCLUDE_DUE_WINDOW,
}

internal fun parseReminderReschedulePolicy(encoded: String?): ReminderReschedulePolicy =
    encoded?.let { value ->
        runCatching { ReminderReschedulePolicy.valueOf(value) }.getOrNull()
    } ?: ReminderReschedulePolicy.INCLUDE_DUE_WINDOW

internal fun shouldScheduleReminder(
    state: ReminderWindowState,
    policy: ReminderReschedulePolicy,
): Boolean = when (state) {
    ReminderWindowState.EARLY -> true
    ReminderWindowState.DUE -> policy == ReminderReschedulePolicy.INCLUDE_DUE_WINDOW
    ReminderWindowState.EXPIRED -> false
}

internal fun reminderReschedulePolicyForTodo(
    todoId: Long,
    defaultPolicy: ReminderReschedulePolicy,
    includeDueTodoId: Long?,
): ReminderReschedulePolicy = if (todoId == includeDueTodoId) {
    ReminderReschedulePolicy.INCLUDE_DUE_WINDOW
} else {
    defaultPolicy
}

internal data class ReminderWindow(
    val triggerAtMillis: Long,
    val expiresAtMillis: Long,
) {
    fun stateAt(nowMillis: Long): ReminderWindowState = when {
        nowMillis < triggerAtMillis -> ReminderWindowState.EARLY
        nowMillis >= expiresAtMillis -> ReminderWindowState.EXPIRED
        else -> ReminderWindowState.DUE
    }
}

/**
 * Calculates a reminder's useful delivery window in the current wall-clock zone.
 *
 * Offsets are non-negative minutes *before* the deadline, matching the Todo editor. A reminder
 * before a deadline expires at the deadline. Exact alarms are intentionally not required, so a
 * due-time reminder remains useful through the end of the todo's local due date.
 */
internal fun calculateReminderWindow(
    deadlineEpochDay: Long?,
    deadlineMinute: Int?,
    defaultAllDayMinute: Int,
    offsetMinutes: Long,
    zone: ZoneId,
): ReminderWindow? {
    val epochDay = deadlineEpochDay ?: return null
    if (offsetMinutes < 0L) return null
    if (deadlineMinute != null && deadlineMinute !in MINUTE_OF_DAY_RANGE) return null
    if (defaultAllDayMinute !in MINUTE_OF_DAY_RANGE) return null

    return runCatching {
        val dueDate = LocalDate.ofEpochDay(epochDay)
        val effectiveMinute = deadlineMinute ?: defaultAllDayMinute
        // Interpret the stored minute as a local wall-clock time. Adding minutes to start-of-day
        // would shift later deadlines by an hour across a daylight-saving gap.
        val dueAt = dueDate
            .atTime(LocalTime.of(effectiveMinute / 60, effectiveMinute % 60))
            .atZone(zone)
        val triggerAt = dueAt.minusMinutes(offsetMinutes)
        val expiresAt = when {
            offsetMinutes > 0L -> dueAt
            else -> dueDate.plusDays(1).atStartOfDay(zone)
        }
        ReminderWindow(
            triggerAtMillis = triggerAt.toInstant().toEpochMilli(),
            expiresAtMillis = expiresAt.toInstant().toEpochMilli(),
        )
    }.getOrNull()
}

private val MINUTE_OF_DAY_RANGE = 0..1_439
