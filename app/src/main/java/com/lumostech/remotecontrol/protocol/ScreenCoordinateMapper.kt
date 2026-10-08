package com.lumostech.remotecontrol.protocol

object ScreenCoordinateMapper {
    fun map(x: Float, y: Float, sourceWidth: Int, sourceHeight: Int,
            viewWidth: Int, viewHeight: Int): Pair<Float, Float>? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || viewWidth <= 0 || viewHeight <= 0 ||
            !x.isFinite() || !y.isFinite()) return null
        val scale = minOf(viewWidth.toFloat() / sourceWidth, viewHeight.toFloat() / sourceHeight)
        val left = (viewWidth - sourceWidth * scale) / 2
        val top = (viewHeight - sourceHeight * scale) / 2
        val mappedX = (x - left) / scale
        val mappedY = (y - top) / scale
        return if (mappedX in 0f..sourceWidth.toFloat() && mappedY in 0f..sourceHeight.toFloat())
            mappedX to mappedY else null
    }
}
