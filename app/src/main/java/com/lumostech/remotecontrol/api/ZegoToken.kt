package com.lumostech.remotecontrol.api

data class ZegoToken(val data: String, val error: ZegoError)

data class ZegoError(val code: String, val message: String)
