package com.ced2711.lifetracker.ui.vault

import com.ced2711.lifetracker.data.vault.VaultSession

/**
 * Grants one independent session lease for one completed, fresh backup authentication.
 * It never owns or closes the ViewModel's source session; the lease caller owns the fork.
 */
internal class VaultBackupSessionLeaseOwner(initialGeneration: Long = 0L) {
    var generation: Long = initialGeneration
        private set

    private var authenticatedSession: VaultSession? = null

    @Synchronized
    fun beginFreshAuthentication(): Long {
        generation++
        authenticatedSession = null
        return generation
    }

    @Synchronized
    fun markAuthenticated(generation: Long, session: VaultSession): Boolean {
        if (this.generation != generation) return false
        authenticatedSession = session
        return true
    }

    @Synchronized
    fun acquire(session: VaultSession): VaultSession? {
        if (authenticatedSession !== session) return null
        authenticatedSession = null
        return session.fork()
    }

    @Synchronized
    fun invalidate() {
        generation++
        authenticatedSession = null
    }
}
