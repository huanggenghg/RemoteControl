package com.lumostech.remotecontrol.protocol

import com.lumostech.communication.*
import org.junit.Assert.*
import org.junit.Test

class RemoteProtocolTest {
    private class Link {
        data class Packet(val from: Peer, val to: Peer, val data: ByteArray)
        var now = 0L
        var executed = 0
        val commands = mutableListOf<RemoteCommand>()
        val packets = java.util.ArrayDeque<Packet>()
        val host = RemoteProtocol(SessionRole.HOST, "h", { now },
            { peer, bytes, _ -> packets.add(Packet(Peer("h"), peer, bytes)) },
            { commands += it; executed++; true }, {}, { _, _ -> })
        val controller = RemoteProtocol(SessionRole.CONTROLLER, "c", { now },
            { peer, bytes, _ -> packets.add(Packet(Peer("c"), peer, bytes)) },
            { throw AssertionError("Controller cannot dispatch") }, {}, { _, _ -> })
        fun connect() {
            host.connection(true)
            controller.connection(true)
            host.sharing(ScreenGeometry(1080, 1920, 0))
            host.peerJoined(Peer("c"))
            controller.peerJoined(Peer("h"))
            pump()
        }
        fun pump() {
            while (packets.isNotEmpty()) {
                val packet = packets.removeFirst()
                if (packet.to.id == "h") host.receive(packet.from, packet.data)
                else controller.receive(packet.from, packet.data)
            }
        }
    }

    @Test fun handshakeAndDuplicateCommandsDispatchExactlyOnce() {
        val link = Link()
        link.connect()
        assertTrue(link.controller.canControl)
        assertTrue(link.controller.command(RemoteCommand("click", .5f, .5f)))
        val replay = link.packets.toList()
        link.pump()
        assertEquals(1, link.executed)
        replay.forEach { link.host.receive(it.from, it.data) }
        link.pump()
        assertEquals(1, link.executed)
    }

    @Test fun delayedCommandCannotExecuteAfterReceiverLeaseExpires() {
        val link = Link()
        link.connect()
        link.controller.command(RemoteCommand("home"))
        link.now += 3001
        link.pump()
        assertEquals(0, link.executed)
        assertFalse(link.controller.canControl)
    }

    @Test fun reconnectRehandshakesWithoutDependingOnNewMemberCallbacks() {
        val link = Link()
        link.connect()
        link.controller.command(RemoteCommand("home"))
        val previous = link.packets.toList()
        link.packets.clear()
        link.host.connection(false)
        link.controller.connection(false)
        link.host.connection(true)
        link.controller.connection(true)
        assertFalse(link.controller.canControl)
        link.controller.tick()
        link.pump()
        assertTrue(link.controller.canControl)
        previous.forEach { link.host.receive(it.from, it.data) }
        link.pump()
        assertEquals(0, link.executed)
        assertTrue(link.controller.command(RemoteCommand("home")))
        link.pump()
        assertEquals(1, link.executed)
    }
    @Test fun fullInputLimitSurvivesFramingAndExecutesUnchanged() {
        val link = Link()
        link.connect()
        listOf("\"", "\\", "\u0000", "\n", "<").forEach { character ->
            val command = RemoteCommand("softInput", inputText = character.repeat(4096))
            assertTrue(link.controller.command(command))
            link.pump()
            assertEquals(command, link.commands.last())
        }
        assertEquals(5, link.executed)
    }

    @Test fun invalidCommandsDoNotThrowOrConsumeSequenceOrPendingSlots() {
        val link = Link()
        link.connect()
        listOf(
            RemoteCommand("softInput", inputText = "x".repeat(4097)),
            RemoteCommand("unknown"),
            RemoteCommand("click", Float.NaN, .5f),
            RemoteCommand("home", inputText = "x".repeat(9000))
        ).forEach { assertFalse(link.controller.command(it)) }
        assertTrue(link.packets.isEmpty())
        repeat(16) { assertTrue(link.controller.command(RemoteCommand("home"))) }
        assertFalse(link.controller.command(RemoteCommand("home")))
        val frames = ControlFrameCodec { link.now }
        val firstCommand = link.packets.firstNotNullOf { frames.receive(it.from.id, it.data)?.toString(Charsets.UTF_8) }
        assertEquals(1L, com.google.gson.JsonParser.parseString(firstCommand).asJsonObject.get("sequence").asLong)
    }

}
