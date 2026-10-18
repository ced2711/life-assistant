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

    private fun packagesDirectory(): File = File(
        System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)
            ?: File(System.getProperty("user.home"), "AppData/Local").absolutePath,
        "Packages",
    )

    /** Marks a start of the app that has already left a redirected AppData; see [appDataRedirectedBy]. */
    const val RELAUNCHED_ARGUMENT = "--relaunched"

    /**
     * The Microsoft Store app (its package folder) whose private copy of AppData this process
     * sees, or null. Store apps such as Claude Desktop run the programs they start, and whatever
     * those start, with new files in AppData redirected into the Store app's own package folder,
     * where they then hide the real ones. A copy of Life Assistant started that way would open
     * and change an old private copy of the data and publish its endpoint where only programs
     * started by that Store app can see it.
     */
    fun appDataRedirectedBy(): String? =
        if (isWindows) appDataRedirectedBy(roamingDirectory(), packagesDirectory(), appDirectory()) else null

    /** Writes a probe into [appDirectory] and looks for it in each package's private AppData. */
    internal fun appDataRedirectedBy(
        roaming: File,
        packages: File,
        appDirectory: File,
        probeName: String = ".redirect-probe-${java.util.UUID.randomUUID()}",
    ): String? {
        val relative = appDirectory.absoluteFile.toRelativeStringOrNull(roaming.absoluteFile) ?: return null
        val probe = File(appDirectory, probeName)
        val created = runCatching { appDirectory.mkdirs(); probe.createNewFile() }.getOrDefault(false)
        if (!created) return null
        return try {
            packages.listFiles()?.firstOrNull { File(it, "LocalCache/Roaming/$relative/$probeName").exists() }?.name
        } finally {
            probe.delete()
        }
    }

    private fun File.toRelativeStringOrNull(base: File): String? =
        relativeToOrNull(base)?.path?.takeIf { it.isNotEmpty() && !it.startsWith("..") }

    /**
     * Starts this app again, with [arguments], outside any redirected AppData: through Windows
     * Management Instrumentation, so it runs as the computer's own child process rather than this
     * one's. False when that did not work.
     */
    fun relaunchOutsideRedirection(arguments: List<String>): Boolean {
        if (!isWindows) return false
        val launcher = launcher() ?: return false
        val commandLine = (listOf(launcher.absolutePath) + arguments + RELAUNCHED_ARGUMENT).joinToString(" ") { "\"$it\"" }
        return com.ced2711.lifetracker.desktop.windows.WindowsDetachedLauncher.launchThroughWmi(commandLine, launcher.parentFile)
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
