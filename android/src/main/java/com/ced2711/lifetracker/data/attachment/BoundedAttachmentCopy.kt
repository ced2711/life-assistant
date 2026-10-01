package com.ced2711.lifetracker.data.attachment

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal class AttachmentFileLimitExceededException : IOException()

internal class AttachmentTotalLimitExceededException : IOException()

/** Copies with fixed memory while enforcing actual bytes, regardless of provider metadata. */
internal fun copyAttachmentBytes(
    input: InputStream,
    output: OutputStream,
    maxFileBytes: Long,
    remainingTotalBytes: Long,
    bufferSize: Int = 8 * 1024,
): Long {
    require(maxFileBytes >= 0) { "Per-file byte limit cannot be negative." }
    require(remainingTotalBytes >= 0) { "Remaining attachment bytes cannot be negative." }
    require(bufferSize > 0) { "Copy buffer size must be positive." }

    val buffer = ByteArray(bufferSize)
    var copiedBytes = 0L
    while (true) {
        var bytesRead = input.read(buffer)
        if (bytesRead < 0) break
        if (bytesRead == 0) {
            val singleByte = input.read()
            if (singleByte < 0) break
            buffer[0] = singleByte.toByte()
            bytesRead = 1
        }

        val readAsLong = bytesRead.toLong()
        if (readAsLong > remainingTotalBytes - copiedBytes) {
            throw AttachmentTotalLimitExceededException()
        }
        if (readAsLong > maxFileBytes - copiedBytes) {
            throw AttachmentFileLimitExceededException()
        }
        output.write(buffer, 0, bytesRead)
        copiedBytes += readAsLong
    }
    return copiedBytes
}
