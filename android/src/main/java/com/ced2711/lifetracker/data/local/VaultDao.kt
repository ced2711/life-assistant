package com.ced2711.lifetracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class VaultSaveOutcome {
    SAVED,
    ENTRY_NOT_FOUND,
    ID_CONFLICT,
    ENTRY_LIMIT_REACHED,
    ENCRYPTED_BYTES_LIMIT_REACHED,
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_entries ORDER BY updatedAt DESC, id")
    fun observeEncrypted(): Flow<List<VaultEntryEntity>>

    @Query("SELECT * FROM vault_entries ORDER BY updatedAt DESC, id")
    suspend fun getEncrypted(): List<VaultEntryEntity>

    @Query("SELECT * FROM vault_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VaultEntryEntity?

    @Query("SELECT COUNT(*) FROM vault_entries")
    suspend fun countEntries(): Int

    @Query(
        "SELECT COALESCE(SUM(LENGTH(payloadIv) + LENGTH(payloadCiphertext)), 0) " +
            "FROM vault_entries",
    )
    suspend fun encryptedBytes(): Long

    @Query(
        "SELECT LENGTH(payloadIv) + LENGTH(payloadCiphertext) FROM vault_entries " +
            "WHERE id = :id LIMIT 1",
    )
    suspend fun encryptedBytesForId(id: String): Long?

    @Insert
    suspend fun insert(entry: VaultEntryEntity)

    @Update
    suspend fun update(entry: VaultEntryEntity): Int

    /**
     * Checks aggregate capacity and writes in one database transaction. Replacements subtract the
     * row's previous encrypted size, so an entry can still be edited when the vault is at capacity.
     */
    @Transaction
    suspend fun saveWithinCapacity(
        entry: VaultEntryEntity,
        expectExisting: Boolean,
        maxEntryCount: Int,
        maxEncryptedBytes: Long,
    ): VaultSaveOutcome {
        require(maxEntryCount > 0) { "Vault entry capacity must be positive." }
        require(maxEncryptedBytes > 0L) { "Vault byte capacity must be positive." }

        val previousBytes = encryptedBytesForId(entry.id)
        if (expectExisting && previousBytes == null) return VaultSaveOutcome.ENTRY_NOT_FOUND
        if (!expectExisting && previousBytes != null) return VaultSaveOutcome.ID_CONFLICT

        val currentCount = countEntries()
        if (previousBytes == null && currentCount >= maxEntryCount) {
            return VaultSaveOutcome.ENTRY_LIMIT_REACHED
        }

        val replacementBytes = entry.payloadIv.size.toLong() + entry.payloadCiphertext.size.toLong()
        val projectedBytes = encryptedBytes() - (previousBytes ?: 0L) + replacementBytes
        if (projectedBytes > maxEncryptedBytes) {
            return VaultSaveOutcome.ENCRYPTED_BYTES_LIMIT_REACHED
        }

        return if (previousBytes == null) {
            insert(entry)
            VaultSaveOutcome.SAVED
        } else if (update(entry) == 1) {
            VaultSaveOutcome.SAVED
        } else {
            VaultSaveOutcome.ENTRY_NOT_FOUND
        }
    }

    @Query("DELETE FROM vault_entries WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM vault_entries")
    suspend fun deleteAll()
}
