package com.ced2711.lifetracker.widget

import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.UiLanguage
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetModelTest {
    private val today: LocalDate = LocalDate.of(2026, 10, 2)
    private val day = today.toEpochDay()
    private val zone: ZoneId = ZoneId.of("UTC")
    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun model(active: List<TodoEntity>, completed: List<TodoEntity> = emptyList(), language: UiLanguage = UiLanguage.ENGLISH) =
        buildWidgetModel(active, completed, today, language, TimeFormatOption.HOUR_12, systemUses24Hour = false, zone = zone)

    @Test
    fun overdueComesFirstThenTodayByTimeAndLaterDaysAreLeftOut() {
        val model = model(
            listOf(
                TodoEntity(id = 1, title = "Tomorrow", description = "", deadlineEpochDay = day + 1),
                TodoEntity(id = 2, title = "Evening", description = "", deadlineEpochDay = day, deadlineMinute = 18 * 60),
                TodoEntity(id = 3, title = "No time", description = "", deadlineEpochDay = day, priority = TodoPriority.HIGH),
                TodoEntity(id = 4, title = "Morning", description = "", deadlineEpochDay = day, deadlineMinute = 9 * 60, seriesId = 7),
                TodoEntity(id = 5, title = "Last week", description = "", deadlineEpochDay = day - 6),
                TodoEntity(id = 6, title = "Yesterday", description = "", deadlineEpochDay = day - 1),
                TodoEntity(id = 7, title = "No date", description = ""),
            ),
        )
        assertEquals(listOf("Last week", "Yesterday", "Morning", "Evening", "No time"), model.todos.map { it.title })
        assertEquals(2, model.overdueCount)
        assertEquals(listOf("Sep 26", "Yesterday", "9:00 AM", "6:00 PM", null), model.todos.map { it.detail })
        assertTrue(model.todos[0].overdue)
        assertFalse(model.todos[2].overdue)
        assertTrue(model.todos[2].repeats)
        assertEquals("Fri, Oct 2", model.dateLabel)
        assertEquals("5 left \u00b7 2 overdue", widgetStatus(model, UiLanguage.ENGLISH))
    }

    @Test
    fun progressCountsWhatWasFinishedToday() {
        val model = model(
            active = listOf(TodoEntity(id = 1, title = "Open", description = "", deadlineEpochDay = day)),
            completed = listOf(
                TodoEntity(id = 2, title = "Done today", description = "", completedAt = at(today, 9)),
                TodoEntity(id = 3, title = "Done today too", description = "", completedAt = at(today, 23)),
                TodoEntity(id = 4, title = "Done yesterday", description = "", completedAt = at(today.minusDays(1), 23)),
            ),
        )
        assertEquals(2, model.doneToday)
        assertEquals(2f / 3f, model.progress, 0.0001f)
        assertEquals("1 left", widgetStatus(model, UiLanguage.ENGLISH))
    }

    @Test
    fun anEmptyDaySaysSoInBothLanguages() {
        val nothing = model(emptyList())
        assertEquals(0f, nothing.progress, 0f)
        assertEquals("Nothing due today", widgetStatus(nothing, UiLanguage.ENGLISH))
        val done = model(emptyList(), listOf(TodoEntity(id = 1, title = "x", description = "", completedAt = at(today, 8))), UiLanguage.SIMPLIFIED_CHINESE)
        assertEquals(1f, done.progress, 0f)
        assertEquals("\u4eca\u5929\u90fd\u5b8c\u6210\u4e86", widgetStatus(done, UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals("10\u67082\u65e5 \u5468\u4e94", done.dateLabel)
    }
}
