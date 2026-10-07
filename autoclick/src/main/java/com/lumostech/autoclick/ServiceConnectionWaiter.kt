package com.lumostech.autoclick

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Bounds the initialization race without retrying any already dispatched gesture. */
internal object ServiceConnectionWaiter {
    suspend fun <T : Any> await(
        timeoutMs: Long,
        pollMs: Long,
        canContinue: suspend () -> Boolean,
        findConnected: suspend () -> T?
    ): T? {
        require(timeoutMs > 0 && pollMs > 0)
        return withTimeoutOrNull(timeoutMs) {
            var connected: T? = null
            while (connected == null && canContinue()) {
                connected = findConnected()
                if (connected == null) delay(pollMs)
            }
            connected
        }
    }
}
