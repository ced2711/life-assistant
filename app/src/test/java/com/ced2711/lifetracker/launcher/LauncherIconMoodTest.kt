package com.ced2711.lifetracker.launcher

import com.ced2711.lifetracker.data.local.TodoEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherIconMoodTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 9, 3)

    @Test
    fun `quiet icon is used before anything is completed today`() {
        assertEquals(
            LauncherIconMood.QUIET,
            launcherIconMood(
                activeTodos = listOf(todo(1, today.plusDays(1).toEpochDay())),
                completedTodos = listOf(completedTodo(2, today.minusDays(1), null)),
                todayEpochDay = today.toEpochDay(),
                zoneId = zone,
            ),
        )
    }

    @Test
    fun `momentum icon is used after a completion while work remains`() {
        assertEquals(
            LauncherIconMood.MOMENTUM,
            launcherIconMood(
                activeTodos = listOf(todo(1, today.toEpochDay())),
                completedTodos = listOf(completedTodo(2, today, null)),
                todayEpochDay = today.toEpochDay(),
                zoneId = zone,
            ),
        )
    }

    @Test
    fun `complete icon is used when all todos due today are complete`() {
        assertEquals(
            LauncherIconMood.COMPLETE,
            launcherIconMood(
                activeTodos = emptyList(),
                completedTodos = listOf(completedTodo(2, today.minusDays(1), today.toEpochDay())),
                todayEpochDay = today.toEpochDay(),
                zoneId = zone,
            ),
        )
    }

    private fun todo(id: Long, deadlineEpochDay: Long?) = TodoEntity(
        id = id,
        title = "Task $id",
        description = "Task $id",
        deadlineEpochDay = deadlineEpochDay,
    )

    private fun completedTodo(id: Long, completedDate: LocalDate, deadlineEpochDay: Long?) =
        todo(id, deadlineEpochDay).copy(
            completedAt = completedDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        )
}
