package com.lumostech.communication.zego

/** Orders ZEGO's coupled video/encoder switch and custom-source selection without opening a camera. */
internal class CaptureDevices(
    private val enableVideo: (Boolean) -> Unit,
    private val enableAudioCapture: (Boolean) -> Unit,
    private val enableCustomCapture: (Boolean) -> Unit
) {
    fun initialize() {
        enableVideo(false)
        enableAudioCapture(false)
    }

    fun start() {
        enableCustomCapture(true)
        // ZEGO's enableCamera also gates the encoder when a custom source is installed.
        enableVideo(true)
    }

    fun stop() {
        // Disable video before reverting to the default source, which is the physical camera.
        enableVideo(false)
        enableCustomCapture(false)
    }
}
