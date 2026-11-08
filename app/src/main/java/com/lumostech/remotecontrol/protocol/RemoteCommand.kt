package com.lumostech.remotecontrol.protocol

data class RemoteCommand(
    val action: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val distance: Float = 200f,
    val duration: Long = 300L,
    val inputText: String = "",
    val endX: Float? = null,
    val endY: Float? = null
) {
    companion object {
        const val MAX_SWIPE_DURATION_MS = 10_000L
    }
}
