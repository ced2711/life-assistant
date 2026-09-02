package com.ced2711.lifetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskLedgerDao {
    @Query("SELECT * FROM categories ORDER BY parentId, sortOrder, name COLLATE NOCASE")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY parentId, sortOrder, name COLLATE NOCASE")
    suspend fun getCategories(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategory(id: Long): CategoryEntity?

    @Insert
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("UPDATE todos SET categoryId = NULL, updatedAt = :updatedAt WHERE categoryId = :categoryId")
    suspend fun uncategorizeTodos(categoryId: Long, updatedAt: Long)

    @Query("UPDATE todo_series SET categoryId = NULL, updatedAt = :updatedAt WHERE categoryId = :categoryId")
    suspend fun uncategorizeTodoSeries(categoryId: Long, updatedAt: Long)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: Long)

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL AND completedAt IS NULL ORDER BY customOrder DESC, createdAt DESC, id DESC")
    fun observeActiveTodos(): Flow<List<TodoEntity>>

    @Query(
        """
        SELECT * FROM todos
        WHERE deletedAt IS NULL
          AND completedAt IS NULL
          AND deadlineEpochDay = :epochDay
        ORDER BY COALESCE(deadlineMinute, -1), customOrder DESC, createdAt DESC, id DESC
        """,
    )
    fun observeActiveTodosForDeadlineDay(epochDay: Long): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL AND completedAt IS NULL ORDER BY customOrder DESC, createdAt DESC, id DESC")
    suspend fun getActiveTodosInCustomOrder(): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL AND completedAt IS NOT NULL ORDER BY completedAt DESC")
    fun observeCompletedTodos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL AND deadlineEpochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY deadlineEpochDay, COALESCE(deadlineMinute, -1)")
    fun observeTodosInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id LIMIT 1")
    suspend fun getTodo(id: Long): TodoEntity?

    @Query(
        "SELECT MAX(CASE WHEN createdAt > updatedAt THEN createdAt ELSE updatedAt END) " +
            "FROM todos WHERE id IN (:ids)",
    )
    suspend fun getMaxTodoMutationPredecessor(ids: List<Long>): Long?

    @Query(
        "SELECT MAX(CASE WHEN createdAt > updatedAt THEN createdAt ELSE updatedAt END) " +
            "FROM todos WHERE categoryId = :categoryId",
    )
    suspend fun getMaxTodoMutationPredecessorInCategory(categoryId: Long): Long?

    @Query("SELECT * FROM todos WHERE clientOperationToken = :token LIMIT 1")
    suspend fun getTodoByClientOperationToken(token: String): TodoEntity?

    @Query("UPDATE todos SET clientOperationToken = NULL WHERE id = :id AND clientOperationToken = :token")
    suspend fun clearTodoClientOperationToken(id: Long, token: String): Int

    @Query("SELECT * FROM todos")
    suspend fun getAllTodosForTagMaintenance(): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE seriesId = :seriesId AND occurrenceEpochDay = :occurrenceEpochDay LIMIT 1")
    suspend fun getTodoOccurrence(seriesId: Long, occurrenceEpochDay: Long): TodoEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTodo(todo: TodoEntity): Long

    @Update
    suspend fun updateTodo(todo: TodoEntity)

    @Query("UPDATE todos SET customOrder = :customOrder WHERE id = :id")
    suspend fun updateTodoCustomOrder(id: Long, customOrder: Long)

    @Query("UPDATE todos SET tagsCsv = :tagsCsv, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTodoTags(id: Long, tagsCsv: String, updatedAt: Long)

    @Query("UPDATE todos SET completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setTodoCompleted(id: Long, completedAt: Long?, updatedAt: Long)

    @Query("UPDATE subtasks SET isCompleted = :completed WHERE todoId = :todoId")
    suspend fun setAllSubtasksCompleted(todoId: Long, completed: Boolean)

    @Query("UPDATE todos SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDeleteTodo(id: Long, deletedAt: Long): Int

    @Query("UPDATE todos SET deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun undoDeleteTodo(id: Long, updatedAt: Long)

    @Query("UPDATE todos SET deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id AND deletedAt = :expectedDeletedAt")
    suspend fun undoDeleteTodoIfDeletedAt(id: Long, expectedDeletedAt: Long, updatedAt: Long): Int

    @Query("DELETE FROM todos WHERE deletedAt IS NOT NULL AND deletedAt <= :before")
    suspend fun purgeDeletedTodos(before: Long): Int

    @Query("SELECT * FROM subtasks WHERE todoId = :todoId ORDER BY sortOrder, id")
    fun observeSubtasks(todoId: Long): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM subtasks WHERE todoId = :todoId ORDER BY sortOrder, id")
    suspend fun getSubtasks(todoId: Long): List<SubtaskEntity>

    @Insert
    suspend fun insertSubtasks(subtasks: List<SubtaskEntity>)

    @Update
    suspend fun updateSubtask(subtask: SubtaskEntity)

    @Query("DELETE FROM subtasks WHERE todoId = :todoId")
    suspend fun deleteSubtasksForTodo(todoId: Long)

    @Query("SELECT * FROM todo_reminders WHERE todoId = :todoId ORDER BY offsetMinutes")
    fun observeReminders(todoId: Long): Flow<List<TodoReminderEntity>>

    @Query("SELECT * FROM todo_reminders WHERE todoId = :todoId ORDER BY offsetMinutes")
    suspend fun getReminders(todoId: Long): List<TodoReminderEntity>

    @Query("SELECT * FROM todo_reminders")
    suspend fun getAllReminders(): List<TodoReminderEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReminders(reminders: List<TodoReminderEntity>)

    @Query("DELETE FROM todo_reminders WHERE todoId = :todoId")
    suspend fun deleteRemindersForTodo(todoId: Long)

    @Insert
    suspend fun insertTodoSeries(series: TodoSeriesEntity): Long

    @Update
    suspend fun updateTodoSeries(series: TodoSeriesEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodoSeriesSubtasks(subtasks: List<TodoSeriesSubtaskEntity>)

    @Query("SELECT * FROM todo_series_subtasks WHERE seriesId = :seriesId ORDER BY sortOrder")
    suspend fun getTodoSeriesSubtasks(seriesId: Long): List<TodoSeriesSubtaskEntity>

    @Query("DELETE FROM todo_series_subtasks WHERE seriesId = :seriesId")
    suspend fun deleteTodoSeriesSubtasks(seriesId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTodoOccurrenceException(exception: TodoOccurrenceExceptionEntity): Long

    @Query("DELETE FROM todo_occurrence_exceptions WHERE seriesId = :seriesId AND occurrenceEpochDay = :occurrenceEpochDay")
    suspend fun deleteTodoOccurrenceException(seriesId: Long, occurrenceEpochDay: Long)

    @Query("DELETE FROM todo_occurrence_exceptions WHERE seriesId = :seriesId AND occurrenceEpochDay = :occurrenceEpochDay AND createdAt = :expectedCreatedAt")
    suspend fun deleteTodoOccurrenceExceptionIfCreatedAt(
        seriesId: Long,
        occurrenceEpochDay: Long,
        expectedCreatedAt: Long,
    ): Int

    @Query("SELECT occurrenceEpochDay FROM todo_occurrence_exceptions WHERE seriesId = :seriesId AND occurrenceEpochDay <= :throughEpochDay")
    suspend fun getTodoOccurrenceExceptionDays(seriesId: Long, throughEpochDay: Long): List<Long>

    @Query("SELECT MAX(occurrenceEpochDay) FROM todos WHERE seriesId = :seriesId")
    suspend fun getMaxTodoOccurrenceDay(seriesId: Long): Long?

    @Query("SELECT MAX(occurrenceEpochDay) FROM todo_occurrence_exceptions WHERE seriesId = :seriesId")
    suspend fun getMaxTodoOccurrenceExceptionDay(seriesId: Long): Long?

    @Query("SELECT occurrenceEpochDay FROM todo_occurrence_exceptions WHERE seriesId = :seriesId AND occurrenceEpochDay > :afterEpochDay AND occurrenceEpochDay <= :throughEpochDay")
    suspend fun getTodoOccurrenceExceptionDaysAfter(
        seriesId: Long,
        afterEpochDay: Long,
        throughEpochDay: Long,
    ): List<Long>

    @Query("SELECT * FROM todo_series WHERE active = 1")
    suspend fun getActiveTodoSeries(): List<TodoSeriesEntity>

    @Query("SELECT * FROM todo_series WHERE id = :id LIMIT 1")
    suspend fun getTodoSeries(id: Long): TodoSeriesEntity?

    @Query(
        "SELECT MAX(CASE WHEN createdAt > updatedAt THEN createdAt ELSE updatedAt END) " +
            "FROM todo_series WHERE categoryId = :categoryId",
    )
    suspend fun getMaxTodoSeriesMutationPredecessorInCategory(categoryId: Long): Long?

    @Query("SELECT * FROM todo_series")
    suspend fun getAllTodoSeriesForTagMaintenance(): List<TodoSeriesEntity>

    @Query("SELECT * FROM todo_series ORDER BY createdAt DESC")
    fun observeTodoSeries(): Flow<List<TodoSeriesEntity>>

    @Query("UPDATE todo_series SET tagsCsv = :tagsCsv, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTodoSeriesTags(id: Long, tagsCsv: String, updatedAt: Long)

    @Query("UPDATE todo_series SET active = 0, updatedAt = :updatedAt WHERE id = :seriesId")
    suspend fun deactivateTodoSeries(seriesId: Long, updatedAt: Long)

    @Query("UPDATE todo_series SET active = :active, updatedAt = :updatedAt WHERE id = :seriesId")
    suspend fun setTodoSeriesActive(seriesId: Long, active: Boolean, updatedAt: Long)

    @Query("UPDATE todo_series SET active = :active, updatedAt = :updatedAt WHERE id = :seriesId AND updatedAt = :expectedUpdatedAt")
    suspend fun restoreTodoSeriesStateIfUpdatedAt(
        seriesId: Long,
        active: Boolean,
        expectedUpdatedAt: Long,
        updatedAt: Long,
    ): Int

    @Query("UPDATE todos SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE seriesId = :seriesId AND occurrenceEpochDay > :afterEpochDay AND deletedAt IS NULL")
    suspend fun softDeleteFutureTodoOccurrences(seriesId: Long, afterEpochDay: Long, deletedAt: Long)

    @Query("SELECT id FROM todos WHERE seriesId = :seriesId AND occurrenceEpochDay >= :fromEpochDay AND deletedAt IS NULL")
    suspend fun getActiveTodoOccurrenceIdsFrom(seriesId: Long, fromEpochDay: Long): List<Long>

    @Query(
        """
        SELECT todo.id
        FROM todos AS todo
        WHERE todo.seriesId = :seriesId
          AND todo.id != :ownerId
          AND todo.occurrenceEpochDay >= :fromEpochDay
          AND todo.completedAt IS NULL
          AND todo.deletedAt IS NULL
          AND todo.updatedAt = todo.createdAt
          AND NOT EXISTS (
              SELECT 1 FROM subtasks AS subtask
              WHERE subtask.todoId = todo.id AND subtask.isCompleted = 1
          )
          AND NOT EXISTS (
              SELECT 1 FROM attachments AS attachment
              WHERE attachment.ownerType = :ownerType
                AND attachment.ownerId = todo.id
                AND attachment.pendingDeleteAt IS NULL
          )
        """,
    )
    suspend fun getCleanableTodoOccurrenceIdsForSeriesReplacement(
        seriesId: Long,
        ownerId: Long,
        fromEpochDay: Long,
        ownerType: String,
    ): List<Long>

    @Query("UPDATE todos SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE seriesId = :seriesId AND occurrenceEpochDay >= :fromEpochDay AND deletedAt IS NULL")
    suspend fun softDeleteTodoOccurrencesFrom(seriesId: Long, fromEpochDay: Long, deletedAt: Long): Int

    @Query("SELECT id FROM todos WHERE seriesId = :seriesId AND occurrenceEpochDay >= :fromEpochDay AND deletedAt = :deletedAt")
    suspend fun getTodoOccurrenceIdsDeletedAtFrom(
        seriesId: Long,
        fromEpochDay: Long,
        deletedAt: Long,
    ): List<Long>

    @Query("UPDATE todos SET deletedAt = NULL, updatedAt = :updatedAt WHERE seriesId = :seriesId AND occurrenceEpochDay >= :fromEpochDay AND deletedAt = :deletedAt")
    suspend fun restoreTodoOccurrencesDeletedAtFrom(
        seriesId: Long,
        fromEpochDay: Long,
        deletedAt: Long,
        updatedAt: Long,
    ): Int

    @Query("SELECT * FROM ledger_entries WHERE deletedAt IS NULL ORDER BY epochDay DESC, minuteOfDay DESC, createdAt DESC")
    fun observeLedgerEntries(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries WHERE deletedAt IS NULL AND epochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY epochDay, minuteOfDay")
    fun observeLedgerEntriesInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries WHERE id = :id LIMIT 1")
    suspend fun getLedgerEntry(id: Long): LedgerEntryEntity?

    @Query(
        "SELECT MAX(CASE WHEN createdAt > updatedAt THEN createdAt ELSE updatedAt END) " +
            "FROM ledger_entries WHERE id IN (:ids)",
    )
    suspend fun getMaxLedgerMutationPredecessor(ids: List<Long>): Long?

    @Query("SELECT * FROM ledger_entries WHERE clientOperationToken = :token LIMIT 1")
    suspend fun getLedgerEntryByClientOperationToken(token: String): LedgerEntryEntity?

    @Query("UPDATE ledger_entries SET clientOperationToken = NULL WHERE id = :id AND clientOperationToken = :token")
    suspend fun clearLedgerClientOperationToken(id: Long, token: String): Int

    @Query("SELECT * FROM ledger_entries WHERE seriesId = :seriesId AND occurrenceEpochDay = :occurrenceEpochDay LIMIT 1")
    suspend fun getLedgerOccurrence(seriesId: Long, occurrenceEpochDay: Long): LedgerEntryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLedgerEntry(entry: LedgerEntryEntity): Long

    @Update
    suspend fun updateLedgerEntry(entry: LedgerEntryEntity)

    @Query("UPDATE ledger_entries SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDeleteLedgerEntry(id: Long, deletedAt: Long): Int

    @Query("UPDATE ledger_entries SET deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun undoDeleteLedgerEntry(id: Long, updatedAt: Long)

    @Query("UPDATE ledger_entries SET deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id AND deletedAt = :expectedDeletedAt")
    suspend fun undoDeleteLedgerEntryIfDeletedAt(
        id: Long,
        expectedDeletedAt: Long,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM ledger_entries WHERE deletedAt IS NOT NULL AND deletedAt <= :before")
    suspend fun purgeDeletedLedgerEntries(before: Long): Int

    @Insert
    suspend fun insertLedgerSeries(series: LedgerSeriesEntity): Long

    @Update
    suspend fun updateLedgerSeries(series: LedgerSeriesEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLedgerOccurrenceException(exception: LedgerOccurrenceExceptionEntity): Long

    @Query("DELETE FROM ledger_occurrence_exceptions WHERE seriesId = :seriesId AND occurrenceEpochDay = :occurrenceEpochDay")
    suspend fun deleteLedgerOccurrenceException(seriesId: Long, occurrenceEpochDay: Long)

    @Query("DELETE FROM ledger_occurrence_exceptions WHERE seriesId = :seriesId AND occurrenceEpochDay = :occurrenceEpochDay AND createdAt = :expectedCreatedAt")
    suspend fun deleteLedgerOccurrenceExceptionIfCreatedAt(
        seriesId: Long,
        occurrenceEpochDay: Long,
        expectedCreatedAt: Long,
    ): Int

    @Query("SELECT occurrenceEpochDay FROM ledger_occurrence_exceptions WHERE seriesId = :seriesId AND occurrenceEpochDay <= :throughEpochDay")
    suspend fun getLedgerOccurrenceExceptionDays(seriesId: Long, throughEpochDay: Long): List<Long>

    @Query("SELECT MAX(occurrenceEpochDay) FROM ledger_entries WHERE seriesId = :seriesId")
    suspend fun getMaxLedgerOccurrenceDay(seriesId: Long): Long?

    @Query("SELECT MAX(occurrenceEpochDay) FROM ledger_occurrence_exceptions WHERE seriesId = :seriesId")
    suspend fun getMaxLedgerOccurrenceExceptionDay(seriesId: Long): Long?

    @Query("SELECT occurrenceEpochDay FROM ledger_occurrence_exceptions WHERE seriesId = :seriesId AND occurrenceEpochDay > :afterEpochDay AND occurrenceEpochDay <= :throughEpochDay")
    suspend fun getLedgerOccurrenceExceptionDaysAfter(
        seriesId: Long,
        afterEpochDay: Long,
        throughEpochDay: Long,
    ): List<Long>

    @Query("SELECT * FROM ledger_series WHERE active = 1")
    suspend fun getActiveLedgerSeries(): List<LedgerSeriesEntity>

    @Query("SELECT * FROM ledger_series WHERE id = :id LIMIT 1")
    suspend fun getLedgerSeries(id: Long): LedgerSeriesEntity?

    @Query("SELECT * FROM ledger_series WHERE clientOperationToken = :token LIMIT 1")
    suspend fun getLedgerSeriesByClientOperationToken(token: String): LedgerSeriesEntity?

    @Query("UPDATE ledger_series SET clientOperationToken = NULL WHERE id = :id AND clientOperationToken = :token")
    suspend fun clearLedgerSeriesClientOperationToken(id: Long, token: String): Int

    @Query("SELECT * FROM ledger_series ORDER BY createdAt DESC")
    fun observeLedgerSeries(): Flow<List<LedgerSeriesEntity>>

    @Query("UPDATE ledger_series SET active = 0, updatedAt = :updatedAt WHERE id = :seriesId")
    suspend fun deactivateLedgerSeries(seriesId: Long, updatedAt: Long)

    @Query("UPDATE ledger_series SET active = :active, updatedAt = :updatedAt WHERE id = :seriesId")
    suspend fun setLedgerSeriesActive(seriesId: Long, active: Boolean, updatedAt: Long)

    @Query("UPDATE ledger_series SET active = :active, updatedAt = :updatedAt WHERE id = :seriesId AND updatedAt = :expectedUpdatedAt")
    suspend fun restoreLedgerSeriesStateIfUpdatedAt(
        seriesId: Long,
        active: Boolean,
        expectedUpdatedAt: Long,
        updatedAt: Long,
    ): Int

    @Query("SELECT id FROM ledger_entries WHERE seriesId = :seriesId AND occurrenceEpochDay >= :fromEpochDay AND deletedAt IS NULL")
    suspend fun getActiveLedgerOccurrenceIdsFrom(seriesId: Long, fromEpochDay: Long): List<Long>

    @Query("UPDATE ledger_entries SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE seriesId = :seriesId AND occurrenceEpochDay >= :fromEpochDay AND deletedAt IS NULL")
    suspend fun softDeleteLedgerOccurrencesFrom(seriesId: Long, fromEpochDay: Long, deletedAt: Long): Int

    @Query("SELECT id FROM ledger_entries WHERE seriesId = :seriesId AND occurrenceEpochDay >= :fromEpochDay AND deletedAt = :deletedAt")
    suspend fun getLedgerOccurrenceIdsDeletedAtFrom(
        seriesId: Long,
        fromEpochDay: Long,
        deletedAt: Long,
    ): List<Long>

    @Query("UPDATE ledger_entries SET deletedAt = NULL, updatedAt = :updatedAt WHERE seriesId = :seriesId AND occurrenceEpochDay >= :fromEpochDay AND deletedAt = :deletedAt")
    suspend fun restoreLedgerOccurrencesDeletedAtFrom(
        seriesId: Long,
        fromEpochDay: Long,
        deletedAt: Long,
        updatedAt: Long,
    ): Int

    @Query(
        """
        SELECT epochDay,
               SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE 0 END) AS incomeCents,
               SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END) AS expenseCents
        FROM ledger_entries
        WHERE deletedAt IS NULL AND epochDay BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY epochDay
        ORDER BY epochDay
        """,
    )
    fun observeDailyLedgerTotals(startEpochDay: Long, endEpochDay: Long): Flow<List<DailyLedgerTotal>>

    @Query("SELECT * FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId AND pendingDeleteAt IS NULL ORDER BY createdAt")
    fun observeAttachments(ownerType: String, ownerId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId AND pendingDeleteAt IS NULL ORDER BY createdAt")
    suspend fun getAttachments(ownerType: String, ownerId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY createdAt, id")
    suspend fun getAllOwnerAttachments(ownerType: String, ownerId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :attachmentId LIMIT 1")
    suspend fun getAttachment(attachmentId: Long): AttachmentEntity?

    @Query(
        "SELECT MAX(createdAt) FROM attachments " +
            "WHERE ownerType = :ownerType AND ownerId IN (:ownerIds)",
    )
    suspend fun getMaxAttachmentCreatedAt(ownerType: String, ownerIds: List<Long>): Long?

    @Query(
        "SELECT EXISTS(SELECT 1 FROM attachments " +
            "WHERE ownerType = :ownerType AND ownerId = :ownerId " +
            "AND pendingDeleteAt = :pendingDeleteAt)",
    )
    suspend fun ownerHasAttachmentMarkedAt(
        ownerType: String,
        ownerId: Long,
        pendingDeleteAt: Long,
    ): Boolean

    @Query("SELECT COUNT(*) FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun getAttachmentCount(ownerType: String, ownerId: Long): Int

    @Query("SELECT sizeBytes FROM attachments")
    suspend fun getAllAttachmentSizeBytes(): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM todos WHERE id = :ownerId AND deletedAt IS NULL)")
    suspend fun activeTodoExists(ownerId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM ledger_entries WHERE id = :ownerId AND deletedAt IS NULL)")
    suspend fun activeLedgerEntryExists(ownerId: Long): Boolean

    @Insert
    suspend fun insertAttachment(attachment: AttachmentEntity): Long

    /**
     * Keeps the per-owner count and global byte limits atomic with insertion. Pending deletions
     * still occupy both limits until their Undo window expires and their rows are physically
     * purged. Existing over-limit data is never changed; this transaction only rejects additions.
     */
    @Transaction
    suspend fun insertAttachmentsWithinLimit(
        ownerType: String,
        ownerId: Long,
        attachments: List<AttachmentEntity>,
        maxAttachments: Int,
        maxTotalBytes: Long,
    ): List<Long> {
        require(maxAttachments >= 0) { "Attachment limit cannot be negative." }
        require(maxTotalBytes >= 0) { "Attachment byte limit cannot be negative." }
        require(attachments.isNotEmpty()) { "At least one attachment is required." }
        val parsedOwnerType = runCatching { AttachmentOwnerType.valueOf(ownerType) }
            .getOrElse { throw IllegalArgumentException("Unknown attachment owner type.", it) }
        require(
            attachments.all {
                    it.ownerType == parsedOwnerType &&
                    it.ownerId == ownerId &&
                    it.sizeBytes >= 0 &&
                    it.pendingDeleteAt == null
            },
        ) { "New attachments must have valid sizes, be active, and belong to the requested owner." }

        val ownerIsActive = when (parsedOwnerType) {
            AttachmentOwnerType.TODO -> activeTodoExists(ownerId)
            AttachmentOwnerType.LEDGER -> activeLedgerEntryExists(ownerId)
        }
        require(ownerIsActive) { "The attachment owner no longer exists." }

        val existingCount = getAttachmentCount(ownerType, ownerId)
        require(isWithinAttachmentLimit(existingCount, attachments.size, maxAttachments)) {
            "An item can have at most $maxAttachments attachments."
        }
        val existingBytes = attachmentBytesTowardLimit(getAllAttachmentSizeBytes(), maxTotalBytes)
        val incomingBytes = sumAttachmentBytesOrNull(attachments.map(AttachmentEntity::sizeBytes))
        require(
            incomingBytes != null &&
                isWithinAttachmentByteLimit(existingBytes, incomingBytes, maxTotalBytes),
        ) {
            "Attachments can use up to 128 MB in total. Remove one or more files and try again."
        }
        return attachments.map { insertAttachment(it) }
    }

    @Update
    suspend fun updateAttachment(attachment: AttachmentEntity)

    @Query("UPDATE attachments SET pendingDeleteAt = :pendingDeleteAt WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun markOwnerAttachmentsForDeletion(ownerType: String, ownerId: Long, pendingDeleteAt: Long)

    @Query("UPDATE attachments SET pendingDeleteAt = :pendingDeleteAt WHERE ownerType = :ownerType AND ownerId = :ownerId AND pendingDeleteAt IS NULL")
    suspend fun markActiveOwnerAttachmentsForDeletion(
        ownerType: String,
        ownerId: Long,
        pendingDeleteAt: Long,
    )

    @Query("UPDATE attachments SET pendingDeleteAt = NULL WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun undoOwnerAttachmentDeletion(ownerType: String, ownerId: Long)

    @Query("UPDATE attachments SET pendingDeleteAt = NULL WHERE ownerType = :ownerType AND ownerId = :ownerId AND pendingDeleteAt = :expectedPendingDeleteAt")
    suspend fun undoOwnerAttachmentsMarkedAt(
        ownerType: String,
        ownerId: Long,
        expectedPendingDeleteAt: Long,
    )

    @Query("UPDATE attachments SET pendingDeleteAt = :pendingDeleteAt WHERE id = :attachmentId AND pendingDeleteAt IS NULL")
    suspend fun markAttachmentForDeletion(attachmentId: Long, pendingDeleteAt: Long): Int

    @Query("UPDATE attachments SET pendingDeleteAt = NULL WHERE id = :attachmentId AND pendingDeleteAt = :expectedPendingDeleteAt")
    suspend fun undoAttachmentDeletion(attachmentId: Long, expectedPendingDeleteAt: Long): Int

    @Query("SELECT * FROM attachments WHERE pendingDeleteAt IS NOT NULL AND pendingDeleteAt <= :before")
    suspend fun getAttachmentsReadyForDeletion(before: Long): List<AttachmentEntity>

    @Query(
        """
        SELECT attachments.*
        FROM attachments
        LEFT JOIN todos
            ON attachments.ownerType = 'TODO' AND attachments.ownerId = todos.id
        LEFT JOIN ledger_entries
            ON attachments.ownerType = 'LEDGER' AND attachments.ownerId = ledger_entries.id
        WHERE (attachments.ownerType = 'TODO' AND todos.id IS NULL)
           OR (attachments.ownerType = 'LEDGER' AND ledger_entries.id IS NULL)
        """,
    )
    suspend fun getOrphanedAttachments(): List<AttachmentEntity>

    @Query("SELECT privatePath FROM attachments")
    suspend fun getAllAttachmentPrivatePaths(): List<String>

    @Delete
    suspend fun deleteAttachment(attachment: AttachmentEntity)
}

internal fun isWithinAttachmentLimit(
    existingCount: Int,
    incomingCount: Int,
    maxAttachments: Int,
): Boolean {
    require(existingCount >= 0) { "Existing attachment count cannot be negative." }
    require(incomingCount >= 0) { "Incoming attachment count cannot be negative." }
    require(maxAttachments >= 0) { "Attachment limit cannot be negative." }
    return existingCount.toLong() + incomingCount.toLong() <= maxAttachments.toLong()
}

internal fun isWithinAttachmentByteLimit(
    existingBytes: Long,
    incomingBytes: Long,
    maxBytes: Long,
): Boolean {
    require(existingBytes >= 0) { "Existing attachment bytes cannot be negative." }
    require(incomingBytes >= 0) { "Incoming attachment bytes cannot be negative." }
    require(maxBytes >= 0) { "Attachment byte limit cannot be negative." }
    if (existingBytes >= maxBytes) return false
    return incomingBytes <= maxBytes - existingBytes
}

/** Sums untrusted sizes without wrapping; null means a negative value or overflow was found. */
internal fun sumAttachmentBytesOrNull(sizeBytes: Iterable<Long>): Long? {
    var total = 0L
    for (size in sizeBytes) {
        if (size < 0 || size > Long.MAX_VALUE - total) return null
        total += size
    }
    return total
}

/**
 * Returns bytes consumed toward a limit without ever overflowing. Values at/over the limit are
 * deliberately saturated so legacy over-limit data is preserved while every addition is blocked.
 */
internal fun attachmentBytesTowardLimit(sizeBytes: Iterable<Long>, maxBytes: Long): Long {
    require(maxBytes >= 0) { "Attachment byte limit cannot be negative." }
    var total = 0L
    for (size in sizeBytes) {
        if (size < 0 || total >= maxBytes || size >= maxBytes - total) return maxBytes
        total += size
    }
    return total
}
