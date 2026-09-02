package com.ced2711.lifetracker.data.vault

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.annotation.MainThread
import androidx.core.content.edit
import java.util.UUID

class VaultClipboard(
    context: Context,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val clipboardManager =
        context.applicationContext.getSystemService(ClipboardManager::class.java)
    private val ownership = VaultClipboardOwnershipPolicy(loadState(preferences))
    private val clipChangedListener =
        ClipboardManager.OnPrimaryClipChangedListener(::onPrimaryClipChanged)
    private var hostHasWindowFocus = false
    private var isMutatingClipboard = false
    private var scheduledClear: Runnable? = null
    private var scheduledForegroundRetry: Runnable? = null

    init {
        clipboardManager.addPrimaryClipChangedListener(clipChangedListener)
        scheduleCurrentDeadline()
    }

    @MainThread
    fun copyAccount(account: CharSequence) {
        copySensitive(account)
    }

    @MainThread
    fun copyPassword(password: CharSequence) {
        copySensitive(password)
    }

    /** Clears the clipboard only when it still contains the value copied by this instance. */
    @MainThread
    fun clearOwnedClip() {
        if (ownership.expireOwnedClip()) {
            persistState()
            reconcileOwnedClip()
        }
    }

    /** Use when the vault is explicitly locked. */
    @MainThread
    fun onVaultLocked() {
        clearOwnedClip()
    }

    /** Expires any owned clip; it is cleared once its ownership can be safely verified. */
    @MainThread
    fun reset() {
        clearOwnedClip()
    }

    /**
     * Clipboard reads are restricted while the app has no input focus on Android 10+.
     * The host must report real window focus, not only lifecycle RESUME, before reconciliation.
     */
    @MainThread
    fun onHostWindowFocusChanged(hasFocus: Boolean) {
        hostHasWindowFocus = hasFocus
        if (hasFocus) {
            reconcileOwnedClip()
        } else {
            cancelForegroundRetry()
        }
    }

    @MainThread
    private fun copySensitive(value: CharSequence) {
        val token = UUID.randomUUID().toString()
        val clearAtEpochMillis = System.currentTimeMillis() + CLEAR_DELAY_MILLIS
        val clip = ClipData.newPlainText(CLIP_LABEL, value).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(sensitiveExtraKey(), true)
                putString(OWNER_TOKEN_EXTRA, token)
            }
        }
        ownership.claim(token, clearAtEpochMillis)
        persistState()
        scheduleCurrentDeadline()

        isMutatingClipboard = true
        try {
            clipboardManager.setPrimaryClip(clip)
        } catch (error: RuntimeException) {
            ownership.forget()
            persistState()
            cancelScheduledClear()
            throw error
        } finally {
            isMutatingClipboard = false
        }
    }

    @MainThread
    private fun onPrimaryClipChanged() {
        if (!isMutatingClipboard && hostHasWindowFocus) {
            reconcileOwnedClip()
        }
    }

    @MainThread
    private fun onClearDeadline(token: String) {
        if (!ownership.markDeadlineReached(token)) return
        persistState()
        if (hostHasWindowFocus) {
            reconcileOwnedClip()
        }
    }

    @MainThread
    private fun reconcileOwnedClip(allowForegroundRetry: Boolean = true) {
        if (!hostHasWindowFocus || ownership.state == null) return
        val readResult = readCurrentOwnershipToken()
        if (readResult !is ClipboardTokenRead.Available) {
            if (allowForegroundRetry) scheduleForegroundRetry()
            return
        }
        cancelForegroundRetry()

        val action = ownership.reconcile(
            currentClipboardToken = readResult.token,
            nowEpochMillis = System.currentTimeMillis(),
        )
        when (action) {
            OwnedClipboardAction.CLEAR -> {
                val cleared = try {
                    clearPrimaryClipCompat()
                    true
                } catch (_: RuntimeException) {
                    false
                }
                if (cleared) ownership.forget()
                persistState()
                cancelScheduledClear()
                if (!cleared && allowForegroundRetry) scheduleForegroundRetry()
            }
            OwnedClipboardAction.LEAVE_UNCHANGED -> {
                persistState()
                if (ownership.state == null) {
                    cancelScheduledClear()
                } else {
                    scheduleCurrentDeadline()
                }
            }
        }
    }

    private fun readCurrentOwnershipToken(): ClipboardTokenRead = try {
        ClipboardTokenRead.Available(
            clipboardManager.primaryClipDescription?.extras?.getString(OWNER_TOKEN_EXTRA),
        )
    } catch (_: SecurityException) {
        ClipboardTokenRead.Unavailable
    }

    private fun scheduleCurrentDeadline() {
        val state = ownership.state ?: return cancelScheduledClear()
        cancelScheduledClear()
        val delayMillis = if (state.deadlineReached) {
            0L
        } else {
            (state.clearAtEpochMillis - System.currentTimeMillis())
                .coerceIn(0L, CLEAR_DELAY_MILLIS)
        }
        Runnable { onClearDeadline(state.token) }.also { runnable ->
            scheduledClear = runnable
            handler.postDelayed(runnable, delayMillis)
        }
    }

    private fun cancelScheduledClear() {
        scheduledClear?.let(handler::removeCallbacks)
        scheduledClear = null
    }

    private fun scheduleForegroundRetry() {
        cancelForegroundRetry()
        Runnable {
            scheduledForegroundRetry = null
            if (hostHasWindowFocus) reconcileOwnedClip(allowForegroundRetry = false)
        }.also { runnable ->
            scheduledForegroundRetry = runnable
            handler.postDelayed(runnable, FOREGROUND_RETRY_DELAY_MILLIS)
        }
    }

    private fun cancelForegroundRetry() {
        scheduledForegroundRetry?.let(handler::removeCallbacks)
        scheduledForegroundRetry = null
    }

    private fun persistState() {
        // Synchronous persistence closes the process-death gap after copying a secret.
        preferences.edit(commit = true) {
            ownership.state?.let { state ->
                putString(PREFERENCE_TOKEN, state.token)
                putLong(PREFERENCE_CLEAR_AT, state.clearAtEpochMillis)
                putBoolean(PREFERENCE_DEADLINE_REACHED, state.deadlineReached)
            } ?: run {
                remove(PREFERENCE_TOKEN)
                remove(PREFERENCE_CLEAR_AT)
                remove(PREFERENCE_DEADLINE_REACHED)
            }
        }
    }

    private fun clearPrimaryClipCompat() {
        isMutatingClipboard = true
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboardManager.clearPrimaryClip()
            } else {
                clipboardManager.setPrimaryClip(
                    ClipData.newPlainText(EMPTY_CLIP_LABEL, EMPTY_CLIP_TEXT).apply {
                        description.extras = PersistableBundle().apply {
                            putBoolean(sensitiveExtraKey(), true)
                        }
                    },
                )
            }
        } finally {
            isMutatingClipboard = false
        }
    }

    private fun sensitiveExtraKey(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            COMPAT_EXTRA_IS_SENSITIVE
        }

    private companion object {
        const val CLEAR_DELAY_MILLIS = 30_000L
        const val FOREGROUND_RETRY_DELAY_MILLIS = 250L
        const val CLIP_LABEL = "Life Tracker"
        const val EMPTY_CLIP_LABEL = ""
        const val EMPTY_CLIP_TEXT = ""
        const val OWNER_TOKEN_EXTRA = "com.ced2711.lifetracker.vault.CLIP_OWNER_TOKEN"
        const val COMPAT_EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
        const val PREFERENCES_NAME = "vault_clipboard_ownership"
        const val PREFERENCE_TOKEN = "token"
        const val PREFERENCE_CLEAR_AT = "clear_at_epoch_millis"
        const val PREFERENCE_DEADLINE_REACHED = "deadline_reached"

        fun loadState(preferences: SharedPreferences): OwnedClipboardState? {
            val token = preferences.getString(PREFERENCE_TOKEN, null)
                ?.takeIf(String::isNotEmpty)
                ?: return null
            val clearAtEpochMillis = preferences.getLong(PREFERENCE_CLEAR_AT, 0L)
                .takeIf { it > 0L }
                ?: return null
            return OwnedClipboardState(
                token = token,
                clearAtEpochMillis = clearAtEpochMillis,
                deadlineReached = preferences.getBoolean(PREFERENCE_DEADLINE_REACHED, false),
            )
        }
    }
}

private sealed interface ClipboardTokenRead {
    data class Available(val token: String?) : ClipboardTokenRead
    data object Unavailable : ClipboardTokenRead
}
