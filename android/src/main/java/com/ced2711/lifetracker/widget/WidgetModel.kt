package com.ced2711.lifetracker.widget

import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.ui.localization.translateUiText
import com.ced2711.lifetracker.ui.localization.uiLocale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

/*
 * What the home-screen widget shows, worked out from the todos. No drawing here, so it can be
 * tested without a launcher.
 */

/** One line of the widget. [detail] is the due time, or the day for something overdue. */
internal data class WidgetTodo(
    val id: Long,
    val title: String,
    val detail: String?,
    val overdue: Boolean,
    val priority: TodoPriority,
    val repeats: Boolean,
)

internal data class WidgetModel(
    /** "Fri, Oct 2" in the user's language. */
    val dateLabel: String,
    /** Overdue first (oldest first), then today's by time, then by priority. */
    val todos: List<WidgetTodo>,
    val overdueCount: Int,
    val doneToday: Int,
) {
    val left: Int get() = todos.size

    /** Share of today's work that is done, for the thin bar under the header. */
    val progress: Float get() = if (left + doneToday == 0) 0f else doneToday.toFloat() / (left + doneToday)
}

internal fun buildWidgetModel(
    active: List<TodoEntity>,
    completed: List<TodoEntity>,
    today: LocalDate,
    language: UiLanguage,
    timeFormat: TimeFormatOption,
    systemUses24Hour: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
): WidgetModel {
    val locale = uiLocale(language)
    val todayDay = today.toEpochDay()
    val due = active
        .filter { it.deadlineEpochDay != null && it.deadlineEpochDay <= todayDay }
        .sortedWith(
            compareBy<TodoEntity> { it.deadlineEpochDay }
                .thenBy { it.deadlineMinute ?: Int.MAX_VALUE }
                .thenByDescending { it.priority.ordinal }
                .thenBy { it.id },
        )
    val todos = due.map { todo ->
        val day = requireNotNull(todo.deadlineEpochDay)
        val overdue = day < todayDay
        val time = todo.deadlineMinute?.let { UserFormatting.formatMinuteOfDay(it, timeFormat, systemUses24Hour, locale) }
        WidgetTodo(
            id = todo.id,
            title = todo.title,
            detail = when {
                !overdue -> time
                day == todayDay - 1 -> translateUiText("Yesterday", language)
                else -> shortDate(LocalDate.ofEpochDay(day), language)
            },
            overdue = overdue,
            priority = todo.priority,
            repeats = todo.seriesId != null,
        )
    }
    return WidgetModel(
        dateLabel = when (language) {
            UiLanguage.SIMPLIFIED_CHINESE -> "${today.monthValue}月${today.dayOfMonth}日 " + today.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            UiLanguage.ENGLISH -> today.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) + ", " + shortDate(today, language)
        },
        todos = todos,
        overdueCount = todos.count { it.overdue },
        doneToday = completed.count { todo ->
            todo.completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today } == true
        },
    )
}

private fun shortDate(date: LocalDate, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> "${date.monthValue}月${date.dayOfMonth}日"
    UiLanguage.ENGLISH -> date.month.getDisplayName(TextStyle.SHORT, uiLocale(language)) + " " + date.dayOfMonth
}

/** "3 left", "All done", "Nothing due": the widget's one-line state. */
internal fun widgetStatus(model: WidgetModel, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> when {
        model.left > 0 && model.overdueCount > 0 -> "剩 ${model.left} 项 · 逾期 ${model.overdueCount}"
        model.left > 0 -> "剩 ${model.left} 项"
        model.doneToday > 0 -> "今天都完成了"
        else -> "今天没有到期的待办"
    }
    UiLanguage.ENGLISH -> when {
        model.left > 0 && model.overdueCount > 0 -> "${model.left} left · ${model.overdueCount} overdue"
        model.left > 0 -> "${model.left} left"
        model.doneToday > 0 -> "All done for today"
        else -> "Nothing due today"
    }
}
