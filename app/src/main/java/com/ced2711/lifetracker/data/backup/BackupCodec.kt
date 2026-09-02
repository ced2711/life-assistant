package com.ced2711.lifetracker.data.backup

import com.ced2711.lifetracker.data.local.CategoryEntity
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
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.TodoQuickAddField
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.VaultEntry
import com.ced2711.lifetracker.domain.model.WeekStart
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** Deterministic, dependency-free logical snapshot codec. It never serializes Room implementation details. */
object BackupCodec {
    private const val MAGIC = 0x544C5331 // TLS1

    fun write(
        snapshot: BackupSnapshot,
        destination: OutputStream,
        attachmentSource: BackupAttachmentSource,
    ) {
        snapshot.validate()
        val budget = DecodeBudget()
        DataOutputStream(BufferedOutputStream(destination)).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(snapshot.formatVersion)
            out.writeLong(snapshot.createdAt)
            out.settings(snapshot.settings, budget, snapshot.formatVersion)
            out.list(snapshot.categories) { e ->
                writeLong(e.id); string(e.name, budget); nullableLong(e.parentId); writeLong(e.sortOrder); writeLong(e.createdAt)
            }
            out.list(snapshot.todoSeries) { e ->
                writeLong(e.id); string(e.title, budget); string(e.description, budget); nullableLong(e.categoryId)
                writeLong(e.startEpochDay); nullableInt(e.startMinute); enum(e.recurrenceUnit, budget); writeInt(e.intervalCount)
                nullableLong(e.endEpochDay); enum(e.priority, budget); string(e.tagsCsv, budget); string(e.reminderOffsetsCsv, budget)
                writeBoolean(e.active); writeLong(e.createdAt); writeLong(e.updatedAt)
            }
            out.list(snapshot.todoSeriesSubtasks) { e -> writeLong(e.seriesId); writeInt(e.sortOrder); string(e.description, budget) }
            out.list(snapshot.todoOccurrenceExceptions) { e -> writeLong(e.seriesId); writeLong(e.occurrenceEpochDay); writeLong(e.createdAt) }
            out.list(snapshot.todos) { e ->
                writeLong(e.id); nullableLong(e.seriesId); nullableLong(e.occurrenceEpochDay); string(e.title, budget)
                string(e.description, budget); nullableLong(e.categoryId); nullableLong(e.deadlineEpochDay); nullableInt(e.deadlineMinute)
                enum(e.priority, budget); string(e.tagsCsv, budget); nullableLong(e.completedAt); writeLong(e.createdAt); writeLong(e.updatedAt)
                writeLong(e.customOrder); nullableLong(e.deletedAt); nullableString(e.clientOperationToken, budget)
            }
            out.list(snapshot.subtasks) { e ->
                writeLong(e.id); writeLong(e.todoId); string(e.description, budget); writeBoolean(e.isCompleted); writeInt(e.sortOrder)
            }
            out.list(snapshot.todoReminders) { e -> writeLong(e.id); writeLong(e.todoId); writeLong(e.offsetMinutes) }
            out.list(snapshot.ledgerSeries) { e ->
                writeLong(e.id); enum(e.type, budget); writeLong(e.amountCents); writeLong(e.startEpochDay); enum(e.recurrenceUnit, budget)
                writeInt(e.intervalCount); nullableLong(e.endEpochDay); string(e.note, budget); string(e.merchant, budget); string(e.tagsCsv, budget)
                writeBoolean(e.active); writeLong(e.createdAt); writeLong(e.updatedAt); nullableString(e.clientOperationToken, budget)
            }
            out.list(snapshot.ledgerOccurrenceExceptions) { e -> writeLong(e.seriesId); writeLong(e.occurrenceEpochDay); writeLong(e.createdAt) }
            out.list(snapshot.ledgerEntries) { e ->
                writeLong(e.id); nullableLong(e.seriesId); nullableLong(e.occurrenceEpochDay); enum(e.type, budget); writeLong(e.amountCents)
                writeLong(e.epochDay); writeInt(e.minuteOfDay); string(e.note, budget); string(e.merchant, budget); string(e.tagsCsv, budget)
                writeLong(e.createdAt); writeLong(e.updatedAt); nullableLong(e.deletedAt); nullableString(e.clientOperationToken, budget)
            }
            out.list(snapshot.attachments) { e ->
                writeLong(e.id); enum(e.ownerType, budget); writeLong(e.ownerId); string(e.archivePath, budget); string(e.originalName, budget)
                string(e.mimeType, budget); writeLong(e.sizeBytes); bytes(e.sha256, BackupLimits.SHA256_BYTES)
                writeLong(e.createdAt); nullableLong(e.pendingDeleteAt)
                attachment(e, attachmentSource)
            }
            out.list(snapshot.vaultEntries) { e ->
                vaultString(e.id, budget); vaultString(e.label, budget); vaultString(e.account, budget)
                vaultString(e.password, budget); vaultString(e.website, budget); vaultString(e.notes, budget)
                writeLong(e.createdAt); writeLong(e.updatedAt)
            }
            out.flush()
        }
    }

    fun read(source: InputStream, attachmentSink: BackupAttachmentSink): BackupSnapshot {
        val buffered = BufferedInputStream(source)
        val budget = DecodeBudget()
        try {
            DataInputStream(buffered).use { input ->
                if (input.readInt() != MAGIC) throw InvalidBackupException("Unknown snapshot format.")
                val version = input.readInt()
                if (version !in BackupLimits.LEGACY_SNAPSHOT_VERSION..BackupLimits.SNAPSHOT_VERSION) {
                    throw UnsupportedBackupException("Unsupported snapshot version $version.")
                }
                val snapshot = BackupSnapshot(
                    formatVersion = version,
                    createdAt = input.readLong(),
                    settings = input.settings(budget, version),
                    categories = input.list(budget) { CategoryEntity(readLong(), string(budget), nullableLong(), readLong(), readLong()) },
                    todoSeries = input.list(budget) {
                        TodoSeriesEntity(
                            id = readLong(), title = string(budget), description = string(budget), categoryId = nullableLong(),
                            startEpochDay = readLong(), startMinute = nullableInt(), recurrenceUnit = enum(budget), intervalCount = readInt(),
                            endEpochDay = nullableLong(), priority = enum(budget), tagsCsv = string(budget), reminderOffsetsCsv = string(budget),
                            active = readBoolean(), createdAt = readLong(), updatedAt = readLong(),
                        )
                    },
                    todoSeriesSubtasks = input.list(budget) { TodoSeriesSubtaskEntity(readLong(), readInt(), string(budget)) },
                    todoOccurrenceExceptions = input.list(budget) { TodoOccurrenceExceptionEntity(readLong(), readLong(), readLong()) },
                    todos = input.list(budget) {
                        TodoEntity(
                            id = readLong(), seriesId = nullableLong(), occurrenceEpochDay = nullableLong(), title = string(budget),
                            description = string(budget), categoryId = nullableLong(), deadlineEpochDay = nullableLong(), deadlineMinute = nullableInt(),
                            priority = enum(budget), tagsCsv = string(budget), completedAt = nullableLong(), createdAt = readLong(), updatedAt = readLong(),
                            customOrder = readLong(), deletedAt = nullableLong(), clientOperationToken = nullableString(budget),
                        )
                    },
                    subtasks = input.list(budget) { SubtaskEntity(readLong(), readLong(), string(budget), readBoolean(), readInt()) },
                    todoReminders = input.list(budget) { TodoReminderEntity(readLong(), readLong(), readLong()) },
                    ledgerSeries = input.list(budget) {
                        LedgerSeriesEntity(
                            id = readLong(), type = enum(budget), amountCents = readLong(), startEpochDay = readLong(), recurrenceUnit = enum(budget),
                            intervalCount = readInt(), endEpochDay = nullableLong(), note = string(budget), merchant = string(budget), tagsCsv = string(budget),
                            active = readBoolean(), createdAt = readLong(), updatedAt = readLong(), clientOperationToken = nullableString(budget),
                        )
                    },
                    ledgerOccurrenceExceptions = input.list(budget) { LedgerOccurrenceExceptionEntity(readLong(), readLong(), readLong()) },
                    ledgerEntries = input.list(budget) {
                        LedgerEntryEntity(
                            id = readLong(), seriesId = nullableLong(), occurrenceEpochDay = nullableLong(), type = enum(budget), amountCents = readLong(),
                            epochDay = readLong(), minuteOfDay = readInt(), note = string(budget), merchant = string(budget), tagsCsv = string(budget),
                            createdAt = readLong(), updatedAt = readLong(), deletedAt = nullableLong(), clientOperationToken = nullableString(budget),
                        )
                    },
                    attachments = input.list(budget) {
                        val id = readLong()
                        val ownerType = enum<AttachmentOwnerType>(budget)
                        val ownerId = readLong()
                        val archivePath = string(budget)
                        val originalName = string(budget)
                        val mimeType = string(budget)
                        val declaredSize = readLong()
                        val hash = bytes(BackupLimits.SHA256_BYTES)
                        val createdAt = readLong()
                        val pendingDeleteAt = nullableLong()
                        val attachment = BackupAttachment(
                            id, ownerType, ownerId, archivePath, originalName, mimeType, declaredSize,
                            hash, createdAt, pendingDeleteAt,
                        )
                        val contentLength = readInt()
                        budget.addAttachmentBytes(contentLength, declaredSize)
                        val body = ExactLengthInputStream(this, contentLength.toLong())
                        attachmentSink.write(attachment, body)
                        if (body.remaining != 0L) {
                            throw InvalidBackupException("Attachment content was not fully consumed.")
                        }
                        attachment
                    },
                    vaultEntries = input.list(
                        budget = budget,
                        maximum = 10_000,
                        countObserver = budget::setVaultCount,
                    ) {
                        VaultEntry(
                            vaultString(budget), vaultString(budget), vaultString(budget),
                            vaultString(budget), vaultString(budget), vaultString(budget),
                            readLong(), readLong(),
                        )
                    },
                )
                if (input.read() != -1) throw InvalidBackupException("Unexpected trailing snapshot data.")
                return snapshot.validate()
            }
        } catch (error: BackupException) {
            throw error
        } catch (error: EOFException) {
            throw InvalidBackupException("The snapshot is truncated.", error)
        } catch (error: Exception) {
            throw InvalidBackupException("The snapshot could not be decoded.", error)
        }
    }
}

fun interface BackupAttachmentSource {
    fun open(attachment: BackupAttachment): InputStream
}

fun interface BackupAttachmentSink {
    fun write(attachment: BackupAttachment, source: InputStream)
}

private fun DataOutputStream.settings(
    value: BackupSettings,
    budget: DecodeBudget,
    formatVersion: Int,
) {
    enum(value.themeMode, budget); enum(value.weekStart, budget); enum(value.timeFormat, budget); enum(value.dateFormat, budget)
    writeBoolean(value.notificationsEnabled); writeInt(value.defaultAllDayReminderMinute)
    list(value.defaultReminderOffsetsMinutes.sorted()) { writeLong(it) }
    list(TodoQuickAddField.entries.filter(value.todoQuickAddFields::contains)) { enum(it, budget) }
    enum(value.lastDestination, budget)
    if (formatVersion >= BackupLimits.ACCENT_COLOR_SNAPSHOT_VERSION) {
        enum(value.accentColor, budget)
    }
}

private fun DataInputStream.settings(budget: DecodeBudget, formatVersion: Int) = BackupSettings(
    themeMode = enum<ThemeMode>(budget), weekStart = enum<WeekStart>(budget), timeFormat = enum<TimeFormatOption>(budget),
    dateFormat = enum<DateFormatOption>(budget), notificationsEnabled = readBoolean(), defaultAllDayReminderMinute = readInt(),
    defaultReminderOffsetsMinutes = smallList(100) { readLong() }.toSet(),
    // SettingsRepository also ignores names introduced by versions it does not understand.
    todoQuickAddFields = decodeTodoQuickAddFields(smallList(100) { string(budget) }),
    lastDestination = enum<TopLevelDestination>(budget),
    accentColor = if (formatVersion >= BackupLimits.ACCENT_COLOR_SNAPSHOT_VERSION) {
        enum<AccentColor>(budget)
    } else {
        AccentColor.TEAL
    },
)

internal fun decodeTodoQuickAddFields(savedNames: List<String>): Set<TodoQuickAddField> =
    savedNames.mapNotNull { saved -> TodoQuickAddField.entries.firstOrNull { it.name == saved } }.toSet()

private inline fun <T> DataOutputStream.list(values: Collection<T>, writeItem: DataOutputStream.(T) -> Unit) {
    writeInt(values.size)
    values.forEach { writeItem(it) }
}

private inline fun <T> DataInputStream.smallList(maximum: Int, readItem: DataInputStream.() -> T): List<T> {
    val count = readInt()
    if (count !in 0..maximum) throw InvalidBackupException("Invalid snapshot record count.")
    return List(count) { readItem() }
}

private inline fun <T> DataInputStream.list(
    budget: DecodeBudget,
    maximum: Int = BackupLimits.MAX_RECORDS_PER_TABLE,
    countObserver: (Int) -> Unit = {},
    readItem: DataInputStream.() -> T,
): List<T> {
    val count = readInt()
    if (count !in 0..maximum) throw InvalidBackupException("Invalid snapshot record count.")
    budget.addRecords(count)
    countObserver(count)
    return List(count) { readItem() }
}

private fun DataOutputStream.string(value: String, budget: DecodeBudget) {
    val bytes = value.encodeToByteArray()
    try {
        if (bytes.size > BackupLimits.MAX_TEXT_UTF8_BYTES) throw InvalidBackupException("Snapshot text is too large.")
        budget.addTextUtf8(bytes.size)
        writeInt(bytes.size); write(bytes)
    } finally { bytes.fill(0) }
}

private fun DataInputStream.string(budget: DecodeBudget): String {
    val size = readInt()
    if (size !in 0..BackupLimits.MAX_TEXT_UTF8_BYTES) throw InvalidBackupException("Invalid text length.")
    budget.addTextUtf8(size)
    return stringBody(size)
}

private fun DataOutputStream.vaultString(value: String, budget: DecodeBudget) {
    val bytes = value.encodeToByteArray()
    try {
        if (bytes.size > 64 * 1024) throw InvalidBackupException("Snapshot vault text is too large.")
        budget.addVaultUtf8(bytes.size)
        writeInt(bytes.size); write(bytes)
    } finally { bytes.fill(0) }
}

private fun DataInputStream.vaultString(budget: DecodeBudget): String {
    val size = readInt()
    if (size !in 0..64 * 1024) throw InvalidBackupException("Invalid vault text length.")
    budget.addVaultUtf8(size)
    return stringBody(size)
}

private fun DataInputStream.stringBody(size: Int): String {
    val bytes = ByteArray(size)
    return try { readFully(bytes); bytes.decodeToString(throwOnInvalidSequence = true) } finally { bytes.fill(0) }
}

private fun DataOutputStream.nullableString(value: String?, budget: DecodeBudget) {
    writeBoolean(value != null); if (value != null) string(value, budget)
}

private fun DataInputStream.nullableString(budget: DecodeBudget): String? = if (readBoolean()) string(budget) else null
private fun DataOutputStream.nullableLong(value: Long?) { writeBoolean(value != null); if (value != null) writeLong(value) }
private fun DataInputStream.nullableLong(): Long? = if (readBoolean()) readLong() else null
private fun DataOutputStream.nullableInt(value: Int?) { writeBoolean(value != null); if (value != null) writeInt(value) }
private fun DataInputStream.nullableInt(): Int? = if (readBoolean()) readInt() else null
private fun DataOutputStream.enum(value: Enum<*>, budget: DecodeBudget) = string(value.name, budget)

private inline fun <reified T : Enum<T>> DataInputStream.enum(budget: DecodeBudget): T {
    val name = string(budget)
    return enumValues<T>().firstOrNull { it.name == name }
        ?: throw InvalidBackupException("Unknown ${T::class.java.simpleName} value.")
}

private fun DataOutputStream.bytes(value: ByteArray, maximum: Int) {
    if (value.size > maximum) throw InvalidBackupException("Snapshot binary field is too large.")
    writeInt(value.size); write(value)
}

private fun DataOutputStream.attachment(
    attachment: BackupAttachment,
    source: BackupAttachmentSource,
) {
    if (attachment.sizeBytes !in 0..BackupLimits.MAX_ATTACHMENT_BYTES) {
        throw InvalidBackupException("Attachment size is invalid.")
    }
    writeInt(attachment.sizeBytes.toInt())
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    try {
        source.open(attachment).use { input ->
            while (copied < attachment.sizeBytes) {
                val requested = minOf(buffer.size.toLong(), attachment.sizeBytes - copied).toInt()
                val read = input.read(buffer, 0, requested)
                if (read < 0) throw InvalidBackupException("Attachment content is truncated.")
                if (read == 0) continue
                write(buffer, 0, read)
                digest.update(buffer, 0, read)
                copied = Math.addExact(copied, read.toLong())
            }
            if (input.read() != -1) throw InvalidBackupException("Attachment content exceeds its stored size.")
        }
        if (!java.security.MessageDigest.isEqual(digest.digest(), attachment.sha256)) {
            throw InvalidBackupException("Attachment changed while the backup was written.")
        }
    } catch (error: BackupException) {
        throw error
    } catch (error: Exception) {
        throw InvalidBackupException("Attachment content could not be written.", error)
    } finally {
        buffer.fill(0)
    }
}

private fun DataInputStream.bytes(maximum: Int, beforeAllocation: (Int) -> Unit = {}): ByteArray {
    val size = readInt()
    if (size !in 0..maximum) throw InvalidBackupException("Invalid binary field length.")
    beforeAllocation(size)
    return ByteArray(size).also(::readFully)
}

internal class DecodeBudget {
    private var records = 0L
    private var textUtf8Bytes = 0L
    private var vaultUtf8Bytes = 0L
    private var attachmentBytes = 0L

    fun addRecords(count: Int) {
        records += count
        if (records > BackupLimits.MAX_TOTAL_RECORDS) throw InvalidBackupException("Snapshot has too many records.")
    }

    fun addTextUtf8(count: Int) {
        if (count < 0) throw InvalidBackupException("Invalid text length.")
        textUtf8Bytes = addExact(textUtf8Bytes, count, "Snapshot text byte count overflowed.")
        if (textUtf8Bytes > BackupLimits.MAX_TOTAL_TEXT_UTF8_BYTES) {
            throw InvalidBackupException("Snapshot text exceeds its decode budget.")
        }
    }

    fun setVaultCount(count: Int) {
        if (count > 10_000) throw InvalidBackupException("Vault has too many entries.")
    }

    fun addVaultUtf8(count: Int) {
        if (count < 0) throw InvalidBackupException("Invalid vault text length.")
        vaultUtf8Bytes = addExact(vaultUtf8Bytes, count, "Vault text byte count overflowed.")
        if (vaultUtf8Bytes > BackupLimits.MAX_TOTAL_VAULT_UTF8_BYTES) {
            throw InvalidBackupException("Vault exceeds its decode budget.")
        }
    }

    fun addAttachmentBytes(actual: Int, declared: Long) {
        if (actual !in 0..BackupLimits.MAX_ATTACHMENT_BYTES.toInt()) {
            throw InvalidBackupException("Invalid attachment content length.")
        }
        if (declared != actual.toLong()) throw InvalidBackupException("Attachment size does not match its content.")
        attachmentBytes = addExact(attachmentBytes, actual, "Attachment byte count overflowed.")
    }

    private fun addExact(current: Long, count: Int, message: String): Long = try {
        Math.addExact(current, count.toLong())
    } catch (error: ArithmeticException) {
        throw InvalidBackupException(message, error)
    }
}

private class ExactLengthInputStream(
    private val source: InputStream,
    length: Long,
) : InputStream() {
    var remaining: Long = length
        private set

    override fun read(): Int {
        if (remaining == 0L) return -1
        val value = source.read()
        if (value < 0) throw EOFException("Attachment content is truncated.")
        remaining--
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining == 0L) return -1
        val requested = minOf(length.toLong(), remaining).toInt()
        val read = source.read(buffer, offset, requested)
        if (read < 0) throw EOFException("Attachment content is truncated.")
        if (read > 0) remaining -= read.toLong()
        return read
    }
}
