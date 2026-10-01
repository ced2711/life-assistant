package com.ced2711.lifetracker.data.backup

import com.ced2711.lifetracker.data.local.NoteEntity
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BackupMergeFilesTest {
    private val root: File = Files.createTempDirectory("merge-files").toFile()
    private val password = "correct horse battery staple"

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun write(name: String, snapshot: BackupSnapshot, secret: String = password): File =
        File(root, name).apply {
            outputStream().buffered().use { BackupCrypto.encrypt(snapshot, secret.toCharArray(), it, fullBackupAttachmentSource()) }
        }

    private fun read(file: File): Pair<BackupSnapshot, Map<Long, ByteArray>> {
        val decoded = file.inputStream().buffered().use { BackupCrypto.decrypt(it, password.toCharArray(), root, root) }
        val staged = decoded.attachmentStage.commit()
        try {
            return decoded.snapshot to staged.entities(decoded.snapshot).associate { it.id to File(it.privatePath).readBytes() }
        } finally {
            staged.root.deleteRecursively()
        }
    }

    @Test
    fun encryptedVersionsFromTwoDevicesMergeWithTheirAttachments() {
        val base = fullBackupSnapshot()
        val local = base.copy(notes = base.notes + NoteEntity(id = 900, title = "Phone note", body = "a", createdAt = 5_000, updatedAt = 5_000))
        val remote = base.copy(notes = base.notes + NoteEntity(id = 900, title = "PC note", body = "b", createdAt = 6_000, updatedAt = 6_000))
        val destination = File(root, "merged.tlb")

        val conflicts = mergeEncryptedBackups(
            local = write("local.tlb", local),
            base = write("base.tlb", base),
            remotes = listOf(write("remote.tlb", remote)),
            password = password.toCharArray(),
            workDirectory = File(root, "work"),
            destination = destination,
            now = 10_000,
        )

        val (merged, attachments) = read(destination)
        assertEquals(0, conflicts)
        assertEquals(base.notes.map { it.title }.toSet() + setOf("Phone note", "PC note"), merged.notes.map { it.title }.toSet())
        assertEquals(base.attachments.size, merged.attachments.size)
        attachments.values.forEach { assertArrayEquals(fullBackupAttachmentBytes(), it) }
    }

    @Test
    fun aVersionWithAnotherPasswordIsRefused() {
        val base = fullBackupSnapshot()
        try {
            mergeEncryptedBackups(
                local = write("local.tlb", base),
                base = null,
                remotes = listOf(write("remote.tlb", base, secret = "another password entirely")),
                password = password.toCharArray(),
                workDirectory = File(root, "work"),
                destination = File(root, "merged.tlb"),
                now = 10_000,
            )
            fail("Another password must not be merged")
        } catch (expected: BackupAuthenticationException) {
            // The caller asks the user instead.
        }
    }
}
