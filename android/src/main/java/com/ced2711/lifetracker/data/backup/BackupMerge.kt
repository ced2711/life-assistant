package com.ced2711.lifetracker.data.backup

import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.data.local.DiaryEntryEntity
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.LedgerOccurrenceExceptionEntity
import com.ced2711.lifetracker.data.local.LedgerSeriesEntity
import com.ced2711.lifetracker.data.local.NoteEntity
import com.ced2711.lifetracker.data.local.NoteFolderEntity
import com.ced2711.lifetracker.data.local.SubtaskEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.local.TodoOccurrenceExceptionEntity
import com.ced2711.lifetracker.data.local.TodoReminderEntity
import com.ced2711.lifetracker.data.local.TodoSeriesEntity
import com.ced2711.lifetracker.data.local.TodoSeriesSubtaskEntity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.VaultEntry

/** Which input snapshot a merged record, or an attachment's bytes, came from. */
enum class MergeSide { BASE, LOCAL, REMOTE }

/** The merged attachment with this id takes its bytes from attachment [originalId] of [side]. */
data class MergedAttachmentSource(val side: MergeSide, val originalId: Long)

data class SnapshotMergeResult(
    val snapshot: BackupSnapshot,
    val attachmentSources: Map<Long, MergedAttachmentSource>,
    /** Texts edited differently on both devices; both versions were kept, one under a marker. */
    val textConflicts: Int,
)

/** Put between two versions of a text that both devices edited, so neither is lost. */
const val MERGE_TEXT_MARKER = "—— Other device · 另一台设备 ——"

/**
 * Three-way, record-by-record merge of two snapshots that both changed since [base], the last
 * snapshot both devices agreed on.
 *
 * - A record changed on one side only takes that change; deleted on one side and untouched on the
 *   other, it is deleted. Edited on one side and deleted on the other, the edit wins.
 * - A record edited on both sides is merged field by field. When both changed the same field, the
 *   newer edit wins, except long texts: both versions are kept, separated by [MERGE_TEXT_MARKER].
 * - Both apps number new records "largest id + 1", so the two sides often give different records
 *   the same id. A record is the same one only when its id and creation time match (or, for
 *   things like diary days and recurring occurrences, its natural key); otherwise one side gets a
 *   fresh id and every reference to it is rewritten.
 * - Without a [base] (first connection, or history lost) nothing can be told apart as deleted, so
 *   the result is the union of both sides.
 *
 * The result is validated; an [InvalidBackupException] means the caller should ask the user.
 */
fun mergeSnapshots(base: BackupSnapshot?, local: BackupSnapshot, remote: BackupSnapshot, now: Long): SnapshotMergeResult =
    SnapshotMerger(base, local, remote).merge(now)

private class SideMaps {
    val local = HashMap<Long, Long>()
    val remote = HashMap<Long, Long>()
    fun of(side: MergeSide): Map<Long, Long> = when (side) {
        MergeSide.BASE -> emptyMap() // Base ids are the merged ids.
        MergeSide.LOCAL -> local
        MergeSide.REMOTE -> remote
    }
}

private class SnapshotMerger(
    private val base: BackupSnapshot?,
    private val local: BackupSnapshot,
    private val remote: BackupSnapshot,
) {
    var textConflicts = 0

    private val categoryIds = SideMaps()
    private val todoSeriesIds = SideMaps()
    private val todoIds = SideMaps()
    private val ledgerSeriesIds = SideMaps()
    private val ledgerIds = SideMaps()
    private val folderIds = SideMaps()
    private val noteIds = SideMaps()
    private val diaryIds = SideMaps()
    private val attachmentIds = SideMaps()

    fun merge(now: Long): SnapshotMergeResult {
        val categories = mergeTable(
            Table(
                rows = { it.categories },
                id = CategoryEntity::id,
                stamp = CategoryEntity::createdAt,
                withId = { row, id -> row.copy(id = id) },
                maps = categoryIds,
                remap = { row, side -> row.copy(parentId = row.parentId?.let { categoryIds.of(side)[it] ?: it }) },
                naturalKey = { row -> row.parentId to row.name.trim().lowercase() },
                selfReferencing = { it.parentId },
                merge = { b, l, r ->
                    val f = fields(b, l, r, localNewer = true)
                    l.copy(name = f.v { it.name }, parentId = f.v { it.parentId }, sortOrder = f.v { it.sortOrder }, createdAt = minOf(l.createdAt, r.createdAt))
                },
            ),
        ).let(::fixCategories)
        val keptCategories = categories.mapTo(HashSet(), CategoryEntity::id)

        val todoSeries = mergeTable(
            Table(
                rows = { it.todoSeries },
                id = TodoSeriesEntity::id,
                stamp = TodoSeriesEntity::createdAt,
                withId = { row, id -> row.copy(id = id) },
                maps = todoSeriesIds,
                remap = { row, side -> row.copy(categoryId = row.categoryId?.let { categoryIds.of(side)[it] ?: it }) },
                merge = { b, l, r ->
                    val f = fields(b, l, r, localNewer = l.updatedAt >= r.updatedAt)
                    l.copy(
                        title = f.v { it.title }, description = f.text { it.description }, categoryId = f.v { it.categoryId },
                        startEpochDay = f.v { it.startEpochDay }, startMinute = f.v { it.startMinute },
                        recurrenceUnit = f.v { it.recurrenceUnit }, intervalCount = f.v { it.intervalCount },
                        endEpochDay = f.v { it.endEpochDay }, priority = f.v { it.priority }, tagsCsv = f.v { it.tagsCsv },
                        reminderOffsetsCsv = f.v { it.reminderOffsetsCsv }, active = f.v { it.active },
                        createdAt = minOf(l.createdAt, r.createdAt), updatedAt = maxOf(l.updatedAt, r.updatedAt),
                    )
                },
            ),
        ).map { if (it.categoryId != null && it.categoryId !in keptCategories) it.copy(categoryId = null) else it }
        val keptTodoSeries = todoSeries.mapTo(HashSet(), TodoSeriesEntity::id)

        val todos = mergeTable(
            Table(
                rows = { it.todos },
                id = TodoEntity::id,
                stamp = TodoEntity::createdAt,
                withId = { row, id -> row.copy(id = id) },
                maps = todoIds,
                remap = { row, side ->
                    row.copy(
                        categoryId = row.categoryId?.let { categoryIds.of(side)[it] ?: it },
                        seriesId = row.seriesId?.let { todoSeriesIds.of(side)[it] ?: it },
                    )
                },
                naturalKey = { row -> row.seriesId?.let { it to row.occurrenceEpochDay } },
                merge = { b, l, r ->
                    val f = fields(b, l, r, localNewer = l.updatedAt >= r.updatedAt)
                    val merged = l.copy(
                        title = f.v { it.title }, description = f.text { it.description }, categoryId = f.v { it.categoryId },
                        deadlineEpochDay = f.v { it.deadlineEpochDay }, deadlineMinute = f.v { it.deadlineMinute },
                        priority = f.v { it.priority }, tagsCsv = f.v { it.tagsCsv }, completedAt = f.v { it.completedAt },
                        customOrder = f.v { it.customOrder }, deletedAt = f.v { it.deletedAt },
                        createdAt = minOf(l.createdAt, r.createdAt), updatedAt = maxOf(l.updatedAt, r.updatedAt),
                        clientOperationToken = f.v { it.clientOperationToken },
                    )
                    // An edit made after the other device deleted the todo brings it back.
                    val deleted = merged.deletedAt
                    if (deleted != null && listOf(l, r).any { it.deletedAt == null && it.updatedAt > deleted }) merged.copy(deletedAt = null) else merged
                },
            ),
        ).map { todo ->
            var fixed = todo
            if (fixed.categoryId != null && fixed.categoryId !in keptCategories) fixed = fixed.copy(categoryId = null)
            if (fixed.seriesId != null && fixed.seriesId !in keptTodoSeries) fixed = fixed.copy(seriesId = null, occurrenceEpochDay = null)
            fixed
        }.let { dedupeOccurrences(it, TodoEntity::id, { t -> t.seriesId?.let { s -> s to t.occurrenceEpochDay } }, TodoEntity::updatedAt) }
            .let { dedupeTokens(it, TodoEntity::clientOperationToken) { row -> row.copy(clientOperationToken = null) } }
        val keptTodos = todos.mapTo(HashSet(), TodoEntity::id)

        val ledgerSeries = mergeTable(
            Table(
                rows = { it.ledgerSeries },
                id = LedgerSeriesEntity::id,
                stamp = LedgerSeriesEntity::createdAt,
                withId = { row, id -> row.copy(id = id) },
                maps = ledgerSeriesIds,
                remap = { row, _ -> row },
                merge = { b, l, r ->
                    val f = fields(b, l, r, localNewer = l.updatedAt >= r.updatedAt)
                    l.copy(
                        type = f.v { it.type }, amountCents = f.v { it.amountCents }, startEpochDay = f.v { it.startEpochDay },
                        recurrenceUnit = f.v { it.recurrenceUnit }, intervalCount = f.v { it.intervalCount },
                        endEpochDay = f.v { it.endEpochDay }, note = f.v { it.note }, merchant = f.v { it.merchant },
                        tagsCsv = f.v { it.tagsCsv }, active = f.v { it.active },
                        createdAt = minOf(l.createdAt, r.createdAt), updatedAt = maxOf(l.updatedAt, r.updatedAt),
                        clientOperationToken = f.v { it.clientOperationToken },
                    )
                },
            ),
        ).let { dedupeTokens(it, LedgerSeriesEntity::clientOperationToken) { row -> row.copy(clientOperationToken = null) } }
        val keptLedgerSeries = ledgerSeries.mapTo(HashSet(), LedgerSeriesEntity::id)

        val ledgerEntries = mergeTable(
            Table(
                rows = { it.ledgerEntries },
                id = LedgerEntryEntity::id,
                stamp = LedgerEntryEntity::createdAt,
                withId = { row, id -> row.copy(id = id) },
                maps = ledgerIds,
                remap = { row, side -> row.copy(seriesId = row.seriesId?.let { ledgerSeriesIds.of(side)[it] ?: it }) },
                naturalKey = { row -> row.seriesId?.let { it to row.occurrenceEpochDay } },
                merge = { b, l, r ->
                    val f = fields(b, l, r, localNewer = l.updatedAt >= r.updatedAt)
                    val merged = l.copy(
                        type = f.v { it.type }, amountCents = f.v { it.amountCents }, epochDay = f.v { it.epochDay },
                        minuteOfDay = f.v { it.minuteOfDay }, note = f.v { it.note }, merchant = f.v { it.merchant },
                        tagsCsv = f.v { it.tagsCsv }, deletedAt = f.v { it.deletedAt },
                        createdAt = minOf(l.createdAt, r.createdAt), updatedAt = maxOf(l.updatedAt, r.updatedAt),
                        clientOperationToken = f.v { it.clientOperationToken },
                    )
                    val deleted = merged.deletedAt
                    if (deleted != null && listOf(l, r).any { it.deletedAt == null && it.updatedAt > deleted }) merged.copy(deletedAt = null) else merged
                },
            ),
        ).map { if (it.seriesId != null && it.seriesId !in keptLedgerSeries) it.copy(seriesId = null, occurrenceEpochDay = null) else it }
            .let { dedupeOccurrences(it, LedgerEntryEntity::id, { e -> e.seriesId?.let { s -> s to e.occurrenceEpochDay } }, LedgerEntryEntity::updatedAt) }
            .let { dedupeTokens(it, LedgerEntryEntity::clientOperationToken) { row -> row.copy(clientOperationToken = null) } }
        val keptLedger = ledgerEntries.mapTo(HashSet(), LedgerEntryEntity::id)

        val folders = mergeTable(
            Table(
                rows = { it.noteFolders },
                id = NoteFolderEntity::id,
                stamp = NoteFolderEntity::createdAt,
                withId = { row, id -> row.copy(id = id) },
                maps = folderIds,
                remap = { row, side -> row.copy(parentId = row.parentId?.let { folderIds.of(side)[it] ?: it }) },
                naturalKey = { row -> row.parentId to row.name.trim().lowercase() },
                selfReferencing = { it.parentId },
                merge = { b, l, r ->
                    val f = fields(b, l, r, localNewer = true)
                    l.copy(name = f.v { it.name }, parentId = f.v { it.parentId }, sortOrder = f.v { it.sortOrder }, createdAt = minOf(l.createdAt, r.createdAt))
                },
            ),
        ).let(::fixFolders)
        val keptFolders = folders.mapTo(HashSet(), NoteFolderEntity::id)

        val notes = mergeTable(
            Table(
                rows = { it.notes },
                id = NoteEntity::id,
                stamp = NoteEntity::createdAt,
                withId = { row, id -> row.copy(id = id) },
                maps = noteIds,
                remap = { row, side -> row.copy(folderId = row.folderId?.let { folderIds.of(side)[it] ?: it }) },
                merge = { b, l, r ->
                    val f = fields(b, l, r, localNewer = l.updatedAt >= r.updatedAt)
                    l.copy(
                        folderId = f.v { it.folderId }, title = f.v { it.title }, body = f.text { it.body }, pinned = f.v { it.pinned },
                        createdAt = minOf(l.createdAt, r.createdAt), updatedAt = maxOf(l.updatedAt, r.updatedAt),
                    )
                },
            ),
        ).map { if (it.folderId != null && it.folderId !in keptFolders) it.copy(folderId = null) else it }
        val keptNotes = notes.mapTo(HashSet(), NoteEntity::id)

        val diary = mergeTable(
            Table(
                rows = { it.diaryEntries },
                id = DiaryEntryEntity::id,
                stamp = DiaryEntryEntity::createdAt,
                withId = { row, id -> row.copy(id = id) },
                maps = diaryIds,
                remap = { row, _ -> row },
                // One page per day: a page created on both devices for the same day is one page.
                naturalKey = DiaryEntryEntity::epochDay,
                merge = { b, l, r ->
                    val f = fields(b, l, r, localNewer = l.updatedAt >= r.updatedAt)
                    l.copy(body = f.text { it.body }, createdAt = minOf(l.createdAt, r.createdAt), updatedAt = maxOf(l.updatedAt, r.updatedAt))
                },
            ),
        ).let(::oneDiaryPagePerDay)

        val vault = mergeVault()

        val subtasks = mergeChildLists(
            parents = keptTodos,
            maps = todoIds,
            rows = { it.subtasks },
            parentOf = SubtaskEntity::todoId,
            order = SubtaskEntity::sortOrder,
            key = { it.description.trim().lowercase() },
            content = { it.description to it.isCompleted },
            mergeItem = { b, l, r -> l.copy(isCompleted = fields(b, l, r, localNewer = true).v { it.isCompleted }) },
        ).let { lists ->
            var nextId = 1L
            lists.flatMap { (todoId, items) -> items.mapIndexed { index, item -> item.copy(id = nextId++, todoId = todoId, sortOrder = index) } }
        }
        val todoSeriesSubtasks = mergeChildLists(
            parents = keptTodoSeries,
            maps = todoSeriesIds,
            rows = { it.todoSeriesSubtasks },
            parentOf = TodoSeriesSubtaskEntity::seriesId,
            order = TodoSeriesSubtaskEntity::sortOrder,
            key = { it.description.trim().lowercase() },
            mergeItem = { _, l, _ -> l },
        ).flatMap { (seriesId, items) -> items.mapIndexed { index, item -> item.copy(seriesId = seriesId, sortOrder = index) } }
        val reminders = mergeChildLists(
            parents = keptTodos,
            maps = todoIds,
            rows = { it.todoReminders },
            parentOf = TodoReminderEntity::todoId,
            order = { it.offsetMinutes },
            key = TodoReminderEntity::offsetMinutes,
            mergeItem = { _, l, _ -> l },
        ).let { lists ->
            var nextId = 1L
            lists.flatMap { (todoId, items) -> items.map { it.copy(id = nextId++, todoId = todoId) } }
        }
        val todoExceptions = mergeSet(
            rows = { it.todoOccurrenceExceptions },
            key = { row, side -> (todoSeriesIds.of(side)[row.seriesId] ?: row.seriesId) to row.occurrenceEpochDay },
            build = { key, row -> row.copy(seriesId = key.first) },
        ).filter { it.seriesId in keptTodoSeries }
        val ledgerExceptions = mergeSet(
            rows = { it.ledgerOccurrenceExceptions },
            key = { row, side -> (ledgerSeriesIds.of(side)[row.seriesId] ?: row.seriesId) to row.occurrenceEpochDay },
            build = { key, row -> row.copy(seriesId = key.first) },
        ).filter { it.seriesId in keptLedgerSeries }

        val (attachments, sources) = mergeAttachments(keptTodos, keptLedger, keptNotes)

        val settings = local.settings.let { l ->
            val f = fields(base?.settings, l, remote.settings, localNewer = true)
            l.copy(
                themeMode = f.v { it.themeMode }, accentColor = f.v { it.accentColor }, weekStart = f.v { it.weekStart },
                timeFormat = f.v { it.timeFormat }, dateFormat = f.v { it.dateFormat },
                notificationsEnabled = f.v { it.notificationsEnabled },
                defaultAllDayReminderMinute = f.v { it.defaultAllDayReminderMinute },
                defaultReminderOffsetsMinutes = f.v { it.defaultReminderOffsetsMinutes },
                todoQuickAddFields = f.v { it.todoQuickAddFields },
                // Where the app was last open is a per-device choice.
                lastDestination = l.lastDestination,
            )
        }

        val snapshot = BackupSnapshot(
            formatVersion = BackupLimits.SNAPSHOT_VERSION,
            createdAt = maxOf(now, local.createdAt, remote.createdAt).coerceAtMost(BackupLimits.MAX_TIMESTAMP_MILLIS),
            settings = settings,
            categories = categories,
            todoSeries = todoSeries,
            todoSeriesSubtasks = todoSeriesSubtasks,
            todoOccurrenceExceptions = todoExceptions,
            todos = todos,
            subtasks = subtasks,
            todoReminders = reminders,
            ledgerSeries = ledgerSeries,
            ledgerOccurrenceExceptions = ledgerExceptions,
            ledgerEntries = ledgerEntries,
            attachments = attachments,
            vaultEntries = vault,
            noteFolders = folders,
            notes = notes,
            diaryEntries = diary,
        ).validate()
        return SnapshotMergeResult(snapshot, sources, textConflicts)
    }

    // ---- Generic table merge -------------------------------------------------------------

    private class Table<T>(
        val rows: (BackupSnapshot) -> List<T>,
        val id: (T) -> Long,
        val stamp: (T) -> Long,
        val withId: (T, Long) -> T,
        val maps: SideMaps,
        /** Rewrites references to other tables (and, for trees, to this one) into merged ids. */
        val remap: (T, MergeSide) -> T,
        val merge: (base: T?, local: T, remote: T) -> T,
        /** Records added on both sides with an equal key are the same record. */
        val naturalKey: ((T) -> Any?)? = null,
        val selfReferencing: ((T) -> Long?)? = null,
    )

    private fun <T> mergeTable(table: Table<T>): List<T> {
        val baseRows = base?.let(table.rows).orEmpty().associateBy(table.id)
        val localRows = table.rows(local)
        val remoteRows = table.rows(remote)
        var nextId = (baseRows.keys + localRows.map(table.id) + remoteRows.map(table.id)).maxOrNull()?.plus(1) ?: 1L
        val used = HashSet<Long>(baseRows.keys)

        // Ids are reused after deletions, so a record is the base one only if it was created then too.
        fun isBaseRecord(row: T): Boolean = baseRows[table.id(row)]?.let { table.stamp(it) == table.stamp(row) } == true

        // Records that already existed in the base keep their id on both sides.
        val localNew = ArrayList<T>()
        val remoteNew = ArrayList<T>()
        localRows.forEach { if (isBaseRecord(it)) table.maps.local[table.id(it)] = table.id(it) else localNew += it }
        remoteRows.forEach { if (isBaseRecord(it)) table.maps.remote[table.id(it)] = table.id(it) else remoteNew += it }

        // New remote records keep their id unless it is taken.
        assignNew(table, remoteNew, MergeSide.REMOTE, used) { nextId++ }

        // New local records: the same as a new remote record (equal id and creation time, or
        // equal natural key), else their own id, else a fresh one.
        val remoteNewById = remoteNew.associateBy(table.id)
        val pending = ArrayList(localNew)
        var progress = true
        while (pending.isNotEmpty() && progress) {
            progress = false
            val iterator = pending.iterator()
            while (iterator.hasNext()) {
                val row = iterator.next()
                val parent = table.selfReferencing?.invoke(row)
                // In a tree, wait until the parent's merged id is known so natural keys compare.
                if (parent != null && parent !in table.maps.local && localNew.any { table.id(it) == parent }) continue
                iterator.remove()
                progress = true
                val twin = remoteNewById[table.id(row)]?.takeIf { table.stamp(it) == table.stamp(row) }
                    ?: table.naturalKey?.let { keyOf ->
                        val key = keyOf(table.remap(row, MergeSide.LOCAL)) ?: return@let null
                        remoteNew.firstOrNull { keyOf(table.remap(it, MergeSide.REMOTE)) == key }
                    }
                if (twin != null) {
                    table.maps.local[table.id(row)] = table.maps.remote.getValue(table.id(twin))
                } else {
                    assignNew(table, listOf(row), MergeSide.LOCAL, used) { nextId++ }
                }
            }
        }
        if (pending.isNotEmpty()) assignNew(table, pending, MergeSide.LOCAL, used) { nextId++ } // a damaged tree

        // Three-way merge per merged id.
        val mergedLocal = localRows.associate { row -> table.maps.local.getValue(table.id(row)) to row }
        val mergedRemote = remoteRows.associate { row -> table.maps.remote.getValue(table.id(row)) to row }
        val result = ArrayList<T>()
        (baseRows.keys + mergedLocal.keys + mergedRemote.keys).toSortedSet().forEach { id ->
            // New records never get a base id, so a base id always means the base record.
            val b = baseRows[id]
            val l = mergedLocal[id]?.let { table.withId(table.remap(it, MergeSide.LOCAL), id) }
            val r = mergedRemote[id]?.let { table.withId(table.remap(it, MergeSide.REMOTE), id) }
            val bb = b?.let { table.withId(it, id) }
            val merged = when {
                l == null && r == null -> null
                l == null -> if (bb != null && r == bb) null else r // deleted locally; kept when edited remotely
                r == null -> if (bb != null && l == bb) null else l
                else -> table.merge(bb, l, r)
            }
            if (merged != null) result += merged
        }
        return result
    }

    private fun <T> assignNew(table: Table<T>, rows: List<T>, side: MergeSide, used: MutableSet<Long>, fresh: () -> Long) {
        val map = if (side == MergeSide.LOCAL) table.maps.local else table.maps.remote
        rows.forEach { row ->
            val wanted = table.id(row)
            val id = if (used.add(wanted)) wanted else generateSequence { fresh() }.first { used.add(it) }
            map[wanted] = id
        }
    }

    // ---- Child lists, sets and special tables ---------------------------------------------

    /** Lists owned by a parent (subtasks, reminders) merged item by item, keyed by content. */
    private fun <T, K> mergeChildLists(
        parents: Set<Long>,
        maps: SideMaps,
        rows: (BackupSnapshot) -> List<T>,
        parentOf: (T) -> Long,
        order: (T) -> Comparable<*>,
        key: (T) -> K,
        content: (T) -> Any = { key(it) as Any },
        mergeItem: (base: T?, local: T, remote: T) -> T,
    ): List<Pair<Long, List<T>>> {
        @Suppress("UNCHECKED_CAST")
        fun group(snapshot: BackupSnapshot?, map: Map<Long, Long>?): Map<Long, List<T>> = snapshot?.let(rows).orEmpty()
            .groupBy { row -> map?.get(parentOf(row)) ?: parentOf(row) }
            .mapValues { (_, items) -> items.sortedWith(compareBy { order(it) as Comparable<Any> }) }
        val baseLists = group(base, null)
        val localLists = group(local, maps.local)
        val remoteLists = group(remote, maps.remote)
        return parents.sorted().mapNotNull { parent ->
            val b = baseLists[parent].orEmpty()
            val l = localLists[parent].orEmpty()
            val r = remoteLists[parent].orEmpty()
            val merged = when {
                l.map(content) == r.map(content) -> l
                l.map(content) == b.map(content) -> r
                r.map(content) == b.map(content) -> l
                else -> {
                    val baseByKey = b.associateBy(key)
                    val remoteByKey = r.associateBy(key)
                    val localKeys = l.mapTo(HashSet(), key)
                    val fromLocal = l.mapNotNull { item ->
                        val k = key(item)
                        val other = remoteByKey[k]
                        when {
                            other != null -> mergeItem(baseByKey[k], item, other)
                            baseByKey[k]?.let(content) == content(item) -> null // removed remotely, untouched here
                            else -> item
                        }
                    }
                    val fromRemote = r.filter { item -> key(item) !in localKeys && baseByKey[key(item)]?.let(content) != content(item) }
                    fromLocal + fromRemote
                }
            }
            if (merged.isEmpty()) null else parent to merged
        }
    }

    /** Three-way merge of a set of records identified only by their content. */
    private fun <T, K> mergeSet(rows: (BackupSnapshot) -> List<T>, key: (T, MergeSide) -> K, build: (K, T) -> T): List<T> {
        val baseKeys = base?.let(rows).orEmpty().map { key(it, MergeSide.BASE) }.toSet()
        val localByKey = rows(local).associateBy { key(it, MergeSide.LOCAL) }
        val remoteByKey = rows(remote).associateBy { key(it, MergeSide.REMOTE) }
        return (localByKey.keys + remoteByKey.keys).mapNotNull { k ->
            val inLocal = k in localByKey
            val inRemote = k in remoteByKey
            val removed = k in baseKeys && !(inLocal && inRemote)
            if (removed) null else build(k, localByKey[k] ?: remoteByKey.getValue(k))
        }
    }

    private fun mergeVault(): List<VaultEntry> {
        val baseById = base?.vaultEntries.orEmpty().associateBy(VaultEntry::id)
        val localById = local.vaultEntries.associateBy(VaultEntry::id)
        val remoteById = remote.vaultEntries.associateBy(VaultEntry::id)
        return (baseById.keys + localById.keys + remoteById.keys).toSortedSet().mapNotNull { id ->
            val b = baseById[id]
            val l = localById[id]
            val r = remoteById[id]
            when {
                l == null && r == null -> null
                l == null -> if (r == b) null else r
                r == null -> if (l == b) null else l
                else -> {
                    val f = fields(b, l, r, localNewer = l.updatedAt >= r.updatedAt)
                    l.copy(
                        label = f.v { it.label }, account = f.v { it.account }, password = f.v { it.password },
                        website = f.v { it.website }, notes = f.text { it.notes },
                        createdAt = minOf(l.createdAt, r.createdAt), updatedAt = maxOf(l.updatedAt, r.updatedAt),
                    )
                }
            }
        }
    }

    private fun mergeAttachments(todos: Set<Long>, ledger: Set<Long>, notes: Set<Long>): Pair<List<BackupAttachment>, Map<Long, MergedAttachmentSource>> {
        fun ownerMap(type: AttachmentOwnerType, side: MergeSide): Map<Long, Long> = when (type) {
            AttachmentOwnerType.TODO -> todoIds.of(side)
            AttachmentOwnerType.LEDGER -> ledgerIds.of(side)
            AttachmentOwnerType.NOTE -> noteIds.of(side)
        }
        val merged = mergeTable(
            Table(
                rows = { it.attachments },
                id = BackupAttachment::id,
                stamp = BackupAttachment::createdAt,
                withId = { row, id -> row.copy(id = id, archivePath = attachmentArchivePath(id, row.sha256)) },
                maps = attachmentIds,
                remap = { row, side -> row.copy(ownerId = ownerMap(row.ownerType, side)[row.ownerId] ?: row.ownerId) },
                naturalKey = { row -> Triple(row.ownerType, row.ownerId, row.sha256.toHex()) },
                merge = { b, l, r ->
                    val f = fields(b, l, r, localNewer = true)
                    l.copy(originalName = f.v { it.originalName }, pendingDeleteAt = f.v { it.pendingDeleteAt })
                },
            ),
        )
        val owned = merged.filter { attachment ->
            when (attachment.ownerType) {
                AttachmentOwnerType.TODO -> attachment.ownerId in todos
                AttachmentOwnerType.LEDGER -> attachment.ownerId in ledger
                AttachmentOwnerType.NOTE -> attachment.ownerId in notes
            }
        }.groupBy { it.ownerType to it.ownerId }
            .flatMap { (_, items) -> items.sortedWith(compareBy<BackupAttachment> { it.pendingDeleteAt != null }.thenBy { it.createdAt }).take(10) }
            .sortedBy(BackupAttachment::id)

        // Bytes come from whichever side still holds the file with that hash.
        val localById = attachmentIds.local.entries.associate { (old, new) -> new to old }
        val remoteById = attachmentIds.remote.entries.associate { (old, new) -> new to old }
        val sources = owned.associate { attachment ->
            val source = localById[attachment.id]?.let { MergedAttachmentSource(MergeSide.LOCAL, it) }
                ?: remoteById[attachment.id]?.let { MergedAttachmentSource(MergeSide.REMOTE, it) }
                ?: MergedAttachmentSource(MergeSide.BASE, attachment.id)
            attachment.id to source
        }
        return owned to sources
    }

    // ---- Repairs after merging --------------------------------------------------------------

    private fun fixCategories(rows: List<CategoryEntity>): List<CategoryEntity> {
        val parents = breakCycles(rows.associate { it.id to it.parentId })
        val fixed = rows.map { it.copy(parentId = parents[it.id]) }
        return renameDuplicates(fixed, { it.parentId to it.name.trim().lowercase() }, CategoryEntity::name) { row, name -> row.copy(name = name) }
    }

    private fun fixFolders(rows: List<NoteFolderEntity>): List<NoteFolderEntity> {
        val parents = breakCycles(rows.associate { it.id to it.parentId })
        val fixed = rows.map { it.copy(parentId = parents[it.id]) }
        return renameDuplicates(fixed, { it.parentId to it.name.trim().lowercase() }, NoteFolderEntity::name) { row, name -> row.copy(name = name) }
    }

    /** Missing parents become top level, and a loop (A in B in A) is cut at its first member. */
    private fun breakCycles(parents: Map<Long, Long?>): Map<Long, Long?> {
        val result = parents.mapValues { (_, parent) -> parent?.takeIf { it in parents } }.toMutableMap()
        result.keys.sorted().forEach { start ->
            val seen = LinkedHashSet<Long>()
            var cursor: Long? = start
            while (cursor != null) {
                if (!seen.add(cursor)) {
                    result[cursor] = null
                    break
                }
                cursor = result[cursor]
            }
        }
        return result
    }

    private fun <T> renameDuplicates(rows: List<T>, key: (T) -> Any, name: (T) -> String, rename: (T, String) -> T): List<T> {
        val taken = HashSet<Any>()
        return rows.map { row ->
            if (taken.add(key(row))) return@map row
            var counter = 2
            var candidate: T
            do {
                candidate = rename(row, "${name(row).trim()} ($counter)")
                counter++
            } while (!taken.add(key(candidate)))
            candidate
        }
    }

    /** A page written on both devices for the same day keeps both texts. */
    private fun oneDiaryPagePerDay(rows: List<DiaryEntryEntity>): List<DiaryEntryEntity> =
        rows.groupBy(DiaryEntryEntity::epochDay).values.map { pages ->
            if (pages.size == 1) return@map pages.single()
            val newestFirst = pages.sortedByDescending(DiaryEntryEntity::updatedAt)
            textConflicts++
            newestFirst.first().copy(
                id = pages.minOf(DiaryEntryEntity::id),
                body = newestFirst.joinToString("\n\n$MERGE_TEXT_MARKER\n") { it.body },
                createdAt = pages.minOf(DiaryEntryEntity::createdAt),
            )
        }.sortedBy(DiaryEntryEntity::id)

    private fun <T> dedupeOccurrences(rows: List<T>, id: (T) -> Long, key: (T) -> Any?, updatedAt: (T) -> Long): List<T> {
        val keep = rows.filter { key(it) != null }.groupBy(key).values
            .mapTo(HashSet()) { group -> id(group.maxWith(compareBy<T> { updatedAt(it) }.thenBy { -id(it) })) }
        return rows.filter { key(it) == null || id(it) in keep }
    }

    private fun <T> dedupeTokens(rows: List<T>, token: (T) -> String?, clear: (T) -> T): List<T> {
        val seen = HashSet<String>()
        return rows.map { row -> token(row)?.let { if (seen.add(it)) row else clear(row) } ?: row }
    }

    // ---- Field merging ------------------------------------------------------------------------

    private fun <T> fields(base: T?, local: T, remote: T, localNewer: Boolean) = Fields(base, local, remote, localNewer)

    private inner class Fields<T>(val base: T?, val local: T, val remote: T, val localNewer: Boolean) {
        fun <V> v(get: (T) -> V): V {
            val l = get(local)
            val r = get(remote)
            if (l == r) return l
            if (base != null) {
                val b = get(base)
                if (l == b) return r
                if (r == b) return l
            }
            return if (localNewer) l else r
        }

        /** Both versions of a text edited on both devices are kept, the newer one first. */
        fun text(get: (T) -> String): String {
            val l = get(local)
            val r = get(remote)
            if (l == r) return l
            if (base != null) {
                val b = get(base)
                if (l == b) return r
                if (r == b) return l
            }
            val (newer, older) = if (localNewer) l to r else r to l
            return when {
                older.isBlank() || newer.contains(older) -> newer
                newer.isBlank() || older.contains(newer) -> older
                else -> {
                    textConflicts++
                    "$newer\n\n$MERGE_TEXT_MARKER\n$older"
                }
            }
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
