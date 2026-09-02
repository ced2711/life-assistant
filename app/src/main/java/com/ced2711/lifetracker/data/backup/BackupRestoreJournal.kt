package com.ced2711.lifetracker.data.backup

import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TodoQuickAddField
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.WeekStart
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal data class RestoreJournalRecord(
    val restoreToken: String,
    val oldSettings: AppSettings,
    val newSettings: AppSettings,
    val stageRoot: String,
    val oldAttachmentPaths: List<String>,
)

internal enum class LegacyRestoreJournalPhase { PREPARED, ROOM_COMMITTED }

internal data class LegacyRestoreJournalRecord(
    val phase: LegacyRestoreJournalPhase,
    val expectedDatabaseFingerprint: String,
    val oldSettings: AppSettings,
    val newSettings: AppSettings,
    val stageRoot: String,
    val oldAttachmentPaths: List<String>,
)

internal sealed interface RestoreJournalReadResult {
    data object None : RestoreJournalReadResult
    data class Current(val record: RestoreJournalRecord) : RestoreJournalReadResult
    data class Legacy(val record: LegacyRestoreJournalRecord) : RestoreJournalReadResult
    data object Quarantined : RestoreJournalReadResult
}

/** A small durable journal containing coordination metadata only, never backed-up business data. */
internal class BackupRestoreJournal(
    private val journalFile: File,
    private val filesRoot: File,
    private val attachmentRoot: File,
) {
    fun read(): RestoreJournalRecord? {
        return when (val result = readPersisted()) {
            RestoreJournalReadResult.None -> null
            is RestoreJournalReadResult.Current -> result.record
            is RestoreJournalReadResult.Legacy -> throw InvalidBackupException(
                "A legacy pending restore must be recovered before starting another restore.",
            )
            RestoreJournalReadResult.Quarantined -> error("Strict journal reads do not quarantine.")
        }
    }

    /** Reads startup recovery metadata, quarantining unreadable bytes instead of wedging startup. */
    fun readForRecovery(): RestoreJournalReadResult = try {
        readPersisted()
    } catch (_: BackupException) {
        quarantine()
        RestoreJournalReadResult.Quarantined
    } catch (_: Exception) {
        quarantine()
        RestoreJournalReadResult.Quarantined
    }

    private fun readPersisted(): RestoreJournalReadResult {
        if (!journalFile.exists()) return RestoreJournalReadResult.None
        validateJournalLocation()
        if (journalFile.length() !in 1L..MAX_JOURNAL_BYTES) {
            throw InvalidBackupException("The pending restore journal size is invalid.")
        }
        try {
            DataInputStream(BufferedInputStream(FileInputStream(journalFile))).use { input ->
                if (input.readInt() != MAGIC) {
                    throw InvalidBackupException("The pending restore journal is unsupported.")
                }
                return when (val version = input.readInt()) {
                    VERSION -> RestoreJournalReadResult.Current(input.readCurrentRecord(hasAccentColor = true))
                    PREVIOUS_VERSION -> RestoreJournalReadResult.Current(
                        input.readCurrentRecord(hasAccentColor = false),
                    )
                    LEGACY_VERSION -> RestoreJournalReadResult.Legacy(input.readLegacyRecord())
                    else -> throw InvalidBackupException(
                        "The pending restore journal version $version is unsupported.",
                    )
                }
            }
        } catch (error: BackupException) {
            throw error
        } catch (error: EOFException) {
            throw InvalidBackupException("The pending restore journal is truncated.", error)
        } catch (error: Exception) {
            throw InvalidBackupException("The pending restore journal could not be read.", error)
        }
    }

    private fun DataInputStream.readCurrentRecord(hasAccentColor: Boolean): RestoreJournalRecord {
        val restoreToken = readText().also(::validateRestoreToken)
        val oldSettings = readSettings(hasAccentColor)
        val newSettings = readSettings(hasAccentColor)
        val stageRoot = readText()
        val oldPaths = readPaths()
        requireEndOfFile()
        validateRestorePaths(stageRoot, oldPaths)
        return RestoreJournalRecord(restoreToken, oldSettings, newSettings, stageRoot, oldPaths)
    }

    private fun DataInputStream.readLegacyRecord(): LegacyRestoreJournalRecord {
        val phase = enumValue<LegacyRestoreJournalPhase>(readText())
        val fingerprint = readText()
        if (!LOWER_SHA_256.matches(fingerprint)) {
            throw InvalidBackupException("The pending restore fingerprint is invalid.")
        }
        val oldSettings = readSettings(hasAccentColor = false)
        val newSettings = readSettings(hasAccentColor = false)
        val stageRoot = readText()
        val oldPaths = readPaths()
        requireEndOfFile()
        validateRestorePaths(stageRoot, oldPaths)
        return LegacyRestoreJournalRecord(
            phase,
            fingerprint,
            oldSettings,
            newSettings,
            stageRoot,
            oldPaths,
        )
    }

    private fun DataInputStream.readPaths(): List<String> {
        val pathCount = readInt()
        if (pathCount !in 0..BackupLimits.MAX_RECORDS_PER_TABLE) {
            throw InvalidBackupException("The pending restore path count is invalid.")
        }
        return List(pathCount) { readText() }
    }

    private fun DataInputStream.requireEndOfFile() {
        if (read() != -1) throw InvalidBackupException("The pending restore journal has trailing data.")
    }

    fun write(record: RestoreJournalRecord) {
        validateJournalLocation()
        validateRestorePaths(record.stageRoot, record.oldAttachmentPaths)
        validateRestoreToken(record.restoreToken)
        val parent = requireNotNull(journalFile.parentFile)
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw InvalidBackupException("Could not create the restore journal directory.")
        }
        val temporary = File(parent, ".${journalFile.name}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { fileOutput ->
                DataOutputStream(BufferedOutputStream(fileOutput)).use { output ->
                    output.writeInt(MAGIC)
                    output.writeInt(VERSION)
                    output.writeText(record.restoreToken)
                    output.writeSettings(record.oldSettings)
                    output.writeSettings(record.newSettings)
                    output.writeText(record.stageRoot)
                    output.writeInt(record.oldAttachmentPaths.size)
                    record.oldAttachmentPaths.forEach { path -> output.writeText(path) }
                    output.flush()
                    fileOutput.fd.sync()
                }
            }
            if (temporary.length() !in 1L..MAX_JOURNAL_BYTES) {
                throw InvalidBackupException("The restore journal exceeds its size limit.")
            }
            Files.move(
                temporary.toPath(),
                journalFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: Exception) {
            temporary.delete()
            throw InvalidBackupException("Could not durably write the restore journal.", error)
        }
    }

    fun clear() {
        validateJournalLocation()
        if (journalFile.exists() && !journalFile.delete()) {
            throw InvalidBackupException("Could not clear the pending restore journal.")
        }
    }

    private fun quarantine() {
        validateJournalLocation()
        if (!journalFile.exists()) return
        val parent = requireNotNull(journalFile.parentFile).canonicalFile
        val quarantineFile = File(parent, QUARANTINE_FILE_NAME)
        try {
            Files.move(
                journalFile.toPath(),
                quarantineFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: Exception) {
            throw InvalidBackupException("Could not quarantine unreadable restore recovery metadata.", error)
        }
    }

    private fun validateJournalLocation() {
        val root = filesRoot.canonicalFile
        val file = journalFile.canonicalFile
        if (file.parentFile != root) throw InvalidBackupException("Restore journal path is outside private storage.")
    }

    private fun validateRestorePaths(stagePath: String, oldPaths: List<String>) {
        val root = attachmentRoot.canonicalFile
        val stage = File(stagePath).canonicalFile
        if (
            stage.parentFile != root ||
            !stage.name.startsWith(RESTORE_DIRECTORY_PREFIX)
        ) {
            throw InvalidBackupException("Restore stage path is outside private attachment storage.")
        }
        val prefix = root.path + File.separator
        if (oldPaths.any { !File(it).canonicalFile.path.startsWith(prefix) }) {
            throw InvalidBackupException("An old attachment path is outside private attachment storage.")
        }
    }

    private companion object {
        const val MAGIC = 0x544C524A // TLRJ
        const val VERSION = 3
        const val PREVIOUS_VERSION = 2
        const val LEGACY_VERSION = 1
        const val MAX_TEXT_BYTES = 16 * 1024
        const val MAX_JOURNAL_BYTES = 32L * 1024L * 1024L
        const val RESTORE_DIRECTORY_PREFIX = "taskledger-restore-"
        const val QUARANTINE_FILE_NAME = "taskledger-restore.journal.quarantined"
        val LOWER_SHA_256 = Regex("[0-9a-f]{64}")

        fun DataOutputStream.writeText(value: String) {
            val bytes = value.encodeToByteArray()
            if (bytes.size > MAX_TEXT_BYTES) throw InvalidBackupException("Restore journal text is too long.")
            writeInt(bytes.size)
            write(bytes)
        }

        fun DataInputStream.readText(): String {
            val size = readInt()
            if (size !in 0..MAX_TEXT_BYTES) throw InvalidBackupException("Restore journal text length is invalid.")
            return ByteArray(size).also(::readFully).decodeToString(throwOnInvalidSequence = true)
        }

        fun DataOutputStream.writeSettings(value: AppSettings) {
            writeText(value.themeMode.name)
            writeText(value.weekStart.name)
            writeText(value.timeFormat.name)
            writeText(value.dateFormat.name)
            writeBoolean(value.notificationsEnabled)
            writeInt(value.defaultAllDayReminderMinute)
            writeInt(value.defaultReminderOffsetsMinutes.size)
            value.defaultReminderOffsetsMinutes.sorted().forEach(::writeLong)
            val quickFields = TodoQuickAddField.entries.filter(value.todoQuickAddFields::contains)
            writeInt(quickFields.size)
            quickFields.forEach { writeText(it.name) }
            writeText(value.lastDestination.name)
            writeText(value.accentColor.name)
        }

        fun DataInputStream.readSettings(hasAccentColor: Boolean): AppSettings {
            val theme = enumValue<ThemeMode>(readText())
            val week = enumValue<WeekStart>(readText())
            val time = enumValue<TimeFormatOption>(readText())
            val date = enumValue<DateFormatOption>(readText())
            val notifications = readBoolean()
            val reminderMinute = readInt()
            if (reminderMinute !in 0..1_439) {
                throw InvalidBackupException("Journal reminder time is invalid.")
            }
            val offsetCount = readInt()
            if (offsetCount !in 0..100) throw InvalidBackupException("Journal reminder count is invalid.")
            val offsets = List(offsetCount) { readLong() }.toSet()
            if (offsets.any { it !in 0..5_256_000L }) {
                throw InvalidBackupException("Journal reminder offset is invalid.")
            }
            val quickCount = readInt()
            if (quickCount !in 0..TodoQuickAddField.entries.size) {
                throw InvalidBackupException("Journal quick-add field count is invalid.")
            }
            val quick = List(quickCount) { enumValue<TodoQuickAddField>(readText()) }.toSet()
            val lastDestination = enumValue<TopLevelDestination>(readText())
            val accentColor = if (hasAccentColor) {
                enumValue<AccentColor>(readText())
            } else {
                AccentColor.TEAL
            }
            return AppSettings(
                themeMode = theme,
                accentColor = accentColor,
                weekStart = week,
                timeFormat = time,
                dateFormat = date,
                notificationsEnabled = notifications,
                defaultAllDayReminderMinute = reminderMinute,
                defaultReminderOffsetsMinutes = offsets,
                todoQuickAddFields = quick,
                lastDestination = lastDestination,
            )
        }

        inline fun <reified T : Enum<T>> enumValue(name: String): T =
            enumValues<T>().firstOrNull { it.name == name }
                ?: throw InvalidBackupException("Restore journal contains an unknown value.")

        fun validateRestoreToken(value: String) {
            val parsed = runCatching { UUID.fromString(value) }.getOrNull()
            if (parsed == null || parsed.toString() != value) {
                throw InvalidBackupException("The pending restore token is invalid.")
            }
        }
    }
}
