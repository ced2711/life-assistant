package com.ced2711.lifetracker.desktop.windows

import com.ced2711.lifetracker.desktop.AutostartEntry
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/**
 * The value under the user's `Run` key that Windows starts at sign-in. It is per user, so no
 * administrator rights are needed, and it shows in Task Manager → Startup apps like any other.
 */
class WindowsAutostart(
    private val name: String,
    private val key: String = RUN_KEY,
) : AutostartEntry {
    override fun read(): String? = runCatching {
        if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, key, name)) {
            Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, key, name)
        } else {
            null
        }
    }.getOrNull()

    override fun write(command: String): Boolean = runCatching {
        if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, key)) Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, key)
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, key, name, command)
        true
    }.getOrDefault(false)

    override fun delete(): Boolean = runCatching {
        if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, key, name)) {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, key, name)
        }
        true
    }.getOrDefault(false)

    companion object {
        const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    }
}
