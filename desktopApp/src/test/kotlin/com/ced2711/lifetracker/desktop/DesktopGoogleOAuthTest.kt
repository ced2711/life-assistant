package com.ced2711.lifetracker.desktop

import com.sun.net.httpserver.HttpServer
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopGoogleOAuthTest {
    @Test
    fun callbackRequiresGetPathAndMatchingState() {
        assertTrue(
            DesktopOAuthDiagnostics.parseCallback(
                method = "GET",
                path = "/oauth2callback",
                rawQuery = "state=expected&code=one",
                expectedState = "expected",
            ) is OAuthCallbackResult.Code,
        )
        val wrongState = DesktopOAuthDiagnostics.parseCallback(
            method = "GET",
            path = "/oauth2callback",
            rawQuery = "state=other&code=one",
            expectedState = "expected",
        ) as OAuthCallbackResult.Failure
        assertTrue(wrongState.message.contains("could not be verified"))
        val wrongMethod = DesktopOAuthDiagnostics.parseCallback(
            method = "POST",
            path = "/oauth2callback",
            rawQuery = "state=expected&code=one",
            expectedState = "expected",
        ) as OAuthCallbackResult.Failure
        assertFalse(wrongMethod.message.contains("cancelled", ignoreCase = true))
    }

    @Test
    fun callbackDistinguishesCancellationFromOtherErrorsAndRejectsMalformedQuery() {
        val cancelled = DesktopOAuthDiagnostics.parseCallback(
            "GET", "/oauth2callback", "state=s&error=access_denied", "s",
        ) as OAuthCallbackResult.Failure
        assertTrue(cancelled.message.contains("denied or cancelled"))
        assertTrue(cancelled.message.contains("existing connection was kept"))

        val policy = DesktopOAuthDiagnostics.parseCallback(
            "GET", "/oauth2callback", "state=s&error=org_internal", "s",
        ) as OAuthCallbackResult.Failure
        assertTrue(policy.message.contains("policy"))
        assertFalse(policy.message.contains("cancelled", ignoreCase = true))

        val malformed = DesktopOAuthDiagnostics.parseCallback(
            "GET", "/oauth2callback", "state=s&code", "s",
        ) as OAuthCallbackResult.Failure
        assertTrue(malformed.message.contains("malformed"))
    }

    @Test
    fun tokenErrorsAreActionableButNeverEchoGoogleResponseDetails() {
        val secret = "do-not-display-this"
        val message = DesktopOAuthDiagnostics.tokenFailureMessage(
            400,
            "{\"error\":\"invalid_grant\",\"error_description\":\"$secret\"}",
        )
        assertEquals("Google rejected the authorization code or refresh token. Reconnect Google Drive.", message)
        assertFalse(message.contains(secret))

        assertTrue(
            DesktopOAuthDiagnostics.tokenFailureMessage(401, "not-json")
                .contains("OAuth configuration"),
        )
        assertTrue(
            DesktopOAuthDiagnostics.tokenFailureMessage(400, "{\"error\":\"invalid_client\"}")
                .contains("OAuth client"),
        )
    }

    @Test
    fun cancelledReconnectKeepsExistingTokenAndClientConfiguration() = runBlocking {
        val root = Files.createTempDirectory("life-tracker-oauth-reconnect-test").toFile()
        val tokenServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var tokenRequests = 0
        tokenServer.createContext("/token") { exchange ->
            tokenRequests += 1
            val response = """
                {"access_token":"access-old","refresh_token":"refresh-old","expires_in":3600,
                "scope":"https://www.googleapis.com/auth/drive.appdata"}
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        tokenServer.start()
        try {
            val config = DesktopConfigStore(root.resolve("desktop.properties"))
            val credentials = WindowsCredentialStore(root.resolve("credentials"))
            val tokenEndpoint = "http://127.0.0.1:${tokenServer.address.port}/token"
            val first = DesktopGoogleOAuth(
                configStore = config,
                credentialStore = credentials,
                tokenEndpoint = tokenEndpoint,
                browserLauncher = browserReturning("code-old"),
                desktopSupported = { true },
            )
            first.connect(validClientId("old"), charArrayOf('o', 'l', 'd'))
            assertEquals(1, tokenRequests)
            assertEquals(validClientId("old"), config.read().clientId)
            assertEquals("access-old", first.accessToken())

            val failed = DesktopGoogleOAuth(
                configStore = config,
                credentialStore = credentials,
                tokenEndpoint = tokenEndpoint,
                browserLauncher = browserReturningError("access_denied"),
                desktopSupported = { true },
            )
            try {
                failed.connect(validClientId("new"), charArrayOf('n', 'e', 'w'))
                throw AssertionError("cancelled reconnect unexpectedly succeeded")
            } catch (error: Exception) {
                assertTrue(error.message.orEmpty().contains("denied or cancelled"))
            }
            assertEquals(1, tokenRequests)
            assertEquals(validClientId("old"), config.read().clientId)
            assertTrue(failed.isConnected())
            assertEquals("access-old", failed.accessToken())
        } finally {
            tokenServer.stop(0)
            root.deleteRecursively()
        }
    }

    @Test
    fun successfulTokenRequiresPrivateDriveScopeBeforeCommit() = runBlocking {
        val root = Files.createTempDirectory("life-tracker-oauth-scope-test").toFile()
        val tokenServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        tokenServer.createContext("/token") { exchange ->
            val response = """{"access_token":"access","refresh_token":"refresh","expires_in":3600,"scope":"openid"}"""
                .toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        tokenServer.start()
        try {
            val config = DesktopConfigStore(root.resolve("desktop.properties"))
            val credentials = WindowsCredentialStore(root.resolve("credentials"))
            val oauth = DesktopGoogleOAuth(
                configStore = config,
                credentialStore = credentials,
                tokenEndpoint = "http://127.0.0.1:${tokenServer.address.port}/token",
                browserLauncher = browserReturning("code"),
                desktopSupported = { true },
            )
            try {
                oauth.connect(validClientId("scope"), charArrayOf('s', 'e', 'c', 'r', 'e', 't'))
                throw AssertionError("scope-less token unexpectedly succeeded")
            } catch (error: Exception) {
                assertTrue(error.message.orEmpty().contains("private Drive permission"))
            }
            assertFalse(oauth.isConnected())
            assertEquals("", config.read().clientId)
        } finally {
            tokenServer.stop(0)
            root.deleteRecursively()
        }
    }

    private fun browserReturning(code: String): (URI) -> Unit = { authorization ->
        val query = queryParameters(authorization.rawQuery)
        requestCallback(
            URI(query.getValue("redirect_uri")),
            "state=${query.getValue("state")}&code=$code",
        )
    }

    private fun browserReturningError(error: String): (URI) -> Unit = { authorization ->
        val query = queryParameters(authorization.rawQuery)
        requestCallback(
            URI(query.getValue("redirect_uri")),
            "state=${query.getValue("state")}&error=$error",
        )
    }

    private fun requestCallback(redirect: URI, query: String) {
        val callback = URI(
            redirect.scheme,
            redirect.userInfo,
            redirect.host,
            redirect.port,
            redirect.path,
            query,
            null,
        )
        (callback.toURL().openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
            inputStream.use { it.readBytes() }
            disconnect()
        }
    }

    private fun queryParameters(query: String): Map<String, String> = query.split('&')
        .associate { part ->
            val (key, value) = part.split('=', limit = 2)
            URLDecoder.decode(key, StandardCharsets.UTF_8) to URLDecoder.decode(value, StandardCharsets.UTF_8)
        }

    private fun validClientId(label: String): String =
        "123456789012-$label.apps.googleusercontent.com"
}
