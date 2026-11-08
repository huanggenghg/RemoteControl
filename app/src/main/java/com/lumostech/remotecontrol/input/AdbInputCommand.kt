package com.lumostech.remotecontrol.input

import com.lumostech.communication.ScreenGeometry
import com.lumostech.remotecontrol.protocol.RemoteCommand

internal class AdbInputCommand private constructor(val shell: String, val durationMs: Long = 0L) {
    companion object {
        fun from(command: RemoteCommand, screen: ScreenGeometry): AdbInputCommand? {
            if (screen.width <= 0 || screen.height <= 0) return null
            return when (command.action) {
                "click" -> {
                    val x = toPixel(command.x, screen.width)
                    val y = toPixel(command.y, screen.height)
                    AdbInputCommand("input tap $x $y")
                }
                "swipe" -> {
                    val swipe = ScreenSwipe.from(command, screen) ?: return null
                    AdbInputCommand("input swipe ${swipe.startX} ${swipe.startY} ${swipe.endX} ${swipe.endY} ${swipe.durationMs}",
                        swipe.durationMs)
                }
                "scrollUp", "scrollDown", "scrollLeft", "scrollRight" -> {
                    val x = screen.width / 2
                    val y = screen.height / 2
                    val distance = command.distance.toInt()
                    val endX = when (command.action) {
                        "scrollLeft" -> (x - distance).coerceAtLeast(0)
                        "scrollRight" -> (x + distance).coerceAtMost(screen.width - 1)
                        else -> x
                    }
                    val endY = when (command.action) {
                        "scrollUp" -> (y - distance).coerceAtLeast(0)
                        "scrollDown" -> (y + distance).coerceAtMost(screen.height - 1)
                        else -> y
                    }
                    AdbInputCommand("input swipe $x $y $endX $endY ${command.duration}", command.duration)
                }
                "back" -> AdbInputCommand("input keyevent KEYCODE_BACK")
                "home" -> AdbInputCommand("input keyevent KEYCODE_HOME")
                "recents" -> AdbInputCommand("input keyevent KEYCODE_APP_SWITCH")
                else -> null
            }
        }
    }
}

internal data class ScreenSwipe(val startX: Int, val startY: Int, val endX: Int, val endY: Int, val durationMs: Long) {
    companion object {
        fun from(command: RemoteCommand, screen: ScreenGeometry): ScreenSwipe? {
            val endX = command.endX ?: return null
            val endY = command.endY ?: return null
            return ScreenSwipe(
                toPixel(command.x, screen.width), toPixel(command.y, screen.height),
                toPixel(endX, screen.width), toPixel(endY, screen.height), command.duration
            )
        }
    }
}

private fun toPixel(position: Float, dimension: Int): Int =
    (position * dimension).toInt().coerceIn(0, dimension - 1)
