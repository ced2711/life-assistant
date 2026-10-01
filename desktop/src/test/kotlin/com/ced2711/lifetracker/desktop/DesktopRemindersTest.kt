package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.local.TodoReminderEntity
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopRemindersTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 10, 2)
    private fun at(hour: Int, minute: Int = 0) = day.atTime(hour, minute).toInstant(zone).toEpochMilli()

    private val snapshot = DesktopDataStore.defaultSnapshot(now = 1_000).let { base ->
        base.copy(
            settings = base.settings.copy(defaultAllDayReminderMinute = 9 * 60),
            todos = listOf(
                TodoEntity(id = 1, title = "Dentist", description = "Dentist", deadlineEpochDay = day.toEpochDay(), deadlineMinute = 15 * 60, createdAt = 1, updatedAt = 1),
                TodoEntity(id = 2, title = "Pay bills", description = "Pay bills", deadlineEpochDay = day.toEpochDay(), createdAt = 1, updatedAt = 1),
                TodoEntity(id = 3, title = "Done already", description = "x", deadlineEpochDay = day.toEpochDay(), deadlineMinute = 15 * 60, completedAt = 5, createdAt = 1, updatedAt = 5),
            ),
            todoReminders = listOf(
                TodoReminderEntity(1, 1, 0), TodoReminderEntity(2, 1, 60),
                TodoReminderEntity(3, 2, 0),
                TodoReminderEntity(4, 3, 0),
            ),
        )
    }

    @Test
    fun remindersFireAtTheirTimeMinusTheOffset() {
        val due = dueReminders(snapshot, at(13, 59), at(14, 0), zone)
        assertEquals(listOf(1L), due.map { it.todoId })
        assertEquals(at(15), due.single().dueAtMillis)
    }

    @Test
    fun todosWithoutATimeUseTheAllDayReminderTime() {
        assertEquals(listOf(2L), dueReminders(snapshot, at(8, 59), at(9, 0), zone).map { it.todoId })
    }

    @Test
    fun completedTodosAndOtherWindowsStayQuiet() {
        val due = dueReminders(snapshot, at(14, 30), at(15, 0), zone)
        assertEquals(listOf(1L), due.map { it.todoId })
        assertTrue(dueReminders(snapshot, at(16), at(17), zone).isEmpty())
    }
}
