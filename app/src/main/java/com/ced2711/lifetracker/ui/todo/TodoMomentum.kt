package com.ced2711.lifetracker.ui.todo

import com.ced2711.lifetracker.data.local.TodoEntity
import java.time.Instant
import java.time.ZoneId

internal data class DailyTodoMomentum(
    val completedToday: Int,
    val dueTodayRemaining: Int,
) {
    val total: Int = completedToday + dueTodayRemaining
    val progress: Float = if (total == 0) 0f else completedToday.toFloat() / total
    val summary: String = when {
        total == 0 -> "No deadlines today — anything you finish is a win."
        dueTodayRemaining == 0 -> "$completedToday completed • All caught up today"
        else -> "$completedToday completed • $dueTodayRemaining due today"
    }
}

internal fun dailyTodoMomentum(
    activeTodos: List<TodoEntity>,
    completedTodos: List<TodoEntity>,
    todayEpochDay: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): DailyTodoMomentum {
    val completedToday = completedTodos.count { todo ->
        todo.completedAt?.let { timestamp ->
            Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate().toEpochDay() == todayEpochDay
        } == true
    }
    return DailyTodoMomentum(
        completedToday = completedToday,
        dueTodayRemaining = activeTodos.count { it.deadlineEpochDay == todayEpochDay },
    )
}

internal fun completionCelebrationMessage(completedToday: Int): String =
    "Nice work — $completedToday completed today"
