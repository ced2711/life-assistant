package com.ced2711.lifetracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.TodoPriority

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("parentId"), Index(value = ["parentId", "name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val sortOrder: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "todo_series",
    indices = [Index("categoryId")],
)
data class TodoSeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val categoryId: Long? = null,
    val startEpochDay: Long,
    val startMinute: Int? = null,
    val recurrenceUnit: RecurrenceUnit,
    val intervalCount: Int = 1,
    val endEpochDay: Long? = null,
    val priority: TodoPriority = TodoPriority.NONE,
    val tagsCsv: String = "",
    val reminderOffsetsCsv: String = "0",
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "todo_series_subtasks",
    primaryKeys = ["seriesId", "sortOrder"],
    foreignKeys = [
        ForeignKey(
            entity = TodoSeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TodoSeriesSubtaskEntity(
    val seriesId: Long,
    val sortOrder: Int,
    val description: String,
)

/**
 * A permanent exclusion for a generated occurrence. The occurrence row itself may be hard-purged
 * after the Undo window, but this key remains so catch-up cannot recreate a user-deleted task.
 */
@Entity(
    tableName = "todo_occurrence_exceptions",
    primaryKeys = ["seriesId", "occurrenceEpochDay"],
    foreignKeys = [
        ForeignKey(
            entity = TodoSeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TodoOccurrenceExceptionEntity(
    val seriesId: Long,
    val occurrenceEpochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "todos",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = TodoSeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("categoryId"),
        Index("deadlineEpochDay"),
        Index("completedAt"),
        Index("deletedAt"),
        Index(value = ["clientOperationToken"], unique = true),
        Index(value = ["seriesId", "occurrenceEpochDay"], unique = true),
    ],
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: Long? = null,
    val occurrenceEpochDay: Long? = null,
    val title: String,
    val description: String,
    val categoryId: Long? = null,
    val deadlineEpochDay: Long? = null,
    val deadlineMinute: Int? = null,
    val priority: TodoPriority = TodoPriority.NONE,
    val tagsCsv: String = "",
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val customOrder: Long = createdAt,
    val deletedAt: Long? = null,
    val clientOperationToken: String? = null,
)

@Entity(
    tableName = "subtasks",
    foreignKeys = [
        ForeignKey(
            entity = TodoEntity::class,
            parentColumns = ["id"],
            childColumns = ["todoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("todoId")],
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val todoId: Long,
    val description: String,
    val isCompleted: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "todo_reminders",
    foreignKeys = [
        ForeignKey(
            entity = TodoEntity::class,
            parentColumns = ["id"],
            childColumns = ["todoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("todoId"), Index(value = ["todoId", "offsetMinutes"], unique = true)],
)
data class TodoReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val todoId: Long,
    val offsetMinutes: Long,
)

@Entity(
    tableName = "ledger_series",
    indices = [Index(value = ["clientOperationToken"], unique = true)],
)
data class LedgerSeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: LedgerType,
    val amountCents: Long,
    val startEpochDay: Long,
    val recurrenceUnit: RecurrenceUnit,
    val intervalCount: Int = 1,
    val endEpochDay: Long? = null,
    val note: String = "",
    val merchant: String = "",
    val tagsCsv: String = "",
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val clientOperationToken: String? = null,
)

/** Ledger counterpart of [TodoOccurrenceExceptionEntity]. */
@Entity(
    tableName = "ledger_occurrence_exceptions",
    primaryKeys = ["seriesId", "occurrenceEpochDay"],
    foreignKeys = [
        ForeignKey(
            entity = LedgerSeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LedgerOccurrenceExceptionEntity(
    val seriesId: Long,
    val occurrenceEpochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "ledger_entries",
    foreignKeys = [
        ForeignKey(
            entity = LedgerSeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("epochDay"),
        Index("deletedAt"),
        Index(value = ["clientOperationToken"], unique = true),
        Index(value = ["seriesId", "occurrenceEpochDay"], unique = true),
    ],
)
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: Long? = null,
    val occurrenceEpochDay: Long? = null,
    val type: LedgerType,
    val amountCents: Long,
    val epochDay: Long,
    val minuteOfDay: Int,
    val note: String = "",
    val merchant: String = "",
    val tagsCsv: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val clientOperationToken: String? = null,
)

@Entity(
    tableName = "attachments",
    indices = [Index(value = ["ownerType", "ownerId"]), Index("pendingDeleteAt")],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerType: AttachmentOwnerType,
    val ownerId: Long,
    val privatePath: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val pendingDeleteAt: Long? = null,
)

data class DailyLedgerTotal(
    val epochDay: Long,
    val incomeCents: Long,
    val expenseCents: Long,
) {
    val netCents: Long get() = incomeCents - expenseCents
}
