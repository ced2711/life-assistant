package com.ced2711.lifetracker.desktop.linux

import com.ced2711.lifetracker.desktop.AutostartEntry
import java.io.File

/**
 * A desktop entry in the XDG autostart folder (`~/.config/autostart`), which GNOME, KDE and other
 * desktops start at sign-in.
 */
class LinuxAutostart(private val file: File, private val name: String) : AutostartEntry {
    override fun read(): String? = runCatching {
        if (!file.isFile) return null
        file.readLines().firstOrNull { it.startsWith("Exec=") }?.removePrefix("Exec=")
    }.getOrNull()

    override fun write(command: String): Boolean = runCatching {
        file.parentFile?.mkdirs()
        file.writeText(
            """
            [Desktop Entry]
            Type=Application
            Name=$name
            Exec=$command
            Terminal=false
            X-GNOME-Autostart-enabled=true
            """.trimIndent() + "\n",
        )
        true
    }.getOrDefault(false)

    override fun delete(): Boolean = runCatching {
        !file.exists() || file.delete()
    }.getOrDefault(false)

    companion object {
        fun forUser(name: String): LinuxAutostart {
            val config = System.getenv("XDG_CONFIG_HOME")?.takeIf(String::isNotBlank)
                ?: File(System.getProperty("user.home"), ".config").absolutePath
            return LinuxAutostart(File(config, "autostart/life-assistant.desktop"), name)
        }
    }
}
