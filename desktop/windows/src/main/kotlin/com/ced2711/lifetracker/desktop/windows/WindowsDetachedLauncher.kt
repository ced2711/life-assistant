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

    /**
     * Starts [commandLine] as a child of the WMI service instead of this process. Microsoft Store
     * apps give the programs they start a private view of AppData that neither breaking away from
     * the job nor the desktop-app process attributes leave; a process WMI creates starts from the
     * real one. PowerShell makes the call, with the command line passed in the environment so no
     * quoting can change it.
     */
    fun launchThroughWmi(commandLine: String, workingDirectory: java.io.File): Boolean = runCatching {
        val windows = System.getenv("SystemRoot")?.takeIf(String::isNotBlank) ?: "C:\\Windows"
        val powershell = java.io.File(windows, "System32\\WindowsPowerShell\\v1.0\\powershell.exe")
        val script = "try { \$r = Invoke-CimMethod -ErrorAction Stop -ClassName Win32_Process -MethodName Create -Arguments @{ " +
            "CommandLine = \$env:LIFE_ASSISTANT_COMMAND_LINE; CurrentDirectory = \$env:LIFE_ASSISTANT_DIRECTORY }; " +
            "if (\$r -and \$r.ReturnValue -eq 0) { exit 0 } else { exit 1 } } catch { exit 1 }"
        val encoded = java.util.Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        val process = ProcessBuilder(powershell.absolutePath, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
            .redirectInput(ProcessBuilder.Redirect.from(java.io.File("NUL")))
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .apply {
                environment()["LIFE_ASSISTANT_COMMAND_LINE"] = commandLine
                environment()["LIFE_ASSISTANT_DIRECTORY"] = workingDirectory.absolutePath
            }
            .start()
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly()
            false
        } else {
            process.exitValue() == 0
        }
    }.getOrDefault(false)

    private const val DETACHED_PROCESS = 0x00000008
    private const val CREATE_NEW_PROCESS_GROUP = 0x00000200
    private const val CREATE_BREAKAWAY_FROM_JOB = 0x01000000
}
