package com.ced2711.lifetracker.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One change an AI assistant made, for the activity list in Settings. */
data class DesktopAgentActivity(val at: Long, val tool: String, val summary: String)

/** Where the running app listens on this computer; written to [DesktopAgentEndpoint.file]. */
data class DesktopAgentEndpoint(val port: Int, val token: String) {
    companion object {
        fun file(appDirectory: File) = File(appDirectory, "agent-endpoint.json")

        fun read(appDirectory: File): DesktopAgentEndpoint? = runCatching {
            val json = Json.parseToJsonElement(file(appDirectory).readText()).jsonObject
            DesktopAgentEndpoint(json["port"]!!.jsonPrimitive.intOrNull!!, json["token"]!!.jsonPrimitive.contentOrNull!!)
        }.getOrNull()
    }
}

/**
 * The running app's door for the `--mcp` bridge and for a second start of the app: a small HTTP
 * server bound to this computer only (127.0.0.1), answering only requests that carry the random
 * token from the endpoint file in the user's own app folder. AI tools work only while the user
 * has turned on AI access; Vault and Confessional are not reachable at all.
 */
class DesktopAgentServer(
    private val appDirectory: File,
    private val tools: DesktopAgentTools,
    private val accessEnabled: () -> Boolean,
    private val changesAllowed: () -> Boolean,
    private val locked: () -> Boolean,
    private val onShowWindow: () -> Unit,
) {
    private var server: HttpServer? = null
    private val token = ByteArray(32).also(SecureRandom()::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    private val json = Json { prettyPrint = false }
    private val _activity = MutableStateFlow<List<DesktopAgentActivity>>(emptyList())

    /** The most recent changes made by AI assistants, newest first. */
    val activity: StateFlow<List<DesktopAgentActivity>> = _activity.asStateFlow()

    fun start() {
        if (server != null) return
        val created = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        created.executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "life-assistant-agent").apply { isDaemon = true } }
        created.createContext("/") { exchange -> exchange.use { handle(it) } }
        created.start()
        server = created
        val endpoint = DesktopAgentEndpoint.file(appDirectory)
        val temporary = File(appDirectory, "${endpoint.name}.part")
        temporary.writeText(buildJsonObject { put("port", created.address.port); put("token", token); put("pid", ProcessHandle.current().pid()) }.toString())
        runCatching { java.nio.file.Files.move(temporary.toPath(), endpoint.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE) }
            .onFailure { java.nio.file.Files.move(temporary.toPath(), endpoint.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
    }

    fun stop() {
        server?.stop(0)
        server = null
        DesktopAgentEndpoint.read(appDirectory)?.takeIf { it.token == token }?.let { DesktopAgentEndpoint.file(appDirectory).delete() }
    }

    private fun handle(exchange: HttpExchange) {
        val presented = exchange.requestHeaders.getFirst("Authorization").orEmpty().removePrefix("Bearer ")
        if (!MessageDigest.isEqual(presented.toByteArray(), token.toByteArray())) return exchange.reply(401, error("Not allowed"))
        when (exchange.requestURI.path) {
            "/show" -> {
                onShowWindow()
                exchange.reply(200, buildJsonObject { put("shown", true) })
            }
            "/status" -> exchange.reply(200, buildJsonObject {
                put("access", accessEnabled())
                put("changes", changesAllowed())
                put("locked", locked())
            })
            "/call" -> call(exchange)
            else -> exchange.reply(404, error("Unknown request"))
        }
    }

    private fun call(exchange: HttpExchange) {
        if (!accessEnabled()) {
            return exchange.reply(200, error("AI access is off. The user can turn it on in Life Assistant → Settings → AI assistants."))
        }
        if (locked()) return exchange.reply(200, error("Life Assistant is locked. Ask the user to unlock it, then try again."))
        val request = runCatching { Json.parseToJsonElement(exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)).jsonObject }
            .getOrElse { return exchange.reply(400, error("Malformed request")) }
        val name = request["name"]?.jsonPrimitive?.contentOrNull ?: return exchange.reply(400, error("Missing tool name"))
        val arguments = request["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        val result = try {
            runBlocking { tools.call(name, arguments, changesAllowed()) }
        } catch (failure: DesktopAgentException) {
            return exchange.reply(200, error(failure.message ?: "The request could not be carried out"))
        } catch (failure: IllegalArgumentException) {
            return exchange.reply(200, error(failure.message ?: "The request was not valid"))
        } catch (failure: IllegalStateException) {
            return exchange.reply(200, error(failure.message ?: "The request could not be carried out"))
        }
        if (tools.definitions.firstOrNull { it.name == name }?.readOnly == false) record(name, arguments)
        exchange.reply(200, buildJsonObject { put("result", result) })
    }

    private fun record(tool: String, arguments: JsonObject) {
        val summary = listOf("title", "id", "amount", "date", "name").mapNotNull { key ->
            arguments[key]?.let { value -> "$key: ${(value as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: value}" }
        }.joinToString(", ").take(120)
        _activity.update { (listOf(DesktopAgentActivity(System.currentTimeMillis(), tool, summary)) + it).take(50) }
    }

    private fun error(message: String) = buildJsonObject { put("error", message) }

    private fun HttpExchange.reply(code: Int, body: JsonObject) {
        val bytes = json.encodeToString(JsonObject.serializer(), body).toByteArray(StandardCharsets.UTF_8)
        responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        sendResponseHeaders(code, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private inline fun <T> HttpExchange.use(block: (HttpExchange) -> T): T = try {
        block(this)
    } finally {
        close()
    }

    companion object {
        /** Talks to a running app; null when none answers. */
        fun request(appDirectory: File, path: String, body: String? = null, timeout: Duration = Duration.ofSeconds(3)): String? {
            val endpoint = DesktopAgentEndpoint.read(appDirectory) ?: return null
            return runCatching {
                // java.net.http is left out of the bundled runtime; this is all the bridge needs.
                val connection = URI("http://127.0.0.1:${endpoint.port}$path").toURL().openConnection() as java.net.HttpURLConnection
                try {
                    connection.connectTimeout = 2_000
                    connection.readTimeout = timeout.toMillis().toInt()
                    connection.setRequestProperty("Authorization", "Bearer ${endpoint.token}")
                    if (body != null) {
                        connection.requestMethod = "POST"
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
                    }
                    val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                    stream?.use { it.readBytes().toString(StandardCharsets.UTF_8) }.orEmpty()
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }
}
