package com.lumostech.autoclick

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/** Release the broadcast independently of blocking preference/legacy-work cleanup. */
internal fun launchBoundedReceiverTask(
    scope: CoroutineScope,
    timeoutMs: Long = 8_000,
    finish: () -> Unit,
    work: suspend () -> Unit
) {
    val finished = AtomicBoolean()
    fun complete() {
        if (finished.compareAndSet(false, true)) {
            try { finish() } finally { scope.cancel() }
        }
    }
    scope.launch(Dispatchers.Default) { delay(timeoutMs); complete() }
    scope.launch {
        try { work() } finally { complete() }
    }
}
