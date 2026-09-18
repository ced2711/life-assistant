package com.ced2711.lifetracker.desktop

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WindowsCredentialStoreTest {
    @Test
    fun dpapiRoundTripAndDelete() {
        assumeTrue(WindowsCredentialStore.isWindows())
        val root = Files.createTempDirectory("life-tracker-dpapi-test").toFile()
        try {
            val store = WindowsCredentialStore(root)
            val value = charArrayOf('t', 'o', 'k', 'e', 'n', '-', 'v', 'a', 'l', 'u', 'e')
            val expected = value.copyOf()
            store.save("test_token", value)

            assertTrue(value.all { it == '\u0000' })
            val restored = store.load("test_token")
            assertArrayEquals(expected, restored)
            restored?.fill('\u0000')

            store.delete("test_token")
            assertFalse(store.exists("test_token"))
            assertNull(store.load("test_token"))
            expected.fill('\u0000')
        } finally {
            root.deleteRecursively()
        }
    }
}
