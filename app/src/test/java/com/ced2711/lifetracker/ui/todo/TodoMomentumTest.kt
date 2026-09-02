package com.ced2711.lifetracker.ui.todo

import com.ced2711.lifetracker.data.local.TodoEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoMomentumTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 9, 3)

    @Test
    fun `momentum counts local completions and active deadlines for today`() {
        val result = dailyTodoMomentum(
            activeTodos = listOf(
                todo(id = 1, deadlineEpochDay = today.toEpochDay()),
                todo(id = 2, deadlineEpochDay = today.toEpochDay()),
                todo(id = 3, deadlineEpochDay = today.plusDays(1).toEpochDay()),
                todo(id = 4, deadlineEpochDay = null),
            ),
            completedTodos = listOf(
                completedTodo(5, today),
                completedTodo(6, today.minusDays(1)),
            ),
            todayEpochDay = today.toEpochDay(),
            zoneId = zone,
        )

        assertEquals(1, result.completedToday)
        assertEquals(2, result.dueTodayRemaining)
        assertEquals(3, result.total)
        assertEquals(1f / 3f, result.progress, 0.0001f)
        assertEquals("1 completed • 2 due today", result.summary)
    }

    @Test
    fun `empty day stays calm and completion feedback is concise`() {
        val result = dailyTodoMomentum(emptyList(), emptyList(), today.toEpochDay(), zone)

        assertEquals(0f, result.progress, 0f)
        assertEquals("No deadlines today — anything you finish is a win.", result.summary)
        assertEquals("Nice work — 4 completed today", completionCelebrationMessage(4))
    }

    private fun todo(id: Long, deadlineEpochDay: Long?) = TodoEntity(
        id = id,
        title = "Task $id",
        description = "Task $id",
        deadlineEpochDay = deadlineEpochDay,
    )

    private fun completedTodo(id: Long, completedDate: LocalDate) = todo(id, null).copy(
        completedAt = completedDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
    )
}
