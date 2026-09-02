package com.ced2711.lifetracker.data.backup

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `settings mapping covers every persisted field`() {
        val settings = fullBackupSnapshot().settings

        assertEquals(settings, settings.toAppSettings().toBackupSettings())
    }

    @Test
    fun `preview reports logical record and attachment totals`() {
        val snapshot = fullBackupSnapshot()

        assertEquals(
            BackupPreview(
                createdAt = 9_000,
                todoCount = 1,
                ledgerCount = 1,
                vaultCount = 1,
                attachmentCount = 1,
                attachmentBytes = snapshot.attachments.single().sizeBytes,
                totalBytes = 0,
                noteCount = 1,
            ),
            buildBackupPreview(snapshot),
        )
    }

    @Test
    fun `password must contain at least eight characters`() {
        validateBackupPassword("abcdefgh".toCharArray())

        assertThrows(InvalidBackupException::class.java) {
            validateBackupPassword("1234567".toCharArray())
        }
    }

    @Test
    fun `managed attachment plan accepts nested private file and hashes bytes`() {
        val root = temporaryFolder.newFolder("attachments")
        val nested = File(root, "restore-batch").apply { mkdir() }
        val bytes = "任意 attachment".encodeToByteArray()
        val file = File(nested, "1.bin").apply { writeBytes(bytes) }
        val row = fullBackupSnapshot().attachments.single().toEntity(file.absolutePath)
            .copy(sizeBytes = bytes.size.toLong())

        val result = planManagedAttachments(root, listOf(row))

        assertEquals(file.canonicalFile, result.single().file)
        assertTrue(result.single().metadata.sha256.contentEquals(sha256(bytes)))
    }

    @Test
    fun `managed attachment read rejects a path outside private storage`() {
        val root = temporaryFolder.newFolder("managed")
        val outside = File(temporaryFolder.root, "outside.bin").apply { writeText("secret") }
        val row = fullBackupSnapshot().attachments.single().toEntity(outside.absolutePath)
            .copy(sizeBytes = outside.length())

        assertThrows(InvalidBackupException::class.java) {
            planManagedAttachments(root, listOf(row))
        }
    }

    @Test
    fun `post-commit cleanup deletes only obsolete managed files`() {
        val root = temporaryFolder.newFolder("cleanup")
        val obsolete = File(root, "obsolete.bin").apply { writeText("old") }
        val keep = File(root, "keep.bin").apply { writeText("new") }
        val outside = File(temporaryFolder.root, "unmanaged.bin").apply { writeText("outside") }
        val template = fullBackupSnapshot().attachments.single()
        val oldRows = listOf(
            template.toEntity(obsolete.absolutePath),
            template.toEntity(keep.absolutePath).copy(id = 71),
            template.toEntity(outside.absolutePath).copy(id = 72),
        )

        val warnings = deleteObsoleteAttachmentFiles(
            attachmentDirectory = root,
            oldAttachments = oldRows,
            keepPaths = setOf(keep.absolutePath),
        )

        assertTrue(warnings.isEmpty())
        assertFalse(obsolete.exists())
        assertTrue(keep.exists())
        assertTrue(outside.exists())
    }

    @Test
    fun `discarding prepared restore removes its private stage`() {
        val snapshot = fullBackupSnapshot()
        val stage = fullStage(snapshot)
        val stageRoot = stage.root
        val prepared = PreparedBackupRestore(snapshot, stage, buildBackupPreview(snapshot), true)

        prepared.close()

        assertFalse(stageRoot.exists())
        assertThrows(IllegalStateException::class.java) { prepared.requireSnapshot() }
        assertThrows(IllegalStateException::class.java) { prepared.beginCommit() }
        assertEquals(PreparedRestoreLifecycle.CLOSED, prepared.lifecycleForTest())
    }

    @Test
    fun `close cannot remove stage during commit and failure safely returns to ready`() {
        val snapshot = fullBackupSnapshot()
        val stage = fullStage(snapshot)
        val stageRoot = stage.root
        val prepared = PreparedBackupRestore(snapshot, stage, buildBackupPreview(snapshot), false)

        prepared.beginCommit()
        assertThrows(IllegalStateException::class.java) { prepared.close() }
        assertTrue(stageRoot.exists())

        prepared.releaseAfterFailure()
        assertEquals(PreparedRestoreLifecycle.READY, prepared.lifecycleForTest())
        prepared.close()
        assertFalse(stageRoot.exists())
    }

    @Test
    fun `successful commit consumes prepared restore and retains committed stage`() {
        val snapshot = fullBackupSnapshot()
        val stage = fullStage(snapshot)
        val stageRoot = stage.root
        val prepared = PreparedBackupRestore(snapshot, stage, buildBackupPreview(snapshot), false)

        prepared.beginCommit()
        prepared.completeCommit()

        assertEquals(PreparedRestoreLifecycle.CONSUMED, prepared.lifecycleForTest())
        assertTrue(stageRoot.exists())
        assertThrows(IllegalStateException::class.java) { prepared.beginCommit() }
    }

    @Test
    fun `stable capture retries the whole attempt up to three times`() = runBlocking {
        val attempts = mutableListOf<Int>()

        val value = retryStableCapture(3) { index ->
            attempts += index
            if (index == 2) "stable" else null
        }

        assertEquals("stable", value)
        assertEquals(listOf(0, 1, 2), attempts)
        assertNull(retryStableCapture<String>(3) { null })
    }

    private fun fullStage(snapshot: BackupSnapshot): AttachmentRestoreStage {
        val parent = temporaryFolder.newFolder()
        return AttachmentRestoreStage.create(parent).also { stage ->
            stage.write(snapshot.attachments.single(), java.io.ByteArrayInputStream(fullBackupAttachmentBytes()))
        }
    }
}
