package com.lumostech.accessibilitycore

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object ClickSequenceExecutor {
    suspend fun execute(
        points: List<ClickCounterPoint>,
        now: () -> Long,
        wait: suspend (Long) -> Unit,
        click: suspend (ClickCounterPoint) -> Boolean
    ): Boolean {
        if (!ClickSequenceCodec.isValid(points)) return false
        val started = now()
        for (point in points) {
            currentCoroutineContext().ensureActive()
            val remaining = point.delay - (now() - started)
            if (remaining > 0) wait(remaining)
            currentCoroutineContext().ensureActive()
            if (!click(point)) return false
        }
        return true
    }
}
