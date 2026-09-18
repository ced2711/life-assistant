package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.cloudsync.AccessTokenProvider
import com.ced2711.lifetracker.cloudsync.CloudAuthorizationException
import com.sun.net.httpserver.HttpServer
import java.awt.Desktop
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class StoredOAuthToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
)

class DesktopGoogleOAuth(
    private val configStore: DesktopConfigStore,
    private val credentialStore: WindowsCredentialStore,
    private val client: OkHttpClient = OkHttpClient(),
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AccessTokenProvider {
    private val json = Json { ignoreUnknownKeys = true }

    fun isConnected(): Boolean = loadToken() != null

    suspend fun connect(clientId: String, clientSecret: CharArray) = withContext(ioDispatcher) {
        validateClientId(clientId)
        configStore.setClientId(clientId)
        if (clientSecret.isNotEmpty()) {
            credentialStore.save(WindowsCredentialStore.OAUTH_CLIENT_SECRET, clientSecret.copyOf())
        } else {
            credentialStore.delete(WindowsCredentialStore.OAUTH_CLIENT_SECRET)
        }
        clientSecret.fill('\u0000')
        val verifier = randomUrlSafe(64)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)),
        )
        val state = randomUrlSafe(32)
        val server = HttpServer.create(
            InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0),
            0,
        )
        val redirect = "http://127.0.0.1:${server.address.port}/oauth2callback"
        val codeFuture = CompletableFuture<String>()
        server.createContext("/oauth2callback") { exchange ->
            val query = parseQuery(exchange.requestURI.rawQuery.orEmpty())
            val body = when {
                query["state"] != state -> {
                    codeFuture.completeExceptionally(CloudAuthorizationException("OAuth state mismatch."))
                    "Life Tracker rejected an invalid authorization response."
                }
                query["error"] != null -> {
                    codeFuture.completeExceptionally(CloudAuthorizationException("Google authorization was cancelled."))
                    "Google Drive authorization was cancelled. You can close this window."
                }
                query["code"].isNullOrBlank() -> {
                    codeFuture.completeExceptionally(CloudAuthorizationException("Authorization code was missing."))
                    "Google did not return an authorization code."
                }
                else -> {
                    codeFuture.complete(requireNotNull(query["code"]))
                    "Life Tracker is connected to Google Drive. You can close this window."
                }
            }.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val authorizationUrl = "https://accounts.google.com/o/oauth2/v2/auth?" + mapOf(
                "client_id" to clientId,
                "redirect_uri" to redirect,
                "response_type" to "code",
                "scope" to DRIVE_APPDATA_SCOPE,
                "access_type" to "offline",
                "prompt" to "consent",
                "include_granted_scopes" to "true",
                "state" to state,
                "code_challenge" to challenge,
                "code_challenge_method" to "S256",
            ).entries.joinToString("&") { (key, value) -> "${key.urlEncoded()}=${value.urlEncoded()}" }
            if (!Desktop.isDesktopSupported()) throw CloudAuthorizationException("A system browser is required.")
            Desktop.getDesktop().browse(URI(authorizationUrl))
            val code = try {
                codeFuture.get(3, TimeUnit.MINUTES)
            } catch (error: ExecutionException) {
                throw (error.cause ?: error)
            }
            exchangeCode(clientId, code, verifier, redirect)
        } finally {
            server.stop(0)
        }
    }

    override suspend fun accessToken(): String = withContext(ioDispatcher) {
        val stored = loadToken() ?: throw CloudAuthorizationException("Google Drive is not connected.")
        if (stored.expiresAt - now() > 60_000L) return@withContext stored.accessToken
        refresh(stored.refreshToken)
    }

    suspend fun disconnect() = withContext(ioDispatcher) {
        val token = loadToken()
        if (token != null) {
            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/revoke")
                .post(FormBody.Builder().add("token", token.refreshToken).build())
                .build()
            runCatching { client.newCall(request).execute().close() }
        }
        credentialStore.delete(WindowsCredentialStore.OAUTH_TOKEN)
        credentialStore.delete(WindowsCredentialStore.OAUTH_CLIENT_SECRET)
        configStore.clearSyncState()
    }

    private fun exchangeCode(clientId: String, code: String, verifier: String, redirect: String) {
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("code", code)
            .add("code_verifier", verifier)
            .add("redirect_uri", redirect)
            .add("grant_type", "authorization_code")
            .addOptionalSecret()
            .build()
        val root = executeTokenRequest(form)
        val access = root["access_token"]?.jsonPrimitive?.contentOrNull
            ?: throw CloudAuthorizationException("Google did not return an access token.")
        val refresh = root["refresh_token"]?.jsonPrimitive?.contentOrNull
            ?: throw CloudAuthorizationException("Google did not return a refresh token.")
        val expires = root["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3_600L
        saveToken(StoredOAuthToken(access, refresh, now() + expires * 1_000L))
    }

    private fun refresh(refreshToken: String): String {
        val clientId = configStore.read().clientId
        validateClientId(clientId)
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .addOptionalSecret()
            .build()
        val root = executeTokenRequest(form)
        val access = root["access_token"]?.jsonPrimitive?.contentOrNull
            ?: throw CloudAuthorizationException("Google token refresh failed.")
        val expires = root["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3_600L
        saveToken(StoredOAuthToken(access, refreshToken, now() + expires * 1_000L))
        return access
    }

    private fun FormBody.Builder.addOptionalSecret(): FormBody.Builder {
        val secret = credentialStore.load(WindowsCredentialStore.OAUTH_CLIENT_SECRET) ?: return this
        return try {
            add("client_secret", secret.concatToString())
        } finally {
            secret.fill('\u0000')
        }
    }

    private fun executeTokenRequest(body: FormBody) = client.newCall(
        Request.Builder().url("https://oauth2.googleapis.com/token").post(body).build(),
    ).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw CloudAuthorizationException("Google token request failed (${response.code}).")
        json.parseToJsonElement(text).jsonObject
    }

    private fun saveToken(token: StoredOAuthToken) {
        credentialStore.save(
            WindowsCredentialStore.OAUTH_TOKEN,
            json.encodeToString(token).toCharArray(),
        )
    }

    private fun loadToken(): StoredOAuthToken? {
        val chars = credentialStore.load(WindowsCredentialStore.OAUTH_TOKEN) ?: return null
        return try {
            json.decodeFromString<StoredOAuthToken>(chars.concatToString())
        } catch (_: Throwable) {
            null
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun validateClientId(clientId: String) {
        require(clientId.endsWith(".apps.googleusercontent.com") && clientId.length > 30) {
            "Enter a Google OAuth Desktop client ID."
        }
    }

    private fun randomUrlSafe(bytes: Int): String = ByteArray(bytes).also(SecureRandom()::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it).also { _ -> it.fill(0) } }

    private fun parseQuery(value: String): Map<String, String> = value.split('&')
        .mapNotNull { part ->
            val pieces = part.split('=', limit = 2)
            if (pieces.size != 2) null else pieces[0].urlDecoded() to pieces[1].urlDecoded()
        }
        .toMap()

    private fun String.urlEncoded() = URLEncoder.encode(this, StandardCharsets.UTF_8)
    private fun String.urlDecoded() = URLDecoder.decode(this, StandardCharsets.UTF_8)

    private companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
