package com.ced2711.lifetracker.ui.todo

import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.TodoQuickAddField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoEditorStateLogicTest {
    @Test
    fun `quick add only carries fields enabled in settings`() {
        val result = buildQuickTodoDraft(
            description = "  Buy milk  ",
            enabledFields = setOf(TodoQuickAddField.DEADLINE, TodoQuickAddField.TAGS),
            deadlineText = "8/21",
            priority = TodoPriority.URGENT,
            categoryId = 42L,
            tagsText = "home, Errands, home",
            todayEpochDay = java.time.LocalDate.of(2026, 8, 20).toEpochDay(),
            defaultReminderOffsets = setOf(1_440L, 0L),
        )

        assertEquals(null, result.error)
        assertEquals("Buy milk", result.draft?.description)
        assertEquals("", result.draft?.title)
        assertEquals(java.time.LocalDate.of(2026, 8, 21).toEpochDay(), result.draft?.deadlineEpochDay)
        assertEquals(listOf(0L, 1_440L), result.draft?.reminderOffsetsMinutes)
        assertEquals(TodoPriority.NONE, result.draft?.priority)
        assertEquals(null, result.draft?.categoryId)
        assertEquals(listOf("home", "Errands", "home"), result.draft?.tags)
    }

    @Test
    fun `hidden quick add fields never leak stale values`() {
        val result = buildQuickTodoDraft(
            description = "Task",
            enabledFields = emptySet(),
            deadlineText = "not a date",
            priority = TodoPriority.HIGH,
            categoryId = 9L,
            tagsText = "stale",
            todayEpochDay = java.time.LocalDate.of(2026, 8, 20).toEpochDay(),
            defaultReminderOffsets = setOf(60L),
        )

        assertEquals(null, result.error)
        assertEquals(null, result.draft?.deadlineEpochDay)
        assertEquals(emptyList<Long>(), result.draft?.reminderOffsetsMinutes)
        assertEquals(TodoPriority.NONE, result.draft?.priority)
        assertEquals(null, result.draft?.categoryId)
        assertEquals(emptyList<String>(), result.draft?.tags)
    }

    @Test
    fun `visible invalid deadline and blank description block quick add`() {
        val invalidDeadline = buildQuickTodoDraft(
            description = "Task",
            enabledFields = setOf(TodoQuickAddField.DEADLINE),
            deadlineText = "tomorrowish",
            priority = TodoPriority.NONE,
            categoryId = null,
            tagsText = "",
            todayEpochDay = java.time.LocalDate.of(2026, 8, 20).toEpochDay(),
            defaultReminderOffsets = emptySet(),
        )
        val blankDescription = buildQuickTodoDraft(
            description = "   ",
            enabledFields = emptySet(),
            deadlineText = "",
            priority = TodoPriority.NONE,
            categoryId = null,
            tagsText = "",
            todayEpochDay = java.time.LocalDate.of(2026, 8, 20).toEpochDay(),
            defaultReminderOffsets = emptySet(),
        )

        assertEquals(QuickTodoDraftError.INVALID_DEADLINE, invalidDeadline.error)
        assertEquals(null, invalidDeadline.draft)
        assertEquals(QuickTodoDraftError.DESCRIPTION_REQUIRED, blankDescription.error)
        assertEquals(null, blankDescription.draft)
    }

    @Test
    fun `blank deadline atomically clears every deadline dependent field`() {
        val current = TodoDeadlineDependentState(
            hasTime = true,
            timeText = "9:30 AM",
            reminderOffsets = listOf(0, 60, 1_440),
            appliedDefaultReminders = false,
            recurrenceEnabled = true,
            recurrenceUnit = RecurrenceUnit.MONTH,
            recurrenceInterval = "3",
            recurrenceEndText = "12/31/2027",
        )

        val cleared = clearTodoDeadlineDependentsIfBlank("", current)

        assertFalse(cleared.hasTime)
        assertEquals("", cleared.timeText)
        assertTrue(cleared.reminderOffsets.isEmpty())
        assertTrue(cleared.appliedDefaultReminders)
        assertFalse(cleared.recurrenceEnabled)
        assertEquals(RecurrenceUnit.WEEK, cleared.recurrenceUnit)
        assertEquals("1", cleared.recurrenceInterval)
        assertEquals("", cleared.recurrenceEndText)
    }

    @Test
    fun `nonblank deadline leaves dependent fields unchanged`() {
        val current = TodoDeadlineDependentState(
            hasTime = true,
            timeText = "21:30",
            reminderOffsets = listOf(60),
            appliedDefaultReminders = true,
            recurrenceEnabled = true,
            recurrenceUnit = RecurrenceUnit.DAY,
            recurrenceInterval = "2",
            recurrenceEndText = "",
        )

        assertEquals(current, clearTodoDeadlineDependentsIfBlank("8/20/2026", current))
    }

    @Test
    fun `existing series rule is editable only for this and future scope`() {
        assertFalse(canEditTodoRecurrenceRule(true, SeriesEditScope.ONLY_THIS_OCCURRENCE))
        assertTrue(canEditTodoRecurrenceRule(true, SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES))
        assertTrue(canEditTodoRecurrenceRule(false, SeriesEditScope.ONLY_THIS_OCCURRENCE))
    }

    @Test
    fun `deadline time cannot be enabled without a deadline`() {
        assertFalse(canEnableTodoDeadlineTime(""))
        assertFalse(canEnableTodoDeadlineTime("   "))
        assertTrue(canEnableTodoDeadlineTime("8/20/2026"))
    }

    @Test
    fun `recurrence control requires a deadline and editable series scope`() {
        assertFalse(canEnableTodoRecurrence("", false, SeriesEditScope.ONLY_THIS_OCCURRENCE))
        assertFalse(canEnableTodoRecurrence("8/20/2026", true, SeriesEditScope.ONLY_THIS_OCCURRENCE))
        assertTrue(
            canEnableTodoRecurrence(
                "8/20/2026",
                true,
                SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES,
            ),
        )
        assertTrue(canEnableTodoRecurrence("8/20/2026", false, SeriesEditScope.ONLY_THIS_OCCURRENCE))
    }

    @Test
    fun `repeat rule validation skips disabled and occurrence-only rules`() {
        assertFalse(
            shouldValidateTodoRecurrenceRule(
                recurrenceEnabled = false,
                editingSeriesOccurrence = false,
                editScope = SeriesEditScope.ONLY_THIS_OCCURRENCE,
            ),
        )
        assertFalse(
            shouldValidateTodoRecurrenceRule(
                recurrenceEnabled = true,
                editingSeriesOccurrence = true,
                editScope = SeriesEditScope.ONLY_THIS_OCCURRENCE,
            ),
        )
        assertTrue(
            shouldValidateTodoRecurrenceRule(
                recurrenceEnabled = true,
                editingSeriesOccurrence = true,
                editScope = SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES,
            ),
        )
        assertTrue(
            shouldValidateTodoRecurrenceRule(
                recurrenceEnabled = true,
                editingSeriesOccurrence = false,
                editScope = SeriesEditScope.ONLY_THIS_OCCURRENCE,
            ),
        )
    }
}
