package com.ced2711.lifetracker.data.backup

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-encrypted, authenticated container around [BackupCodec]. Password arrays are consumed. */
object BackupCrypto {
    private val magic = byteArrayOf(0x54, 0x4c, 0x42, 0x41, 0x43, 0x4b, 0x55, 0x50) // TLBACKUP
    private const val CONTAINER_VERSION = 1
    private const val KDF_PBKDF2_SHA256 = 1
    private const val CIPHER_AES_256_GCM = 1
    private const val PBKDF2_ITERATIONS = 600_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 1_000_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val WORK_PREFIX = "taskledger-encrypted-"
    private const val WORK_SUFFIX = ".backup"
    private const val HEADER_BYTES = 60L
    private val random = SecureRandom()

    /** The supplied [password] is zeroed before this method returns or throws. */
    fun encrypt(
        snapshot: BackupSnapshot,
        password: CharArray,
        destination: OutputStream,
        attachmentSource: BackupAttachmentSource,
    ) {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = header(PBKDF2_ITERATIONS, salt, iv)
        var keyBytes: ByteArray? = null
        try {
            require(password.isNotEmpty()) { "Backup password must not be empty." }
            snapshot.validate()
            keyBytes = derive(password, salt, PBKDF2_ITERATIONS)
            password.fill('\u0000')
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, iv))
                updateAAD(header)
            }
            destination.write(header)
            CipherOutputStream(NonClosingOutputStream(destination), cipher).use { encrypted ->
                BackupCodec.write(snapshot, encrypted, attachmentSource)
            }
            destination.flush()
        } catch (error: BackupException) {
            throw error
        } catch (error: GeneralSecurityException) {
            throw InvalidBackupException("Could not encrypt the backup.", error)
        } finally {
            password.fill('\u0000')
            keyBytes?.fill(0)
            salt.fill(0)
            iv.fill(0)
            header.fill(0)
        }
    }

    /** Authenticates first, then streams a second decryption pass; plaintext is never persisted. */
    @Synchronized
    fun decrypt(
        source: InputStream,
        password: CharArray,
        temporaryDirectory: File,
        stagingParent: File,
    ): DecryptedBackup {
        var encryptedWorkFile: File? = null
        var attachmentStage: AttachmentRestoreStage? = null
        var keyBytes: ByteArray? = null
        var header: ParsedHeader? = null
        try {
            val parent = temporaryDirectory.canonicalFile
            if (!parent.exists() && !parent.mkdirs()) throw InvalidBackupException("Could not create a backup work directory.")
            if (!parent.isDirectory) throw InvalidBackupException("Backup work path is not a directory.")
            require(password.isNotEmpty()) { "Backup password must not be empty." }
            val workFile = File.createTempFile(WORK_PREFIX, WORK_SUFFIX, parent)
            encryptedWorkFile = workFile
            copyEncryptedContainer(source, workFile)

            workFile.inputStream().buffered().use { encrypted ->
                val firstHeader = readHeader(encrypted)
                header = firstHeader
                val derivedKey = derive(password, firstHeader.salt, firstHeader.iterations)
                password.fill('\u0000')
                keyBytes = derivedKey
                authenticateAndDiscard(encrypted, derivedKey, firstHeader)
            }

            val decoded = workFile.inputStream().buffered().use { encrypted ->
                val secondHeader = readHeader(encrypted)
                try {
                    val firstHeader = requireNotNull(header)
                    val derivedKey = requireNotNull(keyBytes)
                    if (!secondHeader.encoded.contentEquals(firstHeader.encoded)) {
                        throw BackupAuthenticationException()
                    }
                    val cipher = decryptCipher(derivedKey, secondHeader)
                    val stage = AttachmentRestoreStage.create(stagingParent)
                    attachmentStage = stage
                    val snapshot = CipherInputStream(encrypted, cipher).use { plaintext ->
                        BackupCodec.read(plaintext, stage)
                    }
                    DecryptedBackup(snapshot, stage)
                } finally {
                    secondHeader.clear()
                }
            }
            attachmentStage = null
            return decoded
        } catch (error: BackupException) {
            throw error
        } catch (error: AEADBadTagException) {
            throw BackupAuthenticationException(error)
        } catch (error: GeneralSecurityException) {
            throw BackupAuthenticationException(error)
        } finally {
            password.fill('\u0000')
            keyBytes?.fill(0)
            header?.clear()
            encryptedWorkFile?.let { if (it.exists()) it.delete() }
            attachmentStage?.close()
        }
    }

    /** Clears only abandoned work artifacts from a previous process, before normal app work starts. */
    @Synchronized
    internal fun cleanupStaleWorkArtifacts(temporaryDirectory: File) {
        val parent = temporaryDirectory.canonicalFile
        if (!parent.exists()) return
        if (!parent.isDirectory) throw InvalidBackupException("Backup work path is not a directory.")
        parent.listFiles()?.forEach { candidate ->
            if (!candidate.isFile || !isRecognizedWorkArtifact(candidate.name)) return@forEach
            if (candidate.canonicalFile.parentFile != parent) return@forEach
            if (!candidate.delete()) {
                throw InvalidBackupException("Could not clear a previous backup work file.")
            }
        }
    }

    private fun copyEncryptedContainer(source: InputStream, destination: File) {
        val usableSpace = destination.parentFile?.usableSpace ?: 0L
        val diskBound = if (usableSpace <= 0L) {
            BackupLimits.MAX_ENCRYPTED_CONTAINER_BYTES
        } else {
            (usableSpace - BackupLimits.MIN_FREE_SPACE_BYTES).coerceAtLeast(0L)
        }
        val maximum = minOf(BackupLimits.MAX_ENCRYPTED_CONTAINER_BYTES, diskBound)
        if (maximum < HEADER_BYTES + TAG_BYTES) {
            throw InvalidBackupException("There is not enough private storage to inspect this backup.")
        }
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var validatedHeader: ParsedHeader? = null
        try {
            val parsedHeader = readHeader(source)
            validatedHeader = parsedHeader
            FileOutputStream(destination).use { output ->
                output.write(parsedHeader.encoded)
                total = parsedHeader.encoded.size.toLong()
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    total = try {
                        Math.addExact(total, read.toLong())
                    } catch (error: ArithmeticException) {
                        throw InvalidBackupException("Encrypted backup size overflowed.", error)
                    }
                    if (total > maximum) {
                        throw InvalidBackupException("Encrypted backup exceeds the storage safety limit.")
                    }
                    output.write(buffer, 0, read)
                }
            }
            if (total < HEADER_BYTES + TAG_BYTES) throw InvalidBackupException("The encrypted backup is truncated.")
        } finally {
            validatedHeader?.clear()
            buffer.fill(0)
        }
    }

    private fun authenticateAndDiscard(input: InputStream, keyBytes: ByteArray, header: ParsedHeader) {
        val cipher = decryptCipher(keyBytes, header)
        val encrypted = ByteArray(DEFAULT_BUFFER_SIZE)
        var ciphertextBytes = 0L
        try {
            while (true) {
                val read = input.read(encrypted)
                if (read < 0) break
                ciphertextBytes += read
                cipher.update(encrypted, 0, read)?.fill(0)
            }
            if (ciphertextBytes < TAG_BYTES) throw BackupAuthenticationException()
            cipher.doFinal()?.fill(0)
        } finally {
            encrypted.fill(0)
        }
    }

    private fun decryptCipher(keyBytes: ByteArray, header: ParsedHeader): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, header.iv))
            updateAAD(header.encoded)
        }

    private fun isRecognizedWorkArtifact(name: String): Boolean =
        (name.startsWith(WORK_PREFIX) && name.endsWith(WORK_SUFFIX)) ||
            (name.startsWith("taskledger-authenticated-") && name.endsWith(".snapshot"))

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun header(iterations: Int, salt: ByteArray, iv: ByteArray): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(magic)
                output.writeInt(CONTAINER_VERSION)
                output.writeInt(KDF_PBKDF2_SHA256)
                output.writeInt(CIPHER_AES_256_GCM)
                output.writeInt(iterations)
                output.writeInt(salt.size)
                output.write(salt)
                output.writeInt(iv.size)
                output.write(iv)
            }
            bytes.toByteArray()
        }

    private fun readHeader(input: InputStream): ParsedHeader {
        try {
            val data = DataInputStream(input)
            val foundMagic = ByteArray(magic.size).also(data::readFully)
            if (!foundMagic.contentEquals(magic)) throw UnsupportedBackupException("Unknown backup file format.")
            val version = data.readInt()
            val kdf = data.readInt()
            val cipher = data.readInt()
            val iterations = data.readInt()
            val saltLength = data.readInt()
            if (version != CONTAINER_VERSION) throw UnsupportedBackupException("Unsupported backup version $version.")
            if (kdf != KDF_PBKDF2_SHA256 || cipher != CIPHER_AES_256_GCM) {
                throw UnsupportedBackupException("Unsupported backup encryption suite.")
            }
            if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS || saltLength != SALT_BYTES) {
                throw InvalidBackupException("Invalid backup encryption parameters.")
            }
            val salt = ByteArray(saltLength).also(data::readFully)
            val ivLength = data.readInt()
            if (ivLength != IV_BYTES) {
                salt.fill(0)
                throw InvalidBackupException("Invalid backup nonce length.")
            }
            val iv = ByteArray(ivLength).also(data::readFully)
            return ParsedHeader(iterations, salt, iv, header(iterations, salt, iv))
        } catch (error: BackupException) {
            throw error
        } catch (error: EOFException) {
            throw InvalidBackupException("The backup header is truncated.", error)
        }
    }

    private data class ParsedHeader(
        val iterations: Int,
        val salt: ByteArray,
        val iv: ByteArray,
        val encoded: ByteArray,
    ) {
        fun clear() { salt.fill(0); iv.fill(0); encoded.fill(0) }
    }

    private class NonClosingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        override fun close() = flush()
    }
}

data class DecryptedBackup(
    val snapshot: BackupSnapshot,
    val attachmentStage: AttachmentRestoreStage,
)
