package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.domain.model.ConfessionCodec
import com.ced2711.lifetracker.domain.model.ConfessionEntry
import com.ced2711.lifetracker.domain.model.MAX_SEALED_CONFESSIONS
import com.ced2711.lifetracker.domain.model.newConfessionEntry
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Sealed confessions for this user on this computer, protected with [DesktopPlatform.protection]
 * (DPAPI on Windows). They are kept apart from the
 * encrypted data file, so they are never exported, imported or synced to Google Drive.
 */
class DesktopConfessionStore(
    private val file: File = File(DesktopDataStore.defaultAppDirectory(), "confessional/sealed.bin"),
    private val protect: (ByteArray) -> ByteArray = { DesktopPlatform.protection.protect(it) },
    private val unprotect: (ByteArray) -> ByteArray = { DesktopPlatform.protection.unprotect(it) },
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<ConfessionEntry>?>(null)

    /** Null until [load] finished; newest first. */
    val entries: StateFlow<List<ConfessionEntry>?> = _entries.asStateFlow()

    suspend fun load() = mutex.withLock {
        if (_entries.value == null) _entries.value = withContext(ioDispatcher) { read() }
    }

    suspend fun seal(text: String) = mutate { current ->
        require(current.size < MAX_SEALED_CONFESSIONS) { "The confessional is full. Burn some sealed entries first." }
        listOf(newConfessionEntry(text, clock())) + current
    }

    suspend fun burn(id: String) = mutate { current -> current.filterNot { it.id == id } }

    suspend fun burnAll() = mutate { emptyList() }

    private suspend fun mutate(change: (List<ConfessionEntry>) -> List<ConfessionEntry>) = mutex.withLock {
        withContext(ioDispatcher) {
            val next = change(_entries.value ?: read())
            write(next)
            _entries.value = next
        }
    }

    private fun read(): List<ConfessionEntry> {
        if (!file.isFile) return emptyList()
        val plaintext = unprotect(file.readBytes())
        return try {
            ConfessionCodec.decode(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun write(entries: List<ConfessionEntry>) {
        if (entries.isEmpty()) {
            file.delete()
            return
        }
        val plaintext = ConfessionCodec.encode(entries)
        val encrypted = try {
            protect(plaintext)
        } finally {
            plaintext.fill(0)
        }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.part")
        try {
            temporary.writeBytes(encrypted)
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }
}
