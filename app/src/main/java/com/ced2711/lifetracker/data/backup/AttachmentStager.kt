package com.ced2711.lifetracker.data.backup

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Streams authenticated attachment bytes into a fresh private staging directory. Nothing in the
 * live attachment graph is touched, and memory use is independent of the total attachment size.
 */
class AttachmentRestoreStage private constructor(
    val root: File,
) : Closeable, BackupAttachmentSink {
    private val pathsById = LinkedHashMap<Long, String>()
    private var committed = false

    val attachments: StagedAttachments
        get() = StagedAttachments(root, pathsById.toMap())

    override fun write(attachment: BackupAttachment, source: InputStream) {
        check(!committed) { "This restore stage is already committed." }
        if (attachment.id in pathsById) throw InvalidBackupException("Duplicate staged attachment id.")
        if (attachment.sizeBytes !in 0..BackupLimits.MAX_ATTACHMENT_BYTES) {
            throw InvalidBackupException("Attachment size is invalid.")
        }
        val usable = root.usableSpace
        if (usable > 0L && attachment.sizeBytes > (usable - BackupLimits.MIN_FREE_SPACE_BYTES).coerceAtLeast(0L)) {
            throw InvalidBackupException("There is not enough private storage to restore attachments.")
        }

        val file = File(root, "${attachment.id}.bin")
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        try {
            FileOutputStream(file).use { output ->
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    copied = Math.addExact(copied, read.toLong())
                    if (copied > attachment.sizeBytes) {
                        throw InvalidBackupException("Attachment content exceeds its declared size.")
                    }
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                }
                output.fd.sync()
            }
            if (copied != attachment.sizeBytes) {
                throw InvalidBackupException("Attachment content is truncated.")
            }
            if (!MessageDigest.isEqual(digest.digest(), attachment.sha256)) {
                throw InvalidBackupException("A staged attachment failed its hash check.")
            }
            pathsById[attachment.id] = file.absolutePath
        } catch (error: Throwable) {
            file.delete()
            if (error is BackupException) throw error
            throw InvalidBackupException("Could not stage a backup attachment.", error)
        } finally {
            buffer.fill(0)
        }
    }

    /** Keep the staged files after a successful Room commit. */
    fun commit(): StagedAttachments {
        committed = true
        return attachments
    }

    override fun close() {
        if (!committed) root.deleteRecursively()
    }

    companion object {
        fun create(stagingParent: File): AttachmentRestoreStage {
            val parent = stagingParent.canonicalFile
            if (!parent.exists() && !parent.mkdirs()) {
                throw InvalidBackupException("Could not create the backup staging directory.")
            }
            if (!parent.isDirectory) throw InvalidBackupException("Backup staging parent is not a directory.")
            val root = File(parent, "taskledger-restore-${UUID.randomUUID()}")
            if (!root.mkdir()) throw InvalidBackupException("Could not create a private restore stage.")
            return AttachmentRestoreStage(root)
        }
    }
}
