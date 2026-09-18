package com.ced2711.lifetracker.desktop

import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class WindowsCredentialStore(
    private val directory: File = File(DesktopDataStore.defaultAppDirectory(), "credentials"),
) {
    fun save(name: String, value: CharArray) {
        require(name.matches(Regex("[a-z0-9_-]+")))
        directory.mkdirs()
        val plaintext = value.concatToString().toByteArray(StandardCharsets.UTF_8)
        try {
            val encrypted = protect(plaintext)
            try {
                val destination = File(directory, "$name.bin")
                val temporary = File(directory, "$name.bin.part")
                try {
                    temporary.writeBytes(encrypted)
                    try {
                        Files.move(
                            temporary.toPath(),
                            destination.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE,
                        )
                    } catch (_: Exception) {
                        Files.move(
                            temporary.toPath(),
                            destination.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }
                } finally {
                    temporary.delete()
                }
            } finally {
                encrypted.fill(0)
            }
        } finally {
            plaintext.fill(0)
            value.fill('\u0000')
        }
    }

    fun load(name: String): CharArray? {
        require(name.matches(Regex("[a-z0-9_-]+")))
        val file = File(directory, "$name.bin")
        if (!file.isFile) return null
        val encrypted = file.readBytes()
        return try {
            val plaintext = unprotect(encrypted)
            try {
                plaintext.toString(StandardCharsets.UTF_8).toCharArray()
            } finally {
                plaintext.fill(0)
            }
        } catch (_: Throwable) {
            null
        } finally {
            encrypted.fill(0)
        }
    }

    fun delete(name: String) {
        require(name.matches(Regex("[a-z0-9_-]+")))
        File(directory, "$name.bin").delete()
    }

    fun exists(name: String): Boolean = File(directory, "$name.bin").isFile

    private fun protect(bytes: ByteArray): ByteArray = if (isWindows()) {
        Crypt32Util.cryptProtectData(bytes)
    } else {
        throw UnsupportedOperationException("Secure credential storage requires Windows DPAPI.")
    }

    private fun unprotect(bytes: ByteArray): ByteArray = if (isWindows()) {
        Crypt32Util.cryptUnprotectData(bytes)
    } else {
        throw UnsupportedOperationException("Secure credential storage requires Windows DPAPI.")
    }

    companion object {
        const val LOCAL_PASSWORD = "local_password"
        const val OAUTH_TOKEN = "google_oauth_token"
        const val OAUTH_CLIENT_SECRET = "google_oauth_client_secret"

        fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    }
}
