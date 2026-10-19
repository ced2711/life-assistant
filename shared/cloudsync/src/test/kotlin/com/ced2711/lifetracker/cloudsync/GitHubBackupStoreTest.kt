package com.ced2711.lifetracker.cloudsync

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubBackupStoreTest {
    private lateinit var server: MockWebServer
    private lateinit var github: FakeGitHub
    private val directory: File = Files.createTempDirectory("github-store").toFile()

    @Before
    fun setUp() {
        github = FakeGitHub()
        server = MockWebServer().apply { dispatcher = github; start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
        directory.deleteRecursively()
    }

    private fun store() = GitHubBackupStore(
        tokenProvider = { "test-token" },
        repository = GitHubRepository("owner", "backups"),
        ioDispatcher = Dispatchers.Unconfined,
        apiBaseUrl = server.url("/"),
    )

    private fun file(name: String, bytes: ByteArray) = File(directory, name).apply { writeBytes(bytes) }

    private fun revision(createdAt: Long, base: String? = null) = NewCloudRevision(
        createdAt = createdAt,
        deviceId = "device-1234-abcd",
        baseRevisionId = base,
        contentFingerprint = "a".repeat(64),
    )

    @Test
    fun emptyRepositoryIsInitializedAndBackupsRoundTrip() = runBlocking {
        github.emptyRepository = true
        val store = store()
        assertEquals(emptyList<CloudRevision>(), store.listRevisions())

        val payload = ByteArray(300_000) { (it % 251).toByte() }
        val first = store.uploadRevision(file("first.tlb", payload), revision(1))
        val second = store.uploadRevision(file("second.tlb", "second".encodeToByteArray()), revision(2, first.fileId))

        val listed = store.listRevisions()
        assertEquals(listOf(second.fileId, first.fileId), listed.map(CloudRevision::fileId))
        assertEquals(second.fileId, latestCloudRevision(listed)?.fileId)

        val downloaded = File(directory, "downloaded.tlb")
        store.downloadRevision(listed.last(), downloaded)
        assertArrayEquals(payload, downloaded.readBytes())
        assertTrue("README commit initializes the empty repository", github.readmeCreated)
    }

    @Test
    fun aTokenGitHubTurnsDownIsRenewedAndTheOperationRunsAgain() = runBlocking {
        var renewals = 0
        val provider = object : RenewableTokenProvider {
            override suspend fun accessToken() = "stale-token"
            override suspend fun renewAfterRejection(rejected: String): String? {
                assertEquals("stale-token", rejected)
                renewals++
                return "test-token"
            }
        }
        val store = GitHubBackupStore(provider, GitHubRepository("owner", "backups"), ioDispatcher = Dispatchers.Unconfined, apiBaseUrl = server.url("/"))
        val uploaded = store.uploadRevision(file("first.tlb", "first".encodeToByteArray()), revision(1))
        assertEquals(1, renewals)
        // Every operation starts with the provider's token again, so each one renews once here.
        assertEquals(listOf(uploaded.fileId), store.listRevisions().map(CloudRevision::fileId))
    }

    @Test
    fun aTokenThatCannotBeRenewedAsksToSignInAgain() = runBlocking {
        val store = GitHubBackupStore({ "stale-token" }, GitHubRepository("owner", "backups"), ioDispatcher = Dispatchers.Unconfined, apiBaseUrl = server.url("/"))
        try {
            store.listRevisions()
            org.junit.Assert.fail("A rejected token without a way to renew it must ask for a new sign-in")
        } catch (expected: GitHubSignInExpiredException) {
            assertTrue(expected.message.orEmpty().contains("Reconnect"))
        }
    }

    @Test
    fun deletingARevisionRemovesItFromTheBranch() = runBlocking {
        val store = store()
        val first = store.uploadRevision(file("a.tlb", "a".encodeToByteArray()), revision(1))
        val second = store.uploadRevision(file("b.tlb", "b".encodeToByteArray()), revision(2, first.fileId))
        store.deleteRevision(first.fileId)
        assertEquals(listOf(second.fileId), store.listRevisions().map(CloudRevision::fileId))
        assertTrue(github.currentTree().keys.none { it.contains(first.fileId) })
    }

    @Test
    fun aRaceWithAnotherDeviceIsRetriedInsteadOfOverwritten() = runBlocking {
        val store = store()
        val base = store.uploadRevision(file("base.tlb", "base".encodeToByteArray()), revision(1))
        // Another device publishes between our read of the branch and our branch update.
        github.beforeNextRefUpdate = {
            runBlocking { store().uploadRevision(file("other.tlb", "other".encodeToByteArray()), revision(2, base.fileId)) }
        }
        val mine = store.uploadRevision(file("mine.tlb", "mine".encodeToByteArray()), revision(3, base.fileId))

        val revisions = store.listRevisions()
        assertEquals(3, revisions.size)
        // Both competing children survive, so the normal conflict handling can resolve them.
        assertEquals(2, cloudRevisionHeads(revisions).size)
        assertTrue(revisions.any { it.fileId == mine.fileId })
    }

    @Test
    fun repositoryNamesAreParsedFromCommonForms() {
        assertEquals(GitHubRepository("ced2711", "life-data"), GitHubRepository.parse(" ced2711/life-data "))
        assertEquals(GitHubRepository("ced2711", "life-data"), GitHubRepository.parse("https://github.com/ced2711/life-data.git"))
    }
}

/** Just enough of GitHub's git data API, kept in memory. */
private class FakeGitHub : Dispatcher() {
    private val json = Json
    private val blobs = mutableMapOf<String, ByteArray>()
    private val trees = mutableMapOf<String, Map<String, String>>()
    private val commits = mutableMapOf<String, Pair<String, List<String>>>()
    private var ref: String? = null
    var emptyRepository = false
    var readmeCreated = false
    var beforeNextRefUpdate: (() -> Unit)? = null

    fun currentTree(): Map<String, String> = trees.getValue(commits.getValue(ref!!).first)

    // Not synchronized: the race hook issues nested requests while a PATCH is being served.
    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.path!!.substringBefore('?')
        val prefix = "/repos/owner/backups/"
        // A token GitHub no longer accepts, as after the eight hours of an expiring token.
        if (request.getHeader("Authorization") == "Bearer stale-token") return MockResponse().setResponseCode(401)
        check(request.getHeader("Authorization") == "Bearer test-token")
        val route = path.removePrefix(prefix)
        return when {
            request.method == "GET" && route == "git/ref/heads/life-assistant-sync" -> when {
                emptyRepository -> MockResponse().setResponseCode(409).setBody("""{"message":"Git Repository is empty."}""")
                ref == null -> MockResponse().setResponseCode(404)
                else -> ok("""{"object":{"sha":"$ref"}}""")
            }
            request.method == "PUT" && route == "contents/README.md" -> {
                emptyRepository = false
                readmeCreated = true
                ok("{}", 201)
            }
            request.method == "GET" && route.startsWith("git/commits/") ->
                ok("""{"tree":{"sha":"${commits.getValue(route.removePrefix("git/commits/")).first}"}}""")
            request.method == "GET" && route == "contents/index.json" -> {
                val commit = request.requestUrl!!.queryParameter("ref")!!
                val sha = trees.getValue(commits.getValue(commit).first)["index.json"]
                    ?: return MockResponse().setResponseCode(404)
                MockResponse().setBody(Buffer().write(blobs.getValue(sha)))
            }
            request.method == "POST" && route == "git/blobs" -> {
                val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                ok("""{"sha":"${store(Base64.getDecoder().decode(body.getValue("content").jsonPrimitive.content))}"}""", 201)
            }
            request.method == "GET" && route.startsWith("git/blobs/") ->
                MockResponse().setBody(Buffer().write(blobs.getValue(route.removePrefix("git/blobs/"))))
            request.method == "POST" && route == "git/trees" -> {
                val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                val files = body["base_tree"]?.jsonPrimitive?.contentOrNull?.let { trees.getValue(it).toMutableMap() }
                    ?: mutableMapOf()
                body.getValue("tree").jsonArray.forEach { element ->
                    val entry = element.jsonObject
                    val filePath = entry.getValue("path").jsonPrimitive.content
                    val content = entry["content"]?.jsonPrimitive?.contentOrNull
                    val sha = entry["sha"]?.jsonPrimitive?.contentOrNull
                    when {
                        content != null -> files[filePath] = store(content.encodeToByteArray())
                        sha != null -> files[filePath] = sha
                        else -> files.remove(filePath)
                    }
                }
                val treeSha = hash("tree" + files.toSortedMap())
                trees[treeSha] = files
                ok("""{"sha":"$treeSha"}""", 201)
            }
            request.method == "POST" && route == "git/commits" -> {
                val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                val tree = body.getValue("tree").jsonPrimitive.content
                val parents = (body.getValue("parents") as JsonArray).map { it.jsonPrimitive.content }
                val sha = hash("commit$tree$parents${commits.size}")
                commits[sha] = tree to parents
                ok("""{"sha":"$sha"}""", 201)
            }
            request.method == "POST" && route == "git/refs" -> {
                if (ref != null) return MockResponse().setResponseCode(422)
                ref = json.parseToJsonElement(request.body.readUtf8()).jsonObject.getValue("sha").jsonPrimitive.content
                ok("{}", 201)
            }
            request.method == "PATCH" && route == "git/refs/heads/life-assistant-sync" -> {
                beforeNextRefUpdate?.let { hook -> beforeNextRefUpdate = null; hook() }
                val target = json.parseToJsonElement(request.body.readUtf8()).jsonObject.getValue("sha").jsonPrimitive.content
                // Fast-forward only: the new commit must build on the current branch tip.
                if (ref !in commits.getValue(target).second) return MockResponse().setResponseCode(422)
                ref = target
                ok("{}")
            }
            else -> MockResponse().setResponseCode(500).setBody("unexpected ${request.method} $route")
        }
    }

    private fun store(bytes: ByteArray): String = hash(bytes.decodeToString() + bytes.size).also { blobs[it] = bytes }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-1")
        .digest(value.encodeToByteArray()).joinToString("") { "%02x".format(it) }

    private fun ok(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setBody(body)
}
