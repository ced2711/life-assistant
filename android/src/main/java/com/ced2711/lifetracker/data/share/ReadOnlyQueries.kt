package com.ced2711.lifetracker.data.share

import android.database.Cursor
import android.database.MatrixCursor
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * The read-only queries behind [ReadOnlyDataProvider]. Every statement is fixed SQL over the todo,
 * ledger, note and diary tables with bound arguments, so no caller input can reach other tables
 * (the Vault and the Confessional are never read). The column contract is docs/POCKET_AI_READ_ACCESS.md.
 */
class ReadOnlyQueries(
    private val database: SupportSQLiteDatabase,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    fun todos(parameters: Parameters): Cursor {
        val day = today().toEpochDay()
        val view = parameters.string("view") ?: "open"
        val days = parameters.int("days", default = 7, range = 1..366)
        val limit = parameters.int("limit", default = 50, range = 1..500)
        val where = StringBuilder("t.deletedAt IS NULL")
        val args = mutableListOf<Any>()
        when (view) {
            "open" -> where.append(" AND t.completedAt IS NULL")
            "today" -> where.append(" AND t.completedAt IS NULL AND t.deadlineEpochDay = ?").also { args += day }
            "overdue" -> where.append(" AND t.completedAt IS NULL AND t.deadlineEpochDay < ?").also { args += day }
            "upcoming" -> where.append(" AND t.completedAt IS NULL AND t.deadlineEpochDay BETWEEN ? AND ?")
                .also { args += day; args += day + days }
            "no_date" -> where.append(" AND t.completedAt IS NULL AND t.deadlineEpochDay IS NULL")
            "completed" -> where.append(" AND t.completedAt IS NOT NULL")
            "all" -> Unit
            else -> throw IllegalArgumentException("view must be one of open, today, overdue, upcoming, no_date, completed, all")
        }
        parameters.date("from")?.let { where.append(" AND t.deadlineEpochDay >= ?"); args += it.toEpochDay() }
        parameters.date("to")?.let { where.append(" AND t.deadlineEpochDay <= ?"); args += it.toEpochDay() }
        parameters.search()?.let { pattern ->
            where.append(" AND (t.title LIKE ? ESCAPE '\\' OR t.description LIKE ? ESCAPE '\\' OR t.tagsCsv LIKE ? ESCAPE '\\')")
            repeat(3) { args += pattern }
        }
        val order = if (view == "completed") {
            "t.completedAt DESC"
        } else {
            "t.deadlineEpochDay IS NULL, t.deadlineEpochDay, t.deadlineMinute IS NULL, t.deadlineMinute, " +
                "CASE t.priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 3 ELSE 4 END, t.createdAt"
        }
        val categories = categoryPaths()
        val result = MatrixCursor(TODO_COLUMNS)
        query(
            """
            SELECT t.id, t.title, t.description, t.deadlineEpochDay, t.deadlineMinute, t.priority, t.categoryId,
                   t.tagsCsv, t.completedAt, t.seriesId,
                   (SELECT COUNT(*) FROM subtasks s WHERE s.todoId = t.id AND s.isCompleted = 1),
                   (SELECT COUNT(*) FROM subtasks s WHERE s.todoId = t.id)
            FROM todos t WHERE $where ORDER BY $order, t.id LIMIT $limit
            """,
            args,
        ) { row ->
            result.addRow(
                arrayOf<Any?>(
                    row.getLong(0),
                    row.getString(1),
                    row.getString(2),
                    row.longOrNull(3)?.let { LocalDate.ofEpochDay(it).toString() },
                    row.longOrNull(4)?.let { clock(it.toInt()) },
                    row.getString(5).lowercase(),
                    row.longOrNull(6)?.let(categories::get),
                    tags(row.getString(7)),
                    if (row.isNull(8)) 0 else 1,
                    row.longOrNull(8)?.let(::localDateTime),
                    if (row.isNull(9)) 0 else 1,
                    row.getInt(10),
                    row.getInt(11),
                ),
            )
        }
        return result
    }

    fun ledger(parameters: Parameters): Cursor {
        val (from, to) = range(parameters)
        val limit = parameters.int("limit", default = 100, range = 1..1000)
        val (where, args) = ledgerFilter(parameters, from, to)
        val result = MatrixCursor(LEDGER_COLUMNS)
        query(
            """
            SELECT id, type, amountCents, epochDay, minuteOfDay, merchant, note, tagsCsv, seriesId
            FROM ledger_entries WHERE $where
            ORDER BY epochDay DESC, minuteOfDay DESC, createdAt DESC, id DESC LIMIT $limit
            """,
            args,
        ) { row ->
            result.addRow(
                arrayOf<Any?>(
                    row.getLong(0),
                    row.getString(1).lowercase(),
                    money(row.getLong(2)),
                    row.getLong(2),
                    LocalDate.ofEpochDay(row.getLong(3)).toString(),
                    clock(row.getInt(4)),
                    row.getString(5),
                    row.getString(6),
                    tags(row.getString(7)),
                    if (row.isNull(8)) 0 else 1,
                ),
            )
        }
        return result
    }

    /** One row of totals for a date range (default: this month up to today). */
    fun ledgerSummary(parameters: Parameters): Cursor {
        val (from, to) = range(parameters)
        val (where, args) = ledgerFilter(parameters, from, to)
        val result = MatrixCursor(LEDGER_SUMMARY_COLUMNS)
        query(
            """
            SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END), 0),
                   COUNT(*)
            FROM ledger_entries WHERE $where
            """,
            args,
        ) { row ->
            val income = row.getLong(0)
            val expense = row.getLong(1)
            val days = to.toEpochDay() - from.toEpochDay() + 1
            result.addRow(
                arrayOf<Any?>(
                    from.toString(), to.toString(),
                    money(income), money(expense), money(income - expense),
                    income, expense, income - expense,
                    row.getInt(2),
                    money(expense / days),
                    CURRENCY,
                ),
            )
        }
        return result
    }

    /** Spending in a date range grouped by tag, largest first; untagged spending is "(untagged)". */
    fun ledgerByTag(parameters: Parameters): Cursor {
        val (from, to) = range(parameters)
        val (where, args) = ledgerFilter(parameters.without("type"), from, to)
        val byTag = HashMap<String, Long>()
        query("SELECT amountCents, tagsCsv FROM ledger_entries WHERE $where AND type = 'EXPENSE'", args) { row ->
            val names = tags(row.getString(1)).split(',').filter(String::isNotEmpty).ifEmpty { listOf(UNTAGGED) }
            names.forEach { byTag.merge(it, row.getLong(0), Long::plus) }
        }
        val result = MatrixCursor(LEDGER_TAG_COLUMNS)
        byTag.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .forEach { (tag, cents) -> result.addRow(arrayOf<Any?>(tag, money(cents), cents)) }
        return result
    }

    /** Notes, pinned first then newest; [id] reads one note with its full text. */
    fun notes(parameters: Parameters, id: Long? = null): Cursor {
        val limit = parameters.int("limit", default = 50, range = 1..500)
        val full = id != null || parameters.string("full") == "1"
        val where = StringBuilder("1 = 1")
        val args = mutableListOf<Any>()
        if (id != null) {
            where.append(" AND id = ?"); args += id
        }
        parameters.search()?.let { pattern ->
            where.append(" AND (title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\')")
            repeat(2) { args += pattern }
        }
        val folders = folderPaths()
        val result = MatrixCursor(NOTE_COLUMNS)
        query(
            "SELECT id, title, body, folderId, pinned, updatedAt FROM notes WHERE $where " +
                "ORDER BY pinned DESC, updatedAt DESC, id DESC LIMIT $limit",
            args,
        ) { row ->
            val body = row.getString(2)
            result.addRow(
                arrayOf<Any?>(
                    row.getLong(0),
                    row.getString(1),
                    row.longOrNull(3)?.let(folders::get),
                    row.getInt(4),
                    localDateTime(row.getLong(5)),
                    body.replace('\n', ' ').take(PREVIEW_LENGTH),
                    if (full) body else null,
                ),
            )
        }
        return result
    }

    /** Diary pages from `from` to `to` (default: the last seven days), oldest first. */
    fun diary(parameters: Parameters): Cursor {
        val single = parameters.date("date")
        val (from, to) = if (single != null) single to single else range(parameters, defaultFrom = today().minusDays(6))
        val result = MatrixCursor(DIARY_COLUMNS)
        query(
            "SELECT epochDay, body FROM diary_entries WHERE epochDay BETWEEN ? AND ? ORDER BY epochDay",
            listOf(from.toEpochDay(), to.toEpochDay()),
        ) { row -> result.addRow(arrayOf<Any?>(LocalDate.ofEpochDay(row.getLong(0)).toString(), row.getString(1))) }
        return result
    }

    private fun ledgerFilter(parameters: Parameters, from: LocalDate, to: LocalDate): Pair<String, List<Any>> {
        val where = StringBuilder("deletedAt IS NULL AND epochDay BETWEEN ? AND ?")
        val args = mutableListOf<Any>(from.toEpochDay(), to.toEpochDay())
        parameters.string("type")?.let { type ->
            where.append(" AND type = ?")
            args += when (type.lowercase()) {
                "expense" -> "EXPENSE"
                "income" -> "INCOME"
                else -> throw IllegalArgumentException("type must be expense or income")
            }
        }
        parameters.search()?.let { pattern ->
            where.append(" AND (merchant LIKE ? ESCAPE '\\' OR note LIKE ? ESCAPE '\\' OR tagsCsv LIKE ? ESCAPE '\\')")
            repeat(3) { args += pattern }
        }
        return where.toString() to args
    }

    private fun range(parameters: Parameters, defaultFrom: LocalDate = today().withDayOfMonth(1)): Pair<LocalDate, LocalDate> {
        val from = parameters.date("from") ?: defaultFrom
        val to = parameters.date("to") ?: today()
        require(!to.isBefore(from)) { "to is before from" }
        require(to.toEpochDay() - from.toEpochDay() <= MAX_RANGE_DAYS) { "The range is longer than ten years" }
        return from to to
    }

    private fun categoryPaths(): Map<Long, String> =
        paths("SELECT id, name, parentId FROM categories")

    private fun folderPaths(): Map<Long, String> =
        paths("SELECT id, name, parentId FROM note_folders")

    /** "Parent / Child" names for a self-referencing folder table, safe against cycles. */
    private fun paths(sql: String): Map<Long, String> {
        val nodes = HashMap<Long, Pair<String, Long?>>()
        query(sql, emptyList()) { row -> nodes[row.getLong(0)] = row.getString(1) to row.longOrNull(2) }
        return nodes.keys.associateWith { id ->
            val names = ArrayList<String>()
            val seen = HashSet<Long>()
            var current: Long? = id
            while (current != null && seen.add(current)) {
                val node = nodes[current] ?: break
                names += node.first
                current = node.second
            }
            names.asReversed().joinToString(" / ")
        }
    }

    private fun query(sql: String, args: List<Any>, onRow: (Cursor) -> Unit) {
        database.query(SimpleSQLiteQuery(sql.trimIndent(), args.toTypedArray())).use { cursor ->
            while (cursor.moveToNext()) onRow(cursor)
        }
    }

    private fun localDateTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone()).toLocalDateTime().withNano(0).toString()

    private fun Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)

    /** Request parameters from the content URI's query string. */
    class Parameters(private val values: Map<String, String>) {
        fun string(name: String): String? = values[name]?.trim()?.takeIf(String::isNotEmpty)

        fun int(name: String, default: Int, range: IntRange): Int {
            val raw = string(name) ?: return default
            val value = requireNotNull(raw.toIntOrNull()) { "$name must be a whole number" }
            return value.coerceIn(range)
        }

        fun date(name: String): LocalDate? = string(name)?.let {
            runCatching { LocalDate.parse(it) }.getOrElse { throw IllegalArgumentException("$name must be a date like 2026-10-18") }
        }

        /** A case-insensitive LIKE pattern for `search`, with a leading # ignored. */
        fun search(): String? = string("search")?.removePrefix("#")?.takeIf(String::isNotBlank)?.let { text ->
            "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        }

        fun without(name: String) = Parameters(values - name)
    }

    companion object {
        const val CURRENCY = "USD"
        private const val UNTAGGED = "(untagged)"
        private const val PREVIEW_LENGTH = 160
        private const val MAX_RANGE_DAYS = 3_660

        val TODO_COLUMNS = arrayOf(
            "_id", "title", "description", "due_date", "due_time", "priority", "category", "tags",
            "completed", "completed_at", "repeats", "subtasks_done", "subtasks_total",
        )
        val LEDGER_COLUMNS = arrayOf(
            "_id", "type", "amount", "amount_cents", "date", "time", "merchant", "note", "tags", "recurring",
        )
        val LEDGER_SUMMARY_COLUMNS = arrayOf(
            "from", "to", "income", "expense", "net", "income_cents", "expense_cents", "net_cents",
            "entry_count", "average_daily_spending", "currency",
        )
        val LEDGER_TAG_COLUMNS = arrayOf("tag", "amount", "amount_cents")
        val NOTE_COLUMNS = arrayOf("_id", "title", "folder", "pinned", "updated", "preview", "body")
        val DIARY_COLUMNS = arrayOf("date", "text")

        internal fun money(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()

        internal fun clock(minuteOfDay: Int): String =
            String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

        /** Tags as one comma-separated string without blanks, e.g. "food,lunch". */
        internal fun tags(csv: String): String = csv.split(',').map(String::trim).filter(String::isNotEmpty).joinToString(",")
    }
}
