package com.ced2711.lifetracker.data.backup

import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TodoQuickAddField
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.WeekStart
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    @Test
    fun unknownFutureQuickAddFieldsAreIgnored() {
        assertEquals(setOf(TodoQuickAddField.DEADLINE), decodeTodoQuickAddFields(listOf("DEADLINE", "FUTURE_FIELD")))
    }

    @Test
    fun decodeBudgetsRejectRecordTextAndVaultTotalsButAllowLegacyAttachmentTotalsAbove128MiB() {
        val records = DecodeBudget()
        records.addRecords(BackupLimits.MAX_TOTAL_RECORDS)
        assertThrows(InvalidBackupException::class.java) { records.addRecords(1) }

        val ordinaryText = DecodeBudget()
        ordinaryText.addTextUtf8(BackupLimits.MAX_TOTAL_TEXT_UTF8_BYTES)
        assertThrows(InvalidBackupException::class.java) { ordinaryText.addTextUtf8(1) }

        val vault = DecodeBudget()
        vault.addVaultUtf8(BackupLimits.MAX_TOTAL_VAULT_UTF8_BYTES)
        assertThrows(InvalidBackupException::class.java) { vault.addVaultUtf8(1) }

        val attachments = DecodeBudget()
        repeat(6) {
            attachments.addAttachmentBytes(
                BackupLimits.MAX_ATTACHMENT_BYTES.toInt(),
                BackupLimits.MAX_ATTACHMENT_BYTES,
            )
        }
    }

    @Test
    fun exportRejectsAggregateOrdinaryTextAboveDecodeLimit() {
        val sharedMaximumField = "x".repeat(BackupLimits.MAX_TEXT_UTF8_BYTES)
        val categoryCount = BackupLimits.MAX_TOTAL_TEXT_UTF8_BYTES / BackupLimits.MAX_TEXT_UTF8_BYTES + 1
        val snapshot = fullBackupSnapshot().copy(
            categories = (1L..categoryCount.toLong()).map { id ->
                com.ced2711.lifetracker.data.local.CategoryEntity(
                    id = id,
                    name = sharedMaximumField,
                    parentId = null,
                    sortOrder = id,
                    createdAt = id,
                )
            },
        )

        assertThrows(InvalidBackupException::class.java) {
            BackupCodec.write(snapshot, NullOutputStream, fullBackupAttachmentSource())
        }
    }

    @Test
    fun roundTripStreamsAttachmentIntoPrivateStage() {
        val expected = fullBackupSnapshot()
        val content = fullBackupAttachmentBytes()
        val bytes = ByteArrayOutputStream().also {
            BackupCodec.write(expected, it, fullBackupAttachmentSource(content))
        }.toByteArray()
        val parent = Files.createTempDirectory("taskledger-codec-stage").toFile()

        AttachmentRestoreStage.create(parent).use { stage ->
            val actual = BackupCodec.read(ByteArrayInputStream(bytes), stage)

            assertEquals(expected, actual)
            val entity = stage.attachments.entities(actual).single()
            assertArrayEquals(content, java.io.File(entity.privatePath).readBytes())
        }
    }

    @Test
    fun roundTripPreservesAnAttachmentOwnedByANote() {
        val content = fullBackupAttachmentBytes()
        val noteAttachment = fullBackupSnapshot().attachments.single().copy(
            ownerType = com.ced2711.lifetracker.domain.model.AttachmentOwnerType.NOTE,
            ownerId = 90,
            originalName = "长期资料.pdf",
            mimeType = "application/pdf",
        )
        val expected = fullBackupSnapshot().copy(attachments = listOf(noteAttachment))
        val bytes = ByteArrayOutputStream().also {
            BackupCodec.write(expected, it, fullBackupAttachmentSource(content))
        }.toByteArray()
        val parent = Files.createTempDirectory("lifetracker-note-attachment-stage").toFile()

        AttachmentRestoreStage.create(parent).use { stage ->
            val actual = BackupCodec.read(ByteArrayInputStream(bytes), stage)
            assertEquals(expected, actual)
            assertEquals(
                com.ced2711.lifetracker.domain.model.AttachmentOwnerType.NOTE,
                stage.attachments.entities(actual).single().ownerType,
            )
        }
    }

    @Test
    fun legacyV1SnapshotRemainsStreamRestorable() {
        val legacy = fullBackupSnapshot().withoutNotesForLegacy(
            BackupLimits.LEGACY_SNAPSHOT_VERSION,
        )
        val encoded = ByteArrayOutputStream().also {
            BackupCodec.write(legacy, it, fullBackupAttachmentSource())
        }.toByteArray()
        val parent = Files.createTempDirectory("taskledger-codec-legacy").toFile()

        AttachmentRestoreStage.create(parent).use { stage ->
            assertEquals(
                legacy.copy(settings = legacy.settings.copy(accentColor = AccentColor.TEAL)),
                BackupCodec.read(ByteArrayInputStream(encoded), stage),
            )
        }
    }

    @Test
    fun versionTwoSnapshotDefaultsToTheOriginalTealAccent() {
        val versionTwo = fullBackupSnapshot().withoutNotesForLegacy(2)
        val encoded = ByteArrayOutputStream().also {
            BackupCodec.write(versionTwo, it, fullBackupAttachmentSource())
        }.toByteArray()
        val parent = Files.createTempDirectory("taskledger-codec-v2").toFile()

        AttachmentRestoreStage.create(parent).use { stage ->
            assertEquals(
                versionTwo.copy(settings = versionTwo.settings.copy(accentColor = AccentColor.TEAL)),
                BackupCodec.read(ByteArrayInputStream(encoded), stage),
            )
        }
    }

    @Test
    fun frozenLegacyV1FixtureRemainsReadable() {
        val fixture = Base64.getDecoder().decode(
            "VExTMQAAAAEAAAAAAAAAAAAAAAREQVJLAAAABlNVTkRBWQAAAAdIT1VSXzEyAAAADk1PTlRIX0RBWV9ZRUFS" +
                "AAAAAAAAAAAAAAAAAAAAAARUT0RPAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        )
        val expected = BackupSnapshot(
            formatVersion = BackupLimits.LEGACY_SNAPSHOT_VERSION,
            createdAt = 0,
            settings = BackupSettings(
                themeMode = ThemeMode.DARK,
                weekStart = WeekStart.SUNDAY,
                timeFormat = TimeFormatOption.HOUR_12,
                dateFormat = DateFormatOption.MONTH_DAY_YEAR,
                notificationsEnabled = false,
                defaultAllDayReminderMinute = 0,
                defaultReminderOffsetsMinutes = emptySet(),
                todoQuickAddFields = emptySet(),
                lastDestination = TopLevelDestination.TODO,
            ),
            categories = emptyList(),
            todoSeries = emptyList(),
            todoSeriesSubtasks = emptyList(),
            todoOccurrenceExceptions = emptyList(),
            todos = emptyList(),
            subtasks = emptyList(),
            todoReminders = emptyList(),
            ledgerSeries = emptyList(),
            ledgerOccurrenceExceptions = emptyList(),
            ledgerEntries = emptyList(),
            attachments = emptyList(),
            vaultEntries = emptyList(),
        )

        assertEquals(
            expected,
            BackupCodec.read(ByteArrayInputStream(fixture), BackupAttachmentSink { _, _ ->
                throw AssertionError("The frozen fixture does not contain attachments.")
            }),
        )
    }

    @Test
    fun exportRejectsTruncatedAndHashMismatchedAttachmentStreams() {
        val snapshot = fullBackupSnapshot()
        val content = fullBackupAttachmentBytes()

        assertThrows(InvalidBackupException::class.java) {
            BackupCodec.write(
                snapshot,
                ByteArrayOutputStream(),
                fullBackupAttachmentSource(content.copyOf(content.size - 1)),
            )
        }
        assertThrows(InvalidBackupException::class.java) {
            BackupCodec.write(
                snapshot,
                ByteArrayOutputStream(),
                fullBackupAttachmentSource(content.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }),
            )
        }
    }

    @Test
    fun attachmentsAboveLegacy128MiBLimitUseOnlyFixedSizeSourceReads() {
        val size = 22 * 1024 * 1024
        val hash = MessageDigest.getInstance("SHA-256").apply {
            val block = ByteArray(DEFAULT_BUFFER_SIZE) { 0x5a }
            repeat(size / block.size) { update(block) }
        }.digest()
        val template = fullBackupSnapshot().attachments.single()
        val snapshot = fullBackupSnapshot().copy(
            attachments = (1L..6L).map { offset ->
                val id = template.id + offset
                template.copy(
                    id = id,
                    archivePath = attachmentArchivePath(id, hash),
                    sizeBytes = size.toLong(),
                    sha256 = hash,
                )
            },
        )
        val sources = ArrayList<RecordingGeneratedInputStream>()

        BackupCodec.write(snapshot, NullOutputStream, BackupAttachmentSource {
            RecordingGeneratedInputStream(size).also(sources::add)
        })

        assertEquals(6, sources.size)
        assertTrue(sources.all { it.maximumRequested <= DEFAULT_BUFFER_SIZE })
        assertTrue(sources.all { it.bytesRead == size.toLong() })
    }
}

private fun BackupSnapshot.withoutNotesForLegacy(version: Int) = copy(
    formatVersion = version,
    noteFolders = emptyList(),
    notes = emptyList(),
    attachments = attachments.filter { it.ownerType != com.ced2711.lifetracker.domain.model.AttachmentOwnerType.NOTE },
)

private class RecordingGeneratedInputStream(private val size: Int) : InputStream() {
    var bytesRead = 0L
        private set
    var maximumRequested = 0
        private set

    override fun read(): Int = if (bytesRead >= size) -1 else 0x5a.also { bytesRead++ }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        maximumRequested = maxOf(maximumRequested, length)
        if (bytesRead >= size) return -1
        val count = minOf(length.toLong(), size - bytesRead).toInt()
        java.util.Arrays.fill(buffer, offset, offset + count, 0x5a.toByte())
        bytesRead += count
        return count
    }
}

private object NullOutputStream : java.io.OutputStream() {
    override fun write(value: Int) = Unit
    override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
}
