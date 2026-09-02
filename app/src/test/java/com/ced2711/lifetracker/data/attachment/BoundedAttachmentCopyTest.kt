package com.ced2711.lifetracker.data.attachment

import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedAttachmentCopyTest {
    @Test
    fun unknownLengthStreamStopsAtTheGlobalBudget() {
        val output = ByteArrayOutputStream()

        assertThrows(AttachmentTotalLimitExceededException::class.java) {
            copyAttachmentBytes(
                input = GeneratedInputStream(11),
                output = output,
                maxFileBytes = 25,
                remainingTotalBytes = 10,
                bufferSize = 4,
            )
        }

        assertEquals(8, output.size())
    }

    @Test
    fun lyingStreamStopsAtThePerFileBudget() {
        val output = ByteArrayOutputStream()

        assertThrows(AttachmentFileLimitExceededException::class.java) {
            copyAttachmentBytes(
                input = GeneratedInputStream(26),
                output = output,
                maxFileBytes = 25,
                remainingTotalBytes = 100,
                bufferSize = 8,
            )
        }

        assertEquals(24, output.size())
    }

    @Test
    fun zeroLengthReadsCannotSpinForever() {
        val output = ByteArrayOutputStream()
        val input = object : InputStream() {
            private var emittedZero = false
            private var emittedByte = false

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (!emittedZero) {
                    emittedZero = true
                    return 0
                }
                return super.read(buffer, offset, length)
            }

            override fun read(): Int = if (emittedByte) -1 else 7.also { emittedByte = true }
        }

        assertEquals(
            1L,
            copyAttachmentBytes(input, output, maxFileBytes = 25, remainingTotalBytes = 25, bufferSize = 8),
        )
        assertEquals(1, output.size())
    }

    private class GeneratedInputStream(private val byteCount: Long) : InputStream() {
        private var emitted = 0L

        override fun read(): Int = if (emitted >= byteCount) -1 else 1.also { emitted += 1 }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (emitted >= byteCount) return -1
            val count = minOf(length.toLong(), byteCount - emitted).toInt()
            buffer.fill(1, offset, offset + count)
            emitted += count
            return count
        }
    }
}
