package com.ced2711.lifetracker.data.vault

internal enum class OwnedClipboardAction {
    CLEAR,
    LEAVE_UNCHANGED,
}

internal data class OwnedClipboardState(
    val token: String,
    val clearAtEpochMillis: Long,
    val deadlineReached: Boolean,
)

/** Tracks only an opaque ownership token; clipboard contents never enter this policy. */
internal class VaultClipboardOwnershipPolicy(
    initialState: OwnedClipboardState? = null,
) {
    var state: OwnedClipboardState? = initialState
        private set

    fun claim(token: String, clearAtEpochMillis: Long) {
        require(token.isNotEmpty())
        require(clearAtEpochMillis > 0L)
        state = OwnedClipboardState(
            token = token,
            clearAtEpochMillis = clearAtEpochMillis,
            deadlineReached = false,
        )
    }

    fun markDeadlineReached(token: String): Boolean {
        val owned = state ?: return false
        if (owned.token != token) return false
        state = owned.copy(deadlineReached = true)
        return true
    }

    fun expireOwnedClip(): Boolean {
        val owned = state ?: return false
        state = owned.copy(deadlineReached = true)
        return true
    }

    fun reconcile(
        currentClipboardToken: String?,
        nowEpochMillis: Long,
    ): OwnedClipboardAction {
        val owned = state ?: return OwnedClipboardAction.LEAVE_UNCHANGED
        if (currentClipboardToken != owned.token) {
            state = null
            return OwnedClipboardAction.LEAVE_UNCHANGED
        }
        if (owned.deadlineReached || nowEpochMillis >= owned.clearAtEpochMillis) {
            return OwnedClipboardAction.CLEAR
        }
        return OwnedClipboardAction.LEAVE_UNCHANGED
    }

    fun forget() {
        state = null
    }
}
