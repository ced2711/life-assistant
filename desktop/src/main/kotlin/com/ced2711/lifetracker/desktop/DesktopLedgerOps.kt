package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.LedgerSeriesEntity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import com.ced2711.lifetracker.domain.model.normalizeTags

/** What a ledger deletion changed, so Undo can put exactly that back. */
data class LedgerDeletion(
    val entryIds: List<Long>,
    val deletedAt: Long,
    val seriesId: Long?,
    val boundaryEpochDay: Long?,
    val seriesWasActive: Boolean,
    val exceptionCreated: Boolean,
)

/**
 * Ledger rules on a whole snapshot, following the Android repository: recurring schedules create
 * their entries as days arrive, a changed or deleted occurrence leaves an exception, "this and
 * later" stops the old schedule (removing its later entries) and starts a new one.
 */
internal object DesktopLedgerOps {
    private const val GENERATED_LEDGER_MINUTE = 0
    private const val MAX_AMOUNT_CENTS = 99_999_999_999L

    private fun validate(draft: LedgerDraft) {
        require(draft.amountCents in 1..MAX_AMOUNT_CENTS) { "Amount must be between 0.01 and 999,999,999.99" }
        require(draft.minuteOfDay in 0..1_439) { "Invalid time" }
        require(draft.recurrence == null || draft.recurrence.interval in 1..10_000) { "Recurrence interval must be positive" }
        require(draft.recurrence?.endEpochDay == null || draft.recurrence.endEpochDay >= draft.epochDay) {
            "Repeat end date cannot be before its first occurrence"
        }
    }

    /** Saves an entry; returns its id, or null when it became a schedule that starts later. */
    fun save(snapshot: BackupSnapshot, draft: LedgerDraft, scope: SeriesEditScope, today: Long, now: Long): Pair<BackupSnapshot, Long?> {
        validate(draft)
        var result = snapshot
        val existing = draft.id?.let { id -> requireNotNull(snapshot.ledgerEntries.firstOrNull { it.id == id }) { "Ledger entry does not exist" } }

        fun insertSeries(): LedgerSeriesEntity {
            val recurrence = requireNotNull(draft.recurrence)
            val series = LedgerSeriesEntity(
                id = (result.ledgerSeries.maxOfOrNull(LedgerSeriesEntity::id) ?: 0L) + 1,
                type = draft.type,
                amountCents = draft.amountCents,
                startEpochDay = draft.epochDay,
                recurrenceUnit = recurrence.unit,
                intervalCount = recurrence.interval,
                endEpochDay = recurrence.endEpochDay,
                note = draft.note.trim(),
                merchant = draft.merchant.trim(),
                tagsCsv = normalizeTags(draft.tags),
                createdAt = now,
                updatedAt = now,
            )
            result = result.copy(ledgerSeries = result.ledgerSeries + series)
            return series
        }

        fun write(entry: LedgerEntryEntity): Long {
            result = result.copy(ledgerEntries = result.ledgerEntries.filterNot { it.id == entry.id } + entry)
            return entry.id
        }

        fun fromDraft(id: Long, seriesId: Long?, occurrence: Long?, minute: Int, base: LedgerEntryEntity?) = LedgerEntryEntity(
            id = id,
            seriesId = seriesId,
            occurrenceEpochDay = occurrence,
            type = draft.type,
            amountCents = draft.amountCents,
            epochDay = draft.epochDay,
            minuteOfDay = minute,
            note = draft.note.trim(),
            merchant = draft.merchant.trim(),
            tagsCsv = normalizeTags(draft.tags),
            createdAt = base?.createdAt ?: now,
            updatedAt = maxOf(now, base?.updatedAt ?: 0L),
            deletedAt = base?.deletedAt,
            clientOperationToken = base?.clientOperationToken,
        )

        fun nextId() = (result.ledgerEntries.maxOfOrNull(LedgerEntryEntity::id) ?: 0L) + 1

        if (existing == null) {
            if (draft.recurrence == null) return write(fromDraft(nextId(), null, null, draft.minuteOfDay, null)).let { id -> result to id }
            val series = insertSeries()
            val startId = if (series.startEpochDay <= today) write(fromDraft(nextId(), series.id, series.startEpochDay, GENERATED_LEDGER_MINUTE, null)) else null
            return DesktopTodoOps.materialize(result, today, now) to startId
        }

        val oldSeriesId = existing.seriesId
        val oldOccurrence = existing.occurrenceEpochDay
        if (oldSeriesId != null && scope == SeriesEditScope.ONLY_THIS_OCCURRENCE) {
            val occurrence = requireNotNull(oldOccurrence) { "A recurring ledger entry must retain its occurrence identity" }
            val keptSeries = if (draft.recurrence == null) {
                result = DesktopTodoOps.addLedgerException(result, oldSeriesId, occurrence, now)
                null
            } else oldSeriesId
            return write(fromDraft(existing.id, keptSeries, keptSeries?.let { occurrence }, draft.minuteOfDay, existing)).let { id -> result to id }
        }
        if (oldSeriesId != null) {
            val boundary = requireNotNull(oldOccurrence) { "A recurring ledger entry must retain its occurrence identity" }
            if (draft.recurrence != null) require(draft.epochDay >= boundary) { "The replacement series cannot start before the edited occurrence" }
            result = stopSeries(result, oldSeriesId, now)
            result = softDelete(result, result.ledgerEntries.filter { it.seriesId == oldSeriesId && (it.occurrenceEpochDay ?: Long.MIN_VALUE) > boundary && it.deletedAt == null }.map { it.id }, now)
        }
        if (draft.recurrence == null) return write(fromDraft(existing.id, null, null, draft.minuteOfDay, existing)).let { id -> result to id }
        val replacement = insertSeries()
        if (draft.epochDay > today) {
            // The entry moves into the future: the new schedule creates it on that day.
            result = softDelete(result, listOf(existing.id), now)
            return result to null
        }
        val id = write(fromDraft(existing.id, replacement.id, draft.epochDay, GENERATED_LEDGER_MINUTE, existing))
        return DesktopTodoOps.materialize(result, today, now) to id
    }

    fun delete(snapshot: BackupSnapshot, entryId: Long, scope: SeriesEditScope, now: Long): Pair<BackupSnapshot, LedgerDeletion> {
        val entry = requireNotNull(snapshot.ledgerEntries.firstOrNull { it.id == entryId }) { "Ledger entry does not exist" }
        check(entry.deletedAt == null) { "Ledger entry is already deleted" }
        val seriesId = entry.seriesId
        val boundary = entry.occurrenceEpochDay
        val series = seriesId?.let { id -> snapshot.ledgerSeries.firstOrNull { it.id == id } }
        val tail = scope == SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES && seriesId != null && boundary != null
        val affected = if (tail) {
            snapshot.ledgerEntries.filter { it.seriesId == seriesId && (it.occurrenceEpochDay ?: Long.MIN_VALUE) >= boundary!! && it.deletedAt == null }.map { it.id }
        } else {
            listOf(entryId)
        }
        val deletedAt = maxOf(now, snapshot.ledgerEntries.filter { it.id in affected }.maxOf { it.updatedAt })
        var result = snapshot
        val exceptionCreated = seriesId != null && boundary != null &&
            result.ledgerOccurrenceExceptions.none { it.seriesId == seriesId && it.occurrenceEpochDay == boundary }
        if (seriesId != null && boundary != null) result = DesktopTodoOps.addLedgerException(result, seriesId, boundary, deletedAt)
        if (tail) result = stopSeries(result, seriesId!!, deletedAt)
        result = softDelete(result, affected, deletedAt)
        return result to LedgerDeletion(affected, deletedAt, seriesId, boundary, series?.active ?: false, exceptionCreated)
    }

    fun undoDelete(snapshot: BackupSnapshot, deletion: LedgerDeletion, now: Long): BackupSnapshot {
        val restoreAt = maxOf(now, deletion.deletedAt)
        var result = snapshot.copy(
            ledgerEntries = snapshot.ledgerEntries.map { entry ->
                if (entry.id in deletion.entryIds && entry.deletedAt == deletion.deletedAt) entry.copy(deletedAt = null, updatedAt = restoreAt) else entry
            },
            attachments = snapshot.attachments.map { attachment ->
                if (attachment.ownerType == AttachmentOwnerType.LEDGER && attachment.ownerId in deletion.entryIds &&
                    attachment.pendingDeleteAt == deletion.deletedAt + DesktopTodoOps.UNDO_WINDOW_MILLIS
                ) attachment.copy(pendingDeleteAt = null) else attachment
            },
        )
        if (deletion.seriesId != null && deletion.boundaryEpochDay != null && deletion.exceptionCreated) {
            result = result.copy(
                ledgerOccurrenceExceptions = result.ledgerOccurrenceExceptions.filterNot {
                    it.seriesId == deletion.seriesId && it.occurrenceEpochDay == deletion.boundaryEpochDay
                },
            )
        }
        if (deletion.seriesId != null && deletion.seriesWasActive && deletion.entryIds.size > 1) {
            result = result.copy(
                ledgerSeries = result.ledgerSeries.map { if (it.id == deletion.seriesId) it.copy(active = true, updatedAt = maxOf(restoreAt, it.updatedAt)) else it },
            )
        }
        return result
    }

    /** Stops a schedule; the entries it already created stay. */
    fun stopSeries(snapshot: BackupSnapshot, seriesId: Long, now: Long): BackupSnapshot = snapshot.copy(
        ledgerSeries = snapshot.ledgerSeries.map { if (it.id == seriesId && it.active) it.copy(active = false, updatedAt = maxOf(now, it.updatedAt)) else it },
    )

    /** Removes a stopped schedule; its entries stay as ordinary entries. */
    fun deleteStoppedSeries(snapshot: BackupSnapshot, seriesId: Long, now: Long): BackupSnapshot {
        val series = requireNotNull(snapshot.ledgerSeries.firstOrNull { it.id == seriesId }) { "Ledger series does not exist" }
        require(!series.active) { "Stop the schedule before deleting it" }
        return snapshot.copy(
            ledgerSeries = snapshot.ledgerSeries.filterNot { it.id == seriesId },
            ledgerOccurrenceExceptions = snapshot.ledgerOccurrenceExceptions.filterNot { it.seriesId == seriesId },
            ledgerEntries = snapshot.ledgerEntries.map { entry ->
                if (entry.seriesId == seriesId) entry.copy(seriesId = null, occurrenceEpochDay = null, updatedAt = maxOf(now, entry.updatedAt)) else entry
            },
        )
    }

    /**
     * Changes a schedule from a future day on: the old one stops and a new one starts on
     * [LedgerDraft.epochDay], which must be after today. Entries already created stay as they are.
     */
    fun editSeriesForFuture(snapshot: BackupSnapshot, seriesId: Long, draft: LedgerDraft, today: Long, now: Long): BackupSnapshot {
        validate(draft)
        requireNotNull(draft.recurrence) { "A schedule needs a repeat rule" }
        require(draft.epochDay > today) { "The changed schedule must start on a future date" }
        requireNotNull(snapshot.ledgerSeries.firstOrNull { it.id == seriesId }) { "Ledger series does not exist" }
        return save(stopSeries(snapshot, seriesId, now), draft.copy(id = null), SeriesEditScope.ONLY_THIS_OCCURRENCE, today, now).first
    }

    private fun softDelete(snapshot: BackupSnapshot, ids: List<Long>, deletedAt: Long): BackupSnapshot {
        if (ids.isEmpty()) return snapshot
        val set = ids.toHashSet()
        return snapshot.copy(
            ledgerEntries = snapshot.ledgerEntries.map { if (it.id in set) it.copy(deletedAt = deletedAt, updatedAt = maxOf(deletedAt, it.updatedAt)) else it },
            attachments = snapshot.attachments.map { attachment ->
                if (attachment.ownerType == AttachmentOwnerType.LEDGER && attachment.ownerId in set && attachment.pendingDeleteAt == null) {
                    attachment.copy(pendingDeleteAt = maxOf(deletedAt + DesktopTodoOps.UNDO_WINDOW_MILLIS, attachment.createdAt))
                } else attachment
            },
        )
    }
}
