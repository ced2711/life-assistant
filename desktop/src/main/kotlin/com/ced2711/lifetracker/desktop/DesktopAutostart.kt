package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.desktop.linux.LinuxAutostart
import com.ced2711.lifetracker.desktop.windows.WindowsAutostart
import com.ced2711.lifetracker.domain.model.AppIdentity
import java.io.File

/** Where the operating system keeps the command it runs at sign-in. */
interface AutostartEntry {
    /** The saved command, or null when there is none. */
    fun read(): String?
    fun write(command: String): Boolean
    fun delete(): Boolean
}

/**
 * Starting Life Assistant when the user signs in. It starts with [BACKGROUND_ARGUMENT], so it
 * waits in the tray without opening its window: reminders, sync and AI assistants are ready, and
 * the tray icon opens the window. Only an installed app can do this; a development build has no
 * launcher of its own to start.
 */
class DesktopAutostart(
    private val entry: AutostartEntry,
    private val launcher: () -> File? = DesktopPlatform::launcher,
) {
    val isAvailable: Boolean get() = launcher() != null

    fun isEnabled(): Boolean = entry.read() != null

    fun setEnabled(enabled: Boolean): Boolean {
        if (!enabled) return entry.delete()
        val app = launcher() ?: return false
        return entry.write(command(app))
    }

    /** Points a saved entry at this launcher again, in case the app was moved or reinstalled elsewhere. */
    fun refresh() {
        val saved = entry.read() ?: return
        val app = launcher() ?: return
        val current = command(app)
        if (saved != current) entry.write(current)
    }

    companion object {
        const val BACKGROUND_ARGUMENT = "--background"

        fun command(launcher: File): String = "\"${launcher.absolutePath}\" $BACKGROUND_ARGUMENT"

        val system: DesktopAutostart by lazy {
            DesktopAutostart(if (DesktopPlatform.isWindows) WindowsAutostart(AppIdentity.NAME) else LinuxAutostart.forUser(AppIdentity.NAME))
        }
    }
}
