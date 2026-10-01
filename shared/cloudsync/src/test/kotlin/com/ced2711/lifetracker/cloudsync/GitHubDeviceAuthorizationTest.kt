package com.ced2711.lifetracker.cloudsync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GitHubDeviceAuthorizationTest {
    private lateinit var server: MockWebServer
    private val fake = FakeGitHubAccount()

    @Before
    fun setUp() {
        server = MockWebServer().apply { dispatcher = fake; start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun authorization(clientId: String) = GitHubDeviceAuthorization(
        clientId = clientId,
        ioDispatcher = Dispatchers.Unconfined,
        webBaseUrl = server.url("/"),
        apiBaseUrl = server.url("/api/"),
    )

    @Test
    fun oauthAppAsksForRepoScope() = runBlocking {
        authorization("Ov23liExample12345").start()
        assertEquals("repo", fake.requestedScope)
    }

    @Test
    fun gitHubAppDoesNotAskForScopes() = runBlocking {
        authorization("Iv23liExample12345").start()
        assertEquals(null, fake.requestedScope)
    }

    @Test
    fun aDroppedConnectionWhileApprovingKeepsWaiting() = runBlocking {
        // The phone cuts connections while the browser is in front; the next check succeeds.
        fake.failTokenPolls = 2
        val token = authorization("Ov23liecZgUuwT63yOWh").awaitToken(
            GitHubDeviceCode("d", "ABCD-1234", "https://github.com/login/device", 0, System.currentTimeMillis() + 60_000),
        )
        assertEquals("gho_token", token.accessToken)
        assertEquals(3, fake.tokenPolls)
    }

    @Test
    fun aBriefOutageAfterApprovalIsRetried() = runBlocking {
        fake.failUserLookups = 1
        val repository = authorization("Ov23liecZgUuwT63yOWh").resolveRepository("token", "")
        assertEquals("octo/life-assistant-data", repository.fullName)
    }

    @Test
    fun oauthAppCreatesPrivateRepositoryOnFirstConnect() = runBlocking {
        val repository = authorization("Ov23liExample12345").resolveRepository("token", "")
        assertEquals("octo/life-assistant-data", repository.fullName)
        val created = requireNotNull(fake.createdBody)
        assertEquals("life-assistant-data", created["name"]?.jsonPrimitive?.content)
        assertEquals("true", created["private"]?.jsonPrimitive?.content)
        assertEquals("true", created["auto_init"]?.jsonPrimitive?.content)
    }

    @Test
    fun oauthAppReusesExistingPrivateRepository() = runBlocking {
        fake.existing = true
        val repository = authorization("Ov23liExample12345").resolveRepository("token", "")
        assertEquals("octo/life-assistant-data", repository.fullName)
        assertEquals(null, fake.createdBody)
    }

    @Test
    fun repositoryCreatedByAnotherDeviceMeanwhileIsUsed() = runBlocking {
        fake.createConflict = true
        val repository = authorization("Ov23liExample12345").resolveRepository("token", "")
        assertEquals("octo/life-assistant-data", repository.fullName)
        assertTrue(fake.existing)
    }

    @Test
    fun publicRepositoryIsRefused() = runBlocking {
        fake.existing = true
        fake.public = true
        try {
            authorization("Ov23liExample12345").resolveRepository("token", "")
            fail("A public repository must not be used")
        } catch (expected: CloudAuthorizationException) {
            assertTrue(expected.message.orEmpty().contains("private"))
        }
        assertFalse(fake.createdBody != null)
    }

    private class FakeGitHubAccount : Dispatcher() {
        var existing = false
        var public = false
        var createConflict = false
        var requestedScope: String? = null
        var createdBody: kotlinx.serialization.json.JsonObject? = null
        var failTokenPolls = 0
        var failUserLookups = 0
        var tokenPolls = 0

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.requestUrl?.encodedPath.orEmpty()
            return when {
                path == "/login/device/code" -> {
                    requestedScope = request.body.readUtf8().split('&')
                        .firstOrNull { it.startsWith("scope=") }?.removePrefix("scope=")
                    json("""{"device_code":"d","user_code":"ABCD-1234","verification_uri":"https://github.com/login/device","interval":5,"expires_in":900}""")
                }
                path == "/login/oauth/access_token" -> {
                    tokenPolls++
                    if (tokenPolls <= failTokenPolls) MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START)
                    else json("""{"access_token":"gho_token","token_type":"bearer","scope":"repo"}""")
                }
                path == "/api/user" -> if (failUserLookups-- > 0) MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START) else json("""{"login":"octo"}""")
                path == "/api/repos/octo/life-assistant-data" ->
                    if (existing) json("""{"full_name":"octo/life-assistant-data","private":${!public}}""")
                    else MockResponse().setResponseCode(404)
                path == "/api/user/repos" && request.method == "POST" -> {
                    if (createConflict) {
                        existing = true
                        MockResponse().setResponseCode(422)
                    } else {
                        createdBody = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        existing = true
                        json("""{"full_name":"octo/life-assistant-data","private":true}""", 201)
                    }
                }
                else -> MockResponse().setResponseCode(500)
            }
        }

        private fun json(body: String, code: Int = 200) =
            MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
    }
}
