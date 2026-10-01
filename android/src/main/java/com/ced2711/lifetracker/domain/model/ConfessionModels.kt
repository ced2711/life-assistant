package com.ced2711.lifetracker.domain.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

/**
 * A confession the user chose to seal instead of burning. Sealed confessions live only on the
 * device that wrote them, encrypted with a platform key; they are never backed up or synced.
 */
data class ConfessionEntry(
    val id: String,
    val createdAt: Long,
    val text: String,
)

const val MAX_CONFESSION_LENGTH = 20_000
const val MAX_SEALED_CONFESSIONS = 500

fun newConfessionEntry(text: String, createdAt: Long): ConfessionEntry {
    require(text.isNotBlank()) { "Confession is empty" }
    require(text.length <= MAX_CONFESSION_LENGTH) { "Confession is too long" }
    return ConfessionEntry(UUID.randomUUID().toString(), createdAt, text)
}

/** Plaintext layout of the sealed-confession file before platform encryption. */
object ConfessionCodec {
    private const val MAGIC = 0x4C414346 // "LACF"
    private const val VERSION = 1

    fun encode(entries: List<ConfessionEntry>): ByteArray {
        require(entries.size <= MAX_SEALED_CONFESSIONS) { "Too many sealed confessions" }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(entries.size)
            entries.forEach { entry ->
                out.writeText(entry.id)
                out.writeLong(entry.createdAt)
                out.writeText(entry.text)
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): List<ConfessionEntry> =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Unknown confession format" }
            require(input.readInt() == VERSION) { "Unsupported confession version" }
            val count = input.readInt()
            require(count in 0..MAX_SEALED_CONFESSIONS) { "Invalid confession count" }
            List(count) {
                ConfessionEntry(
                    id = input.readText(),
                    createdAt = input.readLong(),
                    text = input.readText(),
                )
            }.also { require(input.read() == -1) { "Unexpected trailing confession data" } }
        }

    private fun DataOutputStream.writeText(value: String) {
        val encoded = value.encodeToByteArray()
        writeInt(encoded.size)
        write(encoded)
    }

    private fun DataInputStream.readText(): String {
        val size = readInt()
        // UTF-8 needs at most 4 bytes per UTF-16 unit; reject anything a valid entry cannot be.
        require(size in 0..MAX_CONFESSION_LENGTH * 4) { "Invalid confession text length" }
        return ByteArray(size).also(::readFully).decodeToString()
    }
}
