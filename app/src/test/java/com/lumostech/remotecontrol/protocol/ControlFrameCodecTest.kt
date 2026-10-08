package com.lumostech.remotecontrol.protocol

import org.junit.Assert.*
import org.junit.Test

class ControlFrameCodecTest {
    @Test fun multibyteTextReassemblesOnlyWhenAllFramesArrive() {
        val codec = ControlFrameCodec { 0 }
        val body = "中文🙂".repeat(250).toByteArray()
        val frames = codec.encode(body)
        assertTrue(frames.size > 1)
        assertTrue(frames.all { it.size <= 900 })
        var assembled: ByteArray? = null
        frames.reversed().forEach { assembled = codec.receive("p", it) ?: assembled }
        assertArrayEquals(body, assembled)
    }

    @Test fun incompleteFragmentsExpireAndClearCannotCompleteOldMessage() {
        var now = 0L
        val codec = ControlFrameCodec { now }
        val frames = codec.encode(ByteArray(1000) { 5 })
        assertNull(codec.receive("p", frames.first()))
        now = 2001
        frames.drop(1).forEach { assertNull(codec.receive("p", it)) }
        codec.clear()
        assertNull(codec.receive("p", frames.first()))
    }

    @Test fun differentSendersCannotCompleteEachOthersMessage() {
        val codec = ControlFrameCodec { 0 }
        val frames = codec.encode(ByteArray(1000))
        assertNull(codec.receive("a", frames.first()))
        frames.drop(1).forEach { assertNull(codec.receive("b", it)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedMessagesAreRejectedBeforeSend() {
        ControlFrameCodec { 0 }.encode(ByteArray(8193))
    }
}
