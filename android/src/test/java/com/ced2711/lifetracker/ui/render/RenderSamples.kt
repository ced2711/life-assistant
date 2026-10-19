package com.ced2711.lifetracker.ui.render

import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.data.local.DiaryEntryEntity
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.NoteEntity
import com.ced2711.lifetracker.data.local.NoteFolderEntity
import com.ced2711.lifetracker.data.local.SubtaskEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.TodoPriority
import java.time.LocalDate
import java.time.ZoneId

/** Sample data shared by the render scenes: a believable week of somebody using the app. */
internal object RenderSamples {
    val today: LocalDate = LocalDate.now()
    private val day = today.toEpochDay()
    private val now = System.currentTimeMillis()
    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val settings = AppSettings()

    val categories = listOf(
        CategoryEntity(id = 1, name = "Home"),
        CategoryEntity(id = 2, name = "Work"),
        CategoryEntity(id = 3, name = "Clients", parentId = 2),
    )

    val todos = listOf(
        TodoEntity(id = 1, title = "Renew passport", description = "Renew passport", deadlineEpochDay = day - 2, priority = TodoPriority.URGENT, categoryId = 1, tagsCsv = "errands"),
        TodoEntity(id = 2, title = "Water the plants", description = "Water the plants", deadlineEpochDay = day, deadlineMinute = 18 * 60, seriesId = 1),
        TodoEntity(id = 3, title = "Send the quarterly report to the client", description = "Send the quarterly report to the client", deadlineEpochDay = day, priority = TodoPriority.HIGH, categoryId = 3, tagsCsv = "report,q3"),
        TodoEntity(id = 4, title = "Morning walk", description = "Morning walk", deadlineEpochDay = day, priority = TodoPriority.MEDIUM, tagsCsv = "health"),
        TodoEntity(id = 5, title = "Plan the week", description = "Plan the week", deadlineEpochDay = day + 1, priority = TodoPriority.LOW, categoryId = 2),
        TodoEntity(id = 6, title = "Book flights for the holidays", description = "Book flights for the holidays", deadlineEpochDay = day + 4, deadlineMinute = 9 * 60 + 30),
        TodoEntity(id = 7, title = "Read a chapter", description = "Read a chapter"),
        TodoEntity(id = 8, title = "Call the dentist", description = "Call the dentist", deadlineEpochDay = day, completedAt = at(today, 9)),
        TodoEntity(id = 9, title = "Buy groceries", description = "Buy groceries", deadlineEpochDay = day - 1, completedAt = at(today.minusDays(1), 17)),
    )
    val activeTodos = todos.filter { it.completedAt == null }
    val completedTodos = todos.filter { it.completedAt != null }

    val subtasks = listOf(
        SubtaskEntity(id = 1, todoId = 1, description = "Photos", isCompleted = true),
        SubtaskEntity(id = 2, todoId = 1, description = "Fill in the form", sortOrder = 1),
        SubtaskEntity(id = 3, todoId = 1, description = "Book an appointment", sortOrder = 2),
        SubtaskEntity(id = 4, todoId = 2, description = "Balcony"),
        SubtaskEntity(id = 5, todoId = 2, description = "Kitchen herbs", sortOrder = 1),
    )
    val subtasksByTodo = subtasks.groupBy { it.todoId }

    val ledger = listOf(
        LedgerEntryEntity(id = 1, type = LedgerType.EXPENSE, amountCents = 2_485, epochDay = day, minuteOfDay = 12 * 60 + 5, merchant = "Neighborhood Market", note = "Groceries", tagsCsv = "food"),
        LedgerEntryEntity(id = 2, type = LedgerType.EXPENSE, amountCents = 1_250, epochDay = day, minuteOfDay = 8 * 60 + 40, merchant = "Cafe", tagsCsv = "food"),
        LedgerEntryEntity(id = 3, type = LedgerType.EXPENSE, amountCents = 145_000, epochDay = day - 1, minuteOfDay = 0, merchant = "Rent", tagsCsv = "home", seriesId = 1),
        LedgerEntryEntity(id = 4, type = LedgerType.INCOME, amountCents = 330_000, epochDay = day - 3, minuteOfDay = 9 * 60, merchant = "Salary", tagsCsv = "work"),
        LedgerEntryEntity(id = 5, type = LedgerType.EXPENSE, amountCents = 6_420, epochDay = day - 5, minuteOfDay = 19 * 60 + 15, merchant = "Bookshop", note = "Birthday present", tagsCsv = "gifts"),
        LedgerEntryEntity(id = 6, type = LedgerType.EXPENSE, amountCents = 3_299, epochDay = day - 8, minuteOfDay = 20 * 60, merchant = "Pharmacy", tagsCsv = "health"),
    )

    val noteFolders = listOf(
        NoteFolderEntity(id = 1, name = "Projects"),
        NoteFolderEntity(id = 2, name = "Life Assistant", parentId = 1),
        NoteFolderEntity(id = 3, name = "Recipes"),
    )

    val notes = listOf(
        NoteEntity(id = 1, folderId = 2, title = "Release checklist", body = "Verify the installer, the encrypted backup round trip and the calendar.\nThen write the release notes.", pinned = true, updatedAt = now),
        NoteEntity(id = 2, folderId = 3, title = "Tomato soup", body = "Tomatoes, onion, garlic, basil. Roast everything first.", updatedAt = now - 86_400_000L),
        NoteEntity(id = 3, title = "Gift ideas", body = "A good notebook\nConcert tickets\nA plant that is hard to kill", updatedAt = now - 3 * 86_400_000L),
    )

    val diary = listOf(
        DiaryEntryEntity(id = 1, epochDay = day - 1, body = "Long walk by the river. Finished the book."),
        DiaryEntryEntity(id = 2, epochDay = day - 4, body = "Busy day at work, cooked pasta in the evening."),
    )
}
