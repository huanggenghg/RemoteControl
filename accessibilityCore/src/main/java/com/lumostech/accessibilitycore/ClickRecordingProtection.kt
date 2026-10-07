package com.lumostech.accessibilitycore

data class ClickEnvironment(
    val width: Int, val height: Int, val rotation: Int, val packageName: String,
    val displayId: Int = 0,
    val left: Int = 0, val top: Int = 0, val right: Int = width, val bottom: Int = height
) {
    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

enum class ClickProtectionFailure(val message: String) {
    SCREEN_LOCKED("屏幕关闭或锁定"),
    ENVIRONMENT_UNAVAILABLE("无法确认当前应用"),
    DISPLAY_CHANGED("屏幕尺寸或方向已改变，请重新录制"),
    APP_CHANGED("当前应用与录制时不一致"),
    UNSUPPORTED_DISPLAY("当前应用位于其他屏幕"),
    WINDOW_MISMATCH("点击位置不在目标应用窗口内"),
    INVALID_RECORDING("录制环境信息无效，请重新录制")
}

/** Immutable geometry and the expected foreground application for each recorded point. */
data class ClickRecordingProtection(
    val width: Int,
    val height: Int,
    val rotation: Int,
    val packages: List<String>
) {
    private fun hasValidMetadata(): Boolean = width > 0 && height > 0 && rotation in 0..3 &&
        packages.isNotEmpty() && packages.size <= ClickSequenceCodec.MAX_POINTS &&
        packages.all { it.isNotBlank() && it.none(Char::isWhitespace) && ',' !in it }

    fun isValid(points: List<ClickCounterPoint>): Boolean = hasValidMetadata() &&
        packages.size == points.size && ClickSequenceCodec.isValid(points) &&
        points.all { it.x < width && it.y < height }

    fun hasSameDisplay(environment: ClickEnvironment): Boolean =
        width == environment.width && height == environment.height && rotation == environment.rotation

    fun failureAt(index: Int, environment: ClickEnvironment?, interactive: Boolean, locked: Boolean,
                  point: ClickCounterPoint? = null): ClickProtectionFailure? = when {
        !interactive || locked -> ClickProtectionFailure.SCREEN_LOCKED
        !hasValidMetadata() || index !in packages.indices -> ClickProtectionFailure.INVALID_RECORDING
        environment == null || environment.packageName.isBlank() -> ClickProtectionFailure.ENVIRONMENT_UNAVAILABLE
        environment.displayId != 0 -> ClickProtectionFailure.UNSUPPORTED_DISPLAY
        !hasSameDisplay(environment) -> ClickProtectionFailure.DISPLAY_CHANGED
        packages[index] != environment.packageName -> ClickProtectionFailure.APP_CHANGED
        point != null && !environment.contains(point.x, point.y) -> ClickProtectionFailure.WINDOW_MISMATCH
        else -> null
    }

    fun encode(): String {
        require(hasValidMetadata())
        return "v1\n$width,$height,$rotation\n" + packages.joinToString("\n")
    }

    companion object {
        fun decode(encoded: String): ClickRecordingProtection? = runCatching {
            val lines = encoded.lines()
            require(lines.size >= 3 && lines[0] == "v1")
            val geometry = lines[1].split(',')
            require(geometry.size == 3)
            ClickRecordingProtection(geometry[0].toInt(), geometry[1].toInt(), geometry[2].toInt(), lines.drop(2))
                .takeIf { it.hasValidMetadata() }
        }.getOrNull()
    }
}
