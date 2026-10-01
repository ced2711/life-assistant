package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.desktop.linux.LinuxDeviceProtection
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LinuxDeviceProtectionTest {
    private val root: File = Files.createTempDirectory("linux-protection").toFile()

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private class FakeKeyring(var available: Boolean) : LinuxDeviceProtection.Keyring {
        var secret: String? = null
        override fun lookup(): String? = secret.takeIf { available }
        override fun store(secret: String): Boolean {
            if (!available) return false
            this.secret = secret
            return true
        }
    }

    @Test
    fun keyFileIsUsedWithoutKeyringAndSurvivesRestart() {
        val keyring = FakeKeyring(available = false)
        val sealed = LinuxDeviceProtection(root, keyring).protect("secret".encodeToByteArray())
        assertTrue(File(root, "device.key").isFile)
        assertArrayEquals("secret".encodeToByteArray(), LinuxDeviceProtection(root, keyring).unprotect(sealed))
    }

    @Test
    fun keyringKeepsTheKeyOutOfTheDataFolder() {
        val keyring = FakeKeyring(available = true)
        val sealed = LinuxDeviceProtection(root, keyring).protect("token".encodeToByteArray())
        assertFalse(File(root, "device.key").exists())
        assertArrayEquals("token".encodeToByteArray(), LinuxDeviceProtection(root, keyring).unprotect(sealed))
    }

    @Test
    fun lockedKeyringIsAnErrorInsteadOfANewKey() {
        val keyring = FakeKeyring(available = true)
        val sealed = LinuxDeviceProtection(root, keyring).protect("token".encodeToByteArray())
        keyring.available = false
        try {
            LinuxDeviceProtection(root, keyring).unprotect(sealed)
            fail("A locked keyring must not silently create a new key")
        } catch (expected: IllegalStateException) {
            assertFalse(File(root, "device.key").exists())
        }
    }

    @Test
    fun credentialStoreRoundTripsWithLinuxProtection() {
        val store = DesktopCredentialStore(File(root, "credentials"), LinuxDeviceProtection(root, FakeKeyring(false)))
        store.save("github_token", "abc123".toCharArray())
        assertArrayEquals("abc123".toCharArray(), store.load("github_token"))
    }
}
