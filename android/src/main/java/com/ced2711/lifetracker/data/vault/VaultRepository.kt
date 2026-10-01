package com.ced2711.lifetracker.data.vault

import android.database.sqlite.SQLiteConstraintException
import com.ced2711.lifetracker.data.monotonicMutationTimestamp
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import com.ced2711.lifetracker.data.local.VaultDao
import com.ced2711.lifetracker.data.local.VaultEntryEntity
import com.ced2711.lifetracker.data.local.VaultSaveOutcome
import com.ced2711.lifetracker.domain.model.VaultEntry
import com.ced2711.lifetracker.domain.model.VaultEntryDraft
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed class VaultDataException(message: String, cause: Throwable? = null) : Exception(message, cause)
class VaultCorruptEntryException(id: String, cause: Throwable? = null) :
    VaultDataException("Vault entry '$id' is corrupt or was encrypted for another record.", cause)
class VaultEntryNotFoundException(id: String) :
    VaultDataException("Vault entry '$id' no longer exists.")
class VaultStorageException(message: String, cause: Throwable? = null) :
    VaultDataException(message, cause)
class VaultPayloadTooLargeException(message: String) : VaultDataException(message)
class VaultCapacityExceededException(message: String) : VaultDataException(message)

class VaultRepository(
    private val dao: VaultDao,
    private val keyManager: VaultKeyManager,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cryptoDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutationMutex = Mutex()

    constructor(
        database: TaskLedgerDatabase,
        keyManager: VaultKeyManager,
    ) : this(database.vaultDao(), keyManager)

    /** Safe to observe while locked: the returned objects contain ciphertext only. */
    fun observeEncrypted(): Flow<List<VaultEntryEntity>> =
        dao.observeEncrypted().flowOn(ioDispatcher)

    /** Decryption happens only while collecting with the caller-owned in-memory session. */
    fun observeEntries(session: VaultSession): Flow<List<VaultEntry>> =
        dao.observeEncrypted()
            .map { rows ->
                withContext(cryptoDispatcher) { rows.map { decrypt(it, session) } }
            }
            .flowOn(ioDispatcher)

    suspend fun loadEntries(session: VaultSession): List<VaultEntry> {
        val rows = withContext(ioDispatcher) { dao.getEncrypted() }
        return loadEntries(rows, session)
    }

    /** Decrypts an already transactionally captured version without silently re-querying it. */
    internal suspend fun loadEntries(
        rows: List<VaultEntryEntity>,
        session: VaultSession,
    ): List<VaultEntry> = withContext(cryptoDispatcher) { rows.map { decrypt(it, session) } }

    /** Fails if this session cannot decrypt every row, without retaining a plaintext result list. */
    internal suspend fun verifySession(
        rows: List<VaultEntryEntity>,
        session: VaultSession,
    ) = withContext(cryptoDispatcher) { rows.forEach { decrypt(it, session) } }

    suspend fun save(draft: VaultEntryDraft, session: VaultSession): VaultEntry =
        withContext(ioDispatcher) {
            withContext(cryptoDispatcher) { validateVaultDraft(draft) }
            mutationMutex.withLock {
                val id = draft.id?.also(::requireCanonicalUuid) ?: UUID.randomUUID().toString()
                val expectExisting = draft.id != null
                val existing = if (expectExisting) dao.getById(id) else null
                if (expectExisting && existing == null) throw VaultEntryNotFoundException(id)
                val timestamp = now()
                val createdAt = existing?.createdAt ?: timestamp
                val updatedAt = existing?.let { row ->
                    monotonicMutationTimestamp(timestamp, row.createdAt, row.updatedAt)
                } ?: timestamp
                val entry = VaultEntry(
                    id = id,
                    label = draft.label,
                    account = draft.account,
                    password = draft.password,
                    website = draft.website,
                    notes = draft.notes,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                )

                repeat(MAX_IV_ATTEMPTS) { attempt ->
                    val encrypted = withContext(cryptoDispatcher) {
                        VaultEntryCipher.encrypt(entry, session)
                    }
                    try {
                        when (
                            dao.saveWithinCapacity(
                                entry = encrypted,
                                expectExisting = expectExisting,
                                maxEntryCount = VaultStorageLimits.MAX_ENTRY_COUNT,
                                maxEncryptedBytes = VaultStorageLimits.MAX_ENCRYPTED_BYTES,
                            )
                        ) {
                            VaultSaveOutcome.SAVED -> return@withLock entry
                            VaultSaveOutcome.ENTRY_NOT_FOUND ->
                                throw VaultEntryNotFoundException(id)
                            VaultSaveOutcome.ID_CONFLICT -> throw VaultStorageException(
                                "Could not allocate a unique vault entry id.",
                            )
                            VaultSaveOutcome.ENTRY_LIMIT_REACHED ->
                                throw VaultCapacityExceededException(
                                    "The vault has reached its " +
                                        "${VaultStorageLimits.MAX_ENTRY_COUNT} entry limit.",
                                )
                            VaultSaveOutcome.ENCRYPTED_BYTES_LIMIT_REACHED ->
                                throw VaultCapacityExceededException(
                                    "The vault is full. Delete an entry or shorten existing entries " +
                                        "before saving.",
                                )
                        }
                    } catch (error: SQLiteConstraintException) {
                        if (attempt == MAX_IV_ATTEMPTS - 1) {
                            throw VaultStorageException(
                                "Could not allocate a unique vault nonce.",
                                error,
                            )
                        }
                    }
                }
                throw VaultStorageException("Could not save the vault entry.")
            }
        }

    suspend fun delete(id: String): Boolean = withContext(ioDispatcher) {
        mutationMutex.withLock {
            requireCanonicalUuid(id)
            dao.delete(id) == 1
        }
    }

    /** Deletes ciphertext first, then permanently removes every wrapped DEK and Keystore KEK. */
    suspend fun reset() = withContext(ioDispatcher) {
        mutationMutex.withLock {
            dao.deleteAll()
            keyManager.reset()
        }
    }

    private fun decrypt(row: VaultEntryEntity, session: VaultSession): VaultEntry =
        VaultEntryCipher.decrypt(row, session)

    private fun requireCanonicalUuid(value: String) {
        val parsed = try {
            UUID.fromString(value)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Vault id must be a canonical UUID.", error)
        }
        require(parsed.toString() == value.lowercase()) { "Vault id must be a canonical UUID." }
    }

    private companion object {
        const val MAX_IV_ATTEMPTS = 3
    }
}

internal fun validateVaultDraft(draft: VaultEntryDraft) {
    require(
        listOf(draft.label, draft.account, draft.password, draft.website, draft.notes)
            .any(String::isNotBlank),
    ) { "At least one vault field is required." }
    VaultPayloadLimits.validate(
        listOf(
            "Label" to draft.label,
            "Account" to draft.account,
            "Password" to draft.password,
            "Website" to draft.website,
            "Notes" to draft.notes,
        ),
    )
}

internal object VaultEntryCipher {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val FORMAT_VERSION = 1
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    fun encrypt(entry: VaultEntry, session: VaultSession): VaultEntryEntity {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val plaintext = VaultPayloadCodec.encode(entry)
        return try {
            val ciphertext = session.useKey { key ->
                Cipher.getInstance(TRANSFORMATION).run {
                    init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                    updateAAD(aad(entry.id, FORMAT_VERSION))
                    doFinal(plaintext)
                }
            }
            VaultEntryEntity(
                id = entry.id,
                formatVersion = FORMAT_VERSION,
                payloadIv = iv,
                payloadCiphertext = ciphertext,
                createdAt = entry.createdAt,
                updatedAt = entry.updatedAt,
            )
        } catch (error: GeneralSecurityException) {
            throw VaultStorageException("Could not encrypt the vault entry.", error)
        } finally {
            plaintext.fill(0)
        }
    }

    fun decrypt(row: VaultEntryEntity, session: VaultSession): VaultEntry {
        if (row.formatVersion != FORMAT_VERSION || row.payloadIv.size != IV_BYTES) {
            throw VaultCorruptEntryException(row.id)
        }
        val plaintext = try {
            session.useKey { key ->
                Cipher.getInstance(TRANSFORMATION).run {
                    init(
                        Cipher.DECRYPT_MODE,
                        key,
                        GCMParameterSpec(TAG_BITS, row.payloadIv),
                    )
                    updateAAD(aad(row.id, row.formatVersion))
                    doFinal(row.payloadCiphertext)
                }
            }
        } catch (error: AEADBadTagException) {
            throw VaultCorruptEntryException(row.id, error)
        } catch (error: GeneralSecurityException) {
            throw VaultCorruptEntryException(row.id, error)
        }
        return try {
            val fields = VaultPayloadCodec.decode(plaintext)
            VaultEntry(
                id = row.id,
                label = fields[0],
                account = fields[1],
                password = fields[2],
                website = fields[3],
                notes = fields[4],
                createdAt = row.createdAt,
                updatedAt = row.updatedAt,
            )
        } catch (error: Exception) {
            throw VaultCorruptEntryException(row.id, error)
        } finally {
            plaintext.fill(0)
        }
    }

    internal fun aad(id: String, formatVersion: Int): ByteArray {
        val uuid = try {
            UUID.fromString(id)
        } catch (error: IllegalArgumentException) {
            throw VaultCorruptEntryException(id, error)
        }
        return ByteBuffer.allocate(Int.SIZE_BYTES + 2 * Long.SIZE_BYTES)
            .putInt(formatVersion)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
    }
}

/**
 * Narrow bridge used by the logical backup layer. It deliberately exposes neither the vault key
 * bytes nor the ciphertext format: backup exports carry portable plaintext [VaultEntry] values and
 * restores always encrypt them again with the currently unlocked, caller-owned [VaultSession].
 */
internal object VaultBackupCipher {
    fun encrypt(entry: VaultEntry, session: VaultSession): VaultEntryEntity =
        VaultEntryCipher.encrypt(entry, session)
}

internal object VaultPayloadCodec {
    private const val MAGIC = 0x544C5631 // "TLV1"
    private const val FIELD_COUNT = 5
    private const val HEADER_BYTES = Int.SIZE_BYTES + FIELD_COUNT * Int.SIZE_BYTES

    fun encode(entry: VaultEntry): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            val fields = listOf(
                "Label" to entry.label,
                "Account" to entry.account,
                "Password" to entry.password,
                "Website" to entry.website,
                "Notes" to entry.notes,
            )
            VaultPayloadLimits.validate(fields)
            fields.forEach { (_, value) ->
                    val encoded = value.encodeToByteArray()
                    output.writeInt(encoded.size)
                    output.write(encoded)
                    encoded.fill(0)
                }
        }
        bytes.toByteArray()
    }

    fun decode(payload: ByteArray): List<String> = DataInputStream(
        ByteArrayInputStream(payload),
    ).use { input ->
        require(payload.size <= HEADER_BYTES + VaultPayloadLimits.MAX_TOTAL_UTF8_BYTES) {
            "Vault payload exceeds the supported size."
        }
        require(input.readInt() == MAGIC) { "Unknown vault payload format." }
        val fields = ArrayList<String>(FIELD_COUNT)
        var totalBytes = 0
        repeat(FIELD_COUNT) {
            val length = input.readInt()
            require(length in 0..VaultPayloadLimits.MAX_FIELD_UTF8_BYTES) {
                "Invalid vault field length."
            }
            totalBytes += length
            require(totalBytes <= VaultPayloadLimits.MAX_TOTAL_UTF8_BYTES) {
                "Vault payload exceeds the supported size."
            }
            val encoded = ByteArray(length)
            input.readFully(encoded)
            fields += encoded.decodeToString(throwOnInvalidSequence = true)
            encoded.fill(0)
        }
        require(input.read() == -1) { "Unexpected trailing vault payload data." }
        fields
    }
}

internal object VaultPayloadLimits {
    const val MAX_FIELD_UTF8_BYTES = 64 * 1024
    const val MAX_TOTAL_UTF8_BYTES = 192 * 1024

    fun validate(fields: List<Pair<String, String>>) {
        var totalBytes = 0L
        fields.forEach { (name, value) ->
            val byteCount = if (value.length > MAX_FIELD_UTF8_BYTES) {
                MAX_FIELD_UTF8_BYTES + 1
            } else {
                value.encodeToByteArray().let { encoded ->
                    encoded.size.also { encoded.fill(0) }
                }
            }
            if (byteCount > MAX_FIELD_UTF8_BYTES) {
                throw VaultPayloadTooLargeException(
                    "$name is too large. Each field can use at most 64 KiB of UTF-8 text.",
                )
            }
            totalBytes += byteCount
        }
        if (totalBytes > MAX_TOTAL_UTF8_BYTES) {
            throw VaultPayloadTooLargeException(
                "Combined vault fields are too large. The total limit is 192 KiB of UTF-8 text.",
            )
        }
    }
}

/** Bounds the ciphertext that can be loaded and decrypted as one observed Vault snapshot. */
internal object VaultStorageLimits {
    const val MAX_ENTRY_COUNT = 10_000
    const val MAX_ENCRYPTED_BYTES = 8L * 1024L * 1024L
}
