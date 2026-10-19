package com.ced2711.lifetracker.cloudsync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * New GitHub OAuth apps hand out access tokens that last eight hours. These tests cover what
 * keeps a device connected anyway: keeping the refresh token, renewing in time, and saving the
 * renewed pair.
 */
class GitHubSessionTest {
    private lateinit var server: MockWebServer
    private val github = FakeTokenEndpoint()
    private var clock = 1_000_000L
    private var saved: String? = null
    private var saves = 0
    private var failSaves = 0

    @Before
    fun setUp() {
        server = MockWebServer().apply { dispatcher = github; start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun authorization() = GitHubDeviceAuthorization(
        clientId = "Ov23liExample12345",
        ioDispatcher = Dispatchers.Unconfined,
        webBaseUrl = server.url("/"),
        apiBaseUrl = server.url("/api/"),
        now = { clock },
    )

    private fun session() = GitHubSession(
        load = { saved },
        save = { text ->
            if (failSaves > 0) {
                failSaves--
                error("storage unavailable")
            }
            saves++
            saved = text
        },
        authorization = ::authorization,
        now = { clock },
    )

    private val eightHours = 8 * 3_600_000L

    @Test
    fun signingInKeepsTheRefreshTokenAndTheExpiry() = runBlocking {
        val token = authorization().awaitToken(GitHubDeviceCode("d", "ABCD-1234", "https://github.com/login/device", 0, clock + 60_000))
        assertEquals("gho_first", token.accessToken)
        assertEquals(clock + eightHours, token.expiresAtMillis)
        assertEquals("ghr_first", token.refreshToken)
        assertEquals(clock + 15_897_600_000L, token.refreshExpiresAtMillis)

        val restored = GitHubSignIn.decode(GitHubSignIn.of(token).encode())
        assertEquals(GitHubSignIn.of(token), restored)
    }

    @Test
    fun aSignInWithoutExpiryIsSavedAsTheBareTokenAndNeverRenewed() = runBlocking {
        // What an app with token expiration turned off gets, and what older versions saved.
        assertEquals("gho_forever", GitHubSignIn("gho_forever").encode())
        saved = "gho_forever"
        clock += 400 * 24 * 3_600_000L
        assertEquals("gho_forever", session().accessToken())
        assertEquals(0, github.refreshes)
    }

    @Test
    fun aFreshTokenIsUsedWithoutAskingGitHub() = runBlocking {
        saved = GitHubSignIn("gho_first", clock + eightHours, "ghr_first", clock + 1_000_000_000L).encode()
        clock += eightHours - 11 * 60_000L
        assertEquals("gho_first", session().accessToken())
        assertEquals(0, github.refreshes)
    }

    @Test
    fun aTokenAboutToExpireIsRenewedAndThePairIsSaved() = runBlocking {
        saved = GitHubSignIn("gho_first", clock + eightHours, "ghr_first", clock + 1_000_000_000L).encode()
        val session = session()
        clock += eightHours - 9 * 60_000L

        assertEquals("gho_renewed_1", session.accessToken())
        assertEquals("ghr_first", github.lastRefreshToken)
        assertFalse("no client secret ships in the apps", github.lastBody.contains("client_secret"))
        assertTrue(github.lastBody.contains("grant_type=refresh_token"))
        val stored = GitHubSignIn.decode(saved!!)
        assertEquals("gho_renewed_1", stored.accessToken)
        assertEquals("ghr_renewed_1", stored.refreshToken)
        assertEquals(clock + eightHours, stored.expiresAtMillis)

        // The renewed token is good for another eight hours: no second request.
        assertEquals("gho_renewed_1", session.accessToken())
        assertEquals(1, github.refreshes)

        // A day later it is renewed again with the refresh token from the first renewal.
        clock += 24 * 3_600_000L
        assertEquals("gho_renewed_2", session.accessToken())
        assertEquals("ghr_renewed_1", github.lastRefreshToken)
    }

    @Test
    fun aRenewedPairThatCouldNotBeSavedIsKeptAndSavedLater() = runBlocking {
        saved = GitHubSignIn("gho_first", clock + eightHours, "ghr_first", null).encode()
        val session = session()
        clock += eightHours
        failSaves = 1

        assertEquals("gho_renewed_1", session.accessToken())
        assertEquals("the old pair is still what storage holds", "ghr_first", GitHubSignIn.decode(saved!!).refreshToken)

        // GitHub already retired ghr_first: using storage again would lose the sign-in.
        assertEquals("gho_renewed_1", session.accessToken())
        assertEquals(1, github.refreshes)
        assertEquals("ghr_renewed_1", GitHubSignIn.decode(saved!!).refreshToken)
    }

    @Test
    fun aRefreshTokenGitHubNoLongerAcceptsAsksToSignInAgain() = runBlocking {
        saved = GitHubSignIn("gho_first", clock - 1, "ghr_first", null).encode()
        github.rejectRefresh = true
        try {
            session().accessToken()
            fail("An unusable refresh token must ask for a new sign-in")
        } catch (expected: GitHubSignInExpiredException) {
            assertTrue(expected.message.orEmpty().contains("Reconnect"))
        }
    }

    @Test
    fun aNetworkProblemWhileRenewingKeepsTheSignIn() = runBlocking {
        val before = GitHubSignIn("gho_first", clock - 1, "ghr_first", null).encode()
        saved = before
        github.dropRefreshes = 1
        try {
            session().accessToken()
            fail("A dropped connection is not an answer")
        } catch (expected: CloudTransportException) {
            assertEquals(before, saved)
        }
        // The next sync simply tries again.
        assertEquals("gho_renewed_1", session().accessToken())
    }

    @Test
    fun aRejectedTokenIsRenewedOnceWhenThereIsARefreshToken() = runBlocking {
        saved = GitHubSignIn("gho_first", clock + eightHours, "ghr_first", null).encode()
        val session = session()
        assertEquals("gho_renewed_1", session.renewAfterRejection("gho_first"))
        // A second sync that was turned down with the old token gets the renewed one, no new request.
        assertEquals("gho_renewed_1", session.renewAfterRejection("gho_first"))
        assertEquals(1, github.refreshes)
    }

    @Test
    fun aRejectedTokenWithoutRefreshTokenCannotBeRenewed() = runBlocking {
        saved = "gho_old_version"
        assertNull(session().renewAfterRejection("gho_old_version"))
        assertEquals(0, github.refreshes)
    }

    @Test
    fun nothingSavedMeansSigningIn() = runBlocking {
        try {
            session().accessToken()
            fail("Without a saved sign-in there is nothing to use")
        } catch (expected: GitHubSignInExpiredException) {
            assertEquals(0, saves)
        }
    }

    private class FakeTokenEndpoint : Dispatcher() {
        var refreshes = 0
        var lastRefreshToken: String? = null
        var lastBody = ""
        var rejectRefresh = false
        var dropRefreshes = 0

        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.requestUrl?.encodedPath != "/login/oauth/access_token") return MockResponse().setResponseCode(500)
            val body = request.body.readUtf8()
            val fields = body.split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
            return when (fields["grant_type"]) {
                "refresh_token" -> {
                    lastBody = body
                    lastRefreshToken = fields["refresh_token"]
                    when {
                        dropRefreshes-- > 0 -> MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START)
                        rejectRefresh -> json("""{"error":"bad_refresh_token","error_description":"The refresh token passed is incorrect or expired."}""")
                        else -> {
                            refreshes++
                            json("""{"access_token":"gho_renewed_$refreshes","expires_in":28800,"refresh_token":"ghr_renewed_$refreshes","refresh_token_expires_in":15897600,"scope":"repo","token_type":"bearer"}""")
                        }
                    }
                }
                else -> json("""{"access_token":"gho_first","expires_in":28800,"refresh_token":"ghr_first","refresh_token_expires_in":15897600,"scope":"repo","token_type":"bearer"}""")
            }
        }

        private fun json(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)
    }
}
