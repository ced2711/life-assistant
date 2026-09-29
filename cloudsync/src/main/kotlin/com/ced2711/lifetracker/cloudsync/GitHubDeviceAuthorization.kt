package com.ced2711.lifetracker.cloudsync

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** What the user types at [verificationUri] to approve this device. */
data class GitHubDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val intervalSeconds: Int,
    val expiresAtMillis: Long,
)

/**
 * A GitHub App user token. [expiresAtMillis] is null when the app has token expiration turned
 * off, which the setup guide asks for: refreshing would need a client secret that must not ship
 * inside the apps.
 */
data class GitHubToken(val accessToken: String, val expiresAtMillis: Long?)

/**
 * OAuth device flow for a GitHub App. The client ID is public; no client secret is involved.
 * See https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app#using-the-device-flow-to-generate-a-user-access-token
 */
class GitHubDeviceAuthorization(
    private val clientId: String,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val webBaseUrl: HttpUrl = "https://github.com/".toHttpUrl(),
    private val apiBaseUrl: HttpUrl = "https://api.github.com/".toHttpUrl(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }

    init {
        require(clientId.matches(CLIENT_ID)) { "Enter the GitHub App's Client ID (it starts with Iv)." }
    }

    suspend fun start(): GitHubDeviceCode = withContext(ioDispatcher) {
        val body = FormBody.Builder().add("client_id", clientId).build()
        val result = postForm(webBaseUrl.resolve("login/device/code")!!, body)
        result.error()?.let { throw CloudAuthorizationException(describe(it)) }
        GitHubDeviceCode(
            deviceCode = result.text("device_code"),
            userCode = result.text("user_code"),
            verificationUri = result.text("verification_uri"),
            intervalSeconds = result["interval"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 60) ?: 5,
            expiresAtMillis = now() + (result["expires_in"]?.jsonPrimitive?.longOrNull ?: 900L) * 1_000L,
        )
    }

    /** Polls until the user approves, denies, or the code expires. Cancel the coroutine to stop. */
    suspend fun awaitToken(code: GitHubDeviceCode): GitHubToken {
        var interval = code.intervalSeconds
        while (now() < code.expiresAtMillis) {
            delay(interval * 1_000L)
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("device_code", code.deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .build()
            val result = withContext(ioDispatcher) { postForm(webBaseUrl.resolve("login/oauth/access_token")!!, body) }
            when (val error = result.error()) {
                null -> return GitHubToken(
                    accessToken = result.text("access_token"),
                    expiresAtMillis = result["expires_in"]?.jsonPrimitive?.longOrNull?.let { now() + it * 1_000L },
                )
                "authorization_pending" -> Unit
                "slow_down" -> interval = result["interval"]?.jsonPrimitive?.intOrNull ?: (interval + 5)
                else -> throw CloudAuthorizationException(describe(error))
            }
        }
        throw CloudAuthorizationException("The GitHub code expired. Start again.")
    }

    /** Repositories the GitHub App is installed on and this user can access, as owner/name. */
    suspend fun accessibleRepositories(token: String): List<String> = withContext(ioDispatcher) {
        val installations = getJson(apiBaseUrl.resolve("user/installations")!!, token)["installations"]
            ?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.longOrNull }
        installations.flatMap { id ->
            val url = apiBaseUrl.newBuilder().addPathSegments("user/installations/$id/repositories")
                .addQueryParameter("per_page", "100").build()
            getJson(url, token)["repositories"]?.jsonArray.orEmpty()
                .mapNotNull { it.jsonObject["full_name"]?.jsonPrimitive?.contentOrNull }
        }.distinct().sorted()
    }

    /**
     * Picks the sync repository: the one the user typed, or the only repository the app can
     * access. The repository must be private, even though every backup is encrypted.
     */
    suspend fun resolveRepository(token: String, requested: String): GitHubRepository {
        val repository = if (requested.isNotBlank()) {
            GitHubRepository.parse(requested)
        } else {
            val available = accessibleRepositories(token)
            when (available.size) {
                0 -> throw CloudAuthorizationException("Install the GitHub App on a private repository first.")
                1 -> GitHubRepository.parse(available.single())
                else -> throw CloudAuthorizationException(
                    "The GitHub App can access several repositories (${available.joinToString()}). Enter the one to use.",
                )
            }
        }
        val details = withContext(ioDispatcher) {
            getJson(apiBaseUrl.newBuilder().addPathSegments("repos/${repository.owner}/${repository.name}").build(), token)
        }
        if (details["private"]?.jsonPrimitive?.contentOrNull != "true") {
            throw CloudAuthorizationException("Use a private repository. Backups are encrypted, but their history would be public.")
        }
        return repository
    }

    private fun postForm(url: HttpUrl, body: FormBody): JsonObject {
        val request = Request.Builder().url(url).header("Accept", "application/json").post(body).build()
        return execute(request)
    }

    private fun getJson(url: HttpUrl, token: String): JsonObject {
        val request = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get().build()
        return execute(request)
    }

    private fun execute(request: Request): JsonObject = try {
        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw CloudAuthorizationException("GitHub sign-in expired or was revoked. Reconnect GitHub.")
            if (response.code == 404) throw CloudAuthorizationException("GitHub did not recognize this Client ID.")
            if (response.code !in 200..299) throw CloudTransportException("GitHub request failed (${response.code}).")
            val text = response.body?.string() ?: throw CloudTransportException("GitHub returned an empty response.")
            json.parseToJsonElement(text).jsonObject
        }
    } catch (error: IOException) {
        throw CloudTransportException("Could not reach GitHub.", error)
    } catch (error: IllegalArgumentException) {
        throw CloudTransportException("GitHub returned malformed data.", error)
    }

    private fun JsonObject.error(): String? = this["error"]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.text(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: throw CloudTransportException("GitHub returned incomplete data.")

    private fun describe(error: String): String = when (error) {
        "access_denied" -> "GitHub authorization was cancelled."
        "expired_token" -> "The GitHub code expired. Start again."
        "device_flow_disabled" -> "Device flow is off for this GitHub App. Turn on \"Enable Device Flow\" in its settings."
        "incorrect_client_credentials", "unauthorized_client" -> "GitHub did not recognize this Client ID."
        else -> "GitHub authorization failed ($error)."
    }

    companion object {
        private val CLIENT_ID = Regex("[A-Za-z0-9._-]{8,64}")
    }
}
