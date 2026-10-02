package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.domain.model.AppIdentity
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * `Life Assistant.exe --mcp`: a Model Context Protocol server over standard input and output, so
 * AI assistants on this computer (Claude Desktop, Claude Code and others) can read and change the
 * user's data. Calls go to the running app, which owns the data, syncs it and shows reminders;
 * when it is not running it is started in the tray. See https://modelcontextprotocol.io
 */
class DesktopMcpBridge(
    private val appDirectory: File = DesktopPlatform.appDirectory(),
    private val launchApp: () -> Boolean = { DesktopPlatform.launchDetached(listOf("--background")) },
    private val forward: (String, String?) -> String? = { path, body -> DesktopAgentServer.request(appDirectory, path, body, Duration.ofSeconds(60)) },
    private val waitStepMillis: Long = 500,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = true }

    fun run(input: java.io.InputStream, output: OutputStream) {
        val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
        val writer = PrintStream(output, false, StandardCharsets.UTF_8)
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue
            val reply = handle(line) ?: continue
            writer.print(reply)
            writer.print('\n')
            writer.flush()
        }
    }

    /** The answer to one JSON-RPC message, or null for notifications. */
    fun handle(line: String): String? {
        val message = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull()
            ?: return error(JsonNull, -32700, "Parse error")
        val id = message["id"] ?: return null // A notification, such as notifications/initialized.
        val method = message["method"]?.jsonPrimitive?.contentOrNull ?: return error(id, -32600, "Invalid request")
        val params = message["params"] as? JsonObject ?: JsonObject(emptyMap())
        return when (method) {
            "initialize" -> result(id, initialize(params))
            "ping" -> result(id, JsonObject(emptyMap()))
            "tools/list" -> result(id, toolList())
            "tools/call" -> result(id, callTool(params))
            "resources/list" -> result(id, buildJsonObject { putJsonArray("resources") {} })
            "prompts/list" -> result(id, buildJsonObject { putJsonArray("prompts") {} })
            else -> error(id, -32601, "Method not found: $method")
        }
    }

    private fun initialize(params: JsonObject): JsonObject {
        val requested = params["protocolVersion"]?.jsonPrimitive?.contentOrNull
        return buildJsonObject {
            put("protocolVersion", requested?.takeIf { it in SUPPORTED_VERSIONS } ?: SUPPORTED_VERSIONS.first())
            putJsonObject("capabilities") { putJsonObject("tools") { put("listChanged", false) } }
            putJsonObject("serverInfo") {
                put("name", "life-assistant")
                put("title", AppIdentity.NAME)
                put("version", AppIdentity.VERSION)
            }
            put("instructions", INSTRUCTIONS)
        }
    }

    private fun toolList(): JsonObject = buildJsonObject {
        putJsonArray("tools") {
            DesktopAgentTools.TOOLS.forEach { tool ->
                add(buildJsonObject {
                    put("name", tool.name)
                    put("title", tool.title)
                    put("description", tool.description)
                    put("inputSchema", tool.inputSchema)
                    putJsonObject("annotations") {
                        put("title", tool.title)
                        put("readOnlyHint", tool.readOnly)
                        put("destructiveHint", tool.destructive)
                        put("openWorldHint", false)
                    }
                })
            }
        }
    }

    private fun callTool(params: JsonObject): JsonObject {
        val name = params["name"]?.jsonPrimitive?.contentOrNull ?: return toolError("Missing tool name")
        val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        val request = buildJsonObject { put("name", name); put("arguments", arguments) }.toString()
        val answer = forward("/call", request) ?: run {
            if (!startApp()) return toolError(NOT_RUNNING)
            forward("/call", request)
        } ?: return toolError(NOT_RUNNING)
        val reply = runCatching { Json.parseToJsonElement(answer).jsonObject }.getOrNull() ?: return toolError("Life Assistant gave an unreadable answer.")
        reply["error"]?.jsonPrimitive?.contentOrNull?.let { return toolError(it) }
        val value = reply["result"] ?: JsonNull
        return buildJsonObject {
            putJsonArray("content") {
                add(buildJsonObject { put("type", "text"); put("text", value.toString()) })
            }
            if (value is JsonObject) put("structuredContent", value)
            put("isError", false)
        }
    }

    /** Starts the app in the tray and waits until it answers, up to half a minute. */
    private fun startApp(): Boolean {
        if (!launchApp()) return false
        repeat((30_000 / waitStepMillis).toInt()) {
            Thread.sleep(waitStepMillis)
            if (forward("/status", null) != null) return true
        }
        return false
    }

    private fun toolError(message: String) = buildJsonObject {
        putJsonArray("content") { add(buildJsonObject { put("type", "text"); put("text", message) }) }
        put("isError", true)
    }

    private fun result(id: JsonElement, value: JsonObject): String =
        buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("result", value) }.toString()

    private fun error(id: JsonElement, code: Int, message: String): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        putJsonObject("error") { put("code", code); put("message", message) }
    }.toString()

    companion object {
        private val SUPPORTED_VERSIONS = listOf("2025-06-18", "2025-03-26", "2024-11-05")

        private const val NOT_RUNNING =
            "Life Assistant is not running and could not be started. Ask the user to open Life Assistant on this computer."

        private const val INSTRUCTIONS =
            "Life Assistant is the user's personal organizer: todos, a money ledger (USD), notes, a diary and a calendar. " +
                "Start with get_overview. Dates are YYYY-MM-DD in the user's time zone, times 24-hour, amounts in dollars. " +
                "Changes sync to the user's other devices. Ask before deleting anything. Passwords (Vault) are never available here."

        /** Entry point for `--mcp`: keeps standard output for the protocol only. */
        fun main() {
            val protocolOut = System.out
            System.setOut(System.err)
            DesktopMcpBridge().run(System.`in`, protocolOut)
        }
    }
}
