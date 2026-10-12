package com.ced2711.lifetracker.desktop

import java.awt.CheckboxMenuItem
import java.awt.Component
import java.awt.Menu
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The tray icon opens the window with one left click; the menu stays on the right button. */
class DesktopTrayIconTest {
    private val source = object : Component() {}

    private fun click(button: Int, count: Int) =
        MouseEvent(source, MouseEvent.MOUSE_CLICKED, 0L, 0, 10, 10, count, button == MouseEvent.BUTTON3, button)

    @Test
    fun aSingleLeftClickOpensTheWindowAndOtherClicksDoNot() {
        assertTrue(DesktopTrayIcon.opensWindow(click(MouseEvent.BUTTON1, 1)))
        assertFalse("the right button opens the menu", DesktopTrayIcon.opensWindow(click(MouseEvent.BUTTON3, 1)))
        assertFalse("the middle button does nothing", DesktopTrayIcon.opensWindow(click(MouseEvent.BUTTON2, 1)))
        assertFalse("a double click opens through the action listener only", DesktopTrayIcon.opensWindow(click(MouseEvent.BUTTON1, 2)))
    }

    @Test
    fun theIconOpensOnALeftClickADoubleClickAndFromItsMenu() {
        assumeTrue(DesktopTrayIcon.isSupported)
        var opened = 0
        var quit = 0
        val tray = DesktopTrayIcon(
            image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB),
            tooltip = "Life Assistant",
            openLabel = "Open",
            quitLabel = "Quit",
            onOpen = { opened++ },
            onQuit = { quit++ },
        )
        val icon = tray.icon
        icon.mouseListeners.forEach { it.mouseClicked(click(MouseEvent.BUTTON1, 1)) }
        assertEquals(1, opened)
        icon.mouseListeners.forEach { it.mouseClicked(click(MouseEvent.BUTTON3, 1)) }
        assertEquals("a right click only shows the menu", 1, opened)

        // A double click: the second click event is ignored, the system's action opens once.
        icon.mouseListeners.forEach { it.mouseClicked(click(MouseEvent.BUTTON1, 2)) }
        icon.actionListeners.forEach { it.actionPerformed(ActionEvent(icon, ActionEvent.ACTION_PERFORMED, "")) }
        assertEquals(2, opened)

        val menu = icon.popupMenu
        assertEquals("without settings there is no Settings menu", listOf("Open", "-", "Quit"), (0 until menu.itemCount).map { menu.getItem(it).label })
        menu.getItem(0).actionListeners.forEach { it.actionPerformed(ActionEvent(menu.getItem(0), ActionEvent.ACTION_PERFORMED, "")) }
        menu.getItem(2).actionListeners.forEach { it.actionPerformed(ActionEvent(menu.getItem(2), ActionEvent.ACTION_PERFORMED, "")) }
        assertEquals(3, opened)
        assertEquals(1, quit)
        assertTrue(icon.isImageAutoSize)
    }

    @Test
    fun settingsInTheMenuChangeWithoutOpeningTheWindowAndFollowChangesMadeElsewhere() {
        assumeTrue(DesktopTrayIcon.isSupported)
        var startsWithComputer = false
        var keepInTray = true
        var systemRefuses = false
        var menuOpenings = 0
        val tray = DesktopTrayIcon(
            image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB),
            tooltip = "Life Assistant",
            openLabel = "Open",
            quitLabel = "Quit",
            onOpen = {},
            onQuit = {},
            settingsLabel = "Settings",
            toggles = listOf(
                TrayToggle("Start with Windows", { startsWithComputer }, { wanted -> if (systemRefuses) false else { startsWithComputer = wanted; true } }),
                TrayToggle("Keep in the tray when closed", { keepInTray }, { wanted -> keepInTray = wanted; true }),
            ),
            beforeMenu = { menuOpenings++ },
        )
        val menu = tray.icon.popupMenu
        assertEquals(listOf("Open", "Settings", "-", "Quit"), (0 until menu.itemCount).map { menu.getItem(it).label })
        val settings = menu.getItem(1) as Menu
        val start = settings.getItem(0) as CheckboxMenuItem
        val keep = settings.getItem(1) as CheckboxMenuItem
        assertEquals(listOf("Start with Windows", "Keep in the tray when closed"), listOf(start.label, keep.label))
        assertEquals(listOf(false, true), listOf(start.state, keep.state))

        fun pick(item: CheckboxMenuItem) {
            // What AWT does when the item is chosen: flip the check mark, then tell the listeners.
            item.state = !item.state
            val change = if (item.state) ItemEvent.SELECTED else ItemEvent.DESELECTED
            item.itemListeners.forEach { it.itemStateChanged(ItemEvent(item, ItemEvent.ITEM_STATE_CHANGED, item.label, change)) }
        }
        pick(start)
        assertTrue(startsWithComputer)
        assertTrue(start.state)
        pick(keep)
        assertFalse(keepInTray)
        assertFalse(keep.state)

        // The system refused: the check mark goes back.
        systemRefuses = true
        pick(start)
        assertTrue(startsWithComputer)
        assertTrue(start.state)

        // Changed in the Settings page (or Task Manager): the menu shows it when it opens next.
        startsWithComputer = false
        keepInTray = true
        tray.icon.mouseListeners.forEach { it.mousePressed(MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0L, 0, 10, 10, 1, true, MouseEvent.BUTTON3)) }
        assertEquals(1, menuOpenings)
        assertEquals(listOf(false, true), listOf(start.state, keep.state))
    }

    @Test
    fun theTrayAndTheSettingsPageShareOneSetting() {
        val dir = Files.createTempDirectory("background-settings").toFile()
        try {
            var saved: String? = null
            val entry = object : AutostartEntry {
                override fun read() = saved
                override fun write(command: String): Boolean { saved = command; return true }
                override fun delete(): Boolean { saved = null; return true }
            }
            val config = DesktopConfigStore(File(dir, "desktop.properties"))
            val background = DesktopBackgroundSettings(config, DesktopAutostart(entry) { File("C:/Apps/Life Assistant.exe") })
            assertFalse(background.startsWithComputer.value)
            assertFalse(background.keepInTray.value)

            assertTrue(background.setStartsWithComputer(true))
            assertTrue(background.startsWithComputer.value)
            assertTrue("starting in the tray keeps it there when closed", background.keepInTray.value)
            assertTrue(config.keepInTray())

            background.setKeepInTray(false)
            assertFalse(config.keepInTray())

            // Removed outside the app, for example in Task Manager.
            saved = null
            background.reload()
            assertFalse(background.startsWithComputer.value)
        } finally {
            dir.deleteRecursively()
        }
    }
}
