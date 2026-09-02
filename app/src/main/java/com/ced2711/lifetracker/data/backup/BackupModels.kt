package com.ced2711.lifetracker.data.backup

import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.data.local.CategoryEntity
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
import com.ced2711.lifetracker.data.MAX_PERSISTED_TIMESTAMP_MILLIS
import com.ced2711.lifetracker.domain.date.SmartDateParser
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.TodoQuickAddField
import com.ced2711.lifetracker.domain.model.VaultEntry
import com.ced2711.lifetracker.domain.model.WeekStart
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class BackupSettings(
    val themeMode: ThemeMode,
    val accentColor: AccentColor = AccentColor.TEAL,
    val weekStart: WeekStart,
    val timeFormat: TimeFormatOption,
    val dateFormat: DateFormatOption,
    val notificationsEnabled: Boolean,
    val defaultAllDayReminderMinute: Int,
    val defaultReminderOffsetsMinutes: Set<Long>,
    val todoQuickAddFields: Set<TodoQuickAddField>,
    val lastDestination: TopLevelDestination,
)

/** Device-specific private paths are intentionally replaced by an authenticated archive path. */
data class BackupAttachment(
    val id: Long,
    val ownerType: AttachmentOwnerType,
    val ownerId: Long,
    val archivePath: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: ByteArray,
    val createdAt: Long,
    val pendingDeleteAt: Long?,
) {
    fun toEntity(privatePath: String) = AttachmentEntity(
        id = id,
        ownerType = ownerType,
        ownerId = ownerId,
        privatePath = privatePath,
        originalName = originalName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        createdAt = createdAt,
        pendingDeleteAt = pendingDeleteAt,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupAttachment) return false
        return id == other.id &&
            ownerType == other.ownerType &&
            ownerId == other.ownerId &&
            archivePath == other.archivePath &&
            originalName == other.originalName &&
            mimeType == other.mimeType &&
            sizeBytes == other.sizeBytes &&
            sha256.contentEquals(other.sha256) &&
            createdAt == other.createdAt &&
            pendingDeleteAt == other.pendingDeleteAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + ownerType.hashCode()
        result = 31 * result + ownerId.hashCode()
        result = 31 * result + archivePath.hashCode()
        result = 31 * result + originalName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + sha256.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (pendingDeleteAt?.hashCode() ?: 0)
        return result
    }
}

data class BackupSnapshot(
    val formatVersion: Int = BackupLimits.SNAPSHOT_VERSION,
    val createdAt: Long,
    val settings: BackupSettings,
    val categories: List<CategoryEntity>,
    val todoSeries: List<TodoSeriesEntity>,
    val todoSeriesSubtasks: List<TodoSeriesSubtaskEntity>,
    val todoOccurrenceExceptions: List<TodoOccurrenceExceptionEntity>,
    val todos: List<TodoEntity>,
    val subtasks: List<SubtaskEntity>,
    val todoReminders: List<TodoReminderEntity>,
    val ledgerSeries: List<LedgerSeriesEntity>,
    val ledgerOccurrenceExceptions: List<LedgerOccurrenceExceptionEntity>,
    val ledgerEntries: List<LedgerEntryEntity>,
    val attachments: List<BackupAttachment>,
    val vaultEntries: List<VaultEntry>,
    val noteFolders: List<NoteFolderEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
)

sealed class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
class InvalidBackupException(message: String, cause: Throwable? = null) :
    BackupException(message, cause)
class BackupAuthenticationException(cause: Throwable? = null) :
    BackupException("The backup password is incorrect or the backup was modified.", cause)
class UnsupportedBackupException(message: String) : BackupException(message)

object BackupLimits {
    const val LEGACY_SNAPSHOT_VERSION = 1
    const val ACCENT_COLOR_SNAPSHOT_VERSION = 3
    const val NOTES_SNAPSHOT_VERSION = 4
    const val SNAPSHOT_VERSION = NOTES_SNAPSHOT_VERSION
    const val MAX_RECORDS_PER_TABLE = 100_000
    const val MAX_TOTAL_RECORDS = 300_000
    const val MAX_TEXT_UTF8_BYTES = 1024 * 1024
    // Attachment bodies are streamed separately; these caps bound retained decoded Strings.
    const val MAX_TOTAL_TEXT_UTF8_BYTES = 64 * 1024 * 1024
    const val MAX_TOTAL_VAULT_UTF8_BYTES = 8 * 1024 * 1024
    const val MAX_ATTACHMENT_BYTES = 25L * 1024L * 1024L
    const val MAX_ENCRYPTED_CONTAINER_BYTES = 512L * 1024L * 1024L * 1024L
    const val MIN_FREE_SPACE_BYTES = 16L * 1024L * 1024L
    const val SHA256_BYTES = 32
    const val MIN_EPOCH_DAY = SmartDateParser.MIN_SUPPORTED_EPOCH_DAY
    const val MAX_EPOCH_DAY = SmartDateParser.MAX_SUPPORTED_EPOCH_DAY
    const val MAX_TIMESTAMP_MILLIS = MAX_PERSISTED_TIMESTAMP_MILLIS // 9999-12-31T23:59:59.999Z
}

fun BackupSnapshot.validate(): BackupSnapshot {
    invalidIf(
        formatVersion !in BackupLimits.LEGACY_SNAPSHOT_VERSION..BackupLimits.SNAPSHOT_VERSION,
        "Unsupported snapshot version.",
    )
    timestamp(createdAt, "Backup creation time")
    val tables = listOf(
        categories.size,
        todoSeries.size,
        todoSeriesSubtasks.size,
        todoOccurrenceExceptions.size,
        todos.size,
        subtasks.size,
        todoReminders.size,
        ledgerSeries.size,
        ledgerOccurrenceExceptions.size,
        ledgerEntries.size,
        attachments.size,
        vaultEntries.size,
        noteFolders.size,
        notes.size,
    )
    invalidIf(tables.any { it > BackupLimits.MAX_RECORDS_PER_TABLE }, "A table exceeds the record limit.")
    invalidIf(tables.sumOf(Int::toLong) > BackupLimits.MAX_TOTAL_RECORDS, "Snapshot has too many records.")

    settings.validate()
    invalidIf(
        formatVersion < BackupLimits.NOTES_SNAPSHOT_VERSION &&
            (noteFolders.isNotEmpty() || notes.isNotEmpty()),
        "This snapshot version cannot contain notes.",
    )
    val categoryIds = categories.uniquePositiveIds("category", CategoryEntity::id)
    categories.forEach { category ->
        text(category.name, "Category name")
        timestamp(category.createdAt, "Category creation time")
        invalidIf(category.parentId == category.id, "A category cannot be its own parent.")
        invalidIf(category.parentId != null && category.parentId !in categoryIds, "Category parent is missing.")
    }
    validateCategoryCycles(categories)

    val todoSeriesIds = todoSeries.uniquePositiveIds("todo series", TodoSeriesEntity::id)
    todoSeries.forEach { series ->
        invalidIf(series.categoryId != null && series.categoryId !in categoryIds, "Todo series category is missing.")
        validateMinute(series.startMinute, "Todo series start minute")
        epochDay(series.startEpochDay, "Todo series start date")
        series.endEpochDay?.let { epochDay(it, "Todo series end date") }
        invalidIf(series.intervalCount !in 1..10_000, "Todo recurrence interval is invalid.")
        invalidIf(series.endEpochDay != null && series.endEpochDay < series.startEpochDay, "Todo series end is before its start.")
        listOf(series.title, series.description, series.tagsCsv, series.reminderOffsetsCsv).forEach { text(it, "Todo series text") }
        timestampOrder(series.createdAt, series.updatedAt, "Todo series")
    }
    uniquePairs(todoSeriesSubtasks, "todo series subtask") { it.seriesId to it.sortOrder }
    todoSeriesSubtasks.forEach {
        invalidIf(it.seriesId !in todoSeriesIds, "Todo series subtask owner is missing.")
        invalidIf(it.sortOrder < 0, "Todo series subtask order is invalid.")
        text(it.description, "Todo series subtask")
    }
    uniquePairs(todoOccurrenceExceptions, "todo occurrence exception") { it.seriesId to it.occurrenceEpochDay }
    todoOccurrenceExceptions.forEach { invalidIf(it.seriesId !in todoSeriesIds, "Todo exception series is missing.") }
    todoOccurrenceExceptions.forEach {
        epochDay(it.occurrenceEpochDay, "Todo exception date")
        timestamp(it.createdAt, "Todo exception creation time")
    }

    val todoIds = todos.uniquePositiveIds("todo", TodoEntity::id)
    todos.forEach { todo ->
        invalidIf(todo.categoryId != null && todo.categoryId !in categoryIds, "Todo category is missing.")
        invalidIf(todo.seriesId != null && todo.seriesId !in todoSeriesIds, "Todo series is missing.")
        invalidIf((todo.seriesId == null) != (todo.occurrenceEpochDay == null), "Todo occurrence identity is incomplete.")
        validateMinute(todo.deadlineMinute, "Todo deadline minute")
        todo.occurrenceEpochDay?.let { epochDay(it, "Todo occurrence date") }
        todo.deadlineEpochDay?.let { epochDay(it, "Todo deadline date") }
        invalidIf(todo.deadlineMinute != null && todo.deadlineEpochDay == null, "Todo time has no deadline date.")
        listOf(todo.title, todo.description, todo.tagsCsv).forEach { text(it, "Todo text") }
        timestampOrder(todo.createdAt, todo.updatedAt, "Todo")
        timestamp(todo.customOrder, "Todo custom order")
        todo.completedAt?.let { timestampAfter(it, todo.createdAt, "Todo completion time") }
        todo.deletedAt?.let { timestampAfter(it, todo.createdAt, "Todo deletion time") }
    }
    uniqueNullableTokens(todos.map(TodoEntity::clientOperationToken), "todo operation token")
    uniqueNullablePairs(todos.map { it.seriesId to it.occurrenceEpochDay }, "todo occurrence")

    subtasks.uniquePositiveIds("subtask", SubtaskEntity::id)
    subtasks.forEach {
        invalidIf(it.todoId !in todoIds, "Subtask owner is missing.")
        invalidIf(it.sortOrder < 0, "Subtask order is invalid.")
        text(it.description, "Subtask description")
    }
    todoReminders.uniquePositiveIds("todo reminder", TodoReminderEntity::id)
    uniquePairs(todoReminders, "todo reminder") { it.todoId to it.offsetMinutes }
    todoReminders.forEach {
        invalidIf(it.todoId !in todoIds, "Reminder owner is missing.")
        invalidIf(it.offsetMinutes < 0, "Reminder offset is invalid.")
    }

    val ledgerSeriesIds = ledgerSeries.uniquePositiveIds("ledger series", LedgerSeriesEntity::id)
    ledgerSeries.forEach { series ->
        invalidIf(series.amountCents !in 1L..99_999_999_999L, "Ledger series amount is invalid.")
        invalidIf(series.intervalCount !in 1..10_000, "Ledger recurrence interval is invalid.")
        epochDay(series.startEpochDay, "Ledger series start date")
        series.endEpochDay?.let { epochDay(it, "Ledger series end date") }
        invalidIf(series.endEpochDay != null && series.endEpochDay < series.startEpochDay, "Ledger series end is before its start.")
        listOf(series.note, series.merchant, series.tagsCsv).forEach { text(it, "Ledger series text") }
        timestampOrder(series.createdAt, series.updatedAt, "Ledger series")
    }
    uniqueNullableTokens(ledgerSeries.map(LedgerSeriesEntity::clientOperationToken), "ledger series operation token")
    uniquePairs(ledgerOccurrenceExceptions, "ledger occurrence exception") { it.seriesId to it.occurrenceEpochDay }
    ledgerOccurrenceExceptions.forEach { invalidIf(it.seriesId !in ledgerSeriesIds, "Ledger exception series is missing.") }
    ledgerOccurrenceExceptions.forEach {
        epochDay(it.occurrenceEpochDay, "Ledger exception date")
        timestamp(it.createdAt, "Ledger exception creation time")
    }

    val ledgerIds = ledgerEntries.uniquePositiveIds("ledger entry", LedgerEntryEntity::id)
    ledgerEntries.forEach { entry ->
        invalidIf(entry.seriesId != null && entry.seriesId !in ledgerSeriesIds, "Ledger entry series is missing.")
        invalidIf((entry.seriesId == null) != (entry.occurrenceEpochDay == null), "Ledger occurrence identity is incomplete.")
        invalidIf(entry.amountCents !in 1L..99_999_999_999L, "Ledger amount is invalid.")
        invalidIf(entry.minuteOfDay !in 0..1_439, "Ledger time is invalid.")
        epochDay(entry.epochDay, "Ledger entry date")
        entry.occurrenceEpochDay?.let { epochDay(it, "Ledger occurrence date") }
        listOf(entry.note, entry.merchant, entry.tagsCsv).forEach { text(it, "Ledger text") }
        timestampOrder(entry.createdAt, entry.updatedAt, "Ledger entry")
        entry.deletedAt?.let { timestampAfter(it, entry.createdAt, "Ledger deletion time") }
    }
    uniqueNullableTokens(ledgerEntries.map(LedgerEntryEntity::clientOperationToken), "ledger operation token")
    uniqueNullablePairs(ledgerEntries.map { it.seriesId to it.occurrenceEpochDay }, "ledger occurrence")

    val noteFolderIds = noteFolders.uniquePositiveIds("note folder", NoteFolderEntity::id)
    noteFolders.forEach { folder ->
        text(folder.name, "Note folder name")
        invalidIf(folder.name.isBlank(), "Note folder name is empty.")
        invalidIf(folder.parentId == folder.id, "A note folder cannot be its own parent.")
        invalidIf(
            folder.parentId != null && folder.parentId !in noteFolderIds,
            "Note folder parent is missing.",
        )
        timestamp(folder.createdAt, "Note folder creation time")
    }
    validateNoteFolderCycles(noteFolders)

    val noteIds = notes.uniquePositiveIds("note", NoteEntity::id)
    notes.forEach { note ->
        invalidIf(note.folderId != null && note.folderId !in noteFolderIds, "Note folder is missing.")
        text(note.title, "Note title")
        text(note.body, "Note body")
        invalidIf(note.title.isBlank(), "Note title is empty.")
        timestampOrder(note.createdAt, note.updatedAt, "Note")
    }

    attachments.uniquePositiveIds("attachment", BackupAttachment::id)
    val archivePaths = HashSet<String>()
    val attachmentCounts = HashMap<Pair<AttachmentOwnerType, Long>, Int>()
    attachments.forEach { attachment ->
        invalidIf(!archivePaths.add(attachment.archivePath), "Duplicate attachment archive path.")
        validateArchivePath(attachment.archivePath)
        validateLeafName(attachment.originalName)
        text(attachment.mimeType, "Attachment MIME type")
        invalidIf(attachment.sizeBytes !in 0..BackupLimits.MAX_ATTACHMENT_BYTES, "Attachment size is invalid.")
        invalidIf(attachment.sha256.size != BackupLimits.SHA256_BYTES, "Attachment hash length is invalid.")
        when (attachment.ownerType) {
            AttachmentOwnerType.TODO -> invalidIf(attachment.ownerId !in todoIds, "Attachment todo owner is missing.")
            AttachmentOwnerType.LEDGER -> invalidIf(attachment.ownerId !in ledgerIds, "Attachment ledger owner is missing.")
            AttachmentOwnerType.NOTE -> invalidIf(attachment.ownerId !in noteIds, "Attachment note owner is missing.")
        }
        val owner = attachment.ownerType to attachment.ownerId
        val ownerCount = (attachmentCounts[owner] ?: 0) + 1
        invalidIf(ownerCount > 10, "An item has too many attachments.")
        attachmentCounts[owner] = ownerCount
        timestamp(attachment.createdAt, "Attachment creation time")
        attachment.pendingDeleteAt?.let { timestampAfter(it, attachment.createdAt, "Attachment pending-delete time") }
    }

    val vaultIds = HashSet<String>()
    invalidIf(vaultEntries.size > 10_000, "Vault has too many entries.")
    var vaultEncryptedBytes = 0L
    vaultEntries.forEach { entry ->
        val id = try { UUID.fromString(entry.id).toString() } catch (_: IllegalArgumentException) { null }
        invalidIf(id != entry.id.lowercase(), "Vault id is not a canonical UUID.")
        invalidIf(!vaultIds.add(entry.id), "Duplicate vault id.")
        invalidIf(entry.updatedAt < entry.createdAt, "Vault timestamps are invalid.")
        timestampOrder(entry.createdAt, entry.updatedAt, "Vault entry")
        val fields = listOf(entry.label, entry.account, entry.password, entry.website, entry.notes)
        invalidIf(fields.none(String::isNotBlank), "A vault entry is empty.")
        fields.forEach {
            text(it, "Vault field")
            invalidIf(utf8Size(it) > 64 * 1024, "Vault field is too large.")
        }
        val fieldBytes = fields.sumOf { utf8Size(it).toLong() }
        invalidIf(fieldBytes > 192L * 1024L, "Vault entry is too large.")
        vaultEncryptedBytes += 52L + fieldBytes
        invalidIf(vaultEncryptedBytes > 8L * 1024L * 1024L, "Vault exceeds its storage limit.")
    }
    return this
}

internal fun attachmentArchivePath(id: Long, hash: ByteArray): String =
    "attachments/$id-${hash.joinToString("") { "%02x".format(it) }}.blob"

internal fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

private fun BackupSettings.validate() {
    invalidIf(defaultAllDayReminderMinute !in 0..1_439, "Default reminder time is invalid.")
    invalidIf(defaultReminderOffsetsMinutes.size > 100, "Too many default reminder offsets.")
    invalidIf(defaultReminderOffsetsMinutes.any { it !in 0..5_256_000L }, "Default reminder offset is invalid.")
    invalidIf(todoQuickAddFields.size > TodoQuickAddField.entries.size, "Too many quick-add fields.")
}

private fun validateCategoryCycles(categories: List<CategoryEntity>) {
    val parents = categories.associate { it.id to it.parentId }
    val state = HashMap<Long, Byte>(categories.size)
    categories.forEach { category ->
        if (state[category.id] == 2.toByte()) return@forEach
        val chain = ArrayList<Long>()
        var cursor: Long? = category.id
        while (cursor != null && state[cursor] != 2.toByte()) {
            invalidIf(state[cursor] == 1.toByte(), "Category hierarchy contains a cycle.")
            state[cursor] = 1
            chain += cursor
            cursor = parents[cursor]
        }
        chain.forEach { id -> state[id] = 2 }
    }
}

private fun validateNoteFolderCycles(folders: List<NoteFolderEntity>) {
    val parents = folders.associate { it.id to it.parentId }
    val state = HashMap<Long, Byte>(folders.size)
    folders.forEach { folder ->
        if (state[folder.id] == 2.toByte()) return@forEach
        val chain = ArrayList<Long>()
        var cursor: Long? = folder.id
        while (cursor != null && state[cursor] != 2.toByte()) {
            invalidIf(state[cursor] == 1.toByte(), "Note folder hierarchy contains a cycle.")
            state[cursor] = 1
            chain += cursor
            cursor = parents[cursor]
        }
        chain.forEach { id -> state[id] = 2 }
    }
}

private fun epochDay(value: Long, label: String) {
    invalidIf(value !in BackupLimits.MIN_EPOCH_DAY..BackupLimits.MAX_EPOCH_DAY, "$label is outside the supported range.")
}

private fun timestamp(value: Long, label: String) {
    invalidIf(value !in 0..BackupLimits.MAX_TIMESTAMP_MILLIS, "$label is outside the supported range.")
}

private fun timestampOrder(createdAt: Long, updatedAt: Long, label: String) {
    timestamp(createdAt, "$label creation time")
    timestamp(updatedAt, "$label update time")
    invalidIf(updatedAt < createdAt, "$label update time is before creation.")
}

private fun timestampAfter(value: Long, createdAt: Long, label: String) {
    timestamp(value, label)
    invalidIf(value < createdAt, "$label is before creation.")
}

private fun validateMinute(value: Int?, label: String) {
    invalidIf(value != null && value !in 0..1_439, "$label is invalid.")
}

private fun validateArchivePath(path: String) {
    invalidIf(path.length !in 1..240, "Attachment archive path is invalid.")
    invalidIf(path.contains('\\') || path.startsWith('/') || path.contains(":"), "Attachment archive path is unsafe.")
    val parts = path.split('/')
    invalidIf(parts.any { it.isEmpty() || it == "." || it == ".." }, "Attachment archive path is unsafe.")
    invalidIf(parts.first() != "attachments", "Attachment archive path is outside its directory.")
}

private fun validateLeafName(name: String) {
    invalidIf(name.isBlank() || name.length > 255, "Attachment name is invalid.")
    invalidIf(name == "." || name == ".." || name.contains('/') || name.contains('\\') || name.indexOf('\u0000') >= 0, "Attachment name is unsafe.")
    text(name, "Attachment name")
}

private fun text(value: String, label: String) {
    invalidIf(value.indexOf('\u0000') >= 0, "$label contains a null character.")
    invalidIf(utf8Size(value) > BackupLimits.MAX_TEXT_UTF8_BYTES, "$label is too large.")
}

private fun utf8Size(value: String): Int = value.encodeToByteArray().size

private fun <T> List<T>.uniquePositiveIds(label: String, selector: (T) -> Long): Set<Long> {
    val ids = HashSet<Long>(size)
    forEach { item ->
        val id = selector(item)
        invalidIf(id <= 0, "$label id is invalid.")
        invalidIf(!ids.add(id), "Duplicate $label id.")
    }
    return ids
}

private fun <T, K> uniquePairs(values: List<T>, label: String, selector: (T) -> K) {
    invalidIf(values.mapTo(HashSet(), selector).size != values.size, "Duplicate $label.")
}

private fun uniqueNullableTokens(values: List<String?>, label: String) {
    val present = values.filterNotNull()
    present.forEach { text(it, label) }
    invalidIf(present.toSet().size != present.size, "Duplicate $label.")
}

private fun uniqueNullablePairs(values: List<Pair<Long?, Long?>>, label: String) {
    val present = values.filter { it.first != null && it.second != null }
    invalidIf(present.toSet().size != present.size, "Duplicate $label.")
}

private fun invalidIf(condition: Boolean, message: String) {
    if (condition) throw InvalidBackupException(message)
}

/** A capability produced only after attachment bytes have been written under a checked root. */
class StagedAttachments internal constructor(
    val root: File,
    internal val pathsById: Map<Long, String>,
) {
    fun entities(snapshot: BackupSnapshot): List<AttachmentEntity> {
        val expected = snapshot.attachments.mapTo(HashSet(), BackupAttachment::id)
        if (pathsById.keys != expected) throw InvalidBackupException("Staged attachment set is incomplete.")
        val canonicalRoot = root.canonicalFile
        val seen = HashSet<String>()
        return snapshot.attachments.map { attachment ->
            val file = File(requireNotNull(pathsById[attachment.id])).canonicalFile
            val prefix = canonicalRoot.path + File.separator
            if (!file.path.startsWith(prefix) || !file.isFile || file.length() != attachment.sizeBytes) {
                throw InvalidBackupException("A staged attachment is missing or outside its staging directory.")
            }
            if (!seen.add(file.path)) throw InvalidBackupException("Duplicate staged attachment path.")
            val stagedHash = file.inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
                buffer.fill(0)
                digest.digest()
            }
            if (!MessageDigest.isEqual(stagedHash, attachment.sha256)) {
                throw InvalidBackupException("A staged attachment failed its final hash check.")
            }
            attachment.toEntity(file.absolutePath)
        }
    }
}
