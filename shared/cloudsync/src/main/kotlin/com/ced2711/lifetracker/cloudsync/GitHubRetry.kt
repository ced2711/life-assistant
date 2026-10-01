package com.ced2711.lifetracker.cloudsync

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.delay

/**
 * Runs [block] again when GitHub could not be reached, waiting 1, 2 and 4 seconds. Phones drop
 * connections when an app goes to the background and comes back, which a moment later works again.
 * Only for requests that are safe to repeat.
 */
internal suspend fun <T> retryTransient(attempts: Int = 4, firstDelayMillis: Long = 1_000L, block: suspend () -> T): T {
    var wait = firstDelayMillis
    repeat(attempts - 1) {
        try {
            return block()
        } catch (error: CloudTransportException) {
            if (error.cause !is IOException) throw error
            delay(wait)
            wait *= 2
        }
    }
    return block()
}

/** "Could not reach GitHub" with the reason, so a report says what actually failed. */
internal fun unreachable(error: IOException): CloudTransportException {
    val reason = when (error) {
        is UnknownHostException -> "no connection or name lookup failed"
        is SocketTimeoutException -> "timed out"
        else -> error.javaClass.simpleName + (error.message?.takeIf { it.length <= 80 }?.let { ": $it" } ?: "")
    }
    return CloudTransportException("Could not reach GitHub ($reason).", error)
}
