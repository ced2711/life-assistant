package com.ced2711.lifetracker.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSchedulingPolicyTest {
    @Test
    fun `continuation policy round trips and legacy work defaults to catch-up`() {
        ReminderReschedulePolicy.entries.forEach { policy ->
            assertEquals(policy, parseReminderReschedulePolicy(policy.name))
        }
        assertEquals(
            ReminderReschedulePolicy.INCLUDE_DUE_WINDOW,
            parseReminderReschedulePolicy(null),
        )
        assertEquals(
            ReminderReschedulePolicy.INCLUDE_DUE_WINDOW,
            parseReminderReschedulePolicy("unknown"),
        )
    }

    @Test
    fun `future-only schedules early reminders but never backfills due reminders`() {
        assertTrue(
            shouldScheduleReminder(
                ReminderWindowState.EARLY,
                ReminderReschedulePolicy.FUTURE_ONLY,
            ),
        )
        assertFalse(
            shouldScheduleReminder(
                ReminderWindowState.DUE,
                ReminderReschedulePolicy.FUTURE_ONLY,
            ),
        )
        assertFalse(
            shouldScheduleReminder(
                ReminderWindowState.EXPIRED,
                ReminderReschedulePolicy.FUTURE_ONLY,
            ),
        )
    }

    @Test
    fun `catch-up policy schedules current due window without reviving expired reminders`() {
        assertTrue(
            shouldScheduleReminder(
                ReminderWindowState.EARLY,
                ReminderReschedulePolicy.INCLUDE_DUE_WINDOW,
            ),
        )
        assertTrue(
            shouldScheduleReminder(
                ReminderWindowState.DUE,
                ReminderReschedulePolicy.INCLUDE_DUE_WINDOW,
            ),
        )
        assertFalse(
            shouldScheduleReminder(
                ReminderWindowState.EXPIRED,
                ReminderReschedulePolicy.INCLUDE_DUE_WINDOW,
            ),
        )
    }

    @Test
    fun `saved todo gets due-window policy without backfilling unrelated todos`() {
        val savedTodoId = 42L

        assertEquals(
            ReminderReschedulePolicy.INCLUDE_DUE_WINDOW,
            reminderReschedulePolicyForTodo(
                todoId = savedTodoId,
                defaultPolicy = ReminderReschedulePolicy.FUTURE_ONLY,
                includeDueTodoId = savedTodoId,
            ),
        )
        assertEquals(
            ReminderReschedulePolicy.FUTURE_ONLY,
            reminderReschedulePolicyForTodo(
                todoId = 43L,
                defaultPolicy = ReminderReschedulePolicy.FUTURE_ONLY,
                includeDueTodoId = savedTodoId,
            ),
        )
    }

    @Test
    fun `saved todo policy includes due but still excludes expired reminder windows`() {
        val policy = reminderReschedulePolicyForTodo(
            todoId = 42L,
            defaultPolicy = ReminderReschedulePolicy.FUTURE_ONLY,
            includeDueTodoId = 42L,
        )

        assertTrue(shouldScheduleReminder(ReminderWindowState.DUE, policy))
        assertFalse(shouldScheduleReminder(ReminderWindowState.EXPIRED, policy))
    }

    @Test
    fun `delivery identity is stable across database reminder replacement`() {
        val original = ReminderDeliveryKey(todoId = 42, offsetMinutes = 60, triggerAtMillis = 1_000)
        val replacementRow = ReminderDeliveryKey(todoId = 42, offsetMinutes = 60, triggerAtMillis = 1_000)

        assertEquals(original, replacementRow)
        assertEquals(original.storageKey, replacementRow.storageKey)
        assertEquals(original.notificationTag, replacementRow.notificationTag)
        assertEquals(original.workName, replacementRow.workName)
        assertEquals(original.workTag, replacementRow.workTag)
    }

    @Test
    fun `deadline changes and separate offsets have separate delivery identities`() {
        val baseline = ReminderDeliveryKey(todoId = 42, offsetMinutes = 60, triggerAtMillis = 1_000)
        val changedDeadline = ReminderDeliveryKey(todoId = 42, offsetMinutes = 60, triggerAtMillis = 2_000)
        val secondOffset = ReminderDeliveryKey(todoId = 42, offsetMinutes = 0, triggerAtMillis = 1_000)
        val secondTodo = ReminderDeliveryKey(todoId = 43, offsetMinutes = 60, triggerAtMillis = 1_000)

        listOf(changedDeadline, secondOffset, secondTodo).forEach { distinctKey ->
            assertNotEquals(baseline, distinctKey)
            assertNotEquals(baseline.storageKey, distinctKey.storageKey)
            assertNotEquals(baseline.notificationTag, distinctKey.notificationTag)
            assertNotEquals(baseline.workName, distinctKey.workName)
            assertNotEquals(baseline.workTag, distinctKey.workTag)
        }
    }

    @Test
    fun `reconciliation preserves valid keyed and finite legacy reminder work`() {
        val key = ReminderDeliveryKey(todoId = 42, offsetMinutes = 60, triggerAtMillis = 1_000)
        val validTags = setOf(key.workTag)

        assertEquals(
            ReminderWorkTagStatus.VALID,
            classifyReminderWorkTags(setOf("taskledger_reminder", key.workTag), validTags),
        )
        assertEquals(
            ReminderWorkTagStatus.LEGACY,
            classifyReminderWorkTags(setOf("taskledger_reminder"), validTags),
        )
    }

    @Test
    fun `reconciliation rejects stale and malformed keyed reminder work`() {
        val valid = ReminderDeliveryKey(todoId = 42, offsetMinutes = 60, triggerAtMillis = 1_000)
        val stale = ReminderDeliveryKey(todoId = 42, offsetMinutes = 60, triggerAtMillis = 2_000)

        assertEquals(
            ReminderWorkTagStatus.INVALID,
            classifyReminderWorkTags(setOf("taskledger_reminder", stale.workTag), setOf(valid.workTag)),
        )
        assertEquals(
            ReminderWorkTagStatus.INVALID,
            classifyReminderWorkTags(
                setOf("taskledger_reminder", valid.workTag, stale.workTag),
                setOf(valid.workTag),
            ),
        )
    }
}
