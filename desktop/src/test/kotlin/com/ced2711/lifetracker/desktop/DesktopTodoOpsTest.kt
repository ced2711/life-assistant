package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.data.backup.validate
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import com.ced2711.lifetracker.domain.model.TodoDraft
import com.ced2711.lifetracker.domain.model.TodoPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopTodoOpsTest {
    private val empty = DesktopDataStore.defaultSnapshot(now = 1_000)
    private val day = 20_000L

    private fun save(draft: TodoDraft, snapshot: com.ced2711.lifetracker.data.backup.BackupSnapshot = empty, scope: SeriesEditScope = SeriesEditScope.ONLY_THIS_OCCURRENCE, now: Long = 2_000) =
        DesktopTodoOps.save(snapshot, draft, scope, null, now).let { (s, id) -> s.validate() to id }

    @Test
    fun subtasksRemindersAndTimeAreSaved() {
        val (snapshot, id) = save(
            TodoDraft(description = "Pack for trip", deadlineEpochDay = day, deadlineMinute = 9 * 60, priority = TodoPriority.URGENT,
                reminderOffsetsMinutes = listOf(0, 60), subtasks = listOf("Passport", " ", "Charger")),
        )
        val todo = snapshot.todos.single { it.id == id }
        assertEquals(540, todo.deadlineMinute)
        assertEquals(TodoPriority.URGENT, todo.priority)
        assertEquals(listOf("Passport", "Charger"), snapshot.subtasks.sortedBy { it.sortOrder }.map { it.description })
        assertEquals(setOf(0L, 60L), snapshot.todoReminders.map { it.offsetMinutes }.toSet())
    }

    @Test
    fun editingSubtasksKeepsWhatWasTicked() {
        val (first, id) = save(TodoDraft(description = "Move", subtasks = listOf("Pack", "Clean")))
        val packId = first.subtasks.single { it.description == "Pack" }.id
        val ticked = DesktopTodoOps.setSubtaskCompleted(first, packId, true, 3_000)
        val (edited, _) = save(TodoDraft(id = id, description = "Move", subtasks = listOf("Clean", "Pack", "Return keys")), ticked, now = 4_000)
        assertEquals(listOf("Clean" to false, "Pack" to true, "Return keys" to false), edited.subtasks.sortedBy { it.sortOrder }.map { it.description to it.isCompleted })
    }

    @Test
    fun recurringTodosAreCreatedLikeOnThePhone() {
        val (saved, id) = save(TodoDraft(description = "Water plants", deadlineEpochDay = day, recurrence = RecurrenceRule(RecurrenceUnit.DAY, 2), subtasks = listOf("Balcony")))
        val materialized = DesktopTodoOps.materialize(saved, day + 6, 3_000).validate()
        val series = materialized.todos.filter { it.seriesId != null }.sortedBy { it.occurrenceEpochDay }
        assertEquals(listOf(day, day + 2, day + 4, day + 6), series.map { it.occurrenceEpochDay })
        assertEquals(id, series.first().id)
        assertTrue(series.all { todo -> materialized.subtasks.any { it.todoId == todo.id && it.description == "Balcony" } || todo.id == id })
        // Running again creates nothing new.
        assertEquals(materialized.todos.size, DesktopTodoOps.materialize(materialized, day + 6, 4_000).todos.size)
    }

    @Test
    fun aDeletedOccurrenceNeverComesBack() {
        val (saved, _) = save(TodoDraft(description = "Gym", deadlineEpochDay = day, recurrence = RecurrenceRule(RecurrenceUnit.DAY)))
        val week = DesktopTodoOps.materialize(saved, day + 3, 3_000)
        val last = week.todos.single { it.occurrenceEpochDay == day + 3 }
        val (deleted, deletion) = DesktopTodoOps.delete(week, last.id, SeriesEditScope.ONLY_THIS_OCCURRENCE, 4_000)
        assertTrue(deletion.exceptionCreated)
        val purged = DesktopTodoOps.purgeDeleted(deleted, before = 100_000)
        val again = DesktopTodoOps.materialize(purged, day + 3, 200_000)
        assertTrue(again.todos.none { it.occurrenceEpochDay == day + 3 })
        assertEquals(day + 4, DesktopTodoOps.materialize(purged, day + 4, 200_000).todos.maxOf { it.occurrenceEpochDay ?: 0 })
    }

    @Test
    fun deletingThisAndFutureStopsTheSeriesAndUndoBringsItBack() {
        val (saved, _) = save(TodoDraft(description = "Standup", deadlineEpochDay = day, recurrence = RecurrenceRule(RecurrenceUnit.DAY)))
        val week = DesktopTodoOps.materialize(saved, day + 4, 3_000)
        val middle = week.todos.single { it.occurrenceEpochDay == day + 2 }
        val (deleted, deletion) = DesktopTodoOps.delete(week, middle.id, SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES, 4_000)
        assertEquals(3, deletion.todoIds.size)
        assertTrue(deleted.todoSeries.none { it.active })
        val undone = DesktopTodoOps.undoDelete(deleted, deletion, 5_000).validate()
        assertTrue(undone.todos.all { it.deletedAt == null })
        assertTrue(undone.todoSeries.single().active)
        assertTrue(undone.todoOccurrenceExceptions.isEmpty())
    }

    @Test
    fun editingThisAndFutureStartsANewSeries() {
        val (saved, _) = save(TodoDraft(description = "Report", deadlineEpochDay = day, recurrence = RecurrenceRule(RecurrenceUnit.WEEK)))
        val month = DesktopTodoOps.materialize(saved, day + 21, 3_000)
        val second = month.todos.single { it.occurrenceEpochDay == day + 7 }
        val (edited, _) = save(
            TodoDraft(id = second.id, description = "Weekly report", deadlineEpochDay = day + 7, recurrence = RecurrenceRule(RecurrenceUnit.WEEK)),
            month, SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES, 4_000,
        )
        assertEquals(2, edited.todoSeries.size)
        assertTrue(edited.todoSeries.single { it.title == "Report" }.active.not())
        val live = edited.todos.filter { it.deletedAt == null }
        assertEquals(listOf("Report", "Weekly report"), live.sortedBy { it.occurrenceEpochDay }.map { it.title }.distinct())
    }

    @Test
    fun editingOneOccurrenceWithoutRepeatDetachesItWithAnException() {
        val (saved, id) = save(TodoDraft(description = "Call", deadlineEpochDay = day, recurrence = RecurrenceRule(RecurrenceUnit.DAY)))
        val (edited, _) = save(TodoDraft(id = id, description = "Call mom", deadlineEpochDay = day), saved, now = 4_000)
        val todo = edited.todos.single { it.id == id }
        assertNull(todo.seriesId)
        assertEquals(listOf(day), edited.todoOccurrenceExceptions.map { it.occurrenceEpochDay })
    }

    @Test
    fun deletingACategoryUncategorizesTodosAndPromotesChildren() {
        val parent = com.ced2711.lifetracker.data.local.CategoryEntity(1, "Work", null, 0, 500)
        val child = com.ced2711.lifetracker.data.local.CategoryEntity(2, "Meetings", 1, 0, 500)
        val sibling = com.ced2711.lifetracker.data.local.CategoryEntity(3, "Meetings", null, 0, 500)
        val (saved, id) = save(TodoDraft(description = "Prepare slides", categoryId = 1), empty.copy(categories = listOf(parent, child, sibling)))
        val result = DesktopTodoOps.deleteCategory(saved, 1, 3_000).validate()
        assertNull(result.todos.single { it.id == id }.categoryId)
        assertEquals(setOf("Meetings", "Meetings (2)"), result.categories.map { it.name }.toSet())
        assertTrue(result.categories.all { it.parentId == null })
    }

    @Test
    fun renamingATagChangesItEverywhere() {
        val (first, _) = save(TodoDraft(description = "Gym", tags = listOf("Health", "daily")))
        val (both, _) = save(TodoDraft(description = "Run", deadlineEpochDay = day, tags = listOf("health"), recurrence = RecurrenceRule(RecurrenceUnit.WEEK)), first)
        val renamed = DesktopTodoOps.changeTag(both, "health", "fitness", 3_000)
        assertTrue(renamed.todos.all { "fitness" in it.tagsCsv.split(',') })
        assertEquals("fitness", renamed.todoSeries.single().tagsCsv)
        val removed = DesktopTodoOps.changeTag(renamed, "fitness", null, 4_000)
        assertTrue(removed.todos.none { "fitness" in it.tagsCsv })
    }

    @Test
    fun undoOfASingleDeleteRestoresTheTodo() {
        val (saved, id) = save(TodoDraft(description = "Pay rent"))
        val (deleted, deletion) = DesktopTodoOps.delete(saved, id, SeriesEditScope.ONLY_THIS_OCCURRENCE, 4_000)
        assertTrue(deleted.todos.single().deletedAt != null)
        assertNull(DesktopTodoOps.undoDelete(deleted, deletion, 5_000).todos.single().deletedAt)
        assertTrue(DesktopTodoOps.purgeDeleted(deleted, before = 10_000 + DesktopTodoOps.UNDO_WINDOW_MILLIS).todos.isEmpty())
    }
}
