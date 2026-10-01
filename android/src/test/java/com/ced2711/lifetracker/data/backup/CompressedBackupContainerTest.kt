package com.ced2711.lifetracker.data.backup

import com.ced2711.lifetracker.data.local.NoteEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressedBackupContainerTest {
    private fun encrypted(snapshot: BackupSnapshot, version: Int? = null): ByteArray =
        ByteArrayOutputStream().also { output ->
            val password = "correct horse battery".toCharArray()
            if (version == null) {
                BackupCrypto.encrypt(snapshot, password, output, fullBackupAttachmentSource())
            } else {
                BackupCrypto.encrypt(snapshot, password, output, fullBackupAttachmentSource(), version)
            }
        }.toByteArray()

    private fun decrypt(bytes: ByteArray): BackupSnapshot {
        val work = Files.createTempDirectory("compressed-container").toFile()
        return BackupCrypto.decrypt(
            ByteArrayInputStream(bytes),
            "correct horse battery".toCharArray(),
            work,
            work,
        ).let { decrypted ->
            decrypted.attachmentStage.close()
            decrypted.snapshot
        }
    }

    @Test
    fun newBackupsAreCompressedAndRoundTrip() {
        val snapshot = fullBackupSnapshot()
        assertEquals(snapshot, decrypt(encrypted(snapshot)))
    }

    @Test
    fun backupsWrittenBeforeCompressionRemainReadable() {
        val snapshot = fullBackupSnapshot()
        assertEquals(snapshot, decrypt(encrypted(snapshot, version = 1)))
    }

    @Test
    fun textHeavyDataShrinksSubstantially() {
        val paragraph = "Today I walked to the market, bought vegetables and wrote down the prices. "
        val notes = (1L..200L).map { id ->
            NoteEntity(id = id, title = "Note $id", body = paragraph.repeat(20), createdAt = 1, updatedAt = 1)
        }
        val snapshot = fullBackupSnapshot().copy(
            notes = notes,
            attachments = emptyList(),
        )
        val plain = encrypted(snapshot, version = 1).size
        val compressed = encrypted(snapshot).size
        assertTrue("compressed $compressed bytes vs uncompressed $plain bytes", compressed * 5 < plain)
        assertEquals(snapshot, decrypt(encrypted(snapshot)))
    }
}
