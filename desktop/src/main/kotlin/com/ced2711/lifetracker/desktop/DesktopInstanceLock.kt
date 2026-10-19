package com.ced2711.lifetracker.desktop

import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

/**
 * Lets only one copy of the app run for this user. Asking the running copy over its local door
 * (see DesktopAgentServer) is not enough on its own: that door opens only seconds after start, and
 * copies started in those seconds (by the computer at sign-in, by several AI assistants at once,
 * or by a double click) each thought they were alone, ended up in the tray side by side, and the
 * last one, unable to open the data, was the one the assistants reached. A file lock is taken in
 * the first moment instead; the operating system lets it go when the copy ends, however it ends.
 *
 * The lock file lives in the user's temporary folder rather than the data folder, because the
 * first start of a new version may still move the data folder (see DesktopPlatform).
 */
class DesktopInstanceLock(private val file: File = defaultFile()) {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    /** True when this copy now holds the lock; false when another copy holds it. */
    @Synchronized
    fun acquire(): Boolean {
        if (lock != null) return true
        val opened = runCatching {
            file.parentFile?.mkdirs()
            FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        }.getOrElse { return true } // A lock that cannot be taken at all must not stop the app.
        val taken = try {
            opened.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        } catch (_: java.io.IOException) {
            null
        }
        if (taken == null) {
            opened.close()
            return false
        }
        channel = opened
        lock = taken
        return true
    }

    @Synchronized
    fun release() {
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        lock = null
        channel = null
    }

    companion object {
        fun defaultFile(): File = File(System.getProperty("java.io.tmpdir"), "life-assistant-${userKey()}.instance.lock")

        /** This user's name, safe to use in a file name. */
        internal fun userKey(): String = System.getProperty("user.name").orEmpty().ifBlank { "user" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
