package com.ced2711.lifetracker.desktop.windows

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef

/**
 * Starts the app so it outlives whoever started it. AI clients run the `--mcp` bridge inside a
 * Windows job object that may close all its processes when the client quits; breaking away from
 * that job keeps Life Assistant running in the tray afterwards.
 */
object WindowsDetachedLauncher {
    fun launch(commandLine: String): Boolean {
        val kernel = Kernel32.INSTANCE
        val flags = CREATE_BREAKAWAY_FROM_JOB or DETACHED_PROCESS or CREATE_NEW_PROCESS_GROUP
        listOf(flags, DETACHED_PROCESS or CREATE_NEW_PROCESS_GROUP).forEach { creationFlags ->
            val startup = WinBase.STARTUPINFO()
            val process = WinBase.PROCESS_INFORMATION()
            val started = kernel.CreateProcess(
                null,
                commandLine,
                null,
                null,
                false,
                WinDef.DWORD(creationFlags.toLong()),
                null,
                null,
                startup,
                process,
            )
            if (started) {
                kernel.CloseHandle(process.hThread)
                kernel.CloseHandle(process.hProcess)
                return true
            }
            // The job may not allow breaking away; then start inside it.
        }
        return false
    }

    private const val DETACHED_PROCESS = 0x00000008
    private const val CREATE_NEW_PROCESS_GROUP = 0x00000200
    private const val CREATE_BREAKAWAY_FROM_JOB = 0x01000000
}
