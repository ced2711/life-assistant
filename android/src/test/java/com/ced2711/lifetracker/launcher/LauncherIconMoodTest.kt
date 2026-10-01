package com.ced2711.lifetracker.launcher

import com.ced2711.lifetracker.data.local.TodoEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherIconMoodTest {
    private val today = LocalDate.of(2026, 9, 3)

    @Test
    fun `quiet icon is used before anything is completed today`() {
        assertEquals(
            LauncherIconMood.QUIET,
            launcherIconMood(
                activeTodos = listOf(todo(1, today.plusDays(1).toEpochDay())),
                completedTodos = listOf(completedTodo(2, null)),
                todayEpochDay = today.toEpochDay(),
            ),
        )
    }

    @Test
    fun `white icon stays neutral when something was completed today but was not due today`() {
        assertEquals(
            LauncherIconMood.QUIET,
            launcherIconMood(
                activeTodos = emptyList(),
                completedTodos = listOf(completedTodo(2, null)),
                todayEpochDay = today.toEpochDay(),
            ),
        )
    }

    @Test
    fun `red icon is used while a todo due today remains incomplete`() {
        assertEquals(
            LauncherIconMood.MOMENTUM,
            launcherIconMood(
                activeTodos = listOf(todo(1, today.toEpochDay())),
                completedTodos = listOf(completedTodo(2, today.toEpochDay())),
                todayEpochDay = today.toEpochDay(),
            ),
        )
    }

    @Test
    fun `complete icon is used when all todos due today are complete`() {
        assertEquals(
            LauncherIconMood.COMPLETE,
            launcherIconMood(
                activeTodos = emptyList(),
                completedTodos = listOf(completedTodo(2, today.toEpochDay())),
                todayEpochDay = today.toEpochDay(),
            ),
        )
    }

    @Test
    fun `foreground icon changes wait until the app moves to background`() {
        val deferral = LauncherIconMoodDeferral()

        assertEquals(null, deferral.submit(LauncherIconMood.MOMENTUM, appIsForeground = true))
        assertEquals(LauncherIconMood.MOMENTUM, deferral.consumePending())
        assertEquals(null, deferral.consumePending())
    }

    @Test
    fun `only the latest foreground icon is applied after closing`() {
        val deferral = LauncherIconMoodDeferral()

        deferral.submit(LauncherIconMood.MOMENTUM, appIsForeground = true)
        deferral.submit(LauncherIconMood.COMPLETE, appIsForeground = true)

        assertEquals(LauncherIconMood.COMPLETE, deferral.consumePending())
    }

    @Test
    fun `background icon changes can be applied immediately`() {
        val deferral = LauncherIconMoodDeferral()

        assertEquals(
            LauncherIconMood.QUIET,
            deferral.submit(LauncherIconMood.QUIET, appIsForeground = false),
        )
        assertEquals(null, deferral.consumePending())
    }

    private fun todo(id: Long, deadlineEpochDay: Long?) = TodoEntity(
        id = id,
        title = "Task $id",
        description = "Task $id",
        deadlineEpochDay = deadlineEpochDay,
    )

    private fun completedTodo(id: Long, deadlineEpochDay: Long?) =
        todo(id, deadlineEpochDay).copy(
            completedAt = 1L,
        )
}
