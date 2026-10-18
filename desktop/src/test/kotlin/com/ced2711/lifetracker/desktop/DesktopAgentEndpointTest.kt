package com.ced2711.lifetracker.desktop

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The bridge finds the running app even when the endpoint file it sees in AppData is an old one.
 * Programs started by a Microsoft Store app (Claude Desktop) see AppData through a private copy:
 * an endpoint written there once by a copy of the app started from inside keeps hiding the real
 * file, so every AI request went to a port nothing listened on any more.
 */
class DesktopAgentEndpointTest {
    private val root: File = Files.createTempDirectory("life-assistant-endpoint").toFile()
    private val appDirectory = File(root, "app")
    private val shared = File(root, "temp").apply { mkdirs() }
    private val store = DesktopDataStore(appDirectory)
    private val tools = DesktopAgentTools(store)

    @Before
    fun setUp() = runBlocking {
        assertTrue(store.open("endpoint-test-password".toCharArray()))
        Unit
    }

    @After
    fun tearDown() {
        store.close()
        root.deleteRecursively()
    }

    private fun server(republishMillis: Long = 60_000) = DesktopAgentServer(
        appDirectory, tools, accessEnabled = { true }, changesAllowed = { true }, locked = { false }, onShowWindow = {},
        sharedDirectory = shared, republishMillis = republishMillis,
    )

    private fun bridge() = DesktopMcpBridge(
        appDirectory = appDirectory,
        launchApp = { false },
        forward = { path, body -> DesktopAgentServer.request(appDirectory, path, body, sharedDirectory = shared) },
    )

    /** A port nothing listens on and the id of a process that has ended, as an old endpoint has. */
    private fun staleEndpoint(): String {
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        val ended = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").absolutePath, "-version")
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        ended.waitFor()
        return """{"port":$port,"token":"from-yesterday","pid":${ended.pid()}}"""
    }

    private fun callSucceeds(): Boolean {
        val reply = bridge().handle("""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"list_todos","arguments":{}}}""")!!
        val result = Json.parseToJsonElement(reply).jsonObject["result"]!!.jsonObject
        return !result["isError"]!!.jsonPrimitive.boolean
    }

    @Test
    fun theEndpointIsPublishedInTheSharedFolderAndInTheAppFolder() {
        val server = server()
        server.start()
        try {
            val files = DesktopAgentEndpoint.files(appDirectory, shared)
            assertEquals(2, files.size)
            val published = files.map { assertNotNull(DesktopAgentEndpoint.readFile(it)); DesktopAgentEndpoint.readFile(it)!! }
            assertEquals(published[0], published[1])
            assertEquals(ProcessHandle.current().pid(), published[0].pid)
            assertTrue(files[0].startsWith(shared))
            assertTrue("nothing is left half-written", appDirectory.listFiles()!!.none { it.name.endsWith(".part") })
        } finally {
            server.stop()
        }
        assertTrue(DesktopAgentEndpoint.files(appDirectory, shared).none(File::exists))
    }

    @Test
    fun anOldEndpointFileInAppDataDoesNotHideTheRunningApp() {
        val server = server()
        server.start()
        try {
            // What a bridge started by Claude Desktop saw: yesterday's file in place of today's.
            DesktopAgentEndpoint.file(appDirectory).writeText(staleEndpoint())
            assertTrue(callSucceeds())
        } finally {
            server.stop()
        }
    }

    @Test
    fun anOldSharedEndpointIsPassedOverForTheOneInAppData() {
        val server = server()
        server.start()
        try {
            DesktopAgentEndpoint.sharedFile(appDirectory, shared).writeText(staleEndpoint())
            assertTrue(callSucceeds())
        } finally {
            server.stop()
        }
    }

    @Test
    fun anEndpointWhoseAppHasEndedIsNotTried() {
        val stale = File(root, "stale.json").apply { writeText(staleEndpoint()) }
        DesktopAgentEndpoint.file(appDirectory).apply { parentFile.mkdirs() }.writeText(stale.readText())
        assertTrue(DesktopAgentEndpoint.candidates(appDirectory, shared).isEmpty())
        assertNull(DesktopAgentServer.request(appDirectory, "/status", sharedDirectory = shared))
    }

    @Test
    fun aRemovedEndpointIsWrittenAgainWhileTheAppRuns() {
        val server = server(republishMillis = 100)
        server.start()
        try {
            val sharedFile = DesktopAgentEndpoint.sharedFile(appDirectory, shared)
            assertTrue(sharedFile.delete())
            DesktopAgentEndpoint.file(appDirectory).writeText(staleEndpoint())
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline &&
                (!sharedFile.exists() || DesktopAgentEndpoint.read(appDirectory)?.token == "from-yesterday")
            ) Thread.sleep(50)
            assertTrue(sharedFile.exists())
            assertEquals(DesktopAgentEndpoint.readFile(sharedFile), DesktopAgentEndpoint.read(appDirectory))
        } finally {
            server.stop()
        }
    }

    @Test
    fun anEndpointThatCannotBeWrittenIsLoggedAndTheOtherStillPublished() {
        // A folder where the file should be: the move into place fails.
        File(DesktopAgentEndpoint.file(appDirectory), "in-the-way").apply { parentFile.mkdirs() }.writeText("")
        val server = server()
        server.start()
        try {
            assertTrue(callSucceeds())
            val log = DesktopLog.file(appDirectory).readText()
            assertTrue(log, log.contains("Could not publish the AI assistants' endpoint"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun aTestFolderNeverSharesTheRealAppsSharedEndpoint() {
        val other = File(root, "other-app")
        assertFalse(DesktopAgentEndpoint.sharedFile(appDirectory, shared) == DesktopAgentEndpoint.sharedFile(other, shared))
        assertEquals(DesktopAgentEndpoint.sharedFile(appDirectory, shared), DesktopAgentEndpoint.sharedFile(File(appDirectory.path), shared))
    }

    @Test
    fun aPrivateCopyOfAppDataIsFoundByItsProbe() {
        val roaming = File(root, "Roaming")
        val packages = File(root, "Packages")
        val app = File(roaming, "Life Assistant")
        File(packages, "Some.Other.App_1234").mkdirs()
        assertNull(DesktopPlatform.appDataRedirectedBy(roaming, packages, app, probeName = ".probe-1"))
        assertFalse("the probe is removed", File(app, ".probe-1").exists())

        // Redirected: the probe written to AppData lands in the Store app's package folder.
        File(packages, "Claude_pzs8sxrjxfjjc/LocalCache/Roaming/Life Assistant/.probe-2").apply { parentFile.mkdirs() }.writeText("")
        assertEquals("Claude_pzs8sxrjxfjjc", DesktopPlatform.appDataRedirectedBy(roaming, packages, app, probeName = ".probe-2"))

        // A data folder outside AppData is never redirected this way.
        assertNull(DesktopPlatform.appDataRedirectedBy(roaming, packages, File(root, "elsewhere"), probeName = ".probe-2"))
    }
}
