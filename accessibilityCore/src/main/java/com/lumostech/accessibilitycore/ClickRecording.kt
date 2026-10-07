package com.lumostech.accessibilitycore

/** Recording state belongs to the service, never to a disposable overlay view. */
class ClickRecording(initialPoints: List<ClickCounterPoint> = emptyList()) {
    private val points = initialPoints.toMutableList()
    private var startedAt: Long? = null

    fun record(x: Float, y: Float, now: Long) {
        require(x.isFinite() && y.isFinite() && x >= 0 && y >= 0)
        val start = startedAt ?: (now - (points.lastOrNull()?.delay ?: 0L)).also { startedAt = it }
        points.add(ClickCounterPoint(x, y, (now - start).coerceAtLeast(points.lastOrNull()?.delay ?: 0L)))
    }

    fun snapshot(): List<ClickCounterPoint> = points.toList()

    fun clear() {
        points.clear()
        startedAt = null
    }
}
