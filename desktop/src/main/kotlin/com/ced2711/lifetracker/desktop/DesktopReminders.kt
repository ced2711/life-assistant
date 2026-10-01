package com.ced2711.lifetracker.desktop

import androidx.compose.runtime.staticCompositionLocalOf
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import java.time.LocalDate
import java.time.ZoneId

/** A todo reminder that should be shown now. */
data class DueReminder(val todoId: Long, val title: String, val dueAtMillis: Long, val remindAtMillis: Long)

/** Shows a system notification; the window provides one backed by the tray icon. */
fun interface DesktopNotifier {
    fun notify(title: String, message: String)
}

val LocalDesktopNotifier = staticCompositionLocalOf<DesktopNotifier?> { null }

/** How late a reminder may still be shown, e.g. when the app was closed at the time. */
const val MISSED_REMINDER_GRACE_MILLIS = 60L * 60L * 1_000L

/**
 * Reminders that fall in (fromMillis, toMillis], computed like the phone: due at the todo's time,
 * or at the default all-day reminder time when it has none, minus each reminder's offset.
 * Completed and deleted todos never remind.
 */
fun dueReminders(snapshot: BackupSnapshot, fromMillis: Long, toMillis: Long, zone: ZoneId = ZoneId.systemDefault()): List<DueReminder> {
    if (toMillis <= fromMillis) return emptyList()
    val offsetsByTodo = snapshot.todoReminders.groupBy({ it.todoId }, { it.offsetMinutes })
    return snapshot.todos
        .filter { it.deletedAt == null && it.completedAt == null && it.deadlineEpochDay != null }
        .flatMap { todo ->
            val minute = todo.deadlineMinute ?: snapshot.settings.defaultAllDayReminderMinute
            val dueAt = LocalDate.ofEpochDay(todo.deadlineEpochDay!!).atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant().toEpochMilli()
            offsetsByTodo[todo.id].orEmpty().distinct().map { offset -> DueReminder(todo.id, todo.title, dueAt, dueAt - offset * 60_000L) }
        }
        .filter { it.remindAtMillis in (fromMillis + 1)..toMillis }
        .sortedBy { it.remindAtMillis }
}
