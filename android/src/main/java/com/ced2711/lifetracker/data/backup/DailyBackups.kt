package com.ced2711.lifetracker.data.backup

import com.ced2711.lifetracker.data.cloud.CloudSyncSecretStore
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.time.LocalDate
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One daily copy: the data as it was at the end of [day]. */
data class DailyBackup(val day: LocalDate, val file: File, val sizeBytes: Long)

/**
 * Keeps the data of the previous day on this device, once a day, and removes each copy on its
 * third day, so yesterday's and the day before's copies are always there. Copies are encrypted
 * with a random key that only this app on this device can read; they leave Vault out, and
 * restoring one keeps the Vault as it is.
 */
class DailyBackups(
    private val directory: File,
    private val repository: BackupRepository,
    private val keyStore: CloudSyncSecretStore,
) {
    private val mutex = Mutex()

    fun list(): List<DailyBackup> = files().mapNotNull { file ->
        val day = file.day() ?: return@mapNotNull null
        DailyBackup(day, file, file.length())
    }.sortedByDescending { it.day }

    /** Creates yesterday's copy unless it exists, and removes copies older than the day before. */
    suspend fun backUpIfDue(today: LocalDate): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            val target = File(directory, "${today.minusDays(1)}.tlb")
            var created = false
            if (!target.isFile) {
                val key = key() ?: return@withContext false
                val temporary = File(directory, "${target.name}.part")
                try {
                    temporary.outputStream().buffered().use { output ->
                        repository.export(output, key, includeVault = false)
                    }
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    created = true
                } finally {
                    temporary.delete()
                }
            }
            val oldest = today.minusDays(KEPT_DAYS.toLong())
            files().forEach { file -> if (file.day()?.isBefore(oldest) == true) file.delete() }
            created
        }
    }

    /**
     * Replaces the data with [backup], keeping the Vault. The current data is saved first as
     * [beforeRestoreFile], so the restore can be undone with [undoRestore].
     */
    suspend fun restore(backup: DailyBackup): BackupRestoreResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val key = key() ?: throw InvalidBackupException("The key of the daily backups cannot be read on this device.")
            directory.mkdirs()
            val temporary = File(directory, "${beforeRestoreFile.name}.part")
            try {
                temporary.outputStream().buffered().use { output -> repository.export(output, key.copyOf(), includeVault = false) }
                Files.move(temporary.toPath(), beforeRestoreFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
            restoreFile(backup.file, key)
        }
    }

    /** Puts back the data from right before the last restore. */
    suspend fun undoRestore(): BackupRestoreResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val key = key() ?: throw InvalidBackupException("The key of the daily backups cannot be read on this device.")
            if (!beforeRestoreFile.isFile) throw InvalidBackupException("There is no restore to undo.")
            restoreFile(beforeRestoreFile, key)
        }
    }

    val beforeRestoreFile: File get() = File(directory, BEFORE_RESTORE)

    private suspend fun restoreFile(file: File, key: CharArray): BackupRestoreResult {
        val prepared = file.inputStream().buffered().use { input -> repository.prepareRestore(input, key) }
        return prepared.use { repository.restore(it, keepVault = true) }
    }

    /** The random key, created on first use; null while the Keystore cannot read it. */
    private fun key(): CharArray? {
        keyStore.load()?.let { return it }
        if (keyStore.hasSecret()) return null
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val generated = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).toCharArray()
        bytes.fill(0)
        keyStore.save(generated.copyOf())
        return generated
    }

    private fun files(): List<File> = directory.listFiles().orEmpty().filter { it.isFile && it.day() != null }

    private fun File.day(): LocalDate? = name.takeIf { it.matches(NAME) }
        ?.let { runCatching { LocalDate.parse(it.removeSuffix(".tlb")) }.getOrNull() }

    companion object {
        const val DIRECTORY = "daily-backups"
        /** Yesterday's and the day before's copies; a copy is removed on its third day. */
        const val KEPT_DAYS = 2
        private const val BEFORE_RESTORE = "before-restore.tlb"
        private val NAME = Regex("""\d{4}-\d{2}-\d{2}\.tlb""")
    }
}
