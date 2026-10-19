package com.ced2711.lifetracker.cloudsync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * What a device keeps about its GitHub sign-in. Since August 2026 new GitHub OAuth apps hand out
 * access tokens that expire after eight hours, together with a refresh token that is good for six
 * months of not being used and is replaced each time it is used. Apps whose owner turned
 * expiration off, and sign-ins saved by older versions, have only [accessToken].
 */
data class GitHubSignIn(
    val accessToken: String,
    val expiresAtMillis: Long? = null,
    val refreshToken: String? = null,
    val refreshExpiresAtMillis: Long? = null,
) {
    /** True a little before the access token runs out, so a sync never starts with a dying token. */
    fun needsRenewal(nowMillis: Long): Boolean =
        refreshToken != null && expiresAtMillis != null && nowMillis >= expiresAtMillis - RENEW_EARLY_MILLIS

    /** The text to keep in the device's secret storage. */
    fun encode(): String = if (refreshToken == null && expiresAtMillis == null) {
        // Same as what older versions saved, so a downgrade still reads it.
        accessToken
    } else {
        JsonObject(
            buildMap {
                put("access_token", JsonPrimitive(accessToken))
                expiresAtMillis?.let { put("expires_at", JsonPrimitive(it)) }
                refreshToken?.let { put("refresh_token", JsonPrimitive(it)) }
                refreshExpiresAtMillis?.let { put("refresh_expires_at", JsonPrimitive(it)) }
            },
        ).toString()
    }

    companion object {
        private const val RENEW_EARLY_MILLIS = 10 * 60_000L

        fun of(token: GitHubToken): GitHubSignIn =
            GitHubSignIn(token.accessToken, token.expiresAtMillis, token.refreshToken, token.refreshExpiresAtMillis)

        /** Reads [encode]'s text, and the bare token that versions before 2.0.1 saved. */
        fun decode(saved: String): GitHubSignIn {
            val text = saved.trim()
            if (!text.startsWith("{")) return GitHubSignIn(text)
            val fields = try {
                Json.parseToJsonElement(text).jsonObject
            } catch (error: IllegalArgumentException) {
                throw GitHubSignInExpiredException()
            }
            return GitHubSignIn(
                accessToken = fields["access_token"]?.jsonPrimitive?.contentOrNull ?: throw GitHubSignInExpiredException(),
                expiresAtMillis = fields["expires_at"]?.jsonPrimitive?.longOrNull,
                refreshToken = fields["refresh_token"]?.jsonPrimitive?.contentOrNull,
                refreshExpiresAtMillis = fields["refresh_expires_at"]?.jsonPrimitive?.longOrNull,
            )
        }
    }
}

/** A token source that can get a new token when GitHub rejected the one it handed out. */
interface RenewableTokenProvider : AccessTokenProvider {
    /** A token other than [rejected], or null when only signing in again can help. */
    suspend fun renewAfterRejection(rejected: String): String?
}

/**
 * Hands out the saved GitHub access token and renews it with the refresh token shortly before it
 * expires, so a device stays connected without the user doing anything.
 *
 * GitHub replaces the refresh token on every renewal and the old one stops working at once, so the
 * new pair is saved before it is used. Should saving fail, the pair is kept in memory and saved
 * at the next opportunity rather than lost.
 *
 * [load] returns the saved text or null when nothing is saved; it may throw when the storage is
 * unreadable for the moment. Use one session per device storage so renewals never overlap.
 */
class GitHubSession(
    private val load: () -> String?,
    private val save: (String) -> Unit,
    private val authorization: () -> GitHubDeviceAuthorization,
    private val now: () -> Long = System::currentTimeMillis,
) : RenewableTokenProvider {
    private val mutex = Mutex()
    private var unsaved: GitHubSignIn? = null

    override suspend fun accessToken(): String = mutex.withLock {
        val signIn = current()
        if (signIn.needsRenewal(now())) renew(signIn).accessToken else signIn.accessToken
    }

    override suspend fun renewAfterRejection(rejected: String): String? = mutex.withLock {
        val signIn = current()
        when {
            // Another sync renewed it in the meantime.
            signIn.accessToken != rejected -> signIn.accessToken
            signIn.refreshToken == null -> null
            else -> renew(signIn).accessToken
        }
    }

    /** Forget a pair that could not be saved, when the user signs in again or disconnects. */
    suspend fun reset() = mutex.withLock { unsaved = null }

    private fun current(): GitHubSignIn {
        unsaved?.let { pending ->
            if (runCatching { save(pending.encode()) }.isSuccess) unsaved = null
            return pending
        }
        return GitHubSignIn.decode(load() ?: throw GitHubSignInExpiredException())
    }

    private suspend fun renew(signIn: GitHubSignIn): GitHubSignIn {
        val refreshToken = signIn.refreshToken ?: throw GitHubSignInExpiredException()
        val renewed = GitHubSignIn.of(authorization().refresh(refreshToken))
        unsaved = renewed
        if (runCatching { save(renewed.encode()) }.isSuccess) unsaved = null
        return renewed
    }
}
