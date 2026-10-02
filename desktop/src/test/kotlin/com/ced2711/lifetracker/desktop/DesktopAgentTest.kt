package com.ced2711.lifetracker.desktop

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class DesktopAgentTest {
    private val root: File = Files.createTempDirectory("life-assistant-agent").toFile()
    private val store = DesktopDataStore(root)
    private val today = LocalDate.of(2026, 10, 2)
    private val tools = DesktopAgentTools(store, today = { today })

    @Before
    fun setUp() = runBlocking {
        assertTrue(store.open("agent-test-password".toCharArray()))
        Unit
    }

    @After
    fun tearDown() {
        store.close()
        root.deleteRecursively()
    }

    private fun call(name: String, allowChanges: Boolean = true, build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}): JsonObject =
        runBlocking { tools.call(name, buildJsonObject(build), allowChanges).jsonObject }

    @Test
    fun todosCanBeCreatedFoundChangedCompletedAndDeleted() {
        val created = call("create_todo") {
            put("title", "Pay rent")
            put("due_date", "tomorrow")
            put("due_time", "9:00")
            put("priority", "high")
            put("category", "Home / Bills")
            putJsonArray("tags") { add(JsonPrimitive("money")) }
            putJsonArray("subtasks") { add(JsonPrimitive("Log in")); add(JsonPrimitive("Transfer")) }
        }
        val id = created["id"]!!.jsonPrimitive.long
        assertEquals("2026-10-03", created["due_date"]!!.jsonPrimitive.content)
        assertEquals("09:00", created["due_time"]!!.jsonPrimitive.content)
        assertEquals("Home / Bills", created["category"]!!.jsonPrimitive.content)
        assertEquals(2, created["subtasks"]!!.jsonArray.size)

        val upcoming = call("list_todos") { put("view", "upcoming") }
        assertEquals(1, upcoming["count"]!!.jsonPrimitive.int)

        val updated = call("update_todo") { put("id", id); put("priority", "urgent"); put("due_time", "") }
        assertEquals("urgent", updated["priority"]!!.jsonPrimitive.content)
        assertEquals(kotlinx.serialization.json.JsonNull, updated["due_time"])
        assertEquals("Pay rent", updated["title"]!!.jsonPrimitive.content)

        val completed = call("complete_todo") { put("id", id) }
        assertTrue(completed["completed"]!!.jsonPrimitive.boolean)
        assertTrue(completed["subtasks"]!!.jsonArray.all { it.jsonObject["done"]!!.jsonPrimitive.boolean })

        call("delete_todo") { put("id", id) }
        assertEquals(0, call("list_todos") { put("view", "all") }["count"]!!.jsonPrimitive.int)
    }

    @Test
    fun ledgerEntriesAndSummary() {
        call("create_ledger_entry") { put("type", "expense"); put("amount", "12.50"); put("date", "2026-10-01"); put("merchant", "Cafe"); putJsonArray("tags") { add(JsonPrimitive("food")) } }
        call("create_ledger_entry") { put("type", "income"); put("amount", "100"); put("date", "2026-10-02") }
        val listed = call("list_ledger_entries") { put("from", "2026-10-01"); put("to", "2026-10-02") }
        assertEquals(2, listed["count"]!!.jsonPrimitive.int)
        assertEquals("87.50", listed["totals"]!!.jsonObject["net"]!!.jsonPrimitive.content)
        val summary = call("ledger_summary") { put("from", "2026-10-01"); put("to", "2026-10-02") }
        assertEquals("food", summary["spending_by_tag"]!!.jsonArray.single().jsonObject["tag"]!!.jsonPrimitive.content)
        try {
            call("create_ledger_entry") { put("type", "expense"); put("amount", "1.005") }
            fail("Three decimals must be refused")
        } catch (expected: DesktopAgentException) {
            assertTrue(expected.message!!.contains("decimal"))
        }
    }

    @Test
    fun notesAndDiaryAppend() {
        val note = call("create_note") { put("title", "Ideas"); put("body", "One"); put("folder", "Projects / 2026") }
        val id = note["id"]!!.jsonPrimitive.long
        val appended = call("update_note") { put("id", id); put("append", "Two") }
        assertEquals("One\n\nTwo", appended["body"]!!.jsonPrimitive.content)
        assertEquals("Projects / 2026", appended["folder"]!!.jsonPrimitive.content)

        call("write_diary") { put("text", "Morning") }
        val diary = call("write_diary") { put("text", "Evening") }
        assertEquals("Morning\n\nEvening", diary["text"]!!.jsonPrimitive.content)
        val overview = call("get_overview")
        assertTrue(overview["diary_written"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun readOnlyAccessRefusesChangesAndVaultIsNeverATool() {
        try {
            call("create_todo", allowChanges = false) { put("title", "Nope") }
            fail("Changes must be refused when only reading is allowed")
        } catch (expected: DesktopAgentException) {
            assertTrue(expected.message!!.contains("turned off"))
        }
        call("list_todos", allowChanges = false)
        assertFalse(DesktopAgentTools.TOOLS.any { it.name.contains("vault", ignoreCase = true) || it.name.contains("confess", ignoreCase = true) })
    }

    @Test
    fun mcpSessionThroughTheRunningApp() {
        var access = true
        val server = DesktopAgentServer(root, tools, accessEnabled = { access }, changesAllowed = { true }, locked = { false }, onShowWindow = {})
        server.start()
        try {
            val bridge = DesktopMcpBridge(appDirectory = root, launchApp = { false })
            val input = listOf(
                """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""",
                """{"jsonrpc":"2.0","method":"notifications/initialized"}""",
                """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""",
                """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"create_todo","arguments":{"title":"From an assistant"}}}""",
                """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"list_todos","arguments":{}}}""",
            ).joinToString("\n", postfix = "\n")
            val output = ByteArrayOutputStream()
            bridge.run(input.byteInputStream(), output)
            val replies = output.toString(Charsets.UTF_8).lines().filter(String::isNotBlank).map { Json.parseToJsonElement(it).jsonObject }
            assertEquals(4, replies.size)
            assertEquals("2025-06-18", replies[0]["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)
            assertTrue(replies[1]["result"]!!.jsonObject["tools"]!!.jsonArray.size > 10)
            assertFalse(replies[2]["result"]!!.jsonObject["isError"]!!.jsonPrimitive.boolean)
            val listed = replies[3]["result"]!!.jsonObject["structuredContent"]!!.jsonObject
            assertEquals("From an assistant", listed["todos"]!!.jsonArray.single().jsonObject["title"]!!.jsonPrimitive.content)
            assertEquals("create_todo", server.activity.value.single().tool)

            access = false
            val refused = bridge.handle("""{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"list_todos","arguments":{}}}""")!!
            val result = Json.parseToJsonElement(refused).jsonObject["result"]!!.jsonObject
            assertTrue(result["isError"]!!.jsonPrimitive.boolean)
            assertTrue((result["content"] as JsonArray).single().jsonObject["text"]!!.jsonPrimitive.content.contains("AI access is off"))
        } finally {
            server.stop()
        }
        assertFalse(DesktopAgentEndpoint.file(root).exists())
    }
}

class DesktopAgentSetupTest {
    @Test
    fun claudeDesktopConfigKeepsEverythingElse() {
        val root = Files.createTempDirectory("life-assistant-claude-config").toFile()
        try {
            val config = File(root, "Claude/claude_desktop_config.json")
            config.parentFile.mkdirs()
            config.writeText("""{"preferences":{"sidebarMode":"x"},"mcpServers":{"other":{"command":"other.exe"}}}""")
            val launcher = File(root, "Life Assistant.exe").apply { writeText("") }
            assertTrue(DesktopAgentSetup.connectClaudeDesktop(launcher, config).isSuccess)
            val json = Json.parseToJsonElement(config.readText()).jsonObject
            assertEquals("x", json["preferences"]!!.jsonObject["sidebarMode"]!!.jsonPrimitive.content)
            assertEquals("other.exe", json["mcpServers"]!!.jsonObject["other"]!!.jsonObject["command"]!!.jsonPrimitive.content)
            val entry = json["mcpServers"]!!.jsonObject["life-assistant"]!!.jsonObject
            assertEquals(launcher.absolutePath, entry["command"]!!.jsonPrimitive.content)
            assertEquals("--mcp", entry["args"]!!.jsonArray.single().jsonPrimitive.content)
            assertTrue(File(config.parentFile, "claude_desktop_config.json.before-life-assistant").isFile)
        } finally {
            root.deleteRecursively()
        }
    }
}
