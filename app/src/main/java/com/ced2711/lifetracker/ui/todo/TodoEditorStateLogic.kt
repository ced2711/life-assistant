package com.ced2711.lifetracker.ui.todo

import com.ced2711.lifetracker.domain.date.SmartDateParser
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import com.ced2711.lifetracker.domain.model.TodoDraft
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.TodoQuickAddField

internal enum class QuickTodoDraftError {
    DESCRIPTION_REQUIRED,
    INVALID_DEADLINE,
}

internal data class QuickTodoDraftResult(
    val draft: TodoDraft? = null,
    val error: QuickTodoDraftError? = null,
)

internal fun buildQuickTodoDraft(
    description: String,
    enabledFields: Set<TodoQuickAddField>,
    deadlineText: String,
    priority: TodoPriority,
    categoryId: Long?,
    tagsText: String,
    todayEpochDay: Long,
    defaultReminderOffsets: Set<Long>,
): QuickTodoDraftResult {
    val cleanDescription = description.trim()
    if (cleanDescription.isEmpty()) {
        return QuickTodoDraftResult(error = QuickTodoDraftError.DESCRIPTION_REQUIRED)
    }

    val deadline = if (TodoQuickAddField.DEADLINE in enabledFields && deadlineText.isNotBlank()) {
        SmartDateParser.parse(deadlineText, todayEpochDay)
            ?: return QuickTodoDraftResult(error = QuickTodoDraftError.INVALID_DEADLINE)
    } else {
        null
    }
    return QuickTodoDraftResult(
        draft = TodoDraft(
            description = cleanDescription,
            categoryId = categoryId.takeIf { TodoQuickAddField.CATEGORY in enabledFields },
            deadlineEpochDay = deadline,
            priority = priority.takeIf { TodoQuickAddField.PRIORITY in enabledFields }
                ?: TodoPriority.NONE,
            tags = if (TodoQuickAddField.TAGS in enabledFields) {
                tagsText.split(',').map(String::trim).filter(String::isNotEmpty)
            } else {
                emptyList()
            },
            reminderOffsetsMinutes = if (deadline == null) {
                emptyList()
            } else {
                defaultReminderOffsets.filter { it >= 0 }.distinct().sorted()
            },
        ),
    )
}

/** Deadline-owned editor fields that must never outlive a cleared deadline. */
internal data class TodoDeadlineDependentState(
    val hasTime: Boolean,
    val timeText: String,
    val reminderOffsets: List<Long>,
    val appliedDefaultReminders: Boolean,
    val recurrenceEnabled: Boolean,
    val recurrenceUnit: RecurrenceUnit,
    val recurrenceInterval: String,
    val recurrenceEndText: String,
)

internal fun clearTodoDeadlineDependentsIfBlank(
    deadlineText: String,
    current: TodoDeadlineDependentState,
): TodoDeadlineDependentState = if (deadlineText.isNotBlank()) {
    current
} else {
    TodoDeadlineDependentState(
        hasTime = false,
        timeText = "",
        reminderOffsets = emptyList(),
        // Clearing is explicit; re-entering a date must not silently restore reminder defaults.
        appliedDefaultReminders = true,
        recurrenceEnabled = false,
        recurrenceUnit = RecurrenceUnit.WEEK,
        recurrenceInterval = "1",
        recurrenceEndText = "",
    )
}

internal fun canEditTodoRecurrenceRule(
    editingSeriesOccurrence: Boolean,
    editScope: SeriesEditScope,
): Boolean = !editingSeriesOccurrence || editScope == SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES

internal fun canEnableTodoDeadlineTime(deadlineText: String): Boolean = deadlineText.isNotBlank()

internal fun canEnableTodoRecurrence(
    deadlineText: String,
    editingSeriesOccurrence: Boolean,
    editScope: SeriesEditScope,
): Boolean = deadlineText.isNotBlank() && canEditTodoRecurrenceRule(editingSeriesOccurrence, editScope)

internal fun shouldValidateTodoRecurrenceRule(
    recurrenceEnabled: Boolean,
    editingSeriesOccurrence: Boolean,
    editScope: SeriesEditScope,
): Boolean = recurrenceEnabled && canEditTodoRecurrenceRule(editingSeriesOccurrence, editScope)
