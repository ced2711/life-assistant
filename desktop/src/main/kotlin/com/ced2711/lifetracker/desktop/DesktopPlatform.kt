package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.desktop.linux.LinuxDeviceProtection
import com.ced2711.lifetracker.desktop.windows.WindowsDeviceProtection
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
     * Windows keeps the data in `%APPDATA%/Life Assistant`. Versions before 2.0.4 used
     * `%APPDATA%/Life Tracker`, the app's old name; until [moveLegacyAppDirectory] has moved that
     * folder, it is still the one in use. Linux follows the XDG base directory convention.
     */
    fun appDirectory(): File = if (isWindows) {
        windowsAppDirectory(roamingDirectory())
    } else {
        val base = System.getenv("XDG_DATA_HOME")?.takeIf(String::isNotBlank)
            ?: File(System.getProperty("user.home"), ".local/share").absolutePath
        File(base, "life-assistant")
    }

    /**
     * Gives the data folder of versions before 2.0.4 the app's name. Runs once at startup, before
     * anything opens the data and only when no other copy of the app is running. If the folder
     * cannot be moved (a file in it is in use), the old folder simply stays in use.
     */
    fun moveLegacyAppDirectory(): File = if (isWindows) moveLegacyAppDirectory(roamingDirectory()) else appDirectory()

    internal const val APP_FOLDER = "Life Assistant"
    internal const val LEGACY_APP_FOLDER = "Life Tracker"

    internal fun windowsAppDirectory(base: File): File {
        val current = File(base, APP_FOLDER)
        val legacy = File(base, LEGACY_APP_FOLDER)
        return if (!current.hasFiles() && legacy.hasFiles()) legacy else current
    }

    internal fun moveLegacyAppDirectory(base: File): File {
        val current = File(base, APP_FOLDER)
        val legacy = File(base, LEGACY_APP_FOLDER)
        if (current.hasFiles() || !legacy.hasFiles()) return windowsAppDirectory(base)
        runCatching {
            // An empty folder of the new name (left by something else) must not hide the data.
            if (current.isDirectory) Files.delete(current.toPath())
            Files.move(legacy.toPath(), current.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }
        return windowsAppDirectory(base)
    }

    private fun File.hasFiles(): Boolean = listFiles()?.isNotEmpty() == true

    private fun roamingDirectory(): File = File(
        System.getenv("APPDATA")?.takeIf(String::isNotBlank)
            ?: File(System.getProperty("user.home"), "AppData/Roaming").absolutePath,
    )

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
