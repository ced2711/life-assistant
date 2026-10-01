package com.ced2711.lifetracker.data.backup

import java.io.File

/**
 * Decrypts [local], [base] and each of [remotes] with [password], merges every remote into the
 * local data with [mergeSnapshots], and writes the result, encrypted with the same password, to
 * [destination]. Attachment bytes are copied from whichever file holds them.
 *
 * Returns how many texts were edited on both sides (both versions are kept). Throws
 * [BackupAuthenticationException] when a file uses another password and
 * [InvalidBackupException] when the merge cannot produce valid data; the caller then asks the user.
 * A [base] that cannot be read is treated as unknown, which keeps everything from both sides.
 */
fun mergeEncryptedBackups(
    local: File,
    base: File?,
    remotes: List<File>,
    password: CharArray,
    workDirectory: File,
    destination: File,
    now: Long,
): Int {
    val stages = ArrayList<StagedAttachments>()
    fun open(file: File): Pair<BackupSnapshot, Map<Long, File>> {
        val decoded = file.inputStream().buffered().use {
            BackupCrypto.decrypt(it, password.copyOf(), workDirectory, workDirectory)
        }
        val staged = decoded.attachmentStage.commit().also(stages::add)
        val files = staged.entities(decoded.snapshot).associate { it.id to File(it.privatePath) }
        return decoded.snapshot to files
    }
    try {
        workDirectory.mkdirs()
        val baseOpened = base?.let { file -> runCatching { open(file) }.getOrNull() }
        var (merged, files) = open(local)
        var textConflicts = 0
        remotes.forEach { remoteFile ->
            val (remote, remoteFiles) = open(remoteFile)
            val result = mergeSnapshots(baseOpened?.first, merged, remote, now)
            val previousFiles = files
            files = result.attachmentSources.mapValues { (id, source) ->
                when (source.side) {
                    MergeSide.LOCAL -> previousFiles[source.originalId]
                    MergeSide.REMOTE -> remoteFiles[source.originalId]
                    MergeSide.BASE -> baseOpened?.second?.get(source.originalId)
                } ?: throw InvalidBackupException("Attachment $id is missing after merging.")
            }
            merged = result.snapshot
            textConflicts += result.textConflicts
        }
        try {
            destination.outputStream().buffered().use { output ->
                BackupCrypto.encrypt(
                    merged,
                    password.copyOf(),
                    output,
                    BackupAttachmentSource { attachment ->
                        files[attachment.id]?.inputStream()
                            ?: throw InvalidBackupException("Attachment ${attachment.id} is missing after merging.")
                    },
                )
            }
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
        return textConflicts
    } finally {
        stages.forEach { it.root.deleteRecursively() }
    }
}
