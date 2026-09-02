package com.ced2711.lifetracker.data.backup

import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.data.local.BackupDatabaseState
import com.ced2711.lifetracker.data.local.VaultEntryEntity
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Canonical SHA-256 fingerprints used to prove that pre-commit capture state stayed stable. */
internal fun fullDatabaseFingerprint(state: BackupDatabaseState): String =
    fingerprint(rows(state)).alsoWith { writer ->
        state.vaultEntries.sortedBy(VaultEntryEntity::id).forEach { row ->
            writer.string(row.id)
            writer.int(row.formatVersion)
            writer.bytes(row.payloadIv)
            writer.bytes(row.payloadCiphertext)
            writer.long(row.createdAt)
            writer.long(row.updatedAt)
        }
    }

internal fun semanticDatabaseFingerprint(state: BackupDatabaseState): String =
    fingerprint(rows(state))

internal fun expectedDatabaseFingerprint(
    snapshot: BackupSnapshot,
    attachments: List<AttachmentEntity>,
): String = fingerprint(
    FingerprintRows(
        categories = snapshot.categories,
        todoSeries = snapshot.todoSeries,
        todoSeriesSubtasks = snapshot.todoSeriesSubtasks,
        todoExceptions = snapshot.todoOccurrenceExceptions,
        todos = snapshot.todos,
        subtasks = snapshot.subtasks,
        reminders = snapshot.todoReminders,
        ledgerSeries = snapshot.ledgerSeries,
        ledgerExceptions = snapshot.ledgerOccurrenceExceptions,
        ledgerEntries = snapshot.ledgerEntries,
        attachments = attachments,
        vaultMarkers = snapshot.vaultEntries.map { VaultMarker(it.id, it.createdAt, it.updatedAt) },
    ),
)

internal fun expectedFullDatabaseFingerprint(
    snapshot: BackupSnapshot,
    attachments: List<AttachmentEntity>,
    encryptedVault: List<VaultEntryEntity>,
): String = fullDatabaseFingerprint(
    BackupDatabaseState(
        categories = snapshot.categories,
        todoSeries = snapshot.todoSeries,
        todoSeriesSubtasks = snapshot.todoSeriesSubtasks,
        todoOccurrenceExceptions = snapshot.todoOccurrenceExceptions,
        todos = snapshot.todos,
        subtasks = snapshot.subtasks,
        todoReminders = snapshot.todoReminders,
        ledgerSeries = snapshot.ledgerSeries,
        ledgerOccurrenceExceptions = snapshot.ledgerOccurrenceExceptions,
        ledgerEntries = snapshot.ledgerEntries,
        attachments = attachments,
        vaultEntries = encryptedVault,
    ),
)

private fun rows(state: BackupDatabaseState) = FingerprintRows(
    categories = state.categories,
    todoSeries = state.todoSeries,
    todoSeriesSubtasks = state.todoSeriesSubtasks,
    todoExceptions = state.todoOccurrenceExceptions,
    todos = state.todos,
    subtasks = state.subtasks,
    reminders = state.todoReminders,
    ledgerSeries = state.ledgerSeries,
    ledgerExceptions = state.ledgerOccurrenceExceptions,
    ledgerEntries = state.ledgerEntries,
    attachments = state.attachments,
    vaultMarkers = state.vaultEntries.map { VaultMarker(it.id, it.createdAt, it.updatedAt) },
)

private data class VaultMarker(val id: String, val createdAt: Long, val updatedAt: Long)

private data class FingerprintRows(
    val categories: List<com.ced2711.lifetracker.data.local.CategoryEntity>,
    val todoSeries: List<com.ced2711.lifetracker.data.local.TodoSeriesEntity>,
    val todoSeriesSubtasks: List<com.ced2711.lifetracker.data.local.TodoSeriesSubtaskEntity>,
    val todoExceptions: List<com.ced2711.lifetracker.data.local.TodoOccurrenceExceptionEntity>,
    val todos: List<com.ced2711.lifetracker.data.local.TodoEntity>,
    val subtasks: List<com.ced2711.lifetracker.data.local.SubtaskEntity>,
    val reminders: List<com.ced2711.lifetracker.data.local.TodoReminderEntity>,
    val ledgerSeries: List<com.ced2711.lifetracker.data.local.LedgerSeriesEntity>,
    val ledgerExceptions: List<com.ced2711.lifetracker.data.local.LedgerOccurrenceExceptionEntity>,
    val ledgerEntries: List<com.ced2711.lifetracker.data.local.LedgerEntryEntity>,
    val attachments: List<AttachmentEntity>,
    val vaultMarkers: List<VaultMarker>,
)

private fun fingerprint(rows: FingerprintRows): String {
    val writer = FingerprintWriter()
    writer.string("TaskLedger database semantic fingerprint v1")
    rows.categories.sortedBy { it.id }.forEach {
        writer.tag("category"); writer.long(it.id); writer.string(it.name); writer.longOrNull(it.parentId)
        writer.long(it.sortOrder); writer.long(it.createdAt)
    }
    rows.todoSeries.sortedBy { it.id }.forEach {
        writer.tag("todoSeries"); writer.long(it.id); writer.string(it.title); writer.string(it.description)
        writer.longOrNull(it.categoryId); writer.long(it.startEpochDay); writer.intOrNull(it.startMinute)
        writer.string(it.recurrenceUnit.name); writer.int(it.intervalCount); writer.longOrNull(it.endEpochDay)
        writer.string(it.priority.name); writer.string(it.tagsCsv); writer.string(it.reminderOffsetsCsv)
        writer.bool(it.active); writer.long(it.createdAt); writer.long(it.updatedAt)
    }
    rows.todoSeriesSubtasks.sortedWith(compareBy({ it.seriesId }, { it.sortOrder })).forEach {
        writer.tag("todoSeriesSubtask"); writer.long(it.seriesId); writer.int(it.sortOrder); writer.string(it.description)
    }
    rows.todoExceptions.sortedWith(compareBy({ it.seriesId }, { it.occurrenceEpochDay })).forEach {
        writer.tag("todoException"); writer.long(it.seriesId); writer.long(it.occurrenceEpochDay); writer.long(it.createdAt)
    }
    rows.todos.sortedBy { it.id }.forEach {
        writer.tag("todo"); writer.long(it.id); writer.longOrNull(it.seriesId); writer.longOrNull(it.occurrenceEpochDay)
        writer.string(it.title); writer.string(it.description); writer.longOrNull(it.categoryId)
        writer.longOrNull(it.deadlineEpochDay); writer.intOrNull(it.deadlineMinute); writer.string(it.priority.name)
        writer.string(it.tagsCsv); writer.longOrNull(it.completedAt); writer.long(it.createdAt); writer.long(it.updatedAt)
        writer.long(it.customOrder); writer.longOrNull(it.deletedAt); writer.stringOrNull(it.clientOperationToken)
    }
    rows.subtasks.sortedBy { it.id }.forEach {
        writer.tag("subtask"); writer.long(it.id); writer.long(it.todoId); writer.string(it.description)
        writer.bool(it.isCompleted); writer.int(it.sortOrder)
    }
    rows.reminders.sortedBy { it.id }.forEach {
        writer.tag("reminder"); writer.long(it.id); writer.long(it.todoId); writer.long(it.offsetMinutes)
    }
    rows.ledgerSeries.sortedBy { it.id }.forEach {
        writer.tag("ledgerSeries"); writer.long(it.id); writer.string(it.type.name); writer.long(it.amountCents)
        writer.long(it.startEpochDay); writer.string(it.recurrenceUnit.name); writer.int(it.intervalCount)
        writer.longOrNull(it.endEpochDay); writer.string(it.note); writer.string(it.merchant); writer.string(it.tagsCsv)
        writer.bool(it.active); writer.long(it.createdAt); writer.long(it.updatedAt); writer.stringOrNull(it.clientOperationToken)
    }
    rows.ledgerExceptions.sortedWith(compareBy({ it.seriesId }, { it.occurrenceEpochDay })).forEach {
        writer.tag("ledgerException"); writer.long(it.seriesId); writer.long(it.occurrenceEpochDay); writer.long(it.createdAt)
    }
    rows.ledgerEntries.sortedBy { it.id }.forEach {
        writer.tag("ledger"); writer.long(it.id); writer.longOrNull(it.seriesId); writer.longOrNull(it.occurrenceEpochDay)
        writer.string(it.type.name); writer.long(it.amountCents); writer.long(it.epochDay); writer.int(it.minuteOfDay)
        writer.string(it.note); writer.string(it.merchant); writer.string(it.tagsCsv); writer.long(it.createdAt)
        writer.long(it.updatedAt); writer.longOrNull(it.deletedAt); writer.stringOrNull(it.clientOperationToken)
    }
    rows.attachments.sortedBy { it.id }.forEach {
        writer.tag("attachment"); writer.long(it.id); writer.string(it.ownerType.name); writer.long(it.ownerId)
        writer.string(it.privatePath); writer.string(it.originalName); writer.string(it.mimeType); writer.long(it.sizeBytes)
        writer.long(it.createdAt); writer.longOrNull(it.pendingDeleteAt)
    }
    rows.vaultMarkers.sortedBy(VaultMarker::id).forEach {
        writer.tag("vault"); writer.string(it.id); writer.long(it.createdAt); writer.long(it.updatedAt)
    }
    return writer.finish()
}

private inline fun String.alsoWith(block: (FingerprintWriter) -> Unit): String {
    // Full stability needs the semantic rows and ciphertext in one digest, not two concatenated
    // unframed digests. Seed a second framed digest with the semantic digest.
    val writer = FingerprintWriter()
    writer.tag("full")
    writer.string(this)
    block(writer)
    return writer.finish()
}

private class FingerprintWriter {
    private val digest = MessageDigest.getInstance("SHA-256")

    fun tag(value: String) = string(value)
    fun bool(value: Boolean) = int(if (value) 1 else 0)
    fun int(value: Int) { digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()) }
    fun long(value: Long) { digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array()) }
    fun intOrNull(value: Int?) { bool(value != null); if (value != null) int(value) }
    fun longOrNull(value: Long?) { bool(value != null); if (value != null) long(value) }
    fun stringOrNull(value: String?) { bool(value != null); if (value != null) string(value) }
    fun string(value: String) = bytes(value.encodeToByteArray())
    fun bytes(value: ByteArray) { int(value.size); digest.update(value) }
    fun finish(): String = digest.digest().joinToString("") { "%02x".format(it) }
}
