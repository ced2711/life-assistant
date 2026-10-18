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

/** Where the running app listens on this computer; written to each of [DesktopAgentEndpoint.files]. */
data class DesktopAgentEndpoint(val port: Int, val token: String, val pid: Long? = null) {
    companion object {
        fun file(appDirectory: File) = File(appDirectory, "agent-endpoint.json")

        /**
         * A copy in the user's temporary folder, which every program sees the same way. AI clients
         * installed from the Microsoft Store (Claude Desktop among them) give the programs they
         * start a private view of AppData: an endpoint file written once from inside that view
         * hides the real one from then on, and bridges started there kept reaching for a port
         * from a day before. The name follows the data folder, so a test's folder has its own.
         */
        fun sharedFile(appDirectory: File, sharedDirectory: File = defaultSharedDirectory()): File {
            val digest = MessageDigest.getInstance("SHA-256").digest(appDirectory.absolutePath.lowercase().toByteArray(StandardCharsets.UTF_8))
            val key = digest.take(6).joinToString("") { "%02x".format(it) }
            return File(sharedDirectory, "life-assistant-${DesktopInstanceLock.userKey()}-$key.agent-endpoint.json")
        }

        fun defaultSharedDirectory() = File(System.getProperty("java.io.tmpdir"))

        /** Where the app publishes its endpoint, the copy every program can see first. */
        fun files(appDirectory: File, sharedDirectory: File = defaultSharedDirectory()) =
            listOf(sharedFile(appDirectory, sharedDirectory), file(appDirectory))

        fun read(appDirectory: File): DesktopAgentEndpoint? = readFile(file(appDirectory))

        fun readFile(file: File): DesktopAgentEndpoint? = runCatching {
            val json = Json.parseToJsonElement(file.readText()).jsonObject
            DesktopAgentEndpoint(
                json["port"]!!.jsonPrimitive.intOrNull!!,
                json["token"]!!.jsonPrimitive.contentOrNull!!,
                json["pid"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
            )
        }.getOrNull()

        /** The published endpoints worth trying, in order; one whose app has ended is left out. */
        fun candidates(appDirectory: File, sharedDirectory: File = defaultSharedDirectory()): List<DesktopAgentEndpoint> =
            files(appDirectory, sharedDirectory).mapNotNull(::readFile).distinct().filter { endpoint ->
                endpoint.pid == null || ProcessHandle.of(endpoint.pid).map(ProcessHandle::isAlive).orElse(false)
            }
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
    /** True while the app, just started, is still opening the data by itself. */
    private val opening: () -> Boolean = { false },
    private val openingWaitMillis: Long = 20_000,
    private val sharedDirectory: File = DesktopAgentEndpoint.defaultSharedDirectory(),
    /** How often the endpoint files are checked and, if gone or replaced, written again. */
    private val republishMillis: Long = 60_000,
) {
    private var server: HttpServer? = null
    private var keeper: java.util.concurrent.ScheduledExecutorService? = null
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
        publish(created.address.port)
        // Kept published while the app runs: the temporary folder gets cleaned up now and then.
        keeper = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "life-assistant-agent-endpoint").apply { isDaemon = true } }.also {
            it.scheduleWithFixedDelay({ publish(created.address.port) }, republishMillis, republishMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }

    @Synchronized
    fun stop() {
        keeper?.shutdownNow()
        keeper = null
        server?.stop(0)
        server = null
        DesktopAgentEndpoint.files(appDirectory, sharedDirectory)
            .filter { DesktopAgentEndpoint.readFile(it)?.token == token }
            .forEach(File::delete)
    }

    /** Writes each endpoint file that does not already name this app; a failure is logged, not thrown. */
    @Synchronized
    private fun publish(port: Int) {
        if (server == null) return
        val text = buildJsonObject { put("port", port); put("token", token); put("pid", ProcessHandle.current().pid()) }.toString()
        DesktopAgentEndpoint.files(appDirectory, sharedDirectory).forEach { endpoint ->
            if (DesktopAgentEndpoint.readFile(endpoint)?.token == token) return@forEach
            runCatching { writeReplacing(endpoint, text) }
                .onFailure { DesktopLog.write(appDirectory, "Could not publish the AI assistants' endpoint at $endpoint", it) }
        }
    }

    private fun writeReplacing(target: File, text: String) {
        target.parentFile?.mkdirs()
        val temporary = File.createTempFile("${target.name}.", ".part", target.parentFile)
        try {
            temporary.writeText(text)
            try {
                java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.io.IOException) {
                java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
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
        // Started a moment ago, often by this very request: the data opens by itself in a moment,
        // so wait for it rather than calling the app locked.
        val openBy = System.currentTimeMillis() + openingWaitMillis
        while (opening() && System.currentTimeMillis() < openBy) Thread.sleep(50)
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
        /**
         * Talks to a running app; null when none answers. Each published endpoint is tried in turn
         * while nothing listens there or the token is not the right one, so a stale file is passed
         * over; once a request has gone through, it is not sent anywhere else.
         */
        fun request(
            appDirectory: File,
            path: String,
            body: String? = null,
            timeout: Duration = Duration.ofSeconds(3),
            sharedDirectory: File = DesktopAgentEndpoint.defaultSharedDirectory(),
        ): String? {
            for (endpoint in DesktopAgentEndpoint.candidates(appDirectory, sharedDirectory)) {
                // java.net.http is left out of the bundled runtime; this is all the bridge needs.
                val connection = runCatching { URI("http://127.0.0.1:${endpoint.port}$path").toURL().openConnection() as java.net.HttpURLConnection }
                    .getOrElse { return null }
                try {
                    connection.connectTimeout = 2_000
                    connection.readTimeout = timeout.toMillis().toInt()
                    connection.setRequestProperty("Authorization", "Bearer ${endpoint.token}")
                    if (body != null) {
                        connection.requestMethod = "POST"
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    try {
                        connection.connect()
                    } catch (_: java.io.IOException) {
                        continue // Nothing listens there any more.
                    }
                    val code = runCatching {
                        if (body != null) connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
                        connection.responseCode
                    }.getOrElse { return null }
                    if (code == 401) continue // Another program on that port, or an older token.
                    return runCatching {
                        (if (code in 200..299) connection.inputStream else connection.errorStream)
                            ?.use { it.readBytes().toString(StandardCharsets.UTF_8) }.orEmpty()
                    }.getOrNull()
                } finally {
                    connection.disconnect()
                }
            }
            return null
        }
    }
}
