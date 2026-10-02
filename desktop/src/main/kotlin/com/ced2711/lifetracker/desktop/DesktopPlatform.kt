package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.desktop.linux.LinuxDeviceProtection
import com.ced2711.lifetracker.desktop.windows.WindowsDeviceProtection
import java.io.File

/**
 * Encrypts small secrets so only this user on this computer can read them again: the remembered
 * data password, cloud sign-in tokens and sealed confessions. Nothing protected this way is ever
 * exported or synced.
 */
interface DeviceProtection {
    fun protect(bytes: ByteArray): ByteArray
    fun unprotect(bytes: ByteArray): ByteArray
}

/** The operating-system specific parts of the desktop app; they live in desktop/windows and desktop/linux. */
object DesktopPlatform {
    val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    val protection: DeviceProtection by lazy {
        if (isWindows) WindowsDeviceProtection else LinuxDeviceProtection(appDirectory())
    }

    /**
     * Windows keeps the `%APPDATA%/Life Tracker` folder of earlier versions so existing data stays
     * where it is. Linux follows the XDG base directory convention.
     */
    fun appDirectory(): File = if (isWindows) {
        val base = System.getenv("APPDATA")?.takeIf(String::isNotBlank)
            ?: File(System.getProperty("user.home"), "AppData/Roaming").absolutePath
        File(base, "Life Tracker")
    } else {
        val base = System.getenv("XDG_DATA_HOME")?.takeIf(String::isNotBlank)
            ?: File(System.getProperty("user.home"), ".local/share").absolutePath
        File(base, "life-assistant")
    }

    /** Picks the wording for text that names the operating system. */
    fun text(windows: String, linux: String): String = if (isWindows) windows else linux

    /** The installed app's own launcher, or null when running from a development build. */
    fun launcher(): java.io.File? = ProcessHandle.current().info().command().orElse(null)
        ?.let(::File)
        ?.takeIf { it.isFile && !it.name.startsWith("java", ignoreCase = true) }

    /** Starts this app with [arguments] so that it keeps running on its own. */
    fun launchDetached(arguments: List<String>): Boolean {
        val launcher = launcher() ?: return false
        return if (isWindows) {
            val commandLine = (listOf(launcher.absolutePath) + arguments).joinToString(" ") { "\"$it\"" }
            com.ced2711.lifetracker.desktop.windows.WindowsDetachedLauncher.launch(commandLine)
        } else {
            val setsid = listOf("/usr/bin/setsid", "/bin/setsid").firstOrNull { File(it).canExecute() }
            runCatching {
                ProcessBuilder(listOfNotNull(setsid, launcher.absolutePath) + arguments)
                    .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            }.isSuccess
        }
    }
}
