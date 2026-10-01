package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.LedgerOccurrenceExceptionEntity
import com.ced2711.lifetracker.data.local.LedgerSeriesEntity
import com.ced2711.lifetracker.data.local.SubtaskEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.local.TodoOccurrenceExceptionEntity
import com.ced2711.lifetracker.data.local.TodoReminderEntity
import com.ced2711.lifetracker.data.local.TodoSeriesEntity
import com.ced2711.lifetracker.data.local.TodoSeriesSubtaskEntity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import com.ced2711.lifetracker.domain.model.TodoDraft
import com.ced2711.lifetracker.domain.model.deriveTodoTitle
import com.ced2711.lifetracker.domain.model.normalizeTags
import com.ced2711.lifetracker.domain.recurrence.RecurrenceEngine

/** What a todo deletion changed, so Undo can put exactly that back. */
data class TodoDeletion(
    val todoIds: List<Long>,
    val deletedAt: Long,
    val seriesId: Long?,
    val boundaryEpochDay: Long?,
    val seriesWasActive: Boolean,
    val exceptionCreated: Boolean,
)

/**
 * Todo and recurrence rules on a whole snapshot. They follow the Android repository, so a change
 * made on either device means the same thing on the other: deleted occurrences leave an
 * exception (and never come back), edits of one occurrence keep its slot in the series, and
 * "this and future" stops the old series and starts a new one.
 */
internal object DesktopTodoOps {
    const val UNDO_WINDOW_MILLIS = 6_000L
    private const val MAX_REMINDER_LOOKAHEAD_DAYS = 366L
    private const val MAX_GENERATED_PER_PASS = 2_000
    private const val GENERATED_LEDGER_MINUTE = 0

    fun save(
        snapshot: BackupSnapshot,
        draft: TodoDraft,
        scope: SeriesEditScope,
        completed: Boolean?,
        now: Long,
    ): Pair<BackupSnapshot, Long> {
        require(draft.description.isNotBlank()) { "Description is required" }
        require(draft.deadlineMinute == null || draft.deadlineEpochDay != null) { "A time requires a date" }
        require(draft.deadlineMinute == null || draft.deadlineMinute in 0..1_439) { "Invalid time" }
        require(draft.recurrence == null || draft.deadlineEpochDay != null) { "Repeating todos require a deadline" }
        require(draft.recurrence == null || draft.recurrence.interval > 0) { "Recurrence interval must be positive" }
        require(draft.reminderOffsetsMinutes.all { it >= 0 }) { "Reminder offsets must not be negative" }
        require(draft.recurrence?.endEpochDay == null || draft.recurrence.endEpochDay >= draft.deadlineEpochDay!!) {
            "Repeat end date cannot be before its first occurrence"
        }
        var result = snapshot
        val title = draft.title.trim().ifEmpty { deriveTodoTitle(draft.description) }
        val tagsCsv = normalizeTags(draft.tags)
        val subtaskDescriptions = draft.subtasks.map(String::trim).filter(String::isNotEmpty)
        val existing = draft.id?.let { id -> snapshot.todos.firstOrNull { it.id == id } }
        val oldSeriesId = existing?.seriesId
        val oldOccurrence = existing?.occurrenceEpochDay

        fun insertSeries(): Long {
            val recurrence = requireNotNull(draft.recurrence)
            val id = (result.todoSeries.maxOfOrNull(TodoSeriesEntity::id) ?: 0L) + 1
            result = result.copy(
                todoSeries = result.todoSeries + TodoSeriesEntity(
                    id = id,
                    title = title,
                    description = draft.description.trim(),
                    categoryId = draft.categoryId,
                    startEpochDay = requireNotNull(draft.deadlineEpochDay),
                    startMinute = draft.deadlineMinute,
                    recurrenceUnit = recurrence.unit,
                    intervalCount = recurrence.interval,
                    endEpochDay = recurrence.endEpochDay,
                    priority = draft.priority,
                    tagsCsv = tagsCsv,
                    reminderOffsetsCsv = draft.reminderOffsetsMinutes.distinct().sorted().joinToString(","),
                    createdAt = now,
                    updatedAt = now,
                ),
                todoSeriesSubtasks = result.todoSeriesSubtasks + subtaskDescriptions.mapIndexed { index, description ->
                    TodoSeriesSubtaskEntity(id, index, description)
                },
            )
            return id
        }

        val seriesId: Long?
        val occurrenceEpochDay: Long?
        when {
            oldSeriesId != null && scope == SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES -> {
                val boundary = requireNotNull(oldOccurrence) { "A repeating task must retain its occurrence identity" }
                if (draft.recurrence != null) {
                    require(requireNotNull(draft.deadlineEpochDay) >= boundary) {
                        "The replacement series cannot start before the edited occurrence"
                    }
                }
                result = deactivateTodoSeries(result, oldSeriesId, now)
                result = softDeleteTodos(
                    result,
                    result.todos.filter { it.seriesId == oldSeriesId && (it.occurrenceEpochDay ?: Long.MIN_VALUE) > boundary && it.deletedAt == null }
                        .map(TodoEntity::id),
                    now,
                )
                seriesId = draft.recurrence?.let { insertSeries() }
                occurrenceEpochDay = seriesId?.let { draft.deadlineEpochDay }
            }
            oldSeriesId != null && draft.recurrence == null -> {
                val occurrence = requireNotNull(oldOccurrence) { "A repeating task must retain its occurrence identity" }
                result = addTodoException(result, oldSeriesId, occurrence, now)
                seriesId = null
                occurrenceEpochDay = null
            }
            oldSeriesId != null -> {
                // An occurrence key identifies the slot in its series, not its user-editable date.
                seriesId = oldSeriesId
                occurrenceEpochDay = oldOccurrence
            }
            draft.recurrence != null -> {
                seriesId = insertSeries()
                occurrenceEpochDay = draft.deadlineEpochDay
            }
            else -> {
                seriesId = null
                occurrenceEpochDay = null
            }
        }

        val todoId = existing?.id ?: ((result.todos.maxOfOrNull(TodoEntity::id) ?: 0L) + 1)
        val entity = TodoEntity(
            id = todoId,
            seriesId = seriesId,
            occurrenceEpochDay = occurrenceEpochDay,
            title = title,
            description = draft.description.trim(),
            categoryId = draft.categoryId,
            deadlineEpochDay = draft.deadlineEpochDay,
            deadlineMinute = draft.deadlineMinute,
            priority = draft.priority,
            tagsCsv = tagsCsv,
            completedAt = when (completed) {
                null -> existing?.completedAt
                true -> existing?.completedAt ?: now
                false -> null
            },
            createdAt = existing?.createdAt ?: now,
            updatedAt = maxOf(now, existing?.updatedAt ?: 0L),
            customOrder = existing?.customOrder ?: now,
            deletedAt = existing?.deletedAt,
            clientOperationToken = existing?.clientOperationToken,
        )
        val existingSubtasks = result.subtasks.filter { it.todoId == todoId }.sortedBy(SubtaskEntity::sortOrder)
        var nextSubtaskId = (result.subtasks.maxOfOrNull(SubtaskEntity::id) ?: 0L) + 1
        val subtasks = mergeSubtasks(subtaskDescriptions, existingSubtasks).mapIndexed { index, (description, done) ->
            SubtaskEntity(id = nextSubtaskId++, todoId = todoId, description = description, isCompleted = done, sortOrder = index)
        }
        var nextReminderId = (result.todoReminders.maxOfOrNull(TodoReminderEntity::id) ?: 0L) + 1
        val reminders = if (draft.deadlineEpochDay == null) emptyList() else {
            draft.reminderOffsetsMinutes.distinct().map { TodoReminderEntity(id = nextReminderId++, todoId = todoId, offsetMinutes = it) }
        }
        result = result.copy(
            todos = result.todos.filterNot { it.id == todoId } + entity,
            subtasks = result.subtasks.filterNot { it.todoId == todoId } + subtasks,
            todoReminders = result.todoReminders.filterNot { it.todoId == todoId } + reminders,
        )
        return result to todoId
    }

    /** Keeps the completion state of subtasks that still exist (by description, then by position). */
    private fun mergeSubtasks(descriptions: List<String>, existing: List<SubtaskEntity>): List<Pair<String, Boolean>> {
        val consumed = BooleanArray(existing.size)
        val preserved = arrayOfNulls<Int>(descriptions.size)
        descriptions.forEachIndexed { newIndex, description ->
            val oldIndex = existing.indices.firstOrNull { !consumed[it] && existing[it].description.equals(description, ignoreCase = true) }
            if (oldIndex != null) {
                consumed[oldIndex] = true
                preserved[newIndex] = oldIndex
            }
        }
        if (descriptions.size == existing.size) {
            descriptions.indices.forEach { index ->
                if (preserved[index] == null && !consumed[index]) {
                    consumed[index] = true
                    preserved[index] = index
                }
            }
        }
        return descriptions.mapIndexed { index, description -> description to (preserved[index]?.let { existing[it].isCompleted } ?: false) }
    }

    fun setCompleted(snapshot: BackupSnapshot, todoId: Long, completed: Boolean, completeSubtasks: Boolean, now: Long): BackupSnapshot =
        snapshot.copy(
            todos = snapshot.todos.map { todo ->
                if (todo.id != todoId) todo else todo.copy(
                    completedAt = if (completed) maxOf(now, todo.createdAt) else null,
                    updatedAt = maxOf(now, todo.updatedAt),
                )
            },
            subtasks = if (completed && completeSubtasks) {
                snapshot.subtasks.map { if (it.todoId == todoId) it.copy(isCompleted = true) else it }
            } else {
                snapshot.subtasks
            },
        )

    fun setSubtaskCompleted(snapshot: BackupSnapshot, subtaskId: Long, completed: Boolean, now: Long): BackupSnapshot {
        val subtask = snapshot.subtasks.firstOrNull { it.id == subtaskId } ?: return snapshot
        return snapshot.copy(
            subtasks = snapshot.subtasks.map { if (it.id == subtaskId) it.copy(isCompleted = completed) else it },
            // Touch the owner so a merge sees which side changed the checklist last.
            todos = snapshot.todos.map { if (it.id == subtask.todoId) it.copy(updatedAt = maxOf(now, it.updatedAt)) else it },
        )
    }

    fun delete(snapshot: BackupSnapshot, todoId: Long, scope: SeriesEditScope, now: Long): Pair<BackupSnapshot, TodoDeletion> {
        val todo = requireNotNull(snapshot.todos.firstOrNull { it.id == todoId }) { "Task does not exist" }
        check(todo.deletedAt == null) { "Task is already deleted" }
        val seriesId = todo.seriesId
        val boundary = todo.occurrenceEpochDay
        val series = seriesId?.let { id -> snapshot.todoSeries.firstOrNull { it.id == id } }
        val tail = scope == SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES && seriesId != null && boundary != null
        val affected = if (tail) {
            snapshot.todos.filter { it.seriesId == seriesId && (it.occurrenceEpochDay ?: Long.MIN_VALUE) >= boundary!! && it.deletedAt == null }.map(TodoEntity::id)
        } else {
            listOf(todoId)
        }
        val deletedAt = maxOf(now, snapshot.todos.filter { it.id in affected }.maxOf(TodoEntity::updatedAt))
        var result = snapshot
        val exceptionCreated = seriesId != null && boundary != null &&
            result.todoOccurrenceExceptions.none { it.seriesId == seriesId && it.occurrenceEpochDay == boundary }
        if (seriesId != null && boundary != null) result = addTodoException(result, seriesId, boundary, deletedAt)
        if (tail) result = deactivateTodoSeries(result, seriesId!!, deletedAt)
        result = softDeleteTodos(result, affected, deletedAt)
        return result to TodoDeletion(affected, deletedAt, seriesId, boundary, series?.active ?: false, exceptionCreated)
    }

    fun undoDelete(snapshot: BackupSnapshot, deletion: TodoDeletion, now: Long): BackupSnapshot {
        val restoreAt = maxOf(now, deletion.deletedAt)
        var result = snapshot.copy(
            todos = snapshot.todos.map { todo ->
                if (todo.id in deletion.todoIds && todo.deletedAt == deletion.deletedAt) todo.copy(deletedAt = null, updatedAt = restoreAt) else todo
            },
            attachments = snapshot.attachments.map { attachment ->
                if (attachment.ownerType == AttachmentOwnerType.TODO && attachment.ownerId in deletion.todoIds &&
                    attachment.pendingDeleteAt == deletion.deletedAt + UNDO_WINDOW_MILLIS
                ) attachment.copy(pendingDeleteAt = null) else attachment
            },
        )
        if (deletion.seriesId != null && deletion.boundaryEpochDay != null && deletion.exceptionCreated) {
            result = result.copy(
                todoOccurrenceExceptions = result.todoOccurrenceExceptions.filterNot {
                    it.seriesId == deletion.seriesId && it.occurrenceEpochDay == deletion.boundaryEpochDay
                },
            )
        }
        if (deletion.seriesId != null && deletion.seriesWasActive && deletion.todoIds.size > 1) {
            result = result.copy(
                todoSeries = result.todoSeries.map { if (it.id == deletion.seriesId) it.copy(active = true, updatedAt = maxOf(restoreAt, it.updatedAt)) else it },
            )
        }
        return result
    }

    /** Removes todos and ledger entries whose undo window has passed, with everything they own. */
    fun purgeDeleted(snapshot: BackupSnapshot, before: Long): BackupSnapshot {
        val goneTodos = snapshot.todos.filter { it.deletedAt != null && it.deletedAt < before }.mapTo(HashSet(), TodoEntity::id)
        val goneLedger = snapshot.ledgerEntries.filter { it.deletedAt != null && it.deletedAt < before }.mapTo(HashSet(), LedgerEntryEntity::id)
        if (goneTodos.isEmpty() && goneLedger.isEmpty() && snapshot.attachments.none { (it.pendingDeleteAt ?: Long.MAX_VALUE) < before }) return snapshot
        return snapshot.copy(
            todos = snapshot.todos.filterNot { it.id in goneTodos },
            subtasks = snapshot.subtasks.filterNot { it.todoId in goneTodos },
            todoReminders = snapshot.todoReminders.filterNot { it.todoId in goneTodos },
            ledgerEntries = snapshot.ledgerEntries.filterNot { it.id in goneLedger },
            attachments = snapshot.attachments.filterNot { attachment ->
                (attachment.pendingDeleteAt ?: Long.MAX_VALUE) < before ||
                    (attachment.ownerType == AttachmentOwnerType.TODO && attachment.ownerId in goneTodos) ||
                    (attachment.ownerType == AttachmentOwnerType.LEDGER && attachment.ownerId in goneLedger)
            },
        )
    }

    /** Stops a todo series; its existing occurrences stay. */
    fun stopTodoSeries(snapshot: BackupSnapshot, seriesId: Long, now: Long): BackupSnapshot = deactivateTodoSeries(snapshot, seriesId, now)

    private fun deactivateTodoSeries(snapshot: BackupSnapshot, seriesId: Long, now: Long) = snapshot.copy(
        todoSeries = snapshot.todoSeries.map { if (it.id == seriesId && it.active) it.copy(active = false, updatedAt = maxOf(now, it.updatedAt)) else it },
    )

    private fun addTodoException(snapshot: BackupSnapshot, seriesId: Long, day: Long, now: Long): BackupSnapshot =
        if (snapshot.todoOccurrenceExceptions.any { it.seriesId == seriesId && it.occurrenceEpochDay == day }) snapshot
        else snapshot.copy(todoOccurrenceExceptions = snapshot.todoOccurrenceExceptions + TodoOccurrenceExceptionEntity(seriesId, day, now))

    private fun softDeleteTodos(snapshot: BackupSnapshot, ids: List<Long>, deletedAt: Long): BackupSnapshot {
        if (ids.isEmpty()) return snapshot
        val set = ids.toHashSet()
        return snapshot.copy(
            todos = snapshot.todos.map { if (it.id in set) it.copy(deletedAt = deletedAt, updatedAt = maxOf(deletedAt, it.updatedAt)) else it },
            todoReminders = snapshot.todoReminders.filterNot { it.todoId in set },
            attachments = snapshot.attachments.map { attachment ->
                if (attachment.ownerType == AttachmentOwnerType.TODO && attachment.ownerId in set && attachment.pendingDeleteAt == null) {
                    attachment.copy(pendingDeleteAt = maxOf(deletedAt + UNDO_WINDOW_MILLIS, attachment.createdAt))
                } else attachment
            },
        )
    }

    /**
     * Creates the todo and ledger occurrences that are due, like the phone does: todos through
     * [throughEpochDay] (plus however far ahead their reminders reach), ledger entries through
     * [throughEpochDay]. Days with an exception, and days already created, are skipped.
     */
    fun materialize(snapshot: BackupSnapshot, throughEpochDay: Long, now: Long, todoThroughEpochDay: Long = throughEpochDay): BackupSnapshot {
        var result = snapshot
        var budget = MAX_GENERATED_PER_PASS
        result.todoSeries.filter(TodoSeriesEntity::active).sortedBy(TodoSeriesEntity::id).forEach { series ->
            if (budget <= 0) return@forEach
            val offsets = series.reminderOffsetsCsv.split(',').mapNotNull { it.trim().toLongOrNull() }.filter { it >= 0 }.distinct()
            val horizon = RecurrenceEngine.reminderAwareGenerationHorizon(maxOf(throughEpochDay, todoThroughEpochDay), offsets, MAX_REMINDER_LOOKAHEAD_DAYS)
            val rows = result.todos.filter { it.seriesId == series.id }
            val exceptions = result.todoOccurrenceExceptions.filter { it.seriesId == series.id }.mapTo(HashSet()) { it.occurrenceEpochDay }
            val watermark = watermark(rows.mapNotNull(TodoEntity::occurrenceEpochDay).maxOrNull(), exceptions, series.startEpochDay)
            val page = RecurrenceEngine.generateOccurrencesAfter(
                startEpochDay = series.startEpochDay,
                rule = RecurrenceRule(series.recurrenceUnit, series.intervalCount, series.endEpochDay),
                afterEpochDayExclusive = watermark,
                throughEpochDay = horizon,
                limit = budget,
            )
            budget -= page.epochDays.size
            val templates = result.todoSeriesSubtasks.filter { it.seriesId == series.id }.sortedBy(TodoSeriesSubtaskEntity::sortOrder)
            val existingDays = rows.mapNotNullTo(HashSet(), TodoEntity::occurrenceEpochDay)
            var nextTodoId = (result.todos.maxOfOrNull(TodoEntity::id) ?: 0L) + 1
            var nextSubtaskId = (result.subtasks.maxOfOrNull(SubtaskEntity::id) ?: 0L) + 1
            var nextReminderId = (result.todoReminders.maxOfOrNull(TodoReminderEntity::id) ?: 0L) + 1
            val newTodos = ArrayList<TodoEntity>()
            val newSubtasks = ArrayList<SubtaskEntity>()
            val newReminders = ArrayList<TodoReminderEntity>()
            page.epochDays.filterNot { it in exceptions || it in existingDays }.forEach { day ->
                val id = nextTodoId++
                newTodos += TodoEntity(
                    id = id, seriesId = series.id, occurrenceEpochDay = day, title = series.title, description = series.description,
                    categoryId = series.categoryId, deadlineEpochDay = day, deadlineMinute = series.startMinute, priority = series.priority,
                    tagsCsv = series.tagsCsv, createdAt = now, updatedAt = now, customOrder = now,
                )
                templates.forEach { newSubtasks += SubtaskEntity(id = nextSubtaskId++, todoId = id, description = it.description, sortOrder = it.sortOrder) }
                offsets.forEach { newReminders += TodoReminderEntity(id = nextReminderId++, todoId = id, offsetMinutes = it) }
            }
            result = result.copy(todos = result.todos + newTodos, subtasks = result.subtasks + newSubtasks, todoReminders = result.todoReminders + newReminders)
            if (!page.hasMore && series.endEpochDay != null && series.endEpochDay <= throughEpochDay) {
                result = deactivateTodoSeries(result, series.id, now)
            }
        }
        result.ledgerSeries.filter(LedgerSeriesEntity::active).sortedBy(LedgerSeriesEntity::id).forEach { series ->
            if (budget <= 0) return@forEach
            val rows = result.ledgerEntries.filter { it.seriesId == series.id }
            val exceptions = result.ledgerOccurrenceExceptions.filter { it.seriesId == series.id }.mapTo(HashSet()) { it.occurrenceEpochDay }
            val watermark = watermark(rows.mapNotNull(LedgerEntryEntity::occurrenceEpochDay).maxOrNull(), exceptions, series.startEpochDay)
            val page = RecurrenceEngine.generateOccurrencesAfter(
                startEpochDay = series.startEpochDay,
                rule = RecurrenceRule(series.recurrenceUnit, series.intervalCount, series.endEpochDay),
                afterEpochDayExclusive = watermark,
                throughEpochDay = throughEpochDay,
                limit = budget,
            )
            budget -= page.epochDays.size
            val existingDays = rows.mapNotNullTo(HashSet(), LedgerEntryEntity::occurrenceEpochDay)
            var nextId = (result.ledgerEntries.maxOfOrNull(LedgerEntryEntity::id) ?: 0L) + 1
            val created = page.epochDays.filterNot { it in exceptions || it in existingDays }.map { day ->
                LedgerEntryEntity(
                    id = nextId++, seriesId = series.id, occurrenceEpochDay = day, type = series.type, amountCents = series.amountCents,
                    epochDay = day, minuteOfDay = GENERATED_LEDGER_MINUTE, note = series.note, merchant = series.merchant,
                    tagsCsv = series.tagsCsv, createdAt = now, updatedAt = now,
                )
            }
            result = result.copy(ledgerEntries = result.ledgerEntries + created)
            if (!page.hasMore && series.endEpochDay != null && series.endEpochDay <= throughEpochDay) {
                result = result.copy(
                    ledgerSeries = result.ledgerSeries.map { if (it.id == series.id) it.copy(active = false, updatedAt = maxOf(now, it.updatedAt)) else it },
                )
            }
        }
        return result
    }

    /** Generation resumes after the last created or excluded day, as on Android. */
    private fun watermark(rowMax: Long?, exceptions: Set<Long>, startEpochDay: Long): Long? = when {
        rowMax != null -> maxOf(rowMax, exceptions.maxOrNull() ?: rowMax)
        exceptions.isNotEmpty() && startEpochDay in exceptions -> exceptions.max()
        else -> null
    }

    fun addLedgerException(snapshot: BackupSnapshot, seriesId: Long, day: Long, now: Long): BackupSnapshot =
        if (snapshot.ledgerOccurrenceExceptions.any { it.seriesId == seriesId && it.occurrenceEpochDay == day }) snapshot
        else snapshot.copy(ledgerOccurrenceExceptions = snapshot.ledgerOccurrenceExceptions + LedgerOccurrenceExceptionEntity(seriesId, day, now))
}
