package com.ced2711.lifetracker.desktop.linux

import com.ced2711.lifetracker.desktop.DeviceProtection
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM with a random key that never leaves this computer.
 *
 * The key is kept in the desktop keyring (Secret Service, through `secret-tool` from
 * libsecret-tools) when one is available, and otherwise in a file only this user can read. Once
 * a place is chosen it is remembered, so a keyring that is briefly unavailable is reported as an
 * error instead of silently starting over with a new key.
 */
class LinuxDeviceProtection(
    private val directory: File,
    private val keyring: Keyring = SecretToolKeyring(),
) : DeviceProtection {
    private val random = SecureRandom()

    private val key: ByteArray by lazy { loadOrCreateKey() }

    override fun protect(bytes: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return MAGIC + iv + cipher.doFinal(bytes)
    }

    override fun unprotect(bytes: ByteArray): ByteArray {
        require(bytes.size > MAGIC.size + IV_BYTES && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "This data was not protected on this computer."
        }
        val iv = bytes.copyOfRange(MAGIC.size, MAGIC.size + IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(bytes, MAGIC.size + IV_BYTES, bytes.size - MAGIC.size - IV_BYTES)
    }

    private fun loadOrCreateKey(): ByteArray {
        val keyFile = File(directory, KEY_FILE)
        val keyringMarker = File(directory, KEYRING_MARKER)
        if (keyFile.isFile) return decode(keyFile.readText())
        if (keyringMarker.isFile) {
            return keyring.lookup()?.let(::decode)
                ?: error("The desktop keyring is locked or unavailable. Unlock it and open Life Assistant again.")
        }
        val created = ByteArray(KEY_BYTES).also(random::nextBytes)
        val encoded = Base64.getEncoder().encodeToString(created)
        directory.mkdirs()
        if (keyring.store(encoded)) {
            keyringMarker.writeText("secret-service\n")
        } else {
            writeOwnerOnly(keyFile, encoded)
        }
        return created
    }

    private fun decode(value: String): ByteArray =
        Base64.getDecoder().decode(value.trim()).also { require(it.size == KEY_BYTES) { "The device key is damaged." } }

    private fun writeOwnerOnly(file: File, text: String) {
        val path = file.toPath()
        runCatching {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        }.onFailure { if (!file.exists()) file.createNewFile() }
        file.writeText(text)
    }

    interface Keyring {
        fun lookup(): String?
        fun store(secret: String): Boolean
    }

    /** Talks to the Secret Service (GNOME Keyring, KWallet) through the `secret-tool` command. */
    class SecretToolKeyring : Keyring {
        override fun lookup(): String? = run(listOf("secret-tool", "lookup", *ATTRIBUTES), input = null)
            ?.takeIf(String::isNotBlank)

        override fun store(secret: String): Boolean =
            run(listOf("secret-tool", "store", "--label=Life Assistant device key", *ATTRIBUTES), input = secret) != null &&
                lookup() == secret

        private fun run(command: List<String>, input: String?): String? = try {
            val process = ProcessBuilder(command).redirectErrorStream(false).start()
            process.outputStream.use { stream -> input?.let { stream.write(it.toByteArray()) } }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0) output.trim() else null
        } catch (_: Exception) {
            null // secret-tool is not installed or no keyring is running.
        }

        private companion object {
            val ATTRIBUTES = arrayOf("application", "life-assistant", "purpose", "device-key")
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_FILE = "device.key"
        const val KEYRING_MARKER = "device-key.keyring"
        val MAGIC = byteArrayOf('L'.code.toByte(), 'A'.code.toByte(), 1)
    }
}
