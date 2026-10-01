package com.ced2711.lifetracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.LedgerSeriesEntity
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.local.TodoOccurrenceExceptionEntity
import com.ced2711.lifetracker.data.local.TodoSeriesEntity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import com.ced2711.lifetracker.domain.model.TodoDraft
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskLedgerRepositoryIntegrationTest {
    private lateinit var database: TaskLedgerDatabase
    private lateinit var repository: TaskLedgerRepository
    private var elapsedRealtimeNow = 100_000L
    private var wallClockNow = 1_000_000L

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TaskLedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TaskLedgerRepository(
            database = database,
            elapsedRealtimeMillis = { elapsedRealtimeNow },
            wallClockMillis = { wallClockNow },
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun singularSoftDeletesClaimAnActiveRowOnlyOnce() = runBlocking {
        val dao = database.dao()
        val todoId = dao.insertTodo(TodoEntity(title = "Todo", description = "Todo"))
        val entryId = dao.insertLedgerEntry(
            LedgerEntryEntity(
                type = LedgerType.EXPENSE,
                amountCents = 100,
                epochDay = 10,
                minuteOfDay = 0,
            ),
        )

        assertEquals(1, dao.softDeleteTodo(todoId, 1_000))
        assertEquals(0, dao.softDeleteTodo(todoId, 1_001))
        assertEquals(1_000L, dao.getTodo(todoId)?.deletedAt)

        assertEquals(1, dao.softDeleteLedgerEntry(entryId, 2_000))
        assertEquals(0, dao.softDeleteLedgerEntry(entryId, 2_001))
        assertEquals(2_000L, dao.getLedgerEntry(entryId)?.deletedAt)
    }

    @Test
    fun attachmentOwnerLookupRejectsMissingAndSoftDeletedRows() = runBlocking {
        val dao = database.dao()
        val todoId = dao.insertTodo(TodoEntity(title = "Todo", description = "Todo"))
        val entryId = dao.insertLedgerEntry(
            LedgerEntryEntity(
                type = LedgerType.EXPENSE,
                amountCents = 100,
                epochDay = 10,
                minuteOfDay = 0,
            ),
        )

        assertTrue(repository.attachmentOwnerExists(AttachmentOwnerType.TODO, todoId))
        assertTrue(repository.attachmentOwnerExists(AttachmentOwnerType.LEDGER, entryId))
        assertFalse(repository.attachmentOwnerExists(AttachmentOwnerType.TODO, Long.MAX_VALUE))
        assertFalse(repository.attachmentOwnerExists(AttachmentOwnerType.LEDGER, Long.MAX_VALUE))

        dao.softDeleteTodo(todoId, 1_000)
        dao.softDeleteLedgerEntry(entryId, 1_000)

        assertFalse(repository.attachmentOwnerExists(AttachmentOwnerType.TODO, todoId))
        assertFalse(repository.attachmentOwnerExists(AttachmentOwnerType.LEDGER, entryId))
    }

    @Test
    fun todoClientOperationTokenIsIdempotentConcurrentAndRebindsAfterSoftDelete() = runBlocking {
        val draft = TodoDraft(description = "Idempotent task")
        val token = "todo-operation-one"

        val ids = coroutineScope {
            List(8) {
                async(Dispatchers.IO) { repository.saveTodo(draft, clientOperationToken = token) }
            }.awaitAll()
        }

        assertEquals(1, ids.distinct().size)
        assertEquals(1, database.dao().getAllTodosForTagMaintenance().size)

        val updatedId = repository.saveTodo(
            draft.copy(description = "Updated after restore"),
            clientOperationToken = token,
        )
        assertEquals(ids.first(), updatedId)
        assertEquals("Updated after restore", database.dao().getTodo(updatedId)?.description)
        assertEquals(1, database.dao().getAllTodosForTagMaintenance().size)

        val distinctId = repository.saveTodo(
            draft,
            clientOperationToken = "todo-operation-two",
        )
        assertFalse(distinctId == ids.first())

        database.dao().softDeleteTodo(ids.first(), 1_000)
        val reboundId = repository.saveTodo(draft, clientOperationToken = token)
        assertFalse(reboundId == ids.first())
        assertTrue(repository.attachmentOwnerExists(AttachmentOwnerType.TODO, reboundId))
    }

    @Test
    fun recurringTodoTokenDoesNotDuplicateSeriesOrOccurrence() = runBlocking {
        val today = LocalDate.now().toEpochDay()
        val draft = TodoDraft(
            description = "Repeat safely",
            deadlineEpochDay = today,
            recurrence = RecurrenceRule(RecurrenceUnit.DAY),
        )

        val first = repository.saveTodo(draft, clientOperationToken = "todo-recurring-operation")
        val changedDraft = draft.copy(
            description = "Updated repeat",
            deadlineEpochDay = today + 1,
            recurrence = RecurrenceRule(RecurrenceUnit.WEEK, interval = 2),
        )
        val second = repository.saveTodo(
            changedDraft,
            clientOperationToken = "todo-recurring-operation",
        )

        assertEquals(first, second)
        val allSeries = database.dao().observeTodoSeries().first()
        val series = allSeries.single { it.active }
        assertEquals(2, allSeries.size)
        assertEquals(RecurrenceUnit.WEEK, series.recurrenceUnit)
        assertEquals(2, series.intervalCount)
        assertEquals(today + 1, series.startEpochDay)
        assertEquals("Updated repeat", database.dao().getTodo(first)?.description)
        assertEquals(1, database.dao().getAllTodosForTagMaintenance().size)
    }

    @Test
    fun recurringTodoTokenReplacesMaterializedSeriesWithoutLosingUserChanges() = runBlocking {
        val dao = database.dao()
        val today = LocalDate.now().toEpochDay()
        val start = today - 2
        val token = "todo-materialized-replacement"
        val originalDraft = TodoDraft(
            description = "Original daily task",
            deadlineEpochDay = start,
            reminderOffsetsMinutes = listOf(0),
            recurrence = RecurrenceRule(RecurrenceUnit.DAY),
        )
        val ownerId = repository.saveTodo(originalDraft, clientOperationToken = token)
        val ownerAttachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.TODO,
                ownerId = ownerId,
                privatePath = "/private/owner.txt",
                originalName = "owner.txt",
                mimeType = "text/plain",
                sizeBytes = 10,
            ),
        )
        repository.materializeTodoOccurrencesThrough(today + 3)

        val originalSeriesId = requireNotNull(dao.getTodo(ownerId)?.seriesId)
        val occurrences = dao.getAllTodosForTagMaintenance()
            .filter { it.seriesId == originalSeriesId }
            .associateBy { requireNotNull(it.occurrenceEpochDay) }
        val historicalId = requireNotNull(occurrences[today - 1]?.id)
        val completedFutureId = requireNotNull(occurrences[today + 1]?.id)
        val editedFutureId = requireNotNull(occurrences[today + 2]?.id)
        val cleanFutureId = requireNotNull(occurrences[today + 3]?.id)
        repository.completeTodo(completedFutureId, completeSubtasks = false)
        repository.saveTodo(
            TodoDraft(
                id = editedFutureId,
                description = "Independently edited future task",
                deadlineEpochDay = today + 2,
                reminderOffsetsMinutes = listOf(0),
                recurrence = RecurrenceRule(RecurrenceUnit.DAY),
            ),
        )

        val changedDraft = originalDraft.copy(
            description = "Replacement weekly task",
            deadlineEpochDay = today + 3,
            recurrence = RecurrenceRule(RecurrenceUnit.WEEK, interval = 2),
        )
        val changedOwnerId = repository.saveTodo(changedDraft, clientOperationToken = token)
        val replacementSeriesId = requireNotNull(dao.getTodo(changedOwnerId)?.seriesId)

        assertEquals(ownerId, changedOwnerId)
        assertFalse(originalSeriesId == replacementSeriesId)
        assertFalse(requireNotNull(dao.getTodoSeries(originalSeriesId)).active)
        assertTrue(requireNotNull(dao.getTodoSeries(replacementSeriesId)).active)
        assertNull(dao.getTodo(historicalId)?.deletedAt)
        assertNull(dao.getTodo(completedFutureId)?.deletedAt)
        assertNull(dao.getTodo(editedFutureId)?.deletedAt)
        assertNotNull(dao.getTodo(cleanFutureId)?.deletedAt)
        assertTrue(dao.getReminders(cleanFutureId).isEmpty())
        assertEquals(
            ownerAttachmentId,
            dao.getAttachments(AttachmentOwnerType.TODO.name, ownerId).single().id,
        )
        assertEquals(today + 3, dao.getTodo(ownerId)?.occurrenceEpochDay)

        val repeatedOwnerId = repository.saveTodo(changedDraft, clientOperationToken = token)
        assertEquals(ownerId, repeatedOwnerId)
        assertEquals(2, dao.observeTodoSeries().first().size)
    }

    @Test
    fun ledgerClientOperationTokenCoversStandaloneDueAndFutureSeries() = runBlocking {
        val today = LocalDate.now().toEpochDay()
        val standalone = LedgerDraft(
            type = LedgerType.EXPENSE,
            amountCents = 123,
            epochDay = today,
            minuteOfDay = 1,
        )
        val standaloneIds = coroutineScope {
            List(8) {
                async(Dispatchers.IO) {
                    repository.saveLedgerWithResult(
                        standalone,
                        clientOperationToken = "ledger-standalone-operation",
                    ).entryId
                }
            }.awaitAll()
        }
        assertEquals(1, standaloneIds.distinct().size)
        val updatedStandalone = repository.saveLedgerWithResult(
            standalone.copy(amountCents = 321, merchant = "Updated merchant"),
            clientOperationToken = "ledger-standalone-operation",
        )
        assertEquals(standaloneIds.first(), updatedStandalone.entryId)
        assertEquals(321L, database.dao().getLedgerEntry(requireNotNull(updatedStandalone.entryId))?.amountCents)

        val differentToken = repository.saveLedgerWithResult(
            standalone,
            clientOperationToken = "ledger-standalone-operation-two",
        )
        assertFalse(differentToken.entryId == standaloneIds.first())

        val dueDraft = standalone.copy(
            amountCents = 456,
            recurrence = RecurrenceRule(RecurrenceUnit.DAY),
        )
        val dueFirst = repository.saveLedgerWithResult(
            dueDraft,
            clientOperationToken = "ledger-due-operation",
        )
        val dueSecond = repository.saveLedgerWithResult(
            dueDraft.copy(amountCents = 654, merchant = "Changed due"),
            clientOperationToken = "ledger-due-operation",
        )
        assertEquals(dueFirst.entryId, dueSecond.entryId)
        assertFalse(dueFirst.seriesId == dueSecond.seriesId)
        assertEquals(654L, database.dao().getLedgerEntry(requireNotNull(dueSecond.entryId))?.amountCents)
        assertEquals(654L, database.dao().getLedgerSeries(requireNotNull(dueSecond.seriesId))?.amountCents)

        val futureDraft = dueDraft.copy(epochDay = today + 1)
        val futureFirst = repository.saveLedgerWithResult(
            futureDraft,
            throughEpochDay = today,
            clientOperationToken = "ledger-future-operation",
        )
        val changedFutureDraft = futureDraft.copy(
            amountCents = 777,
            epochDay = today + 2,
            recurrence = RecurrenceRule(RecurrenceUnit.MONTH, interval = 3),
        )
        val futureSecond = repository.saveLedgerWithResult(
            changedFutureDraft,
            throughEpochDay = today,
            clientOperationToken = "ledger-future-operation",
        )
        assertFalse(futureFirst.seriesId == futureSecond.seriesId)
        assertNull(futureFirst.entryId)
        assertNull(futureSecond.entryId)
        val futureSeries = database.dao().getLedgerSeries(requireNotNull(futureSecond.seriesId))
        assertEquals(777L, futureSeries?.amountCents)
        assertEquals(today + 2, futureSeries?.startEpochDay)
        assertEquals(RecurrenceUnit.MONTH, futureSeries?.recurrenceUnit)

        val futureWhenDue = repository.saveLedgerWithResult(
            changedFutureDraft,
            throughEpochDay = today + 2,
            clientOperationToken = "ledger-future-operation",
        )
        assertEquals(futureSecond.seriesId, futureWhenDue.seriesId)
        assertNotNull(futureWhenDue.entryId)

        assertEquals(4, database.dao().observeLedgerSeries().first().size)
        assertEquals(4, database.dao().observeLedgerEntries().first().size)

        val originalStandaloneId = requireNotNull(standaloneIds.first())
        database.dao().softDeleteLedgerEntry(originalStandaloneId, 1_000)
        val rebound = repository.saveLedgerWithResult(
            standalone,
            clientOperationToken = "ledger-standalone-operation",
        )
        assertFalse(rebound.entryId == originalStandaloneId)
        assertTrue(repository.attachmentOwnerExists(AttachmentOwnerType.LEDGER, requireNotNull(rebound.entryId)))
    }

    @Test
    fun ledgerCreationTokenKeepsIdentityWhenRecurrenceIsAddedAndChanged() = runBlocking {
        val today = LocalDate.now().toEpochDay()
        val token = "ledger-recurrence-transition"
        val standalone = LedgerDraft(
            type = LedgerType.EXPENSE,
            amountCents = 100,
            epochDay = today,
            minuteOfDay = 10,
        )
        val first = repository.saveLedgerWithResult(standalone, clientOperationToken = token)

        val recurring = repository.saveLedgerWithResult(
            standalone.copy(
                amountCents = 200,
                recurrence = RecurrenceRule(RecurrenceUnit.WEEK),
            ),
            clientOperationToken = token,
        )
        val changed = repository.saveLedgerWithResult(
            standalone.copy(
                amountCents = 300,
                recurrence = RecurrenceRule(RecurrenceUnit.MONTH, interval = 2),
            ),
            clientOperationToken = token,
        )

        assertEquals(first.entryId, recurring.entryId)
        assertEquals(recurring.entryId, changed.entryId)
        assertFalse(recurring.seriesId == changed.seriesId)
        assertEquals(1, database.dao().observeLedgerEntries().first().size)
        assertEquals(2, database.dao().observeLedgerSeries().first().size)
        assertFalse(requireNotNull(database.dao().getLedgerSeries(requireNotNull(recurring.seriesId))).active)
        assertEquals(300L, database.dao().getLedgerEntry(requireNotNull(changed.entryId))?.amountCents)
        assertEquals(RecurrenceUnit.MONTH, database.dao().getLedgerSeries(requireNotNull(changed.seriesId))?.recurrenceUnit)
    }

    @Test
    fun recurringLedgerTokenReplacesMaterializedSeriesWithoutChangingGeneratedHistory() = runBlocking {
        val dao = database.dao()
        val today = LocalDate.now().toEpochDay()
        val token = "ledger-materialized-replacement"
        val originalDraft = LedgerDraft(
            type = LedgerType.EXPENSE,
            amountCents = 100,
            epochDay = today - 2,
            minuteOfDay = 0,
            recurrence = RecurrenceRule(RecurrenceUnit.DAY),
        )
        val original = repository.saveLedgerWithResult(
            originalDraft,
            throughEpochDay = today,
            clientOperationToken = token,
        )
        val ownerId = requireNotNull(original.entryId)
        val originalSeriesId = requireNotNull(original.seriesId)
        val ownerAttachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.LEDGER,
                ownerId = ownerId,
                privatePath = "/private/receipt.jpg",
                originalName = "receipt.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 10,
            ),
        )
        repository.catchUpRecurring(today + 3)
        val otherEntriesBefore = dao.observeLedgerEntries().first()
            .filter { it.id != ownerId }
            .associateBy { it.id }

        val changedDraft = originalDraft.copy(
            type = LedgerType.INCOME,
            amountCents = 250,
            epochDay = today + 2,
            recurrence = RecurrenceRule(RecurrenceUnit.WEEK, interval = 2),
        )
        val changed = repository.saveLedgerWithResult(
            changedDraft,
            throughEpochDay = today + 3,
            clientOperationToken = token,
        )
        val replacementSeriesId = requireNotNull(changed.seriesId)

        assertEquals(ownerId, changed.entryId)
        assertFalse(originalSeriesId == replacementSeriesId)
        assertFalse(requireNotNull(dao.getLedgerSeries(originalSeriesId)).active)
        assertNull(dao.getLedgerSeries(originalSeriesId)?.clientOperationToken)
        assertTrue(requireNotNull(dao.getLedgerSeries(replacementSeriesId)).active)
        assertEquals(token, dao.getLedgerSeries(replacementSeriesId)?.clientOperationToken)
        assertEquals(otherEntriesBefore, dao.observeLedgerEntries().first()
            .filter { it.id != ownerId }
            .associateBy { it.id })
        assertEquals(today + 2, dao.getLedgerEntry(ownerId)?.occurrenceEpochDay)
        assertEquals(
            ownerAttachmentId,
            dao.getAttachments(AttachmentOwnerType.LEDGER.name, ownerId).single().id,
        )

        val repeated = repository.saveLedgerWithResult(
            changedDraft,
            throughEpochDay = today + 3,
            clientOperationToken = token,
        )
        assertEquals(changed, repeated)
        assertEquals(2, dao.observeLedgerSeries().first().size)
    }

    @Test
    fun duplicateRecurringTodoDeleteCannotReplaceItsUndoTombstone() = runBlocking {
        val dao = database.dao()
        val occurrence = LocalDate.of(2026, 8, 18).toEpochDay()
        val seriesId = dao.insertTodoSeries(
            TodoSeriesEntity(
                title = "Weekly",
                description = "Weekly",
                startEpochDay = occurrence,
                recurrenceUnit = RecurrenceUnit.WEEK,
            ),
        )
        val todoId = dao.insertTodo(
            TodoEntity(
                seriesId = seriesId,
                occurrenceEpochDay = occurrence,
                title = "Weekly",
                description = "Weekly",
                deadlineEpochDay = occurrence,
            ),
        )

        val receipt = repository.deleteTodoWithResult(
            todoId,
            SeriesEditScope.ONLY_THIS_OCCURRENCE,
        )
        val duplicateFailure = runCatching {
            repository.deleteTodoWithResult(todoId, SeriesEditScope.ONLY_THIS_OCCURRENCE)
        }.exceptionOrNull()

        assertNotNull(duplicateFailure)
        assertEquals(
            listOf(occurrence),
            dao.getTodoOccurrenceExceptionDays(seriesId, occurrence),
        )

        repository.undoDeleteTodo(receipt)

        assertNull(dao.getTodo(todoId)?.deletedAt)
        assertTrue(dao.getTodoOccurrenceExceptionDays(seriesId, occurrence).isEmpty())
    }

    @Test
    fun calendarExpansionCreatesOnlyTodoOccurrencesAndHonorsExceptions() = runBlocking {
        val dao = database.dao()
        val start = LocalDate.of(2026, 8, 3).toEpochDay()
        val todoSeriesId = dao.insertTodoSeries(
            TodoSeriesEntity(
                title = "Weekly",
                description = "Weekly",
                startEpochDay = start,
                recurrenceUnit = RecurrenceUnit.WEEK,
                reminderOffsetsCsv = "",
            ),
        )
        dao.insertTodoOccurrenceException(
            TodoOccurrenceExceptionEntity(
                seriesId = todoSeriesId,
                occurrenceEpochDay = start + 7,
            ),
        )
        dao.insertLedgerSeries(
            LedgerSeriesEntity(
                type = LedgerType.EXPENSE,
                amountCents = 500,
                startEpochDay = start,
                recurrenceUnit = RecurrenceUnit.WEEK,
            ),
        )

        repository.materializeTodoOccurrencesThrough(start + 21)
        repository.materializeTodoOccurrencesThrough(start + 21)

        val occurrenceDays = dao.observeActiveTodos().first()
            .filter { it.seriesId == todoSeriesId }
            .mapNotNull { it.occurrenceEpochDay }
            .sorted()
        assertEquals(listOf(start, start + 14, start + 21), occurrenceDays)
        assertTrue(dao.observeLedgerEntries().first().isEmpty())
    }

    @Test
    fun customMoveSwapsVisibleNeighborsWithoutReorderingRowsBetweenThem() = runBlocking {
        val dao = database.dao()
        val first = dao.insertTodo(TodoEntity(title = "First", description = "First", customOrder = 40))
        val hiddenOne = dao.insertTodo(TodoEntity(title = "Hidden one", description = "Hidden", customOrder = 30))
        val hiddenTwo = dao.insertTodo(TodoEntity(title = "Hidden two", description = "Hidden", customOrder = 20))
        val last = dao.insertTodo(TodoEntity(title = "Last", description = "Last", customOrder = 10))

        repository.moveTodoRelativeToVisibleNeighbor(last, first)

        val reordered = dao.getActiveTodosInCustomOrder()
        assertEquals(listOf(last, hiddenOne, hiddenTwo, first), reordered.map(TodoEntity::id))
        assertEquals(listOf(4L, 3L, 2L, 1L), reordered.map(TodoEntity::customOrder))
    }

    @Test
    fun todoTagRenameAndDeleteUpdateRowsSeriesAndFutureOccurrences() = runBlocking {
        val dao = database.dao()
        val start = LocalDate.of(2026, 8, 18).toEpochDay()
        val standaloneId = dao.insertTodo(
            TodoEntity(
                title = "Standalone",
                description = "Standalone",
                tagsCsv = "Work,Focus,Other",
            ),
        )
        val deletedId = dao.insertTodo(
            TodoEntity(
                title = "Deleted",
                description = "Deleted",
                tagsCsv = "WORK,Archive",
                deletedAt = 1L,
            ),
        )
        val seriesId = dao.insertTodoSeries(
            TodoSeriesEntity(
                title = "Weekly",
                description = "Weekly",
                startEpochDay = start,
                recurrenceUnit = RecurrenceUnit.WEEK,
                tagsCsv = "work,FOCUS",
                reminderOffsetsCsv = "",
            ),
        )

        repository.renameTodoTag(sourceTag = "work", replacementTag = "Focus")

        assertEquals("Focus,Other", dao.getTodo(standaloneId)?.tagsCsv)
        assertEquals("Focus,Archive", dao.getTodo(deletedId)?.tagsCsv)
        assertEquals("Focus", dao.getTodoSeries(seriesId)?.tagsCsv)

        repository.deleteTodoTag("FOCUS")
        repository.materializeTodoOccurrencesThrough(start)

        assertEquals("Other", dao.getTodo(standaloneId)?.tagsCsv)
        assertEquals("Archive", dao.getTodo(deletedId)?.tagsCsv)
        assertEquals("", dao.getTodoSeries(seriesId)?.tagsCsv)
        val generated = dao.getTodoOccurrence(seriesId, start)
        assertFalse(generated == null)
        assertEquals("", generated?.tagsCsv)
    }

    @Test
    fun deletingCategoryPromotesChildrenWithCollisionSafeNamesAndPreservesDescendants() = runBlocking {
        val dao = database.dao()
        val parentId = repository.addCategory("Parent")
        repository.addCategory("Games", parentId)
        repository.addCategory("Games (2)", parentId)
        val deletedId = repository.addCategory("Archive", parentId)
        val promotedId = repository.addCategory("Games", deletedId)
        val descendantId = repository.addCategory("Saves", promotedId)
        val todoId = repository.saveTodo(
            TodoDraft(description = "Move me to Uncategorized", categoryId = deletedId),
        )

        repository.deleteCategory(deletedId)

        assertNull(dao.getCategory(deletedId))
        assertNull(dao.getTodo(todoId)?.categoryId)
        assertEquals(parentId, dao.getCategory(promotedId)?.parentId)
        assertEquals("Games (3)", dao.getCategory(promotedId)?.name)
        assertEquals(promotedId, dao.getCategory(descendantId)?.parentId)
    }

    @Test
    fun futureRecurringConversionFailsBeforeMutatingEntrySeriesOrReceipt() = runBlocking {
        val dao = database.dao()
        val today = LocalDate.now().toEpochDay()
        val entryId = dao.insertLedgerEntry(
            LedgerEntryEntity(
                type = LedgerType.EXPENSE,
                amountCents = 500,
                epochDay = today,
                minuteOfDay = 600,
                note = "Original",
            ),
        )
        val attachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.LEDGER,
                ownerId = entryId,
                privatePath = "/private/receipt.jpg",
                originalName = "receipt.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 100,
            ),
        )

        val failure = runCatching {
            repository.saveLedgerWithResult(
                LedgerDraft(
                    id = entryId,
                    amountCents = 700,
                    epochDay = today + 1,
                    minuteOfDay = 0,
                    note = "Replacement",
                    recurrence = RecurrenceRule(RecurrenceUnit.DAY),
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(dao.observeLedgerSeries().first().isEmpty())
        val unchangedEntry = requireNotNull(dao.getLedgerEntry(entryId))
        assertNull(unchangedEntry.deletedAt)
        assertEquals(today, unchangedEntry.epochDay)
        assertEquals(500L, unchangedEntry.amountCents)
        assertEquals("Original", unchangedEntry.note)
        assertNull(dao.getAttachment(attachmentId)?.pendingDeleteAt)
    }

    @Test
    fun saveRejectsSoftDeletedLedgerEntryWithoutChangingIt() = runBlocking {
        val dao = database.dao()
        val today = LocalDate.now().toEpochDay()
        val entryId = dao.insertLedgerEntry(
            LedgerEntryEntity(
                type = LedgerType.INCOME,
                amountCents = 900,
                epochDay = today,
                minuteOfDay = 0,
            ),
        )
        assertEquals(1, dao.softDeleteLedgerEntry(entryId, 1_000L))

        val failure = runCatching {
            repository.saveLedgerWithResult(
                LedgerDraft(
                    id = entryId,
                    type = LedgerType.INCOME,
                    amountCents = 1_200,
                    epochDay = today,
                    minuteOfDay = 0,
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        val unchangedEntry = requireNotNull(dao.getLedgerEntry(entryId))
        assertEquals(1_000L, unchangedEntry.deletedAt)
        assertEquals(900L, unchangedEntry.amountCents)
    }

    @Test
    fun deleteUndoBeforeDeadlineRestoresLedgerEntryAndItsAttachmentsTogether() = runBlocking {
        val dao = database.dao()
        val entryId = dao.insertLedgerEntry(
            LedgerEntryEntity(
                type = LedgerType.EXPENSE,
                amountCents = 250,
                epochDay = 10,
                minuteOfDay = 0,
            ),
        )
        val attachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.LEDGER,
                ownerId = entryId,
                privatePath = "/private/undo-receipt.jpg",
                originalName = "undo-receipt.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 100,
            ),
        )
        val receipt = repository.deleteLedgerEntryWithResult(
            entryId,
            SeriesEditScope.ONLY_THIS_OCCURRENCE,
        )

        assertEquals(receipt.attachmentsPendingAt, dao.getAttachment(attachmentId)?.pendingDeleteAt)

        wallClockNow -= 3_600_000L
        val undoResult = repository.undoDeleteLedgerEntry(
            receipt,
            nowElapsedRealtimeMillis = receipt.undoExpiresAtElapsedRealtime - 1,
        )

        assertEquals(DeleteUndoResult.RESTORED, undoResult)
        assertNull(dao.getLedgerEntry(entryId)?.deletedAt)
        assertTrue(requireNotNull(dao.getLedgerEntry(entryId)).updatedAt > receipt.deletedAt)
        assertNull(dao.getAttachment(attachmentId)?.pendingDeleteAt)
    }

    @Test
    fun deleteUndoAtDeadlineExpiresWithoutRestoringParentOrAttachment() = runBlocking {
        val dao = database.dao()
        val todoId = dao.insertTodo(TodoEntity(title = "Todo", description = "Todo"))
        val attachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.TODO,
                ownerId = todoId,
                privatePath = "/private/expired-attachment.txt",
                originalName = "expired-attachment.txt",
                mimeType = "text/plain",
                sizeBytes = 10,
            ),
        )
        val receipt = repository.deleteTodoWithResult(
            todoId,
            SeriesEditScope.ONLY_THIS_OCCURRENCE,
        )

        val undoResult = repository.undoDeleteTodo(
            receipt,
            nowElapsedRealtimeMillis = receipt.undoExpiresAtElapsedRealtime,
        )

        assertEquals(DeleteUndoResult.EXPIRED, undoResult)
        assertNotNull(dao.getTodo(todoId)?.deletedAt)
        assertEquals(receipt.attachmentsPendingAt, dao.getAttachment(attachmentId)?.pendingDeleteAt)
    }

    @Test
    fun todoUndoDeadlineUsesDeletionWallClockWhenMutationClockIsFarAhead() = runBlocking {
        val dao = database.dao()
        val beforeDelete = wallClockNow
        val futureUpdatedAt = beforeDelete + 3_600_000L
        val todoId = dao.insertTodo(
            TodoEntity(
                title = "Future mutation Todo",
                description = "Future mutation Todo",
                createdAt = futureUpdatedAt,
                updatedAt = futureUpdatedAt,
            ),
        )
        val attachmentCreatedAt = futureUpdatedAt + 100L
        val attachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.TODO,
                ownerId = todoId,
                privatePath = "/private/future-todo.txt",
                originalName = "future-todo.txt",
                mimeType = "text/plain",
                sizeBytes = 10,
                createdAt = attachmentCreatedAt,
            ),
        )

        val receipt = repository.deleteTodoWithResult(
            todoId,
            SeriesEditScope.ONLY_THIS_OCCURRENCE,
        )
        assertEquals(futureUpdatedAt + 1L, receipt.deletedAt)
        assertEquals(elapsedRealtimeNow + 6_000L, receipt.undoExpiresAtElapsedRealtime)
        assertEquals(maxOf(beforeDelete + 6_000L, attachmentCreatedAt), receipt.attachmentsPendingAt)
        assertEquals(receipt.attachmentsPendingAt, dao.getAttachment(attachmentId)?.pendingDeleteAt)

        val undoResult = repository.undoDeleteTodo(
            receipt,
            nowElapsedRealtimeMillis = receipt.undoExpiresAtElapsedRealtime,
        )

        assertEquals(DeleteUndoResult.EXPIRED, undoResult)
        assertEquals(receipt.deletedAt, dao.getTodo(todoId)?.deletedAt)
        assertEquals(receipt.attachmentsPendingAt, dao.getAttachment(attachmentId)?.pendingDeleteAt)
    }

    @Test
    fun ledgerUndoDeadlineUsesDeletionWallClockWhenMutationClockIsFarAhead() = runBlocking {
        val dao = database.dao()
        val beforeDelete = wallClockNow
        val futureUpdatedAt = beforeDelete + 7_200_000L
        val entryId = dao.insertLedgerEntry(
            LedgerEntryEntity(
                type = LedgerType.EXPENSE,
                amountCents = 500,
                epochDay = 10,
                minuteOfDay = 0,
                createdAt = futureUpdatedAt,
                updatedAt = futureUpdatedAt,
            ),
        )
        val attachmentCreatedAt = futureUpdatedAt + 100L
        val attachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.LEDGER,
                ownerId = entryId,
                privatePath = "/private/future-ledger.txt",
                originalName = "future-ledger.txt",
                mimeType = "text/plain",
                sizeBytes = 10,
                createdAt = attachmentCreatedAt,
            ),
        )

        val receipt = repository.deleteLedgerEntryWithResult(
            entryId,
            SeriesEditScope.ONLY_THIS_OCCURRENCE,
        )
        assertEquals(futureUpdatedAt + 1L, receipt.deletedAt)
        assertEquals(elapsedRealtimeNow + 6_000L, receipt.undoExpiresAtElapsedRealtime)
        assertEquals(maxOf(beforeDelete + 6_000L, attachmentCreatedAt), receipt.attachmentsPendingAt)
        assertEquals(receipt.attachmentsPendingAt, dao.getAttachment(attachmentId)?.pendingDeleteAt)

        val undoResult = repository.undoDeleteLedgerEntry(
            receipt,
            nowElapsedRealtimeMillis = receipt.undoExpiresAtElapsedRealtime,
        )

        assertEquals(DeleteUndoResult.EXPIRED, undoResult)
        assertEquals(receipt.deletedAt, dao.getLedgerEntry(entryId)?.deletedAt)
        assertEquals(receipt.attachmentsPendingAt, dao.getAttachment(attachmentId)?.pendingDeleteAt)
    }

    @Test
    fun todoUndoExpiresEvenWhenWallClockMovesBackwardAfterDeletion() = runBlocking {
        val dao = database.dao()
        val todoId = dao.insertTodo(TodoEntity(title = "Todo", description = "Todo"))
        val attachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.TODO,
                ownerId = todoId,
                privatePath = "/private/rollback-todo.txt",
                originalName = "rollback-todo.txt",
                mimeType = "text/plain",
                sizeBytes = 10,
            ),
        )
        val receipt = repository.deleteTodoWithResult(
            todoId,
            SeriesEditScope.ONLY_THIS_OCCURRENCE,
        )
        wallClockNow -= 3_600_000L
        assertTrue(wallClockNow < receipt.attachmentsPendingAt)

        elapsedRealtimeNow = receipt.undoExpiresAtElapsedRealtime
        val undoResult = repository.undoDeleteTodo(receipt)

        assertEquals(DeleteUndoResult.EXPIRED, undoResult)
        assertEquals(receipt.deletedAt, dao.getTodo(todoId)?.deletedAt)
        assertEquals(receipt.attachmentsPendingAt, dao.getAttachment(attachmentId)?.pendingDeleteAt)
    }

    @Test
    fun ledgerUndoExpiresEvenWhenWallClockMovesBackwardAfterDeletion() = runBlocking {
        val dao = database.dao()
        val entryId = dao.insertLedgerEntry(
            LedgerEntryEntity(
                type = LedgerType.INCOME,
                amountCents = 500,
                epochDay = 10,
                minuteOfDay = 0,
            ),
        )
        val attachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.LEDGER,
                ownerId = entryId,
                privatePath = "/private/rollback-ledger.txt",
                originalName = "rollback-ledger.txt",
                mimeType = "text/plain",
                sizeBytes = 10,
            ),
        )
        val receipt = repository.deleteLedgerEntryWithResult(
            entryId,
            SeriesEditScope.ONLY_THIS_OCCURRENCE,
        )
        wallClockNow -= 7_200_000L
        assertTrue(wallClockNow < receipt.attachmentsPendingAt)

        elapsedRealtimeNow = receipt.undoExpiresAtElapsedRealtime
        val undoResult = repository.undoDeleteLedgerEntry(receipt)

        assertEquals(DeleteUndoResult.EXPIRED, undoResult)
        assertEquals(receipt.deletedAt, dao.getLedgerEntry(entryId)?.deletedAt)
        assertEquals(receipt.attachmentsPendingAt, dao.getAttachment(attachmentId)?.pendingDeleteAt)
    }

    @Test
    fun todoEditCompletionRestoreAndCategoryDeleteStayMonotonicAfterRollback() = runBlocking {
        val dao = database.dao()
        val todoId = dao.insertTodo(
            TodoEntity(
                title = "Before",
                description = "Before",
                createdAt = 2_000_000L,
                updatedAt = 2_100_000L,
            ),
        )

        repository.saveTodo(TodoDraft(id = todoId, description = "Edited"))
        assertEquals(2_100_001L, dao.getTodo(todoId)?.updatedAt)
        repository.completeTodo(todoId, completeSubtasks = false)
        assertEquals(2_100_002L, dao.getTodo(todoId)?.completedAt)
        repository.restoreTodo(todoId)
        assertEquals(2_100_003L, dao.getTodo(todoId)?.updatedAt)

        val categoryId = dao.insertCategory(CategoryEntity(name = "Delete me"))
        val categorizedTodoId = dao.insertTodo(
            TodoEntity(
                title = "Categorized",
                description = "Categorized",
                categoryId = categoryId,
                createdAt = 3_000_000L,
                updatedAt = 3_100_000L,
            ),
        )
        val seriesId = dao.insertTodoSeries(
            TodoSeriesEntity(
                title = "Series",
                description = "Series",
                categoryId = categoryId,
                startEpochDay = 10L,
                recurrenceUnit = RecurrenceUnit.DAY,
                createdAt = 3_500_000L,
                updatedAt = 3_600_000L,
            ),
        )

        repository.deleteCategory(categoryId)

        assertEquals(3_600_001L, dao.getTodo(categorizedTodoId)?.updatedAt)
        assertEquals(3_600_001L, dao.getTodoSeries(seriesId)?.updatedAt)
    }

    @Test
    fun bulkRecurringDeletesUseEveryAffectedRowAndAttachmentAsTimestampFloors() = runBlocking {
        val dao = database.dao()
        val todoSeriesId = dao.insertTodoSeries(
            TodoSeriesEntity(
                title = "Todo series",
                description = "Todo series",
                startEpochDay = 10L,
                recurrenceUnit = RecurrenceUnit.DAY,
                createdAt = 2_000_000L,
                updatedAt = 2_100_000L,
            ),
        )
        val selectedTodoId = dao.insertTodo(
            TodoEntity(
                seriesId = todoSeriesId,
                occurrenceEpochDay = 10L,
                title = "Selected",
                description = "Selected",
                deadlineEpochDay = 10L,
                createdAt = 2_200_000L,
                updatedAt = 2_300_000L,
            ),
        )
        val laterTodoId = dao.insertTodo(
            TodoEntity(
                seriesId = todoSeriesId,
                occurrenceEpochDay = 11L,
                title = "Later",
                description = "Later",
                deadlineEpochDay = 11L,
                createdAt = 4_000_000L,
                updatedAt = 4_100_000L,
            ),
        )
        val todoAttachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.TODO,
                ownerId = laterTodoId,
                privatePath = "/private/later-todo.txt",
                originalName = "later-todo.txt",
                mimeType = "text/plain",
                sizeBytes = 10,
                createdAt = 4_500_000L,
            ),
        )

        val todoReceipt = repository.deleteTodoWithResult(
            selectedTodoId,
            SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES,
        )

        assertEquals(4_100_001L, todoReceipt.deletedAt)
        assertEquals(todoReceipt.deletedAt, dao.getTodo(laterTodoId)?.deletedAt)
        assertEquals(4_500_000L, todoReceipt.attachmentsPendingAt)
        assertEquals(4_500_000L, dao.getAttachment(todoAttachmentId)?.pendingDeleteAt)

        val ledgerSeriesId = dao.insertLedgerSeries(
            LedgerSeriesEntity(
                type = LedgerType.EXPENSE,
                amountCents = 100,
                startEpochDay = 20L,
                recurrenceUnit = RecurrenceUnit.DAY,
                createdAt = 5_000_000L,
                updatedAt = 5_100_000L,
            ),
        )
        val selectedEntryId = dao.insertLedgerEntry(
            LedgerEntryEntity(
                seriesId = ledgerSeriesId,
                occurrenceEpochDay = 20L,
                type = LedgerType.EXPENSE,
                amountCents = 100,
                epochDay = 20L,
                minuteOfDay = 0,
                createdAt = 5_200_000L,
                updatedAt = 5_300_000L,
            ),
        )
        val laterEntryId = dao.insertLedgerEntry(
            LedgerEntryEntity(
                seriesId = ledgerSeriesId,
                occurrenceEpochDay = 21L,
                type = LedgerType.EXPENSE,
                amountCents = 100,
                epochDay = 21L,
                minuteOfDay = 0,
                createdAt = 7_000_000L,
                updatedAt = 7_100_000L,
            ),
        )
        val ledgerAttachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.LEDGER,
                ownerId = laterEntryId,
                privatePath = "/private/later-ledger.txt",
                originalName = "later-ledger.txt",
                mimeType = "text/plain",
                sizeBytes = 10,
                createdAt = 7_500_000L,
            ),
        )

        val ledgerReceipt = repository.deleteLedgerEntryWithResult(
            selectedEntryId,
            SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES,
        )

        assertEquals(7_100_001L, ledgerReceipt.deletedAt)
        assertEquals(ledgerReceipt.deletedAt, dao.getLedgerEntry(laterEntryId)?.deletedAt)
        assertEquals(7_500_000L, ledgerReceipt.attachmentsPendingAt)
        assertEquals(7_500_000L, dao.getAttachment(ledgerAttachmentId)?.pendingDeleteAt)
    }

    @Test
    fun recurringDeleteAndUndoSupportMoreThanApi26SqliteVariableLimit() = runBlocking {
        val dao = database.dao()
        val occurrenceCount = 1_001
        val todoMutationPredecessors = mutableListOf(100_000L, 200_000L)
        val todoSeriesId = dao.insertTodoSeries(
            TodoSeriesEntity(
                title = "Large Todo series",
                description = "Large Todo series",
                startEpochDay = 1L,
                recurrenceUnit = RecurrenceUnit.DAY,
                createdAt = 100_000L,
                updatedAt = 200_000L,
            ),
        )
        val todoIds = (0 until occurrenceCount).map { index ->
            val createdAt = 300_000L + index
            val updatedAt = 400_000L + index
            todoMutationPredecessors += createdAt
            todoMutationPredecessors += updatedAt
            dao.insertTodo(
                TodoEntity(
                    seriesId = todoSeriesId,
                    occurrenceEpochDay = index + 1L,
                    title = "Todo $index",
                    description = "Todo $index",
                    deadlineEpochDay = index + 1L,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                ),
            )
        }
        val todoAttachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.TODO,
                ownerId = todoIds.last(),
                privatePath = "/private/large-todo-tail.txt",
                originalName = "large-todo-tail.txt",
                mimeType = "text/plain",
                sizeBytes = 10,
                createdAt = 2_000_000L,
            ),
        )

        val todoReceipt = repository.deleteTodoWithResult(
            todoIds.first(),
            SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES,
        )

        val todoPredecessorMax = checkNotNull(todoMutationPredecessors.maxOrNull())
        assertEquals(maxOf(wallClockNow, todoPredecessorMax + 1L), todoReceipt.deletedAt)
        todoMutationPredecessors.forEach { predecessor ->
            assertTrue(todoReceipt.deletedAt > predecessor)
        }
        assertEquals(2_000_000L, todoReceipt.attachmentsPendingAt)
        assertEquals(todoReceipt.deletedAt, dao.getTodo(todoIds.last())?.deletedAt)
        assertEquals(
            DeleteUndoResult.RESTORED,
            repository.undoDeleteTodo(
                todoReceipt,
                nowElapsedRealtimeMillis = todoReceipt.undoExpiresAtElapsedRealtime - 1L,
            ),
        )
        val restoredFirstTodo = checkNotNull(dao.getTodo(todoIds.first()))
        val restoredLastTodo = checkNotNull(dao.getTodo(todoIds.last()))
        val restoredTodoSeries = checkNotNull(dao.getTodoSeries(todoSeriesId))
        assertNull(restoredFirstTodo.deletedAt)
        assertNull(restoredLastTodo.deletedAt)
        assertTrue(restoredFirstTodo.updatedAt > todoReceipt.deletedAt)
        assertTrue(restoredLastTodo.updatedAt > todoReceipt.deletedAt)
        assertTrue(restoredTodoSeries.active)
        assertTrue(restoredTodoSeries.updatedAt > todoReceipt.deletedAt)
        assertNull(dao.getAttachment(todoAttachmentId)?.pendingDeleteAt)

        val ledgerMutationPredecessors = mutableListOf(3_000_000L, 3_100_000L)
        val ledgerSeriesId = dao.insertLedgerSeries(
            LedgerSeriesEntity(
                type = LedgerType.EXPENSE,
                amountCents = 100,
                startEpochDay = 1L,
                recurrenceUnit = RecurrenceUnit.DAY,
                createdAt = 3_000_000L,
                updatedAt = 3_100_000L,
            ),
        )
        val entryIds = (0 until occurrenceCount).map { index ->
            val createdAt = 3_200_000L + index
            val updatedAt = 3_300_000L + index
            ledgerMutationPredecessors += createdAt
            ledgerMutationPredecessors += updatedAt
            dao.insertLedgerEntry(
                LedgerEntryEntity(
                    seriesId = ledgerSeriesId,
                    occurrenceEpochDay = index + 1L,
                    type = LedgerType.EXPENSE,
                    amountCents = 100,
                    epochDay = index + 1L,
                    minuteOfDay = 0,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                ),
            )
        }
        val ledgerAttachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.LEDGER,
                ownerId = entryIds.last(),
                privatePath = "/private/large-ledger-tail.txt",
                originalName = "large-ledger-tail.txt",
                mimeType = "text/plain",
                sizeBytes = 10,
                createdAt = 4_000_000L,
            ),
        )

        val ledgerReceipt = repository.deleteLedgerEntryWithResult(
            entryIds.first(),
            SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES,
        )

        val ledgerPredecessorMax = checkNotNull(ledgerMutationPredecessors.maxOrNull())
        assertEquals(maxOf(wallClockNow, ledgerPredecessorMax + 1L), ledgerReceipt.deletedAt)
        ledgerMutationPredecessors.forEach { predecessor ->
            assertTrue(ledgerReceipt.deletedAt > predecessor)
        }
        assertEquals(4_000_000L, ledgerReceipt.attachmentsPendingAt)
        assertEquals(ledgerReceipt.deletedAt, dao.getLedgerEntry(entryIds.last())?.deletedAt)
        assertEquals(
            DeleteUndoResult.RESTORED,
            repository.undoDeleteLedgerEntry(
                ledgerReceipt,
                nowElapsedRealtimeMillis = ledgerReceipt.undoExpiresAtElapsedRealtime - 1L,
            ),
        )
        val restoredFirstEntry = checkNotNull(dao.getLedgerEntry(entryIds.first()))
        val restoredLastEntry = checkNotNull(dao.getLedgerEntry(entryIds.last()))
        val restoredLedgerSeries = checkNotNull(dao.getLedgerSeries(ledgerSeriesId))
        assertNull(restoredFirstEntry.deletedAt)
        assertNull(restoredLastEntry.deletedAt)
        assertTrue(restoredFirstEntry.updatedAt > ledgerReceipt.deletedAt)
        assertTrue(restoredLastEntry.updatedAt > ledgerReceipt.deletedAt)
        assertTrue(restoredLedgerSeries.active)
        assertTrue(restoredLedgerSeries.updatedAt > ledgerReceipt.deletedAt)
        assertNull(dao.getAttachment(ledgerAttachmentId)?.pendingDeleteAt)
    }

    @Test
    fun ownerUndoDoesNotRestoreAnAttachmentThatWasAlreadyPendingAtCandidateDeadline() = runBlocking {
        val dao = database.dao()
        val collidingDeadline = wallClockNow + TaskLedgerRepository.DEFAULT_UNDO_WINDOW
        val todoId = dao.insertTodo(
            TodoEntity(
                title = "Todo",
                description = "Todo",
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        val independentlyPendingTodoAttachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.TODO,
                ownerId = todoId,
                privatePath = "/private/already-pending-todo.txt",
                originalName = "already-pending-todo.txt",
                mimeType = "text/plain",
                sizeBytes = 10,
                createdAt = 1L,
                pendingDeleteAt = collidingDeadline,
            ),
        )
        val activeTodoAttachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.TODO,
                ownerId = todoId,
                privatePath = "/private/active-todo.txt",
                originalName = "active-todo.txt",
                mimeType = "text/plain",
                sizeBytes = 10,
                createdAt = 2L,
            ),
        )

        val todoReceipt = repository.deleteTodoWithResult(
            todoId,
            SeriesEditScope.ONLY_THIS_OCCURRENCE,
        )
        assertEquals(collidingDeadline + 1L, todoReceipt.attachmentsPendingAt)
        assertEquals(
            DeleteUndoResult.RESTORED,
            repository.undoDeleteTodo(
                todoReceipt,
                nowElapsedRealtimeMillis = todoReceipt.undoExpiresAtElapsedRealtime - 1L,
            ),
        )
        assertEquals(
            collidingDeadline,
            dao.getAttachment(independentlyPendingTodoAttachmentId)?.pendingDeleteAt,
        )
        assertNull(dao.getAttachment(activeTodoAttachmentId)?.pendingDeleteAt)

        val entryId = dao.insertLedgerEntry(
            LedgerEntryEntity(
                type = LedgerType.EXPENSE,
                amountCents = 100,
                epochDay = 1L,
                minuteOfDay = 0,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        val independentlyPendingLedgerAttachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.LEDGER,
                ownerId = entryId,
                privatePath = "/private/already-pending-ledger.txt",
                originalName = "already-pending-ledger.txt",
                mimeType = "text/plain",
                sizeBytes = 10,
                createdAt = 1L,
                pendingDeleteAt = collidingDeadline,
            ),
        )
        val activeLedgerAttachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.LEDGER,
                ownerId = entryId,
                privatePath = "/private/active-ledger.txt",
                originalName = "active-ledger.txt",
                mimeType = "text/plain",
                sizeBytes = 10,
                createdAt = 2L,
            ),
        )

        val ledgerReceipt = repository.deleteLedgerEntryWithResult(
            entryId,
            SeriesEditScope.ONLY_THIS_OCCURRENCE,
        )
        assertEquals(collidingDeadline + 1L, ledgerReceipt.attachmentsPendingAt)
        assertEquals(
            DeleteUndoResult.RESTORED,
            repository.undoDeleteLedgerEntry(
                ledgerReceipt,
                nowElapsedRealtimeMillis = ledgerReceipt.undoExpiresAtElapsedRealtime - 1L,
            ),
        )
        assertEquals(
            collidingDeadline,
            dao.getAttachment(independentlyPendingLedgerAttachmentId)?.pendingDeleteAt,
        )
        assertNull(dao.getAttachment(activeLedgerAttachmentId)?.pendingDeleteAt)
    }

    @Test
    fun ledgerEditStopAndRecurrenceEndStayMonotonicAfterRollback() = runBlocking {
        val dao = database.dao()
        val entryId = dao.insertLedgerEntry(
            LedgerEntryEntity(
                type = LedgerType.INCOME,
                amountCents = 100,
                epochDay = 1L,
                minuteOfDay = 0,
                createdAt = 2_000_000L,
                updatedAt = 2_100_000L,
            ),
        )
        repository.saveLedger(
            LedgerDraft(
                id = entryId,
                type = LedgerType.INCOME,
                amountCents = 200,
                epochDay = 1L,
                minuteOfDay = 0,
            ),
        )
        assertEquals(2_100_001L, dao.getLedgerEntry(entryId)?.updatedAt)

        val stoppedSeriesId = dao.insertLedgerSeries(
            LedgerSeriesEntity(
                type = LedgerType.EXPENSE,
                amountCents = 100,
                startEpochDay = 2L,
                recurrenceUnit = RecurrenceUnit.DAY,
                createdAt = 3_000_000L,
                updatedAt = 3_100_000L,
            ),
        )
        repository.deactivateLedgerSeries(stoppedSeriesId)
        assertEquals(3_100_001L, dao.getLedgerSeries(stoppedSeriesId)?.updatedAt)

        val endedTodoSeriesId = dao.insertTodoSeries(
            TodoSeriesEntity(
                title = "Ended",
                description = "Ended",
                startEpochDay = 10L,
                recurrenceUnit = RecurrenceUnit.DAY,
                endEpochDay = 10L,
                createdAt = 4_000_000L,
                updatedAt = 4_100_000L,
            ),
        )
        repository.materializeTodoOccurrencesThrough(10L)
        assertFalse(requireNotNull(dao.getTodoSeries(endedTodoSeriesId)).active)
        assertEquals(4_100_001L, dao.getTodoSeries(endedTodoSeriesId)?.updatedAt)
    }

    @Test
    fun elapsedRealtimeOverflowRejectsDeleteBeforeChangingDatabaseState() = runBlocking {
        val dao = database.dao()
        val todoId = dao.insertTodo(TodoEntity(title = "Todo", description = "Todo"))
        val attachmentId = dao.insertAttachment(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.TODO,
                ownerId = todoId,
                privatePath = "/private/overflow.txt",
                originalName = "overflow.txt",
                mimeType = "text/plain",
                sizeBytes = 10,
            ),
        )
        elapsedRealtimeNow = Long.MAX_VALUE - 1_000L

        val failure = runCatching {
            repository.deleteTodoWithResult(todoId, SeriesEditScope.ONLY_THIS_OCCURRENCE)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertNull(dao.getTodo(todoId)?.deletedAt)
        assertNull(dao.getAttachment(attachmentId)?.pendingDeleteAt)
    }

    @Test
    fun atomicAttachmentInsertRejectsSoftDeletedOwner() = runBlocking {
        val dao = database.dao()
        val todoId = dao.insertTodo(TodoEntity(title = "Deleted", description = "Deleted"))
        assertEquals(1, dao.softDeleteTodo(todoId, 1_000L))

        val failure = runCatching {
            dao.insertAttachmentsWithinLimit(
                ownerType = AttachmentOwnerType.TODO.name,
                ownerId = todoId,
                attachments = listOf(
                    AttachmentEntity(
                        ownerType = AttachmentOwnerType.TODO,
                        ownerId = todoId,
                        privatePath = "/private/rejected.txt",
                        originalName = "rejected.txt",
                        mimeType = "text/plain",
                        sizeBytes = 10,
                    ),
                ),
                maxAttachments = 10,
                maxTotalBytes = 128L * 1024L * 1024L,
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(dao.getAllOwnerAttachments(AttachmentOwnerType.TODO.name, todoId).isEmpty())
    }

    @Test
    fun purgedOccurrenceExceptionRemainsTheTodoCatchUpWatermark() = runBlocking {
        val dao = database.dao()
        val start = LocalDate.of(2026, 1, 1).toEpochDay()
        val seriesId = dao.insertTodoSeries(
            TodoSeriesEntity(
                title = "Daily",
                description = "Daily",
                startEpochDay = start,
                recurrenceUnit = RecurrenceUnit.DAY,
                reminderOffsetsCsv = "",
            ),
        )
        dao.insertTodo(
            TodoEntity(
                seriesId = seriesId,
                occurrenceEpochDay = start,
                title = "Daily",
                description = "Daily",
                deadlineEpochDay = start,
            ),
        )
        dao.insertTodo(
            TodoEntity(
                seriesId = seriesId,
                occurrenceEpochDay = start + 1,
                title = "Daily",
                description = "Daily",
                deadlineEpochDay = start + 1,
                deletedAt = 5,
            ),
        )
        dao.insertTodoOccurrenceException(TodoOccurrenceExceptionEntity(seriesId, start + 1, 10))
        assertEquals(1, dao.purgeDeletedTodos(5))

        repository.materializeTodoOccurrencesThrough(start + 3)

        val days = dao.observeActiveTodos().first()
            .filter { it.seriesId == seriesId }
            .mapNotNull(TodoEntity::occurrenceEpochDay)
            .sorted()
        assertEquals(listOf(start, start + 2, start + 3), days)
        assertEquals(start + 3, dao.getMaxTodoOccurrenceDay(seriesId))
        assertEquals(start + 1, dao.getMaxTodoOccurrenceExceptionDay(seriesId))
    }

    @Test
    fun boundedCatchUpContinuesAcrossInvocationsWithoutDuplicates() = runBlocking {
        val dao = database.dao()
        val start = LocalDate.of(2020, 1, 1).toEpochDay()
        val seriesId = dao.insertLedgerSeries(
            LedgerSeriesEntity(
                type = LedgerType.EXPENSE,
                amountCents = 100,
                startEpochDay = start,
                recurrenceUnit = RecurrenceUnit.DAY,
            ),
        )

        val first = repository.catchUpRecurring(start + TaskLedgerRepository.MAX_OCCURRENCES_PER_CATCH_UP + 2)
        val firstRows = dao.observeLedgerEntries().first().filter { it.seriesId == seriesId }
        val second = repository.catchUpRecurring(start + TaskLedgerRepository.MAX_OCCURRENCES_PER_CATCH_UP + 2)
        val finalRows = dao.observeLedgerEntries().first().filter { it.seriesId == seriesId }

        assertTrue(first.hasMore)
        assertEquals(TaskLedgerRepository.MAX_OCCURRENCES_PER_CATCH_UP, firstRows.size)
        assertFalse(second.hasMore)
        assertEquals(TaskLedgerRepository.MAX_OCCURRENCES_PER_CATCH_UP + 3, finalRows.size)
        assertEquals(finalRows.size, finalRows.mapNotNull { it.occurrenceEpochDay }.distinct().size)
    }

    @Test
    fun catchUpBudgetAdvancesMultipleSeriesRoundRobin() = runBlocking {
        val dao = database.dao()
        val start = LocalDate.of(2020, 1, 1).toEpochDay()
        val firstSeries = dao.insertLedgerSeries(
            LedgerSeriesEntity(
                type = LedgerType.EXPENSE,
                amountCents = 100,
                startEpochDay = start,
                recurrenceUnit = RecurrenceUnit.DAY,
            ),
        )
        val secondSeries = dao.insertLedgerSeries(
            LedgerSeriesEntity(
                type = LedgerType.INCOME,
                amountCents = 200,
                startEpochDay = start,
                recurrenceUnit = RecurrenceUnit.DAY,
            ),
        )

        val result = repository.catchUpRecurring(start + 2_000)
        val rows = dao.observeLedgerEntries().first()

        assertTrue(result.hasMore)
        assertEquals(1_024, rows.count { it.seriesId == firstSeries })
        assertEquals(1_024, rows.count { it.seriesId == secondSeries })
    }

    @Test
    fun catchUpGivesNineMixedBacklogSeriesProgressInTheSameInvocation() = runBlocking {
        val dao = database.dao()
        val start = LocalDate.of(2020, 1, 1).toEpochDay()
        val todoSeriesIds = (1..5).map { index ->
            dao.insertTodoSeries(
                TodoSeriesEntity(
                    title = "Todo $index",
                    description = "Todo $index",
                    startEpochDay = start,
                    recurrenceUnit = RecurrenceUnit.DAY,
                    reminderOffsetsCsv = "",
                ),
            )
        }
        val ledgerSeriesIds = (1..4).map { index ->
            dao.insertLedgerSeries(
                LedgerSeriesEntity(
                    type = LedgerType.EXPENSE,
                    amountCents = index * 100L,
                    startEpochDay = start,
                    recurrenceUnit = RecurrenceUnit.DAY,
                ),
            )
        }

        val result = repository.catchUpRecurring(start + 5_000)
        val todos = dao.observeActiveTodos().first()
        val ledger = dao.observeLedgerEntries().first()

        assertTrue(result.hasMore)
        todoSeriesIds.forEach { seriesId ->
            assertTrue("Todo series $seriesId made no progress", todos.any { it.seriesId == seriesId })
        }
        ledgerSeriesIds.forEach { seriesId ->
            assertTrue("Ledger series $seriesId made no progress", ledger.any { it.seriesId == seriesId })
        }
    }

    @Test
    fun continuationCursorReachesSeriesBeyondBudgetAfterProcessedSeriesDeactivate() = runBlocking {
        val dao = database.dao()
        val start = LocalDate.of(2026, 1, 1).toEpochDay()
        val seriesIds = (1..5).map { index ->
            dao.insertLedgerSeries(
                LedgerSeriesEntity(
                    type = LedgerType.INCOME,
                    amountCents = index * 100L,
                    startEpochDay = start,
                    recurrenceUnit = RecurrenceUnit.DAY,
                    endEpochDay = start,
                ),
            )
        }
        val limitedRepository = TaskLedgerRepository(
            database = database,
            maxOccurrencesPerCatchUp = 3,
        )

        val first = limitedRepository.catchUpRecurring(start)
        val second = limitedRepository.catchUpRecurring(start, first.nextCursor)
        val rows = dao.observeLedgerEntries().first()

        assertTrue(first.hasMore)
        assertNotNull(first.nextCursor)
        assertFalse(second.hasMore)
        seriesIds.forEach { seriesId ->
            assertEquals(1, rows.count { it.seriesId == seriesId })
        }
    }

    @Test
    fun calendarMaterializationContinuesPastBudgetWithoutGeneratingLedgerRows() = runBlocking {
        val dao = database.dao()
        val start = LocalDate.of(2020, 1, 1).toEpochDay()
        val todoSeries = dao.insertTodoSeries(
            TodoSeriesEntity(
                title = "Long daily",
                description = "Long daily",
                startEpochDay = start,
                recurrenceUnit = RecurrenceUnit.DAY,
                reminderOffsetsCsv = "",
            ),
        )
        dao.insertLedgerSeries(
            LedgerSeriesEntity(
                type = LedgerType.EXPENSE,
                amountCents = 100,
                startEpochDay = start,
                recurrenceUnit = RecurrenceUnit.DAY,
            ),
        )
        val through = start + TaskLedgerRepository.MAX_OCCURRENCES_PER_CATCH_UP + 2

        val first = repository.materializeTodoOccurrencesThrough(through)
        val second = repository.materializeTodoOccurrencesThrough(through)
        val todos = dao.observeActiveTodos().first().filter { it.seriesId == todoSeries }

        assertTrue(first.hasMore)
        assertFalse(second.hasMore)
        assertEquals(TaskLedgerRepository.MAX_OCCURRENCES_PER_CATCH_UP + 3, todos.size)
        assertTrue(dao.observeLedgerEntries().first().isEmpty())
    }

    @Test
    fun recurringLedgerSaveReturnsOnlyItsDueStartEntrySynchronously() = runBlocking {
        val start = LocalDate.of(1, 1, 1).toEpochDay()
        val result = repository.saveLedgerWithResult(
            LedgerDraft(
                type = LedgerType.EXPENSE,
                amountCents = 100,
                epochDay = start,
                minuteOfDay = 0,
                recurrence = RecurrenceRule(RecurrenceUnit.DAY),
            ),
            throughEpochDay = LocalDate.now().toEpochDay(),
        )

        assertNotNull(result.entryId)
        assertEquals(1, database.dao().observeLedgerEntries().first().size)
        assertEquals(start, database.dao().getLedgerEntry(requireNotNull(result.entryId))?.occurrenceEpochDay)
    }

    @Test
    fun futureRecurringLedgerSaveReturnsNoEntryUntilItsDate() = runBlocking {
        val today = LocalDate.now().toEpochDay()
        val result = repository.saveLedgerWithResult(
            LedgerDraft(
                type = LedgerType.INCOME,
                amountCents = 100,
                epochDay = today + 1,
                minuteOfDay = 0,
                recurrence = RecurrenceRule(RecurrenceUnit.DAY),
            ),
            throughEpochDay = today,
        )

        assertNull(result.entryId)
        assertTrue(database.dao().observeLedgerEntries().first().isEmpty())
    }
}
