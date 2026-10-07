package com.lumostech.autoclick

import com.lumostech.accessibilitycore.ClickCounterPoint

data class ClickControlPosition(val x: Int, val y: Int) {
    fun covers(point: ClickCounterPoint, width: Int, height: Int): Boolean =
        point.x >= x && point.x < x + width && point.y >= y && point.y < y + height
}

object StopControlPlacement {
    fun find(screenWidth: Int, screenHeight: Int, width: Int, height: Int,
             points: List<ClickCounterPoint>): ClickControlPosition? {
        if (width <= 0 || height <= 0 || screenWidth < width || screenHeight < height) return null
        val maxX = screenWidth - width
        val maxY = screenHeight - height
        // Search edges first, then a small grid; maintain an extra touch margin.
        val xs = (listOf(maxX, 0) + (0..16).map { (maxX.toLong() * it / 16).toInt() }).distinct()
        val ys = (listOf(0, maxY) + (0..24).map { (maxY.toLong() * it / 24).toInt() }).distinct()
        return ys.asSequence().flatMap { y -> xs.asSequence().map { x -> ClickControlPosition(x, y) } }
            .firstOrNull { position -> points.none { point ->
                point.x >= position.x - 8 && point.x < position.x + width + 8 &&
                    point.y >= position.y - 8 && point.y < position.y + height + 8
            } }
    }
}
