package com.ced2711.lifetracker.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val temporaryDirectory get() = Files.createTempDirectory("taskledger-backup-test").toFile()

    @Test
    fun encryptedRoundTripConsumesPasswordsAndStreamsAttachmentStage() {
        val snapshot = fullBackupSnapshot()
        val content = fullBackupAttachmentBytes()
        val encryptionPassword = "correct horse battery staple".toCharArray()
        val encrypted = ByteArrayOutputStream().also {
            BackupCrypto.encrypt(snapshot, encryptionPassword, it, fullBackupAttachmentSource(content))
        }.toByteArray()
        assertTrue(encryptionPassword.all { it == '\u0000' })

        val decryptionPassword = "correct horse battery staple".toCharArray()
        val decoded = BackupCrypto.decrypt(
            ByteArrayInputStream(encrypted),
            decryptionPassword,
            temporaryDirectory,
            temporaryDirectory,
        )
        decoded.attachmentStage.use { stage ->
            assertEquals(snapshot, decoded.snapshot)
            assertArrayEquals(content, java.io.File(stage.attachments.entities(decoded.snapshot).single().privatePath).readBytes())
        }
        assertTrue(decryptionPassword.all { it == '\u0000' })
    }

    @Test
    fun wrongPasswordFailsAuthenticationConsumesPasswordAndCreatesNoStage() {
        val encrypted = encrypt()
        val password = "wrong".toCharArray()
        val work = temporaryDirectory
        val stages = temporaryDirectory
        assertThrows(BackupAuthenticationException::class.java) {
            BackupCrypto.decrypt(ByteArrayInputStream(encrypted), password, work, stages)
        }
        assertTrue(password.all { it == '\u0000' })
        assertTrue(stages.listFiles().orEmpty().none { it.name.startsWith("taskledger-restore-") })
    }

    @Test
    fun authenticatedHeaderTamperFailsAuthentication() {
        val encrypted = encrypt()
        encrypted[28] = (encrypted[28].toInt() xor 1).toByte() // salt byte, structurally valid
        assertThrows(BackupAuthenticationException::class.java) {
            BackupCrypto.decrypt(
                ByteArrayInputStream(encrypted),
                "password".toCharArray(),
                temporaryDirectory,
                temporaryDirectory,
            )
        }
    }

    @Test
    fun truncatedCiphertextFailsAuthenticationAndCleansEncryptedSpool() {
        val encrypted = encrypt()
        val work = temporaryDirectory
        assertThrows(BackupAuthenticationException::class.java) {
            BackupCrypto.decrypt(
                ByteArrayInputStream(encrypted.copyOf(encrypted.size - 8)),
                "password".toCharArray(),
                work,
                temporaryDirectory,
            )
        }
        assertTrue(work.listFiles().orEmpty().none { it.name.startsWith("taskledger-encrypted-") })
    }

    @Test
    fun startupCleanupRemovesOnlyCanonicalRecognizedWorkArtifacts() {
        val directory = temporaryDirectory
        java.io.File(directory, "taskledger-encrypted-stale.backup").writeBytes(byteArrayOf(1))
        java.io.File(directory, "taskledger-authenticated-stale.snapshot").writeText("plaintext")
        val unrelated = java.io.File(directory, "keep.backup").apply { writeText("keep") }

        BackupCrypto.cleanupStaleWorkArtifacts(directory)

        assertTrue(unrelated.isFile)
        assertTrue(directory.listFiles().orEmpty().none {
            it.name.startsWith("taskledger-encrypted-") || it.name.startsWith("taskledger-authenticated-")
        })
    }

    @Test
    fun invalidMagicIsRejectedBeforeTheRemainingSourceIsSpooled() {
        val source = CountingByteArrayInputStream(ByteArray(1024 * 1024))

        assertThrows(UnsupportedBackupException::class.java) {
            BackupCrypto.decrypt(
                source,
                "password".toCharArray(),
                temporaryDirectory,
                temporaryDirectory,
            )
        }

        assertEquals(8, source.bytesRead)
    }

    @Test
    fun concurrentDecryptsAreSerializedAndDoNotTouchAnotherActiveSpool() {
        val encrypted = encrypt()
        val firstEnteredPayload = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val directory = temporaryDirectory
        try {
            val first = executor.submit<DecryptedBackup> {
                BackupCrypto.decrypt(
                    HeaderThenBlockingInputStream(encrypted, firstEnteredPayload, releaseFirst),
                    "password".toCharArray(),
                    directory,
                    directory,
                )
            }
            assertTrue(firstEnteredPayload.await(5, TimeUnit.SECONDS))
            val second = executor.submit<DecryptedBackup> {
                BackupCrypto.decrypt(
                    ByteArrayInputStream(encrypted),
                    "password".toCharArray(),
                    directory,
                    directory,
                )
            }
            assertThrows(TimeoutException::class.java) { second.get(100, TimeUnit.MILLISECONDS) }

            releaseFirst.countDown()
            first.get(10, TimeUnit.SECONDS).attachmentStage.close()
            second.get(10, TimeUnit.SECONDS).attachmentStage.close()
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
        assertTrue(directory.listFiles().orEmpty().none {
            it.name.startsWith("taskledger-encrypted-")
        })
    }

    @Test
    fun decryptNeverLeavesPlaintextSnapshot() {
        val directory = temporaryDirectory
        val decoded = BackupCrypto.decrypt(
            ByteArrayInputStream(encrypt()),
            "password".toCharArray(),
            directory,
            temporaryDirectory,
        )
        decoded.attachmentStage.close()
        assertTrue(directory.listFiles().orEmpty().none {
            it.name.startsWith("taskledger-encrypted-") || it.name.startsWith("taskledger-authenticated-")
        })
    }

    @Test
    fun oversizedSnapshotIsRejectedBeforeEncryption() {
        val oversized = fullBackupSnapshot().copy(
            categories = List(BackupLimits.MAX_RECORDS_PER_TABLE + 1) { fullBackupSnapshot().categories.single() },
        )
        assertThrows(InvalidBackupException::class.java) {
            BackupCrypto.encrypt(
                oversized,
                "password".toCharArray(),
                ByteArrayOutputStream(),
                fullBackupAttachmentSource(),
            )
        }
    }

    private fun encrypt(): ByteArray = ByteArrayOutputStream().also {
        BackupCrypto.encrypt(fullBackupSnapshot(), "password".toCharArray(), it, fullBackupAttachmentSource())
    }.toByteArray()

    private class CountingByteArrayInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var bytesRead: Int = 0
            private set

        override fun read(): Int = super.read().also { if (it >= 0) bytesRead++ }

        override fun read(target: ByteArray, offset: Int, length: Int): Int =
            super.read(target, offset, length).also { if (it > 0) bytesRead += it }
    }

    private class HeaderThenBlockingInputStream(
        bytes: ByteArray,
        private val enteredPayload: CountDownLatch,
        private val releasePayload: CountDownLatch,
    ) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        private var consumed = 0
        private var blocked = false

        override fun read(): Int {
            blockAfterHeader()
            return delegate.read().also { if (it >= 0) consumed++ }
        }

        override fun read(target: ByteArray, offset: Int, length: Int): Int {
            blockAfterHeader()
            return delegate.read(target, offset, length).also { if (it > 0) consumed += it }
        }

        private fun blockAfterHeader() {
            if (!blocked && consumed >= 60) {
                blocked = true
                enteredPayload.countDown()
                if (!releasePayload.await(10, TimeUnit.SECONDS)) error("Timed out waiting to release the source.")
            }
        }
    }
}
