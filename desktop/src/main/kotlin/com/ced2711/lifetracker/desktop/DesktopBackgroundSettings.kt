package com.ced2711.lifetracker.desktop

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How the app runs in the background: whether it starts with the computer and whether closing the
 * window keeps it in the tray. Both the Settings page and the tray menu change these, so each
 * shows what the other set.
 */
class DesktopBackgroundSettings(
    private val config: DesktopConfigStore,
    private val autostart: DesktopAutostart = DesktopAutostart.system,
) {
    /** Only an installed app can start with the computer. */
    val canStartWithComputer: Boolean get() = autostart.isAvailable

    private val startsWithComputerState = MutableStateFlow(canStartWithComputer && autostart.isEnabled())
    val startsWithComputer: StateFlow<Boolean> = startsWithComputerState.asStateFlow()

    private val keepInTrayState = MutableStateFlow(config.keepInTray())
    val keepInTray: StateFlow<Boolean> = keepInTrayState.asStateFlow()

    /** False when the system did not take the change; nothing changes then. */
    fun setStartsWithComputer(enabled: Boolean): Boolean {
        if (!autostart.setEnabled(enabled)) return false
        startsWithComputerState.value = enabled
        // Started in the tray, it should stay there when its window is closed.
        if (enabled) setKeepInTray(true)
        return true
    }

    fun setKeepInTray(enabled: Boolean) {
        config.setKeepInTray(enabled)
        keepInTrayState.value = enabled
    }

    /** Reads both again, for a change made elsewhere (such as Task Manager → Startup apps). */
    fun reload() {
        startsWithComputerState.value = canStartWithComputer && autostart.isEnabled()
        keepInTrayState.value = config.keepInTray()
    }
}

/** The app's [DesktopBackgroundSettings], shared with the tray; null in tests that run the app alone. */
val LocalDesktopBackgroundSettings = staticCompositionLocalOf<DesktopBackgroundSettings?> { null }
