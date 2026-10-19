package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.domain.model.TodoPriority
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app was called Life Tracker before; 2.0.4 gives its data folder, its data file and its
 * install folder the name Life Assistant without losing anything on the way.
 */
class DesktopRenameTest {
    private val base: File = Files.createTempDirectory("life-assistant-rename").toFile()
    private val legacy = File(base, "Life Tracker")
    private val current = File(base, "Life Assistant")

    @After
    fun tearDown() {
        base.deleteRecursively()
    }

    private fun password() = "rename-test-password".toCharArray()

    /** Data as a version before 2.0.4 left it: old folder, old file name. */
    private fun legacyData(title: String) = runBlocking {
        val store = DesktopDataStore(legacy)
        assertTrue(store.open(password()))
        assertTrue(store.upsertTodo(null, title, title, null, TodoPriority.NONE, null, "", false))
        store.close()
        Files.move(File(legacy, DesktopDataStore.LOCAL_DATA_FILE).toPath(), File(legacy, DesktopDataStore.LEGACY_LOCAL_DATA_FILE).toPath())
        File(legacy, "desktop.properties").writeText("ui.language=SIMPLIFIED_CHINESE\n")
    }

    @Test
    fun aNewComputerUsesTheNewFolder() {
        assertEquals(current, DesktopPlatform.windowsAppDirectory(base))
        assertEquals(current, DesktopPlatform.moveLegacyAppDirectory(base))
        assertFalse(legacy.exists())
    }

    @Test
    fun theOldFolderIsUsedUntilItIsMovedAndThenEverythingInItIsThere() = runBlocking {
        legacyData("Kept through the rename")
        assertEquals("until the move, the old folder is the one in use", legacy, DesktopPlatform.windowsAppDirectory(base))

        assertEquals(current, DesktopPlatform.moveLegacyAppDirectory(base))
        assertFalse(legacy.exists())
        assertEquals(current, DesktopPlatform.windowsAppDirectory(base))
        assertTrue(File(current, "desktop.properties").isFile)

        val store = DesktopDataStore(current)
        assertTrue(store.open(password()))
        assertEquals(listOf("Kept through the rename"), store.currentSnapshot()!!.todos.map { it.title })
        store.close()
        assertTrue("the data file has the new name", File(current, DesktopDataStore.LOCAL_DATA_FILE).isFile)
        assertFalse(File(current, DesktopDataStore.LEGACY_LOCAL_DATA_FILE).exists())
    }

    @Test
    fun anEmptyFolderOfTheNewNameDoesNotHideTheData() {
        legacyData("Not hidden")
        current.mkdirs()
        assertEquals(legacy, DesktopPlatform.windowsAppDirectory(base))
        assertEquals(current, DesktopPlatform.moveLegacyAppDirectory(base))
        assertTrue(File(current, DesktopDataStore.LEGACY_LOCAL_DATA_FILE).isFile)
    }

    @Test
    fun whenBothFoldersHaveDataTheNewOneWinsAndTheOldOneIsLeftAlone() {
        legacyData("Old")
        current.mkdirs()
        File(current, "desktop.properties").writeText("ui.language=ENGLISH\n")
        assertEquals(current, DesktopPlatform.moveLegacyAppDirectory(base))
        assertTrue("nothing is deleted", File(legacy, DesktopDataStore.LEGACY_LOCAL_DATA_FILE).isFile)
    }

    @Test
    fun aDataFileOfTheNewNameIsNeverReplacedByAnOldOne() {
        current.mkdirs()
        File(current, DesktopDataStore.LOCAL_DATA_FILE).writeText("new")
        File(current, DesktopDataStore.LEGACY_LOCAL_DATA_FILE).writeText("old")
        assertEquals(File(current, DesktopDataStore.LOCAL_DATA_FILE), DesktopDataStore.localDataFile(current))
        assertEquals("new", File(current, DesktopDataStore.LOCAL_DATA_FILE).readText())
        assertEquals("old", File(current, DesktopDataStore.LEGACY_LOCAL_DATA_FILE).readText())
    }

    @Test
    fun aiAssistantsRegisteredWithTheOldInstallFolderArePointedAtTheNewOne() {
        val launcher = File(base, "Local/Life Assistant/Life Assistant.exe")
        val old = File(base, "Local/Life Tracker/Life Assistant.exe").absolutePath.replace("\\", "\\\\")
        val desktopConfig = File(base, "claude_desktop_config.json")
        desktopConfig.writeText("""{"theme":"dark","mcpServers":{"other":{"command":"x"},"life-assistant":{"command":"$old","args":["--mcp"]}}}""")
        val codeConfig = File(base, ".claude.json")
        codeConfig.writeText("""{"mcpServers":{"life-assistant":{"type":"stdio","command":"$old","args":["--mcp"]}}}""")
        var claudeCodeAsked = 0

        DesktopAgentSetup.refreshRegistrations(launcher, desktopConfig, codeConfig) { claudeCodeAsked++; null }

        val updated = Json.parseToJsonElement(desktopConfig.readText()).jsonObject
        assertEquals(launcher.absolutePath, DesktopAgentSetup.registeredCommand(desktopConfig))
        assertEquals("dark", updated["theme"]!!.jsonPrimitive.content)
        assertEquals("x", updated["mcpServers"]!!.jsonObject["other"]!!.jsonObject["command"]!!.jsonPrimitive.content)
        assertEquals("--mcp", updated["mcpServers"]!!.jsonObject["life-assistant"]!!.jsonObject["args"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("Claude Code is asked to re-add it", 1, claudeCodeAsked)

        // Already current, or never registered: nothing is touched.
        val before = desktopConfig.readText()
        DesktopAgentSetup.refreshRegistrations(launcher, desktopConfig, File(base, "missing.json")) { claudeCodeAsked++; null }
        assertEquals(before, desktopConfig.readText())
        assertEquals(1, claudeCodeAsked)
        val unregistered = File(base, "plain.json").apply { writeText("""{"mcpServers":{}}""") }
        DesktopAgentSetup.refreshRegistrations(launcher, unregistered, unregistered) { claudeCodeAsked++; null }
        assertEquals("""{"mcpServers":{}}""", unregistered.readText())
        assertEquals(1, claudeCodeAsked)
    }
}
