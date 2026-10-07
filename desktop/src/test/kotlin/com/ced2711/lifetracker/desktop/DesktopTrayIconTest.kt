package com.ced2711.lifetracker.desktop

import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
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
        assertEquals(listOf("Open", "Quit"), (0 until menu.itemCount).map { menu.getItem(it).label })
        menu.getItem(0).actionListeners.forEach { it.actionPerformed(ActionEvent(menu.getItem(0), ActionEvent.ACTION_PERFORMED, "")) }
        menu.getItem(1).actionListeners.forEach { it.actionPerformed(ActionEvent(menu.getItem(1), ActionEvent.ACTION_PERFORMED, "")) }
        assertEquals(3, opened)
        assertEquals(1, quit)
        assertTrue(icon.isImageAutoSize)
    }
}
