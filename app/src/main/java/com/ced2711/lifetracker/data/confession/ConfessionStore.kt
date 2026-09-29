package com.ced2711.lifetracker.data.confession

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.ced2711.lifetracker.domain.model.ConfessionCodec
import com.ced2711.lifetracker.domain.model.ConfessionEntry
import com.ced2711.lifetracker.domain.model.MAX_SEALED_CONFESSIONS
import com.ced2711.lifetracker.domain.model.newConfessionEntry
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Sealed confessions, encrypted with a non-exportable Android Keystore key and kept in
 * no-backup storage. Nothing here is part of the app's backup, restore or cloud sync.
 */
class ConfessionStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val file = File(context.noBackupFilesDir, "confessional/sealed.bin")
    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<ConfessionEntry>?>(null)

    /** Null until [load] finished; newest first. */
    val entries: StateFlow<List<ConfessionEntry>?> = _entries.asStateFlow()

    suspend fun load() = mutex.withLock {
        if (_entries.value == null) _entries.value = withContext(Dispatchers.IO) { read() }
    }

    suspend fun seal(text: String) = mutate { current ->
        require(current.size < MAX_SEALED_CONFESSIONS) { "The confessional is full. Burn some sealed entries first." }
        listOf(newConfessionEntry(text, clock())) + current
    }

    suspend fun burn(id: String) = mutate { current -> current.filterNot { it.id == id } }

    suspend fun burnAll() = mutate { emptyList() }

    private suspend fun mutate(change: (List<ConfessionEntry>) -> List<ConfessionEntry>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val next = change(_entries.value ?: read())
            write(next)
            _entries.value = next
        }
    }

    private fun read(): List<ConfessionEntry> {
        if (!file.isFile) return emptyList()
        val bytes = file.readBytes()
        require(bytes.size > IV_BYTES) { "Sealed confessions are damaged" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        return ConfessionCodec.decode(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
    }

    private fun write(entries: List<ConfessionEntry>) {
        if (entries.isEmpty()) {
            file.delete()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val plaintext = ConfessionCodec.encode(entries)
        val encrypted = cipher.iv + cipher.doFinal(plaintext)
        plaintext.fill(0)
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeBytes(encrypted)
        if (!temporary.renameTo(file)) {
            file.delete()
            check(temporary.renameTo(file)) { "Could not save sealed confessions" }
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "life_assistant_confessional_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
