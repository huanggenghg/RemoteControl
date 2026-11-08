package com.lumostech.remotecontrol.api

data class ZegoToken(
    val data: String? = null,
    val error: ZegoError? = null,
    val appId: String? = null,
    val roomId: String? = null,
    val userId: String? = null,
    val expiresAt: Long? = null
) {
    override fun toString() = "ZegoToken(data=<redacted>)"
}
data class ZegoError(val code: String? = null, val message: String? = null) {
    override fun toString() = "ZegoError(code=<redacted>, message=<redacted>)"
}
