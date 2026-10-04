package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.NoteEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.MAX_DIARY_LENGTH
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import com.ced2711.lifetracker.domain.model.TodoDraft
import com.ced2711.lifetracker.domain.model.TodoPriority
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** A tool an AI assistant can call; [readOnly] tools never change data. */
data class DesktopAgentTool(
    val name: String,
    val title: String,
    val description: String,
    val inputSchema: JsonObject,
    val readOnly: Boolean,
    val destructive: Boolean = false,
)

/** A tool call that cannot be carried out; the message is shown to the assistant. */
class DesktopAgentException(message: String) : Exception(message)

/**
 * What AI assistants on this PC may do with Life Assistant: read and change todos, ledger
 * entries, notes, diary pages, the daily checklist and categories. Vault and Confessional are never reachable.
 * Dates are ISO (2026-10-02), times 24-hour (18:30), amounts in dollars ("12.50").
 */
class DesktopAgentTools(
    private val store: DesktopDataStore,
    private val today: () -> LocalDate = LocalDate::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    val definitions: List<DesktopAgentTool> get() = TOOLS

    /** Runs [name]; [allowChanges] false refuses every tool that would change data. */
    suspend fun call(name: String, arguments: JsonObject, allowChanges: Boolean): JsonElement {
        val tool = TOOLS.firstOrNull { it.name == name } ?: throw DesktopAgentException("Unknown tool: $name")
        if (!tool.readOnly && !allowChanges) {
            throw DesktopAgentException("Changes by AI assistants are turned off in Life Assistant settings. Only reading is allowed.")
        }
        val args = Args(arguments)
        return when (name) {
            "get_overview" -> overview(args)
            "list_todos" -> listTodos(args)
            "get_todo" -> todoJson(snapshot(), todo(snapshot(), args.long("id")), full = true)
            "create_todo" -> createTodo(args)
            "update_todo" -> updateTodo(args)
            "complete_todo" -> completeTodo(args)
            "delete_todo" -> deleteTodo(args)
            "list_ledger_entries" -> listLedger(args)
            "ledger_summary" -> ledgerSummary(args)
            "create_ledger_entry" -> createLedger(args)
            "update_ledger_entry" -> updateLedger(args)
            "delete_ledger_entry" -> deleteLedger(args)
            "list_notes" -> listNotes(args)
            "get_note" -> noteJson(snapshot(), note(snapshot(), args.long("id")), full = true)
            "create_note" -> createNote(args)
            "update_note" -> updateNote(args)
            "delete_note" -> deleteNote(args)
            "get_diary" -> getDiary(args)
            "write_diary" -> writeDiary(args)
            "get_day" -> day(args)
            "get_checklist" -> checklist(args)
            "check_checklist_item" -> checkChecklistItem(args)
            "add_checklist_item" -> addChecklistItem(args)
            "remove_checklist_item" -> removeChecklistItem(args)
            "list_categories_and_tags" -> categoriesAndTags()
            "create_category" -> createCategory(args)
            "search" -> search(args)
            else -> throw DesktopAgentException("Unknown tool: $name")
        }
    }

    // ---- Reading -------------------------------------------------------------------------

    private fun overview(args: Args): JsonElement {
        val snapshot = snapshot()
        val day = args.date("date") ?: today()
        val epoch = day.toEpochDay()
        val open = liveTodos(snapshot).filter { it.completedAt == null }
        val month = day.withDayOfMonth(1)
        return buildJsonObject {
            put("date", day.toString())
            put("weekday", day.dayOfWeek.name.lowercase().replaceFirstChar(Char::uppercase))
            put("todos_due", JsonArray(open.filter { it.deadlineEpochDay == epoch }.sortedForList().map { todoJson(snapshot, it) }))
            put("todos_overdue", JsonArray(open.filter { (it.deadlineEpochDay ?: Long.MAX_VALUE) < epoch }.sortedForList().map { todoJson(snapshot, it) }))
            put("todos_next_7_days", JsonArray(open.filter { it.deadlineEpochDay in (epoch + 1)..(epoch + 7) }.sortedForList().map { todoJson(snapshot, it) }))
            put("todos_completed_on_date", liveTodos(snapshot).count { it.completedAt?.let(::dayOf) == day })
            put("open_todos_total", open.size)
            put("ledger_on_date", totals(liveLedger(snapshot).filter { it.epochDay == epoch }))
            put("ledger_month_to_date", totals(liveLedger(snapshot).filter { it.epochDay in month.toEpochDay()..epoch }))
            put("diary_written", snapshot.diaryEntries.any { it.epochDay == epoch })
            val ticked = snapshot.checklistChecks.filter { it.epochDay == epoch }.mapTo(HashSet()) { it.itemId }
            put("daily_checklist_done", snapshot.checklistItems.count { it.id in ticked })
            put("daily_checklist_total", snapshot.checklistItems.size)
        }
    }

    /** The daily checklist and what is ticked on a date (default today). */
    private fun checklist(args: Args): JsonElement {
        val snapshot = snapshot()
        val day = args.date("date") ?: today()
        val ticked = snapshot.checklistChecks.filter { it.epochDay == day.toEpochDay() }.mapTo(HashSet()) { it.itemId }
        val items = snapshot.checklistItems.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        return buildJsonObject {
            put("date", day.toString())
            put("items", JsonArray(items.map { item -> buildJsonObject { put("id", item.id); put("title", item.title); put("done", item.id in ticked) } }))
            put("done", items.count { it.id in ticked })
            put("total", items.size)
        }
    }

    private fun checklistItem(snapshot: BackupSnapshot, args: Args): com.ced2711.lifetracker.data.local.ChecklistItemEntity {
        if (args.has("id")) {
            val id = args.long("id")
            return snapshot.checklistItems.firstOrNull { it.id == id } ?: throw DesktopAgentException("No checklist item with id $id")
        }
        val title = args.requireString("title").trim().lowercase()
        return snapshot.checklistItems.firstOrNull { it.title.trim().lowercase() == title }
            ?: throw DesktopAgentException("No checklist item called \"${args.requireString("title")}\"")
    }

    private suspend fun checkChecklistItem(args: Args): JsonElement {
        val item = checklistItem(snapshot(), args)
        val day = args.date("date") ?: today()
        val done = args.boolean("done") ?: true
        if (!store.setChecklistChecked(item.id, day.toEpochDay(), done)) throw DesktopAgentException("The checklist could not be saved")
        return saved("checklist", buildJsonObject { put("id", item.id); put("title", item.title); put("date", day.toString()); put("done", done) })
    }

    private suspend fun addChecklistItem(args: Args): JsonElement {
        val title = args.requireString("title").trim()
        snapshot().checklistItems.firstOrNull { it.title.trim().equals(title, ignoreCase = true) }?.let { existing ->
            return buildJsonObject { put("saved", "nothing"); put("id", existing.id); put("title", existing.title); put("note", "Already on the checklist") }
        }
        if (!store.addChecklistItem(title)) throw DesktopAgentException("The checklist could not be saved")
        val added = snapshot().checklistItems.maxBy { it.id }
        return saved("checklist", buildJsonObject { put("id", added.id); put("title", added.title) })
    }

    private suspend fun removeChecklistItem(args: Args): JsonElement {
        val item = checklistItem(snapshot(), args)
        if (!store.deleteChecklistItem(item.id)) throw DesktopAgentException("The checklist item could not be removed")
        return buildJsonObject { put("deleted", true); put("id", item.id); put("title", item.title) }
    }

    private fun listTodos(args: Args): JsonElement {
        val snapshot = snapshot()
        val epoch = today().toEpochDay()
        val view = args.string("view") ?: "open"
        val search = args.string("search")?.trim()?.removePrefix("#")?.lowercase()
        val category = args.string("category")?.let { categoryId(snapshot, it, create = false) }
        val tag = args.string("tag")?.trim()?.removePrefix("#")?.lowercase()
        val priority = args.string("priority")?.let { priority(it) }
        val limit = args.int("limit")?.coerceIn(1, 500) ?: 50
        val matches = liveTodos(snapshot).filter { todo ->
            val due = todo.deadlineEpochDay
            val open = todo.completedAt == null
            when (view) {
                "open" -> open
                "today" -> open && due == epoch
                "overdue" -> open && due != null && due < epoch
                "upcoming" -> open && due != null && due in epoch..(epoch + 7)
                "no_date" -> open && due == null
                "completed" -> !open
                "all" -> true
                else -> throw DesktopAgentException("view must be one of open, today, overdue, upcoming, no_date, completed, all")
            }
        }.filter { todo ->
            (search == null || listOf(todo.title, todo.description, todo.tagsCsv).any { it.lowercase().contains(search) }) &&
                (category == null || todo.categoryId == category) &&
                (tag == null || tags(todo.tagsCsv).any { it.lowercase() == tag }) &&
                (priority == null || todo.priority == priority)
        }.let { list -> if (view == "completed") list.sortedByDescending { it.completedAt } else list.sortedForList() }
        return buildJsonObject {
            put("count", matches.size)
            put("todos", JsonArray(matches.take(limit).map { todoJson(snapshot, it) }))
            if (matches.size > limit) put("note", "Showing the first $limit; use limit or filters for more.")
        }
    }

    private fun listLedger(args: Args): JsonElement {
        val snapshot = snapshot()
        val (from, to) = range(args, defaultFrom = today().withDayOfMonth(1))
        val type = args.string("type")?.let(::ledgerType)
        val search = args.string("search")?.trim()?.removePrefix("#")?.lowercase()
        val tag = args.string("tag")?.trim()?.removePrefix("#")?.lowercase()
        val limit = args.int("limit")?.coerceIn(1, 1000) ?: 100
        val entries = liveLedger(snapshot).filter { it.epochDay in from.toEpochDay()..to.toEpochDay() }
            .filter { entry ->
                (type == null || entry.type == type) &&
                    (search == null || listOf(entry.merchant, entry.note, entry.tagsCsv).any { it.lowercase().contains(search) }) &&
                    (tag == null || tags(entry.tagsCsv).any { it.lowercase() == tag })
            }
            .sortedWith(compareByDescending<LedgerEntryEntity> { it.epochDay }.thenByDescending { it.minuteOfDay })
        return buildJsonObject {
            put("from", from.toString())
            put("to", to.toString())
            put("count", entries.size)
            put("totals", totals(entries))
            put("entries", JsonArray(entries.take(limit).map(::ledgerJson)))
            if (entries.size > limit) put("note", "Showing the newest $limit; narrow the dates or use limit for more.")
        }
    }

    private fun ledgerSummary(args: Args): JsonElement {
        val snapshot = snapshot()
        val (from, to) = range(args, defaultFrom = today().withDayOfMonth(1))
        val entries = liveLedger(snapshot).filter { it.epochDay in from.toEpochDay()..to.toEpochDay() }
        val expenses = entries.filter { it.type == LedgerType.EXPENSE }
        val byTag = expenses.flatMap { entry -> tags(entry.tagsCsv).ifEmpty { listOf("(untagged)") }.map { it to entry.amountCents } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
            .entries.sortedByDescending { it.value }
        val days = (to.toEpochDay() - from.toEpochDay() + 1).coerceAtLeast(1)
        return buildJsonObject {
            put("from", from.toString())
            put("to", to.toString())
            put("totals", totals(entries))
            put("average_daily_spending", money(expenses.sumOf { it.amountCents } / days))
            expenses.maxByOrNull { it.amountCents }?.let { put("largest_expense", ledgerJson(it)) }
            put("spending_by_tag", buildJsonArray {
                byTag.forEach { (tag, cents) -> add(buildJsonObject { put("tag", tag); put("amount", money(cents)) }) }
            })
        }
    }

    private fun listNotes(args: Args): JsonElement {
        val snapshot = snapshot()
        val search = args.string("search")?.trim()?.lowercase()
        val folder = args.string("folder")?.let { folderId(snapshot, it, create = false) }
        val limit = args.int("limit")?.coerceIn(1, 500) ?: 50
        val notes = snapshot.notes
            .filter { folder == null || it.folderId == folder }
            .filter { search == null || it.title.lowercase().contains(search) || it.body.lowercase().contains(search) }
            .sortedWith(compareByDescending<NoteEntity> { it.pinned }.thenByDescending { it.updatedAt })
        return buildJsonObject {
            put("count", notes.size)
            put("notes", JsonArray(notes.take(limit).map { noteJson(snapshot, it, full = false) }))
            put("folders", JsonArray(snapshot.noteFolders.map { JsonPrimitive(noteFolderPath(it.id, snapshot)) }.sortedBy { it.content }))
        }
    }

    private fun getDiary(args: Args): JsonElement {
        val snapshot = snapshot()
        val single = args.date("date")
        val (from, to) = if (single != null) single to single else range(args, defaultFrom = today().minusDays(6))
        val pages = snapshot.diaryEntries.filter { it.epochDay in from.toEpochDay()..to.toEpochDay() }.sortedBy { it.epochDay }
        return buildJsonObject {
            put("pages", buildJsonArray {
                pages.forEach { page -> add(buildJsonObject { put("date", LocalDate.ofEpochDay(page.epochDay).toString()); put("text", page.body) }) }
            })
            if (pages.isEmpty()) put("note", "No diary pages in this range.")
        }
    }

    private fun day(args: Args): JsonElement {
        val snapshot = snapshot()
        val day = args.date("date") ?: today()
        val epoch = day.toEpochDay()
        return buildJsonObject {
            put("date", day.toString())
            put("todos", JsonArray(liveTodos(snapshot).filter { it.deadlineEpochDay == epoch }.sortedForList().map { todoJson(snapshot, it) }))
            val entries = liveLedger(snapshot).filter { it.epochDay == epoch }.sortedBy { it.minuteOfDay }
            put("ledger_entries", JsonArray(entries.map(::ledgerJson)))
            put("ledger_totals", totals(entries))
            snapshot.diaryEntries.firstOrNull { it.epochDay == epoch }?.let { put("diary", it.body) }
        }
    }

    private fun categoriesAndTags(): JsonElement {
        val snapshot = snapshot()
        return buildJsonObject {
            put("todo_categories", JsonArray(snapshot.categories.map { JsonPrimitive(categoryPath(it.id, snapshot)) }.sortedBy { it.content }))
            put("todo_tags", JsonArray(liveTodos(snapshot).flatMap { tags(it.tagsCsv) }.distinct().sorted().map(::JsonPrimitive)))
            put("ledger_tags", JsonArray(liveLedger(snapshot).flatMap { tags(it.tagsCsv) }.distinct().sorted().map(::JsonPrimitive)))
            put("note_folders", JsonArray(snapshot.noteFolders.map { JsonPrimitive(noteFolderPath(it.id, snapshot)) }.sortedBy { it.content }))
        }
    }

    private fun search(args: Args): JsonElement {
        val snapshot = snapshot()
        val query = args.requireString("query").trim().removePrefix("#").lowercase()
        if (query.isEmpty()) throw DesktopAgentException("query must not be empty")
        val limit = args.int("limit")?.coerceIn(1, 200) ?: 20
        return buildJsonObject {
            put("todos", JsonArray(liveTodos(snapshot).filter { listOf(it.title, it.description, it.tagsCsv).any { text -> text.lowercase().contains(query) } }.sortedForList().take(limit).map { todoJson(snapshot, it) }))
            put("ledger_entries", JsonArray(liveLedger(snapshot).filter { listOf(it.merchant, it.note, it.tagsCsv).any { text -> text.lowercase().contains(query) } }.sortedByDescending { it.epochDay }.take(limit).map(::ledgerJson)))
            put("notes", JsonArray(snapshot.notes.filter { it.title.lowercase().contains(query) || it.body.lowercase().contains(query) }.sortedByDescending { it.updatedAt }.take(limit).map { noteJson(snapshot, it, full = false) }))
            put("diary_dates", JsonArray(snapshot.diaryEntries.filter { it.body.lowercase().contains(query) }.sortedByDescending { it.epochDay }.take(limit).map { JsonPrimitive(LocalDate.ofEpochDay(it.epochDay).toString()) }))
        }
    }

    // ---- Changing ------------------------------------------------------------------------

    private suspend fun createTodo(args: Args): JsonElement {
        val description = args.string("description")?.takeIf(String::isNotBlank) ?: args.requireString("title")
        val due = args.date("due_date")
        val time = args.time("due_time")
        if (time != null && due == null) throw DesktopAgentException("due_time needs due_date")
        val repeat = recurrence(args, due)
        val category = ensureCategories(args)
        val snapshot = snapshot()
        val draft = TodoDraft(
            title = args.string("title").orEmpty().takeIf { it != description }.orEmpty(),
            description = description,
            categoryId = category,
            deadlineEpochDay = due?.toEpochDay(),
            deadlineMinute = time?.let { it.hour * 60 + it.minute },
            priority = args.string("priority")?.let(::priority) ?: TodoPriority.NONE,
            tags = args.stringList("tags"),
            reminderOffsetsMinutes = args.longList("reminders_minutes_before")
                ?: snapshot.settings.defaultReminderOffsetsMinutes.toList().takeIf { due != null }.orEmpty(),
            subtasks = args.stringList("subtasks"),
            recurrence = repeat,
        )
        val id = store.saveTodo(draft, SeriesEditScope.ONLY_THIS_OCCURRENCE)
            ?: throw DesktopAgentException("The todo could not be saved")
        return saved("todo", todoJson(snapshot(), todo(snapshot(), id), full = true))
    }

    private suspend fun updateTodo(args: Args): JsonElement {
        var snapshot = snapshot()
        val existing = todo(snapshot, args.long("id"))
        val category = ensureCategories(args)
        snapshot = snapshot()
        val due = if (args.has("due_date")) args.date("due_date") else existing.deadlineEpochDay?.let(LocalDate::ofEpochDay)
        val time = if (args.has("due_time")) args.time("due_time") else existing.deadlineMinute?.let { LocalTime.of(it / 60, it % 60) }
        if (time != null && due == null) throw DesktopAgentException("due_time needs due_date")
        val series = existing.seriesId?.let { id -> snapshot.todoSeries.firstOrNull { it.id == id } }
        val currentRule = series?.let { RecurrenceRule(it.recurrenceUnit, it.intervalCount, it.endEpochDay) }
        val scope = scope(args)
        val rule = when {
            args.has("repeat") -> recurrence(args, due)
            due == null -> null
            else -> currentRule
        }
        val draft = TodoDraft(
            id = existing.id,
            title = if (args.has("title")) args.string("title").orEmpty() else existing.title,
            description = args.string("description")?.takeIf(String::isNotBlank) ?: existing.description,
            categoryId = when {
                category != null -> category
                args.has("category") && args.string("category").isNullOrBlank() -> null
                else -> existing.categoryId
            },
            deadlineEpochDay = due?.toEpochDay(),
            deadlineMinute = time?.let { it.hour * 60 + it.minute }.takeIf { due != null },
            priority = args.string("priority")?.let(::priority) ?: existing.priority,
            tags = if (args.has("tags")) args.stringList("tags") else tags(existing.tagsCsv),
            reminderOffsetsMinutes = args.longList("reminders_minutes_before")
                ?: snapshot.todoReminders.filter { it.todoId == existing.id }.map { it.offsetMinutes }.takeIf { due != null }.orEmpty(),
            subtasks = if (args.has("subtasks")) args.stringList("subtasks") else snapshot.subtasks.filter { it.todoId == existing.id }.sortedBy { it.sortOrder }.map { it.description },
            recurrence = rule,
        )
        if (series != null && args.has("repeat") && scope == SeriesEditScope.ONLY_THIS_OCCURRENCE) {
            throw DesktopAgentException("Changing how a todo repeats needs scope \"this_and_future\".")
        }
        val id = store.saveTodo(draft, scope) ?: throw DesktopAgentException("The todo could not be saved")
        // Keep completed subtasks ticked when the subtask list itself was not changed.
        if (!args.has("subtasks")) {
            val before = snapshot.subtasks.filter { it.todoId == existing.id && it.isCompleted }.map { it.description }.toSet()
            snapshot().subtasks.filter { it.todoId == id && it.description in before && !it.isCompleted }
                .forEach { store.setSubtaskCompleted(it.id, true) }
        }
        return saved("todo", todoJson(snapshot(), todo(snapshot(), id), full = true))
    }

    private suspend fun completeTodo(args: Args): JsonElement {
        val existing = todo(snapshot(), args.long("id"))
        val completed = args.boolean("completed") ?: true
        val subtasks = args.boolean("complete_subtasks") ?: true
        if (!store.setTodoCompleted(existing.id, completed, completeSubtasks = completed && subtasks)) {
            throw DesktopAgentException("The todo could not be changed")
        }
        return saved("todo", todoJson(snapshot(), todo(snapshot(), existing.id), full = true))
    }

    private suspend fun deleteTodo(args: Args): JsonElement {
        val existing = todo(snapshot(), args.long("id"))
        store.deleteTodoWithUndo(existing.id, scope(args)) ?: throw DesktopAgentException("The todo could not be deleted")
        return buildJsonObject { put("deleted", true); put("id", existing.id); put("title", existing.title) }
    }

    private suspend fun createLedger(args: Args): JsonElement {
        val now = LocalTime.now(zone)
        val day = args.date("date") ?: today()
        val time = args.time("time") ?: if (day == today()) now else LocalTime.NOON
        val draft = LedgerDraft(
            type = ledgerType(args.requireString("type")),
            amountCents = cents(args.requireAmount("amount")),
            epochDay = day.toEpochDay(),
            minuteOfDay = time.hour * 60 + time.minute,
            note = args.string("note").orEmpty(),
            merchant = args.string("merchant").orEmpty(),
            tags = args.stringList("tags"),
            recurrence = recurrence(args, day),
        )
        val id = store.saveLedger(draft, SeriesEditScope.ONLY_THIS_OCCURRENCE) ?: throw DesktopAgentException("The entry could not be saved")
        return saved("ledger_entry", ledgerJson(ledger(snapshot(), id)))
    }

    private suspend fun updateLedger(args: Args): JsonElement {
        val snapshot = snapshot()
        val existing = ledger(snapshot, args.long("id"))
        val day = args.date("date") ?: LocalDate.ofEpochDay(existing.epochDay)
        val time = args.time("time") ?: LocalTime.of(existing.minuteOfDay / 60, existing.minuteOfDay % 60)
        if (args.has("repeat")) throw DesktopAgentException("Repeating ledger schedules are changed in the app (Ledger → Recurring).")
        val draft = LedgerDraft(
            id = existing.id,
            type = args.string("type")?.let(::ledgerType) ?: existing.type,
            amountCents = args.amount("amount")?.let(::cents) ?: existing.amountCents,
            epochDay = day.toEpochDay(),
            minuteOfDay = time.hour * 60 + time.minute,
            note = if (args.has("note")) args.string("note").orEmpty() else existing.note,
            merchant = if (args.has("merchant")) args.string("merchant").orEmpty() else existing.merchant,
            tags = if (args.has("tags")) args.stringList("tags") else tags(existing.tagsCsv),
            recurrence = null,
        )
        val id = store.saveLedger(draft, SeriesEditScope.ONLY_THIS_OCCURRENCE) ?: throw DesktopAgentException("The entry could not be saved")
        return saved("ledger_entry", ledgerJson(ledger(snapshot(), id)))
    }

    private suspend fun deleteLedger(args: Args): JsonElement {
        val existing = ledger(snapshot(), args.long("id"))
        store.deleteLedgerWithUndo(existing.id, scope(args)) ?: throw DesktopAgentException("The entry could not be deleted")
        return buildJsonObject { put("deleted", true); put("id", existing.id) }
    }

    private suspend fun createNote(args: Args): JsonElement {
        val body = args.string("body").orEmpty()
        val title = args.string("title").orEmpty()
        if (body.isBlank() && title.isBlank()) throw DesktopAgentException("A note needs a title or a body")
        val folder = args.string("folder")?.let { path -> ensureFolder(path) }
        val id = store.saveNote(null, folder, title, body, args.boolean("pinned") ?: false)
            ?: throw DesktopAgentException("The note could not be saved")
        return saved("note", noteJson(snapshot(), note(snapshot(), id), full = true))
    }

    private suspend fun updateNote(args: Args): JsonElement {
        val existing = note(snapshot(), args.long("id"))
        val body = when {
            args.has("append") -> existing.body.trimEnd().let { if (it.isEmpty()) "" else "$it\n\n" } + args.string("append").orEmpty()
            args.has("body") -> args.string("body").orEmpty()
            else -> existing.body
        }
        val folder = if (args.has("folder")) args.string("folder")?.takeIf(String::isNotBlank)?.let { ensureFolder(it) } else existing.folderId
        val id = store.saveNote(
            existing.id,
            folder,
            if (args.has("title")) args.string("title").orEmpty() else existing.title,
            body,
            args.boolean("pinned") ?: existing.pinned,
        ) ?: throw DesktopAgentException("The note could not be saved")
        return saved("note", noteJson(snapshot(), note(snapshot(), id), full = true))
    }

    private suspend fun deleteNote(args: Args): JsonElement {
        val existing = note(snapshot(), args.long("id"))
        if (!store.deleteNote(existing.id)) throw DesktopAgentException("The note could not be deleted")
        return buildJsonObject { put("deleted", true); put("id", existing.id); put("title", existing.title) }
    }

    private suspend fun writeDiary(args: Args): JsonElement {
        val day = args.date("date") ?: today()
        val text = args.requireString("text")
        val existing = snapshot().diaryEntries.firstOrNull { it.epochDay == day.toEpochDay() }?.body.orEmpty()
        val body = when (args.string("mode") ?: "append") {
            "append" -> existing.trimEnd().let { if (it.isEmpty()) "" else "$it\n\n" } + text
            "replace" -> text
            else -> throw DesktopAgentException("mode must be append or replace")
        }
        if (body.length > MAX_DIARY_LENGTH) throw DesktopAgentException("The diary page would be too long")
        if (!store.upsertDiary(day.toEpochDay(), body)) throw DesktopAgentException("The diary page could not be saved")
        return saved("diary", buildJsonObject { put("date", day.toString()); put("text", body) })
    }

    private suspend fun createCategory(args: Args): JsonElement {
        val path = args.requireString("name")
        val id = categoryId(snapshot(), path, create = false) ?: run {
            ensureCategoryPath(path)
            categoryId(snapshot(), path, create = false)
        } ?: throw DesktopAgentException("The category could not be created")
        return saved("category", buildJsonObject { put("id", id); put("path", categoryPath(id, snapshot())) })
    }

    // ---- Helpers -------------------------------------------------------------------------

    private fun snapshot(): BackupSnapshot = store.currentSnapshot()
        ?: throw DesktopAgentException("Life Assistant is locked. Ask the user to open and unlock it.")

    private fun liveTodos(snapshot: BackupSnapshot) = snapshot.todos.filter { it.deletedAt == null }

    private fun liveLedger(snapshot: BackupSnapshot) = snapshot.ledgerEntries.filter { it.deletedAt == null }

    private fun todo(snapshot: BackupSnapshot, id: Long) = liveTodos(snapshot).firstOrNull { it.id == id }
        ?: throw DesktopAgentException("No todo with id $id")

    private fun ledger(snapshot: BackupSnapshot, id: Long) = liveLedger(snapshot).firstOrNull { it.id == id }
        ?: throw DesktopAgentException("No ledger entry with id $id")

    private fun note(snapshot: BackupSnapshot, id: Long) = snapshot.notes.firstOrNull { it.id == id }
        ?: throw DesktopAgentException("No note with id $id")

    private fun List<TodoEntity>.sortedForList() = sortedWith(
        compareBy<TodoEntity> { it.deadlineEpochDay ?: Long.MAX_VALUE }
            .thenBy { it.deadlineMinute ?: Int.MAX_VALUE }
            .thenByDescending { it.priority.ordinal }
            .thenBy { it.createdAt },
    )

    private fun todoJson(snapshot: BackupSnapshot, todo: TodoEntity, full: Boolean = false): JsonObject = buildJsonObject {
        put("id", todo.id)
        put("title", todo.title)
        if (full || todo.description != todo.title) put("description", todo.description)
        put("due_date", todo.deadlineEpochDay?.let { LocalDate.ofEpochDay(it).toString() })
        put("due_time", todo.deadlineMinute?.let { "%02d:%02d".format(it / 60, it % 60) })
        put("priority", todo.priority.name.lowercase())
        put("category", todo.categoryId?.let { categoryPath(it, snapshot) })
        put("tags", JsonArray(tags(todo.tagsCsv).map(::JsonPrimitive)))
        put("completed", todo.completedAt != null)
        val subtasks = snapshot.subtasks.filter { it.todoId == todo.id }.sortedBy { it.sortOrder }
        if (subtasks.isNotEmpty() || full) {
            putJsonArray("subtasks") {
                subtasks.forEach { subtask -> add(buildJsonObject { put("text", subtask.description); put("done", subtask.isCompleted) }) }
            }
        }
        todo.seriesId?.let { seriesId ->
            snapshot.todoSeries.firstOrNull { it.id == seriesId }?.let { series ->
                put("repeats", repeatText(series.recurrenceUnit, series.intervalCount, series.endEpochDay))
            }
        }
        if (full) {
            put("reminders_minutes_before", JsonArray(snapshot.todoReminders.filter { it.todoId == todo.id }.map { JsonPrimitive(it.offsetMinutes) }))
            put("attachments", snapshot.attachments.count { it.ownerId == todo.id && it.ownerType.name == "TODO" })
        }
    }

    private fun ledgerJson(entry: LedgerEntryEntity): JsonObject = buildJsonObject {
        put("id", entry.id)
        put("type", entry.type.name.lowercase())
        put("amount", money(entry.amountCents))
        put("date", LocalDate.ofEpochDay(entry.epochDay).toString())
        put("time", "%02d:%02d".format(entry.minuteOfDay / 60, entry.minuteOfDay % 60))
        if (entry.merchant.isNotBlank()) put("merchant", entry.merchant)
        if (entry.note.isNotBlank()) put("note", entry.note)
        put("tags", JsonArray(tags(entry.tagsCsv).map(::JsonPrimitive)))
        if (entry.seriesId != null) put("from_repeating_schedule", true)
    }

    private fun noteJson(snapshot: BackupSnapshot, note: NoteEntity, full: Boolean): JsonObject = buildJsonObject {
        put("id", note.id)
        put("title", note.title)
        put("folder", note.folderId?.let { noteFolderPath(it, snapshot) })
        put("pinned", note.pinned)
        put("updated", Instant.ofEpochMilli(note.updatedAt).atZone(zone).toLocalDateTime().withNano(0).toString())
        if (full) put("body", note.body) else put("preview", note.body.replace('\n', ' ').take(160))
    }

    private fun totals(entries: List<LedgerEntryEntity>): JsonObject {
        val income = entries.filter { it.type == LedgerType.INCOME }.sumOf { it.amountCents }
        val expense = entries.filter { it.type == LedgerType.EXPENSE }.sumOf { it.amountCents }
        return buildJsonObject {
            put("income", money(income))
            put("expense", money(expense))
            put("net", money(income - expense))
            put("currency", "USD")
        }
    }

    private fun saved(kind: String, value: JsonObject): JsonElement = buildJsonObject {
        put("saved", kind)
        value.forEach { (key, element) -> put(key, element) }
    }

    private fun money(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()

    private fun cents(amount: BigDecimal): Long {
        if (amount.scale() > 2 && amount.stripTrailingZeros().scale() > 2) throw DesktopAgentException("amount has more than 2 decimal places")
        val cents = amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
        if (cents !in 1L..99_999_999_999L) throw DesktopAgentException("amount must be between 0.01 and 999999999.99")
        return cents
    }

    private fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun tags(csv: String): List<String> = csv.split(',').map(String::trim).filter(String::isNotEmpty)

    private fun priority(value: String): TodoPriority = TodoPriority.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
        ?: throw DesktopAgentException("priority must be none, low, medium, high or urgent")

    private fun ledgerType(value: String): LedgerType = when (value.trim().lowercase()) {
        "expense", "spend", "spending", "out" -> LedgerType.EXPENSE
        "income", "in", "earning" -> LedgerType.INCOME
        else -> throw DesktopAgentException("type must be expense or income")
    }

    private fun scope(args: Args): SeriesEditScope = when (args.string("scope") ?: "this") {
        "this" -> SeriesEditScope.ONLY_THIS_OCCURRENCE
        "this_and_future" -> SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES
        else -> throw DesktopAgentException("scope must be this or this_and_future")
    }

    private fun recurrence(args: Args, start: LocalDate?): RecurrenceRule? {
        val repeat = args.obj("repeat") ?: return null
        if (start == null) throw DesktopAgentException("Repeating needs a date to start from")
        val unit = when (repeat["every"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
            "day", "daily" -> RecurrenceUnit.DAY
            "week", "weekly" -> RecurrenceUnit.WEEK
            "month", "monthly" -> RecurrenceUnit.MONTH
            "year", "yearly" -> RecurrenceUnit.YEAR
            else -> throw DesktopAgentException("repeat.every must be day, week, month or year")
        }
        val interval = repeat["interval"]?.jsonPrimitive?.intOrNull ?: 1
        if (interval !in 1..10_000) throw DesktopAgentException("repeat.interval must be between 1 and 10000")
        val until = repeat["until"]?.jsonPrimitive?.contentOrNull?.let { parseDate(it, "repeat.until") }
        if (until != null && until < start) throw DesktopAgentException("repeat.until is before the start date")
        return RecurrenceRule(unit, interval, until?.toEpochDay())
    }

    private fun repeatText(unit: RecurrenceUnit, interval: Int, end: Long?): String {
        val name = unit.name.lowercase()
        val every = if (interval == 1) "every $name" else "every $interval ${name}s"
        return every + (end?.let { " until ${LocalDate.ofEpochDay(it)}" } ?: "")
    }

    private fun range(args: Args, defaultFrom: LocalDate): Pair<LocalDate, LocalDate> {
        val from = args.date("from") ?: defaultFrom
        val to = args.date("to") ?: today()
        if (to < from) throw DesktopAgentException("to is before from")
        if (to.toEpochDay() - from.toEpochDay() > 3_660) throw DesktopAgentException("The range is longer than ten years")
        return from to to
    }

    /** A category given as "Parent / Child"; with [create], missing levels are made. */
    private fun categoryId(snapshot: BackupSnapshot, path: String, create: Boolean): Long? {
        val wanted = path.split('/').map(String::trim).filter(String::isNotEmpty).joinToString(" / ")
        if (wanted.isEmpty()) return null
        snapshot.categories.firstOrNull { categoryPath(it.id, snapshot).equals(wanted, ignoreCase = true) }?.let { return it.id }
        // A bare name is enough when it is unique.
        snapshot.categories.filter { it.name.equals(wanted, ignoreCase = true) }.singleOrNull()?.let { return it.id }
        if (!create) throw DesktopAgentException("No category \"$path\". Existing: " + snapshot.categories.joinToString { categoryPath(it.id, snapshot) })
        return null
    }

    /** Makes sure the category named in [args] exists and returns its id. */
    private suspend fun ensureCategories(args: Args): Long? {
        val path = args.string("category")?.takeIf(String::isNotBlank) ?: return null
        categoryId(snapshot(), path, create = true)?.let { return it }
        ensureCategoryPath(path)
        return categoryId(snapshot(), path, create = false)
    }

    private suspend fun ensureCategoryPath(path: String) {
        var parent: Long? = null
        path.split('/').map(String::trim).filter(String::isNotEmpty).forEach { name ->
            val snapshot = snapshot()
            val existing = snapshot.categories.firstOrNull { it.parentId == parent && it.name.equals(name, ignoreCase = true) }
            parent = existing?.id ?: run {
                store.addCategory(name, parent)
                snapshot().categories.firstOrNull { it.parentId == parent && it.name.equals(name, ignoreCase = true) }?.id
                    ?: throw DesktopAgentException("Could not create the category $name")
            }
        }
    }

    private fun folderId(snapshot: BackupSnapshot, path: String, create: Boolean): Long? {
        val wanted = path.split('/').map(String::trim).filter(String::isNotEmpty).joinToString(" / ")
        snapshot.noteFolders.firstOrNull { noteFolderPath(it.id, snapshot).equals(wanted, ignoreCase = true) }?.let { return it.id }
        snapshot.noteFolders.filter { it.name.equals(wanted, ignoreCase = true) }.singleOrNull()?.let { return it.id }
        if (!create) throw DesktopAgentException("No note folder \"$path\".")
        return null
    }

    private suspend fun ensureFolder(path: String): Long? {
        folderId(snapshot(), path, create = true)?.let { return it }
        var parent: Long? = null
        path.split('/').map(String::trim).filter(String::isNotEmpty).forEach { name ->
            val existing = snapshot().noteFolders.firstOrNull { it.parentId == parent && it.name.equals(name, ignoreCase = true) }
            parent = existing?.id ?: run {
                store.addNoteFolder(name, parent)
                snapshot().noteFolders.firstOrNull { it.parentId == parent && it.name.equals(name, ignoreCase = true) }?.id
                    ?: throw DesktopAgentException("Could not create the folder $name")
            }
        }
        return parent
    }

    private inner class Args(private val values: JsonObject) {
        fun has(name: String) = values.containsKey(name)
        fun string(name: String): String? = values[name]?.takeUnless { it is JsonNull }?.let { element ->
            (element as? JsonPrimitive)?.contentOrNull ?: throw DesktopAgentException("$name must be text")
        }
        fun requireString(name: String): String = string(name)?.takeIf(String::isNotBlank) ?: throw DesktopAgentException("$name is required")
        fun long(name: String): Long = (values[name] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
            ?: throw DesktopAgentException("$name is required and must be a number")
        fun int(name: String): Int? = (values[name] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
        fun boolean(name: String): Boolean? = (values[name] as? JsonPrimitive)?.booleanOrNull
        fun obj(name: String): JsonObject? = values[name]?.takeUnless { it is JsonNull }?.let { it as? JsonObject ?: throw DesktopAgentException("$name must be an object") }
        fun date(name: String): LocalDate? = string(name)?.takeIf(String::isNotBlank)?.let { parseDate(it, name) }
        fun time(name: String): LocalTime? = string(name)?.takeIf(String::isNotBlank)?.let {
            try {
                LocalTime.parse(it.trim().padStart(5, '0'))
            } catch (_: DateTimeParseException) {
                throw DesktopAgentException("$name must be a 24-hour time like 18:30")
            }
        }
        fun amount(name: String): BigDecimal? = (values[name] as? JsonPrimitive)?.contentOrNull?.let {
            it.trim().removePrefix("$").replace(",", "").toBigDecimalOrNull() ?: throw DesktopAgentException("$name must be a number like 12.50")
        }
        fun requireAmount(name: String): BigDecimal = amount(name) ?: throw DesktopAgentException("$name is required")
        fun stringList(name: String): List<String> = when (val element = values[name]) {
            null, is JsonNull -> emptyList()
            is JsonArray -> element.map { (it as? JsonPrimitive)?.contentOrNull?.trim()?.removePrefix("#") ?: throw DesktopAgentException("$name must be a list of text") }.filter(String::isNotEmpty)
            is JsonPrimitive -> element.contentOrNull.orEmpty().split(',').map { it.trim().removePrefix("#") }.filter(String::isNotEmpty)
            else -> throw DesktopAgentException("$name must be a list of text")
        }
        fun longList(name: String): List<Long>? = when (val element = values[name]) {
            null, is JsonNull -> null
            is JsonArray -> element.map {
                (it as? JsonPrimitive)?.longOrNull?.takeIf { minutes -> minutes in 0..527_040 }
                    ?: throw DesktopAgentException("$name must be a list of minutes between 0 and 527040")
            }.distinct()
            else -> throw DesktopAgentException("$name must be a list of minutes")
        }
    }

    private fun parseDate(value: String, name: String): LocalDate = when (value.trim().lowercase()) {
        "today" -> today()
        "tomorrow" -> today().plusDays(1)
        "yesterday" -> today().minusDays(1)
        else -> try {
            LocalDate.parse(value.trim())
        } catch (_: DateTimeParseException) {
            throw DesktopAgentException("$name must be a date like 2026-10-02")
        }
    }

    companion object {
        private fun schema(required: List<String> = emptyList(), build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject(build))
            if (required.isNotEmpty()) put("required", JsonArray(required.map(::JsonPrimitive)))
            put("additionalProperties", false)
        }

        private fun kotlinx.serialization.json.JsonObjectBuilder.prop(name: String, type: String, description: String, enum: List<String>? = null) {
            put(name, buildJsonObject {
                put("type", type)
                put("description", description)
                enum?.let { put("enum", JsonArray(it.map(::JsonPrimitive))) }
            })
        }

        private fun kotlinx.serialization.json.JsonObjectBuilder.list(name: String, itemType: String, description: String) {
            put(name, buildJsonObject {
                put("type", "array")
                put("items", buildJsonObject { put("type", itemType) })
                put("description", description)
            })
        }

        private fun kotlinx.serialization.json.JsonObjectBuilder.repeat() {
            put("repeat", buildJsonObject {
                put("type", "object")
                put("description", "Makes it repeat, starting on the date.")
                put("properties", buildJsonObject {
                    prop("every", "string", "Unit", listOf("day", "week", "month", "year"))
                    prop("interval", "integer", "Every N units, default 1")
                    prop("until", "string", "Optional last date, YYYY-MM-DD")
                })
                put("required", JsonArray(listOf(JsonPrimitive("every"))))
            })
        }

        private const val DATE = "Date as YYYY-MM-DD, or today, tomorrow, yesterday"

        val TOOLS: List<DesktopAgentTool> = listOf(
            DesktopAgentTool(
                "get_overview", "Today at a glance",
                "Todos due, overdue and in the next 7 days, money in and out today and this month, and whether the diary was written. Start here.",
                schema { prop("date", "string", "$DATE; default today") }, readOnly = true,
            ),
            DesktopAgentTool(
                "list_todos", "List todos",
                "Todos filtered by view, text, category, tag or priority. Ids from here are used to change todos.",
                schema {
                    prop("view", "string", "Which todos; default open", listOf("open", "today", "overdue", "upcoming", "no_date", "completed", "all"))
                    prop("search", "string", "Text in title, description or tags")
                    prop("category", "string", "Category path, e.g. Work / Clients")
                    prop("tag", "string", "One tag")
                    prop("priority", "string", "Priority", listOf("none", "low", "medium", "high", "urgent"))
                    prop("limit", "integer", "Most to return, default 50")
                }, readOnly = true,
            ),
            DesktopAgentTool("get_todo", "Get a todo", "One todo with subtasks, reminders and repeat rule.", schema(listOf("id")) { prop("id", "integer", "Todo id") }, readOnly = true),
            DesktopAgentTool(
                "create_todo", "Create a todo",
                "Adds a todo. With a due date it gets the user's default reminders unless reminders_minutes_before is given. Missing categories are created.",
                schema(listOf("title")) {
                    prop("title", "string", "What needs to be done")
                    prop("description", "string", "Longer description; the title is used when empty")
                    prop("due_date", "string", DATE)
                    prop("due_time", "string", "24-hour time like 18:30; needs due_date")
                    prop("priority", "string", "Priority", listOf("none", "low", "medium", "high", "urgent"))
                    prop("category", "string", "Category path, e.g. Home or Work / Clients")
                    list("tags", "string", "Tags without #")
                    list("subtasks", "string", "Subtasks in order")
                    list("reminders_minutes_before", "integer", "Reminders as minutes before the due time (0 = at due time, 60 = 1 hour, 1440 = 1 day)")
                    repeat()
                }, readOnly = false,
            ),
            DesktopAgentTool(
                "update_todo", "Change a todo",
                "Changes only the given fields; give due_date \"\" to remove the date and category \"\" to remove the category. For a repeating todo, scope this_and_future changes the series.",
                schema(listOf("id")) {
                    prop("id", "integer", "Todo id")
                    prop("title", "string", "New title")
                    prop("description", "string", "New description")
                    prop("due_date", "string", "$DATE, or empty to remove")
                    prop("due_time", "string", "24-hour time, or empty to remove")
                    prop("priority", "string", "Priority", listOf("none", "low", "medium", "high", "urgent"))
                    prop("category", "string", "Category path, or empty to remove")
                    list("tags", "string", "Replaces all tags")
                    list("subtasks", "string", "Replaces all subtasks (their ticks are reset)")
                    list("reminders_minutes_before", "integer", "Replaces the reminders")
                    repeat()
                    prop("scope", "string", "For repeating todos; default this", listOf("this", "this_and_future"))
                }, readOnly = false,
            ),
            DesktopAgentTool(
                "complete_todo", "Complete a todo", "Ticks a todo (and by default its subtasks), or unticks it with completed false.",
                schema(listOf("id")) {
                    prop("id", "integer", "Todo id")
                    prop("completed", "boolean", "Default true")
                    prop("complete_subtasks", "boolean", "Also tick open subtasks, default true")
                }, readOnly = false,
            ),
            DesktopAgentTool(
                "delete_todo", "Delete a todo", "Deletes a todo; for a repeating todo, scope this_and_future also deletes later ones.",
                schema(listOf("id")) {
                    prop("id", "integer", "Todo id")
                    prop("scope", "string", "Default this", listOf("this", "this_and_future"))
                }, readOnly = false, destructive = true,
            ),
            DesktopAgentTool(
                "list_ledger_entries", "List ledger entries", "Money in and out between two dates (default this month), newest first, with totals.",
                schema {
                    prop("from", "string", "$DATE; default first day of this month")
                    prop("to", "string", "$DATE; default today")
                    prop("type", "string", "Only one type", listOf("expense", "income"))
                    prop("search", "string", "Text in merchant, note or tags")
                    prop("tag", "string", "One tag")
                    prop("limit", "integer", "Most to return, default 100")
                }, readOnly = true,
            ),
            DesktopAgentTool(
                "ledger_summary", "Ledger summary", "Income, expense, net, average daily spending, largest expense and spending by tag between two dates (default this month).",
                schema {
                    prop("from", "string", "$DATE; default first day of this month")
                    prop("to", "string", "$DATE; default today")
                }, readOnly = true,
            ),
            DesktopAgentTool(
                "create_ledger_entry", "Add a ledger entry", "Records money spent or received (USD).",
                schema(listOf("type", "amount")) {
                    prop("type", "string", "Expense or income", listOf("expense", "income"))
                    prop("amount", "string", "Amount in dollars, e.g. 12.50")
                    prop("date", "string", "$DATE; default today")
                    prop("time", "string", "24-hour time; default now (today) or 12:00")
                    prop("merchant", "string", "Where or who")
                    prop("note", "string", "Note")
                    list("tags", "string", "Tags without #")
                    repeat()
                }, readOnly = false,
            ),
            DesktopAgentTool(
                "update_ledger_entry", "Change a ledger entry", "Changes only the given fields of one entry.",
                schema(listOf("id")) {
                    prop("id", "integer", "Entry id")
                    prop("type", "string", "Expense or income", listOf("expense", "income"))
                    prop("amount", "string", "Amount in dollars")
                    prop("date", "string", DATE)
                    prop("time", "string", "24-hour time")
                    prop("merchant", "string", "Where or who")
                    prop("note", "string", "Note")
                    list("tags", "string", "Replaces all tags")
                }, readOnly = false,
            ),
            DesktopAgentTool(
                "delete_ledger_entry", "Delete a ledger entry", "Deletes one entry; for scheduled entries, scope this_and_future also stops later ones.",
                schema(listOf("id")) {
                    prop("id", "integer", "Entry id")
                    prop("scope", "string", "Default this", listOf("this", "this_and_future"))
                }, readOnly = false, destructive = true,
            ),
            DesktopAgentTool(
                "list_notes", "List notes", "Notes (pinned first, then recently changed) with a short preview, and all folders.",
                schema {
                    prop("search", "string", "Text in title or body")
                    prop("folder", "string", "Folder path")
                    prop("limit", "integer", "Most to return, default 50")
                }, readOnly = true,
            ),
            DesktopAgentTool("get_note", "Read a note", "The full text of one note.", schema(listOf("id")) { prop("id", "integer", "Note id") }, readOnly = true),
            DesktopAgentTool(
                "create_note", "Create a note", "Adds a note; a missing folder is created.",
                schema {
                    prop("title", "string", "Title; the first line is used when empty")
                    prop("body", "string", "Text")
                    prop("folder", "string", "Folder path")
                    prop("pinned", "boolean", "Pin it")
                }, readOnly = false,
            ),
            DesktopAgentTool(
                "update_note", "Change a note", "Replaces the body, or adds text at the end with append; changes title, folder or pin.",
                schema(listOf("id")) {
                    prop("id", "integer", "Note id")
                    prop("title", "string", "New title")
                    prop("body", "string", "Replaces the whole text")
                    prop("append", "string", "Added after the existing text")
                    prop("folder", "string", "Folder path, or empty for Unfiled")
                    prop("pinned", "boolean", "Pin or unpin")
                }, readOnly = false,
            ),
            DesktopAgentTool("delete_note", "Delete a note", "Deletes a note and its files.", schema(listOf("id")) { prop("id", "integer", "Note id") }, readOnly = false, destructive = true),
            DesktopAgentTool(
                "get_diary", "Read the diary", "Diary pages for one date, or a range (default the last 7 days).",
                schema {
                    prop("date", "string", DATE)
                    prop("from", "string", DATE)
                    prop("to", "string", DATE)
                }, readOnly = true,
            ),
            DesktopAgentTool(
                "write_diary", "Write in the diary", "Adds to (default) or replaces the diary page of a date.",
                schema(listOf("text")) {
                    prop("date", "string", "$DATE; default today")
                    prop("text", "string", "Text to write")
                    prop("mode", "string", "Default append", listOf("append", "replace"))
                }, readOnly = false,
            ),
            DesktopAgentTool(
                "get_checklist", "Daily checklist",
                "Things done every day (brush teeth, shower) and which are ticked on a date. They are not todos; ticks count per day.",
                schema { prop("date", "string", "$DATE; default today") }, readOnly = true,
            ),
            DesktopAgentTool(
                "check_checklist_item", "Tick a checklist item", "Ticks (or unticks) a daily checklist item for a date. Name it by id or title.",
                schema {
                    prop("id", "integer", "Checklist item id")
                    prop("title", "string", "Checklist item title, when no id is given")
                    prop("done", "boolean", "Default true; false unticks")
                    prop("date", "string", "$DATE; default today")
                }, readOnly = false,
            ),
            DesktopAgentTool(
                "add_checklist_item", "Add to the daily checklist", "Adds something done every day to the end of the daily checklist.",
                schema(listOf("title")) { prop("title", "string", "What is done every day") }, readOnly = false,
            ),
            DesktopAgentTool(
                "remove_checklist_item", "Remove from the daily checklist", "Removes an item from the daily checklist on every device. Name it by id or title.",
                schema {
                    prop("id", "integer", "Checklist item id")
                    prop("title", "string", "Checklist item title, when no id is given")
                }, readOnly = false, destructive = true,
            ),
            DesktopAgentTool("get_day", "One day", "Todos due, ledger entries and the diary page of one date.", schema { prop("date", "string", "$DATE; default today") }, readOnly = true),
            DesktopAgentTool("list_categories_and_tags", "Categories and tags", "Todo categories and tags, ledger tags and note folders.", schema {}, readOnly = true),
            DesktopAgentTool("create_category", "Create a category", "Adds a todo category; use \"Parent / Child\" for a sub-category.", schema(listOf("name")) { prop("name", "string", "Category path") }, readOnly = false),
            DesktopAgentTool(
                "search", "Search everything", "Finds text in todos, ledger entries, notes and diary pages.",
                schema(listOf("query")) {
                    prop("query", "string", "Text to find")
                    prop("limit", "integer", "Most per kind, default 20")
                }, readOnly = true,
            ),
        )
    }
}
