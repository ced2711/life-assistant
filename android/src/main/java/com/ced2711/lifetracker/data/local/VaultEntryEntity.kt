package com.ced2711.lifetracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Only non-sensitive bookkeeping is stored in clear text. The label, account, password, website,
 * and notes are serialized together into [payloadCiphertext].
 */
@Entity(
    tableName = "vault_entries",
    indices = [
        Index(value = ["payloadIv"], unique = true),
        Index("updatedAt"),
    ],
)
data class VaultEntryEntity(
    @PrimaryKey val id: String,
    val formatVersion: Int,
    val payloadIv: ByteArray,
    val payloadCiphertext: ByteArray,
    val createdAt: Long,
    val updatedAt: Long,
)
