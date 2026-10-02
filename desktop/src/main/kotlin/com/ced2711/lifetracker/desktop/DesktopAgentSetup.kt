package com.ced2711.lifetracker.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * One-click setup of AI assistants on this computer: registers `Life Assistant --mcp` with
 * Claude Desktop (its config file) and Claude Code (its own `claude mcp add` command), and gives
 * the same settings as text for any other assistant that speaks MCP.
 */
object DesktopAgentSetup {
    const val SERVER_NAME = "life-assistant"

    /** The command assistants start; null in a development build without an installed launcher. */
    fun launcher(): File? = DesktopPlatform.launcher()

    /** The `mcpServers` entry, for assistants configured by hand. */
    fun manualConfig(launcher: File): String = Json { prettyPrint = true }.encodeToString(
        JsonObject.serializer(),
        buildJsonObject { put("mcpServers", buildJsonObject { put(SERVER_NAME, serverEntry(launcher)) }) },
    )

    fun claudeDesktopConfig(): File = if (DesktopPlatform.isWindows) {
        File(System.getenv("APPDATA") ?: File(System.getProperty("user.home"), "AppData/Roaming").path, "Claude/claude_desktop_config.json")
    } else {
        File(System.getProperty("user.home"), ".config/Claude/claude_desktop_config.json")
    }

    fun claudeDesktopInstalled(): Boolean = claudeDesktopConfig().parentFile.isDirectory

    fun claudeDesktopConnected(launcher: File? = launcher()): Boolean = runCatching {
        val servers = Json.parseToJsonElement(claudeDesktopConfig().readText()).jsonObject["mcpServers"]?.jsonObject
        servers?.get(SERVER_NAME)?.jsonObject?.get("command")?.let { (it as JsonPrimitive).content } == launcher?.absolutePath
    }.getOrDefault(false)

    /**
     * Adds Life Assistant to Claude Desktop's config, keeping everything else in it (a copy of the
     * old file is kept next to it). Claude Desktop reads it when it starts.
     */
    fun connectClaudeDesktop(launcher: File, config: File = claudeDesktopConfig()): Result<Unit> = runCatching {
        config.parentFile.mkdirs()
        val existing = if (config.isFile) Json.parseToJsonElement(config.readText()).jsonObject else JsonObject(emptyMap())
        val servers = (existing["mcpServers"] as? JsonObject).orEmpty() + (SERVER_NAME to serverEntry(launcher))
        val updated = JsonObject(existing + ("mcpServers" to JsonObject(servers)))
        if (config.isFile) Files.copy(config.toPath(), File(config.parentFile, "${config.name}.before-life-assistant").toPath(), StandardCopyOption.REPLACE_EXISTING)
        val temporary = File(config.parentFile, "${config.name}.life-assistant.part")
        temporary.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), updated))
        Files.move(temporary.toPath(), config.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    /** The `claude` command of Claude Code, if installed. */
    fun claudeCodeCommand(): File? {
        val names = if (DesktopPlatform.isWindows) listOf("claude.exe", "claude.cmd", "claude") else listOf("claude")
        val path = System.getenv("PATH").orEmpty().split(File.pathSeparator).map(::File) +
            File(System.getProperty("user.home"), ".local/bin") + File(System.getProperty("user.home"), ".claude/local")
        return path.flatMap { directory -> names.map { File(directory, it) } }.firstOrNull { it.isFile && it.canExecute() }
    }

    /** Registers Life Assistant for every Claude Code project of this user. */
    fun connectClaudeCode(launcher: File, claude: File): Result<Unit> = runCatching {
        // Re-adding replaces an older registration (for example from a previous install path).
        run(listOf(claude.absolutePath, "mcp", "remove", "--scope", "user", SERVER_NAME))
        val added = run(listOf(claude.absolutePath, "mcp", "add", "--scope", "user", SERVER_NAME, "--", launcher.absolutePath, "--mcp"))
        check(added.first == 0) { added.second.ifBlank { "Claude Code could not add Life Assistant." } }
    }

    private fun run(command: List<String>): Pair<Int, String> {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        process.outputStream.close()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("Claude Code did not answer.")
        }
        return process.exitValue() to output.trim().take(400)
    }

    private fun serverEntry(launcher: File) = buildJsonObject {
        put("command", launcher.absolutePath)
        put("args", JsonArray(listOf(JsonPrimitive("--mcp"))))
    }

    private fun JsonObject?.orEmpty(): Map<String, kotlinx.serialization.json.JsonElement> = this ?: emptyMap()
}
