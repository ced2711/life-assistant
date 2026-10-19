package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.desktop.linux.LinuxAutostart
import com.ced2711.lifetracker.desktop.windows.WindowsAutostart
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Starting with the computer: the command saved, kept current, and removed again. */
class DesktopAutostartTest {
    private class MemoryEntry : AutostartEntry {
        var command: String? = null
        override fun read() = command
        override fun write(command: String): Boolean { this.command = command; return true }
        override fun delete(): Boolean { command = null; return true }
    }

    private val installed = File("C:/Users/someone/AppData/Local/Life Tracker/Life Assistant.exe")

    @Test
    fun turningItOnSavesTheLauncherStartingInTheTray() {
        val entry = MemoryEntry()
        val autostart = DesktopAutostart(entry) { installed }
        assertTrue(autostart.isAvailable)
        assertFalse(autostart.isEnabled())

        assertTrue(autostart.setEnabled(true))
        assertEquals("\"${installed.absolutePath}\" --background", entry.command)
        assertTrue(autostart.isEnabled())

        assertTrue(autostart.setEnabled(false))
        assertNull(entry.command)
        assertFalse(autostart.isEnabled())
    }

    @Test
    fun aMovedAppIsStartedFromItsNewPlaceAndNothingIsAddedWhenItWasOff() {
        val entry = MemoryEntry()
        var launcher = installed
        val autostart = DesktopAutostart(entry) { launcher }
        autostart.refresh()
        assertNull("refreshing never turns it on", entry.command)

        autostart.setEnabled(true)
        launcher = File("D:/Apps/Life Assistant/Life Assistant.exe")
        autostart.refresh()
        assertEquals(DesktopAutostart.command(launcher), entry.command)
    }

    @Test
    fun aDevelopmentBuildCannotStartWithTheComputer() {
        val entry = MemoryEntry()
        val autostart = DesktopAutostart(entry) { null }
        assertFalse(autostart.isAvailable)
        assertFalse(autostart.setEnabled(true))
        assertNull(entry.command)
    }

    @Test
    fun linuxWritesADesktopEntryInTheAutostartFolder() {
        val dir = Files.createTempDirectory("autostart").toFile()
        try {
            val file = File(dir, "autostart/life-assistant.desktop")
            val entry = LinuxAutostart(file, "Life Assistant")
            assertNull(entry.read())
            assertTrue(entry.write("\"/opt/life-assistant/bin/Life Assistant\" --background"))
            val text = file.readText()
            assertTrue(text.startsWith("[Desktop Entry]\n"))
            assertTrue(text.contains("\nName=Life Assistant\n"))
            assertEquals("\"/opt/life-assistant/bin/Life Assistant\" --background", entry.read())
            assertTrue(entry.delete())
            assertFalse(file.exists())
            assertTrue("deleting twice is fine", entry.delete())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun windowsSavesAValueUnderTheUsersRunKey() {
        assumeTrue(DesktopPlatform.isWindows)
        // A test key, so the real startup list of this computer is never touched.
        val key = "Software\\LifeAssistantAutostartTest"
        val entry = WindowsAutostart("Life Assistant", key)
        try {
            assertNull(entry.read())
            assertTrue(entry.write("\"C:\\Apps\\Life Assistant.exe\" --background"))
            assertEquals("\"C:\\Apps\\Life Assistant.exe\" --background", entry.read())
            assertTrue(entry.delete())
            assertNull(entry.read())
            assertTrue(entry.delete())
        } finally {
            runCatching { Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, key) }
        }
        assertEquals("Software\\Microsoft\\Windows\\CurrentVersion\\Run", WindowsAutostart.RUN_KEY)
    }
}
