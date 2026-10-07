package com.lumostech.accessibilitycore

object ClickSequenceCodec {
    const val MAX_POINTS = 200
    const val MAX_DELAY_MS = 8 * 60 * 1_000L

    fun isValid(points: List<ClickCounterPoint>): Boolean =
        points.isNotEmpty() && points.size <= MAX_POINTS &&
            points.all { it.x.isFinite() && it.y.isFinite() && it.x >= 0 && it.y >= 0 && it.delay in 0..MAX_DELAY_MS } &&
            points.zipWithNext().all { (first, second) -> first.delay <= second.delay }

    fun encode(points: List<ClickCounterPoint>): String {
        require(points.isEmpty() || isValid(points))
        return "v1\n" + points.joinToString("\n") { "${it.x},${it.y},${it.delay}" }
    }

    fun decode(encoded: String): List<ClickCounterPoint> {
        if (!encoded.startsWith("v1\n")) return emptyList()
        return runCatching {
            val body = encoded.removePrefix("v1\n")
            if (body.isEmpty()) return emptyList()
            val points = body.lines().map { line ->
                val parts = line.split(',')
                require(parts.size == 3)
                ClickCounterPoint(parts[0].toFloat(), parts[1].toFloat(), parts[2].toLong())
            }
            points.takeIf(::isValid) ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
