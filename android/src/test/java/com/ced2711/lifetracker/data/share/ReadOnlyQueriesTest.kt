package com.ced2711.lifetracker.data.share

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.data.local.DiaryEntryEntity
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.NoteEntity
import com.ced2711.lifetracker.data.local.NoteFolderEntity
import com.ced2711.lifetracker.data.local.SubtaskEntity
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import com.ced2711.lifetracker.data.local.TaskLedgerDao
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.TodoPriority
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ReadOnlyQueriesTest {
    private val today = LocalDate.of(2026, 10, 18)
    private lateinit var database: TaskLedgerDatabase
    private lateinit var dao: TaskLedgerDao
    private lateinit var queries: ReadOnlyQueries

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TaskLedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.dao()
        queries = ReadOnlyQueries(database.openHelper.readableDatabase, today = { today }, zone = { ZoneOffset.UTC })
    }

    @After
    fun tearDown() = database.close()

    private fun params(vararg pairs: Pair<String, String>) = ReadOnlyQueries.Parameters(pairs.toMap())

    private fun Cursor.rows(): List<Map<String, Any?>> = use {
        buildList {
            while (moveToNext()) {
                add(columnNames.associateWith { name ->
                    val index = getColumnIndexOrThrow(name)
                    when (getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> getLong(index)
                        else -> getString(index)
                    }
                })
            }
        }
    }

    private fun todo(title: String, due: LocalDate?, minute: Int? = null, completedAt: Long? = null, deletedAt: Long? = null, categoryId: Long? = null, priority: TodoPriority = TodoPriority.NONE, tags: String = "") =
        runBlocking {
            dao.insertTodo(
                TodoEntity(
                    title = title, description = title, deadlineEpochDay = due?.toEpochDay(), deadlineMinute = minute,
                    completedAt = completedAt, deletedAt = deletedAt, categoryId = categoryId, priority = priority, tagsCsv = tags,
                ),
            )
        }

    private fun ledger(type: LedgerType, cents: Long, day: LocalDate, merchant: String = "", tags: String = "", deletedAt: Long? = null) =
        runBlocking {
            dao.insertLedgerEntry(
                LedgerEntryEntity(type = type, amountCents = cents, epochDay = day.toEpochDay(), minuteOfDay = 12 * 60 + 5, merchant = merchant, tagsCsv = tags, deletedAt = deletedAt),
            )
        }

    @Test
    fun `todo views select by due date and leave out deleted todos`() {
        val school = runBlocking { dao.insertCategory(CategoryEntity(name = "School")) }
        val hw = runBlocking { dao.insertCategory(CategoryEntity(name = "HW", parentId = school)) }
        val due = todo("Physics HW", today, minute = 9 * 60 + 30, categoryId = hw, priority = TodoPriority.HIGH, tags = " physics, ,hw ")
        runBlocking {
            dao.insertSubtasks(listOf(SubtaskEntity(todoId = due, description = "Q1", isCompleted = true), SubtaskEntity(todoId = due, description = "Q2")))
        }
        todo("Overdue essay", today.minusDays(2))
        todo("Next week", today.plusDays(7))
        todo("Far away", today.plusDays(30))
        todo("Someday", null)
        todo("Done", today, completedAt = 1_000L)
        todo("Deleted", today, deletedAt = 1_000L)

        val todayRows = queries.todos(params("view" to "today")).rows()
        assertEquals(listOf("Physics HW"), todayRows.map { it["title"] })
        val row = todayRows.single()
        assertEquals("2026-10-18", row["due_date"])
        assertEquals("09:30", row["due_time"])
        assertEquals("high", row["priority"])
        assertEquals("School / HW", row["category"])
        assertEquals("physics,hw", row["tags"])
        assertEquals(0L, row["completed"])
        assertEquals(1L, row["subtasks_done"])
        assertEquals(2L, row["subtasks_total"])

        assertEquals(listOf("Overdue essay"), queries.todos(params("view" to "overdue")).rows().map { it["title"] })
        assertEquals(listOf("Physics HW", "Next week"), queries.todos(params("view" to "upcoming")).rows().map { it["title"] })
        assertEquals(
            listOf("Physics HW", "Next week", "Far away"),
            queries.todos(params("view" to "upcoming", "days" to "30")).rows().map { it["title"] },
        )
        assertEquals(listOf("Someday"), queries.todos(params("view" to "no_date")).rows().map { it["title"] })
        assertEquals(listOf("Done"), queries.todos(params("view" to "completed")).rows().map { it["title"] })
        // Open todos: dated first by date, undated last.
        assertEquals(
            listOf("Overdue essay", "Physics HW", "Next week", "Far away", "Someday"),
            queries.todos(params()).rows().map { it["title"] },
        )
        assertEquals(listOf("Physics HW"), queries.todos(params("search" to "#physics")).rows().map { it["title"] })
        assertEquals(2, queries.todos(params("limit" to "2")).rows().size)
    }

    @Test
    fun `search treats LIKE wildcards as plain text`() {
        todo("100% done", null)
        todo("1000 words", null)
        assertEquals(listOf("100% done"), queries.todos(params("search" to "100%")).rows().map { it["title"] })
    }

    @Test
    fun `bad parameters are rejected with a message`() {
        assertThrows(IllegalArgumentException::class.java) { queries.todos(params("view" to "tomorrow")) }
        assertThrows(IllegalArgumentException::class.java) { queries.ledger(params("from" to "18/10/2026")) }
        assertThrows(IllegalArgumentException::class.java) { queries.ledger(params("from" to "2026-10-18", "to" to "2026-10-01")) }
        assertThrows(IllegalArgumentException::class.java) { queries.ledger(params("type" to "transfer")) }
        assertThrows(IllegalArgumentException::class.java) { queries.notes(params("limit" to "lots")) }
    }

    @Test
    fun `ledger lists, sums and groups this month by default`() {
        ledger(LedgerType.EXPENSE, 1_250, today, merchant = "Cafe", tags = "food")
        ledger(LedgerType.EXPENSE, 4_000, today.minusDays(3), merchant = "Books", tags = "school,books")
        ledger(LedgerType.EXPENSE, 500, today.minusDays(1))
        ledger(LedgerType.INCOME, 100_000, today.minusDays(10), merchant = "Job")
        ledger(LedgerType.EXPENSE, 9_999, today.minusMonths(1), merchant = "Last month")
        ledger(LedgerType.EXPENSE, 7_777, today, merchant = "Deleted", deletedAt = 1L)

        val entries = queries.ledger(params()).rows()
        assertEquals(listOf("Cafe", "", "Books", "Job"), entries.map { it["merchant"] })
        val cafe = entries.first()
        assertEquals("expense", cafe["type"])
        assertEquals("12.50", cafe["amount"])
        assertEquals(1_250L, cafe["amount_cents"])
        assertEquals("2026-10-18", cafe["date"])
        assertEquals("12:05", cafe["time"])

        assertEquals(listOf("Job"), queries.ledger(params("type" to "income")).rows().map { it["merchant"] })
        assertEquals(listOf("Books"), queries.ledger(params("search" to "school")).rows().map { it["merchant"] })

        val summary = queries.ledgerSummary(params()).rows().single()
        assertEquals("2026-10-01", summary["from"])
        assertEquals("2026-10-18", summary["to"])
        assertEquals("1000.00", summary["income"])
        assertEquals("57.50", summary["expense"])
        assertEquals("942.50", summary["net"])
        assertEquals(4L, summary["entry_count"])
        assertEquals("3.19", summary["average_daily_spending"])
        assertEquals("USD", summary["currency"])

        val byTag = queries.ledgerByTag(params()).rows().map { it["tag"] to it["amount"] }
        assertEquals(listOf("books" to "40.00", "school" to "40.00", "food" to "12.50", "(untagged)" to "5.00"), byTag)

        val lastMonth = queries.ledgerSummary(params("from" to "2026-09-01", "to" to "2026-09-30")).rows().single()
        assertEquals("99.99", lastMonth["expense"])
    }

    @Test
    fun `notes give previews by default and full text by id`() {
        val folder = runBlocking { dao.insertNoteFolder(NoteFolderEntity(name = "Ideas")) }
        val pinned = runBlocking { dao.insertNote(NoteEntity(title = "Pinned", body = "line one\nline two", pinned = true, folderId = folder, createdAt = 1_000, updatedAt = 1_000)) }
        runBlocking { dao.insertNote(NoteEntity(title = "Recent", body = "x".repeat(400), createdAt = 2_000, updatedAt = 2_000)) }

        val list = queries.notes(params()).rows()
        assertEquals(listOf("Pinned", "Recent"), list.map { it["title"] })
        assertEquals("Ideas", list[0]["folder"])
        assertEquals("line one line two", list[0]["preview"])
        assertEquals(160, (list[1]["preview"] as String).length)
        assertNull(list[1]["body"])
        assertEquals("1970-01-01T00:00:01", list[0]["updated"])

        assertEquals(listOf("Recent"), queries.notes(params("search" to "xxx")).rows().map { it["title"] })
        assertEquals("line one\nline two", queries.notes(params(), id = pinned).rows().single()["body"])
        assertEquals(400, (queries.notes(params("full" to "1")).rows()[1]["body"] as String).length)
    }

    @Test
    fun `diary defaults to the last seven days`() {
        listOf(0L, 6L, 7L).forEach { back ->
            runBlocking { dao.insertDiaryEntry(DiaryEntryEntity(epochDay = today.minusDays(back).toEpochDay(), body = "day -$back")) }
        }
        assertEquals(listOf("day -6", "day -0"), queries.diary(params()).rows().map { it["text"] })
        assertEquals(listOf("day -7"), queries.diary(params("date" to "2026-10-11")).rows().map { it["text"] })
    }
}
