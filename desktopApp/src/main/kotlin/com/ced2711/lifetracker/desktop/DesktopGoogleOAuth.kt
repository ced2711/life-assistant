package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.cloudsync.AccessTokenProvider
import com.ced2711.lifetracker.cloudsync.CloudAuthorizationException
import com.ced2711.lifetracker.domain.model.AppIdentity
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
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** Token fields are stored together so a failed reconnect cannot pair an old token with new settings. */
@Serializable
private data class StoredOAuthToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val credentialsBound: Boolean = false,
)

class DesktopGoogleOAuth(
    private val configStore: DesktopConfigStore,
    private val credentialStore: WindowsCredentialStore,
    private val client: OkHttpClient = OkHttpClient(),
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val authorizationEndpoint: String = DEFAULT_AUTHORIZATION_ENDPOINT,
    private val tokenEndpoint: String = DEFAULT_TOKEN_ENDPOINT,
    private val revokeEndpoint: String = DEFAULT_REVOKE_ENDPOINT,
    private val browserLauncher: (URI) -> Unit = ::openInSystemBrowser,
    private val desktopSupported: () -> Boolean = { Desktop.isDesktopSupported() },
) : AccessTokenProvider {
    private val json = Json { ignoreUnknownKeys = true }

    /** This check only reads DPAPI-backed local state and never contacts Google. */
    fun isConnected(): Boolean = loadToken() != null

    suspend fun connect(clientId: String, clientSecret: CharArray) = withContext(ioDispatcher) {
        val secret = clientSecret.concatToString().takeIf(String::isNotEmpty)
        clientSecret.fill('\u0000')
        validateClientId(clientId)

        // Persist neither client setting until the browser callback and token exchange succeed.
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
            val callback = DesktopOAuthDiagnostics.parseCallback(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                rawQuery = exchange.requestURI.rawQuery.orEmpty(),
                expectedState = state,
            )
            val body = when (callback) {
                is OAuthCallbackResult.Code -> {
                    codeFuture.complete(callback.code)
                    "Authorization received. ${AppIdentity.NAME} is verifying Google Drive access. You can close this window."
                }
                is OAuthCallbackResult.Failure -> {
                    codeFuture.completeExceptionally(CloudAuthorizationException(callback.message))
                    callback.browserMessage
                }
            }.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val authorizationUrl = "$authorizationEndpoint?" + mapOf(
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
            if (!desktopSupported()) throw CloudAuthorizationException(
                "A system browser is required to connect Google Drive.",
            )
            try {
                browserLauncher(URI(authorizationUrl))
            } catch (error: Throwable) {
                throw CloudAuthorizationException(
                    "Could not open the system browser. Open it manually and try again.",
                    error,
                )
            }
            val code = try {
                codeFuture.get(3, TimeUnit.MINUTES)
            } catch (error: java.util.concurrent.ExecutionException) {
                throw (error.cause ?: error)
            } catch (_: TimeoutException) {
                throw CloudAuthorizationException(
                    "Google sign-in timed out. Try again; your existing connection was kept.",
                )
            }
            val token = exchangeCode(clientId, secret, code, verifier, redirect)
            saveToken(token)
            // Deliberately after successful verification. The token contains its own client binding,
            // so a settings-file failure must not turn an already-committed connection into an error.
            runCatching { configStore.setClientId(clientId) }
        } finally {
            server.stop(0)
        }
    }

    override suspend fun accessToken(): String = withContext(ioDispatcher) {
        val stored = loadToken() ?: throw CloudAuthorizationException("Google Drive is not connected.")
        if (stored.expiresAt - now() > 60_000L) return@withContext stored.accessToken
        refresh(stored)
    }

    suspend fun disconnect() = withContext(ioDispatcher) {
        val token = loadToken()
        if (token != null) {
            val request = Request.Builder()
                .url(revokeEndpoint)
                .post(FormBody.Builder().add("token", token.refreshToken).build())
                .build()
            runCatching { client.newCall(request).execute().close() }
        }
        credentialStore.delete(WindowsCredentialStore.OAUTH_TOKEN)
        credentialStore.delete(WindowsCredentialStore.OAUTH_CLIENT_SECRET)
        configStore.clearSyncState()
    }

    private fun exchangeCode(
        clientId: String,
        clientSecret: String?,
        code: String,
        verifier: String,
        redirect: String,
    ): StoredOAuthToken {
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("code", code)
            .add("code_verifier", verifier)
            .add("redirect_uri", redirect)
            .add("grant_type", "authorization_code")
            .addOptionalSecret(clientSecret)
            .build()
        val root = executeTokenRequest(form, requireDriveScope = true)
        val access = root["access_token"]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: throw CloudAuthorizationException("Google did not return an access token. Try again.")
        val refresh = root["refresh_token"]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: throw CloudAuthorizationException(
                "Google did not return a refresh token. Remove this app's Google access and reconnect.",
            )
        val expires = root["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3_600L
        return StoredOAuthToken(
            accessToken = access,
            refreshToken = refresh,
            expiresAt = now() + expires * 1_000L,
            clientId = clientId,
            clientSecret = clientSecret,
            credentialsBound = true,
        )
    }

    private fun refresh(stored: StoredOAuthToken): String {
        val clientId = stored.clientId ?: configStore.read().clientId
        validateClientId(clientId)
        val secret = if (stored.credentialsBound) stored.clientSecret else loadLegacySecret()
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("refresh_token", stored.refreshToken)
            .add("grant_type", "refresh_token")
            .addOptionalSecret(secret)
            .build()
        val root = executeTokenRequest(form, requireDriveScope = false)
        val access = root["access_token"]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: throw CloudAuthorizationException("Google token refresh returned no access token. Reconnect Google Drive.")
        val expires = root["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3_600L
        saveToken(stored.copy(accessToken = access, expiresAt = now() + expires * 1_000L))
        return access
    }

    private fun loadLegacySecret(): String? {
        val secret = credentialStore.load(WindowsCredentialStore.OAUTH_CLIENT_SECRET) ?: return null
        return try {
            secret.concatToString().takeIf(String::isNotEmpty)
        } finally {
            secret.fill('\u0000')
        }
    }

    private fun FormBody.Builder.addOptionalSecret(secret: String?): FormBody.Builder {
        if (!secret.isNullOrEmpty()) add("client_secret", secret)
        return this
    }

    private fun executeTokenRequest(
        body: FormBody,
        requireDriveScope: Boolean,
    ): Map<String, JsonElement> {
        return try {
            client.newCall(
                Request.Builder().url(tokenEndpoint).post(body).build(),
            ).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw CloudAuthorizationException(DesktopOAuthDiagnostics.tokenFailureMessage(response.code, text))
                }
                val root = runCatching { json.parseToJsonElement(text).jsonObject }
                    .getOrElse {
                        throw CloudAuthorizationException("Google returned an invalid token response. Try again.")
                    }
                val grantedScope = root["scope"]?.jsonPrimitive?.contentOrNull
                if (requireDriveScope && (grantedScope == null || !grantedScope.split(' ').contains(DRIVE_APPDATA_SCOPE))) {
                    throw CloudAuthorizationException(
                        "Google did not grant the required private Drive permission. Reconnect and allow access.",
                    )
                }
                root
            }
        } catch (error: CloudAuthorizationException) {
            throw error
        } catch (error: java.net.SocketTimeoutException) {
            throw CloudAuthorizationException("Google token request timed out. Try again.", error)
        } catch (error: java.io.IOException) {
            throw CloudAuthorizationException(
                "Google could not be reached during sign-in. Check your internet connection and try again.",
                error,
            )
        }
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
        if (!clientId.endsWith(".apps.googleusercontent.com") || clientId.length <= 30) {
            throw CloudAuthorizationException("Enter a valid Google OAuth Desktop client ID.")
        }
    }

    private fun randomUrlSafe(bytes: Int): String = ByteArray(bytes).also(SecureRandom()::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it).also { _ -> it.fill(0) } }

    private fun String.urlEncoded() = URLEncoder.encode(this, StandardCharsets.UTF_8)

    private companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val DEFAULT_AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        const val DEFAULT_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        const val DEFAULT_REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"

        private fun openInSystemBrowser(uri: URI) {
            Desktop.getDesktop().browse(uri)
        }
    }
}

internal sealed interface OAuthCallbackResult {
    data class Code(val code: String) : OAuthCallbackResult
    data class Failure(val message: String, val browserMessage: String) : OAuthCallbackResult
}

/** Pure OAuth response handling kept separate so callback/error behavior can be tested offline. */
internal object DesktopOAuthDiagnostics {
    private const val CALLBACK_PATH = "/oauth2callback"

    fun parseCallback(
        method: String,
        path: String,
        rawQuery: String,
        expectedState: String,
    ): OAuthCallbackResult {
        if (!method.equals("GET", ignoreCase = true) || path != CALLBACK_PATH) {
            return failure("Google sent an invalid authorization response.", "Invalid authorization response.")
        }
        val query = parseQuery(rawQuery)
            ?: return failure("Google sent a malformed authorization response.", "Malformed authorization response.")
        if (query["state"] != expectedState) {
            return failure("Google authorization could not be verified. Try again.", "Authorization could not be verified.")
        }
        val error = query["error"]
        if (error != null) {
            val message = when (error) {
                "access_denied" -> "Google authorization was denied or cancelled. Your existing connection was kept."
                "org_internal", "admin_policy_enforced", "disallowed_useragent" ->
                    "Google policy blocked this sign-in. Use an allowed account or review the OAuth consent settings."
                "temporarily_unavailable", "server_error" -> "Google is temporarily unavailable. Try again."
                else -> "Google could not authorize this app. Check the OAuth client and try again."
            }
            return failure(message, message.removeSuffix(" Your existing connection was kept."))
        }
        val code = query["code"]?.takeIf(String::isNotBlank)
            ?: return failure("Google did not return an authorization code. Try again.", "No authorization code was returned.")
        return OAuthCallbackResult.Code(code)
    }

    fun tokenFailureMessage(httpCode: Int, responseBody: String): String {
        val error = runCatching {
            Json { ignoreUnknownKeys = true }.parseToJsonElement(responseBody).jsonObject["error"]
                ?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return when (error) {
            "invalid_client", "unauthorized_client", "deleted_client" ->
                "Google rejected this OAuth client. Check the Desktop OAuth client ID/secret and that the client is enabled."
            "invalid_grant" -> "Google rejected the authorization code or refresh token. Reconnect Google Drive."
            "access_denied" -> "Google denied Drive access. Choose an account that can use this app and try again."
            "org_internal", "admin_policy_enforced", "disallowed_useragent" ->
                "Google policy blocked this sign-in. Use an allowed account or review the OAuth consent settings."
            "temporarily_unavailable", "server_error" -> "Google is temporarily unavailable. Try again."
            else -> if (httpCode == 401 || httpCode == 403) {
                "Google rejected the OAuth configuration. Check the client settings and try again."
            } else {
                "Google token exchange failed. Check the OAuth client configuration and try again."
            }
        }
    }

    private fun failure(message: String, browserMessage: String): OAuthCallbackResult.Failure =
        OAuthCallbackResult.Failure(message, browserMessage)

    private fun parseQuery(value: String): Map<String, String>? {
        if (value.isBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        for (part in value.split('&')) {
            val pieces = part.split('=', limit = 2)
            if (pieces.size != 2) return null
            val key = runCatching { URLDecoder.decode(pieces[0], StandardCharsets.UTF_8) }.getOrNull() ?: return null
            val item = runCatching { URLDecoder.decode(pieces[1], StandardCharsets.UTF_8) }.getOrNull() ?: return null
            if (key.isBlank() || result.put(key, item) != null) return null
        }
        return result
    }
}
