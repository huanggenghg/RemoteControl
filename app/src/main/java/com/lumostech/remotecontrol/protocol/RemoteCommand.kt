package com.lumostech.remotecontrol.protocol

data class RemoteCommand(
    val action: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val distance: Float = 200f,
    val duration: Long = 300L,
    val inputText: String = ""
)
