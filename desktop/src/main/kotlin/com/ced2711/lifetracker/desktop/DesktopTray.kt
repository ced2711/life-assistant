package com.ced2711.lifetracker.desktop

import java.awt.CheckboxMenuItem
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.Menu
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.ItemEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/** An on/off setting in the tray menu: what it shows now, and the change (false when refused). */
internal class TrayToggle(val label: String, val isOn: () -> Boolean, val onChange: (Boolean) -> Boolean)

/**
 * The icon in the system tray. A single left click opens the window; the system's own tray
 * handling only reacts to a double click, which is easy to miss. The right-click menu has Open,
 * Settings with the [toggles] that can be changed without opening the window, and Quit.
 * Reminder notifications appear from this icon too.
 */
internal class DesktopTrayIcon(
    image: Image,
    tooltip: String,
    openLabel: String,
    quitLabel: String,
    private val onOpen: () -> Unit,
    onQuit: () -> Unit,
    settingsLabel: String = "Settings",
    toggles: List<TrayToggle> = emptyList(),
    /** Runs before the menu opens, to read settings that may have changed elsewhere. */
    private val beforeMenu: () -> Unit = {},
) : DesktopNotifier {
    private val toggleItems: List<Pair<TrayToggle, CheckboxMenuItem>> = toggles.map { toggle ->
        val item = CheckboxMenuItem(toggle.label, toggle.isOn())
        item.addItemListener { event ->
            val wanted = event.stateChange == ItemEvent.SELECTED
            if (!toggle.onChange(wanted)) item.state = !wanted
            refreshToggles()
        }
        toggle to item
    }

    val icon: TrayIcon = TrayIcon(
        image,
        tooltip,
        PopupMenu().apply {
            add(MenuItem(openLabel).apply { addActionListener { onOpen() } })
            if (toggleItems.isNotEmpty()) {
                add(Menu(settingsLabel).apply { toggleItems.forEach { (_, item) -> add(item) } })
            }
            addSeparator()
            add(MenuItem(quitLabel).apply { addActionListener { onQuit() } })
        },
    ).apply {
        isImageAutoSize = true
        // A double click or the keyboard (Enter on the focused icon).
        addActionListener { onOpen() }
        addMouseListener(object : MouseAdapter() {
            // Before the menu opens, so it shows changes made in the Settings page or elsewhere.
            override fun mousePressed(event: MouseEvent) {
                runCatching(beforeMenu)
                refreshToggles()
            }

            override fun mouseClicked(event: MouseEvent) {
                if (opensWindow(event)) onOpen()
            }
        })
    }

    /** Makes each check mark match its setting. */
    fun refreshToggles() {
        toggleItems.forEach { (toggle, item) ->
            val on = toggle.isOn()
            if (item.state != on) item.state = on
        }
    }

    /** Puts the icon in the tray; false when the system refuses it. */
    fun install(): Boolean = runCatching { SystemTray.getSystemTray().add(icon); true }.getOrDefault(false)

    fun remove() {
        runCatching { SystemTray.getSystemTray().remove(icon) }
    }

    override fun notify(title: String, message: String) {
        icon.displayMessage(title, message, TrayIcon.MessageType.INFO)
    }

    companion object {
        /** A single left click; the second click of a double click is left to the action listener. */
        fun opensWindow(event: MouseEvent): Boolean = event.button == MouseEvent.BUTTON1 && event.clickCount == 1

        val isSupported: Boolean by lazy { runCatching { SystemTray.isSupported() }.getOrDefault(false) }

        /** The size, in pixels, the tray draws its icons at on the main screen. */
        fun iconPixels(): Int = runCatching {
            val logical = SystemTray.getSystemTray().trayIconSize.width
            val scale = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
            (logical * scale).toInt().coerceAtLeast(16)
        }.getOrDefault(32)
    }
}
