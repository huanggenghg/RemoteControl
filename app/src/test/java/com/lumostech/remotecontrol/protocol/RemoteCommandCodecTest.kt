package com.lumostech.remotecontrol.protocol

import org.junit.Assert.*
import org.junit.Test

class RemoteCommandCodecTest {
    @Test fun rejectsUnknownActionsAndNonFiniteCoordinates() {
        assertNull(RemoteCommandCodec.decode("{\"action\":\"whatever\",\"x\":1,\"y\":2}"))
        assertNull(RemoteCommandCodec.decode("{\"action\":\"click\",\"x\":\"NaN\",\"y\":2}"))
        assertNull(RemoteCommandCodec.decode("{}"))
        assertNull(RemoteCommandCodec.decode("not json"))
    }

    @Test fun keepsExplicitActionsAndValidatesInputByteLength() {
        val command = RemoteCommand("softInput", inputText = "你好")
        assertEquals(command, RemoteCommandCodec.decode(RemoteCommandCodec.encode(command)))
        assertNull(RemoteCommandCodec.decode("{\"action\":\"softInput\",\"inputText\":\"${"中".repeat(1400)}\"}"))
        assertEquals(RemoteCommand("home"), RemoteCommandCodec.decode("{\"action\":\"home\"}"))
    }

    @Test fun fullInputByteLimitRemainsCompactForJsonEscapes() {
        val inputs = listOf("\"", "\\", "\u0000", "\n", "<").map { it.repeat(4096) } +
            ("中".repeat(1000) + "\"".repeat(1096))
        inputs.forEach { text ->
            val command = RemoteCommand("softInput", inputText = text)
            val encoded = RemoteCommandCodec.encode(command)
            assertTrue("Input must fit alongside the protocol envelope", encoded.toByteArray(Charsets.UTF_8).size < 6000)
            assertEquals(command, RemoteCommandCodec.decode(encoded))
        }
    }

    @Test fun legacyRawTextStillDecodesAndInvalidBase64IsRejected() {
        assertEquals("legacy", RemoteCommandCodec.decode("{\"action\":\"softInput\",\"inputText\":\"legacy\"}")?.inputText)
        assertNull(RemoteCommandCodec.decode("{\"action\":\"softInput\",\"inputTextBase64\":\"!\"}"))
        assertNull(RemoteCommandCodec.decode("{\"action\":\"softInput\",\"inputTextBase64\":\"/w==\"}"))
    }

    @Test fun aspectFitIgnoresBlackBarsAndMapsToSourceCoordinates() {
        assertNull(ScreenCoordinateMapper.map(20f, 10f, 100, 200, 200, 200))
        assertEquals(Pair(50f, 100f), ScreenCoordinateMapper.map(100f, 100f, 100, 200, 200, 200))
        assertNull(ScreenCoordinateMapper.map(1f, 1f, 0, 200, 200, 200))
        assertEquals(Pair(0f, 200f), ScreenCoordinateMapper.map(
            20f, 210f, 100, 200, 200, 200, clampToContent = true))
    }

    @Test fun swipeRequiresTwoValidEndpointsAndAnExplicitDuration() {
        val command = RemoteCommand("swipe", .25f, .75f, duration = 180L, endX = 1f, endY = .25f)
        assertEquals(command, RemoteCommandCodec.decode(RemoteCommandCodec.encode(command)))
        val payload = """{"action":"swipe","x":0.25,"y":0.75,"endX":1,"endY":0.25,"duration":180}"""
        assertNull(RemoteCommandCodec.decode(payload.replace(",\"endY\":0.25", "")))
        assertNull(RemoteCommandCodec.decode(payload.replace(",\"duration\":180", "")))
        assertNull(RemoteCommandCodec.decode(payload.replace("180", "10001")))
        assertNull(RemoteCommandCodec.decode(payload.replace("\"endX\":1", "\"endX\":1.01")))
        assertNull(RemoteCommandCodec.decode(payload.replace("\"endX\":1", "\"endX\":\"NaN\"")))
        assertEquals(RemoteCommand("scrollUp"), RemoteCommandCodec.decode("""{"action":"scrollUp"}"""))
    }
}
