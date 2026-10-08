package com.lumostech.remotecontrol.protocol

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lumostech.communication.Peer
import com.lumostech.communication.ScreenGeometry
import com.lumostech.communication.SessionRole
import java.util.UUID

/** All calls are confined to the session's main dispatcher. No SDK or gesture execution lives here. */
class RemoteProtocol(
    private val role: SessionRole,
    private val localId: String,
    private val nowMs: () -> Long,
    private val send: (Peer, ByteArray, Long?) -> Unit,
    private val execute: (RemoteCommand) -> Boolean,
    private val changed: () -> Unit,
    private val result: (String, String) -> Unit
) {
    private val gson = Gson()
    private val frames = ControlFrameCodec(nowMs)
    private val gate = ControlLeaseGate(nowMs)
    private val peers = linkedSetOf<Peer>()
    private val pending = mutableMapOf<String, Long>()
    private var hello = UUID.randomUUID().toString()
    private var remoteHello: String? = null
    private var session: String? = null
    private var lease: String? = null
    private var leaseDeadline = 0L
    private var sequence = 0L
    private var hostGeometry: ScreenGeometry? = null
    private var sharing = false
    var peer: Peer? = null
        private set
    var geometry: ScreenGeometry? = null
        private set
    var connected = false
        private set
    val canControl: Boolean get() = role == SessionRole.CONTROLLER && connected &&
        peer != null && geometry != null && lease != null && nowMs() < leaseDeadline

    fun connection(available: Boolean) {
        connected = available
        resetHandshake()
        changed()
    }

    fun sharing(geometry: ScreenGeometry?) {
        hostGeometry = geometry
        sharing = geometry != null
        if (!sharing) resetHandshake()
        changed()
    }

    fun peerJoined(value: Peer) { if (value.id != localId && peers.size < 8) peers += value; tick() }

    fun peerLeft(value: Peer) {
        peers -= value
        if (peer == value) resetHandshake()
        changed()
    }

    fun tick() {
        if (!connected) return
        val now = nowMs()
        pending.filterValues { it <= now }.keys.toList().forEach { id ->
            pending.remove(id)
            result(id, "unknown")
        }
        if (role == SessionRole.CONTROLLER) {
            peers.filter { peer == null || it == peer }.forEach {
                transmit(it, "hello") { addProperty("hello", hello); addProperty("role", "controller") }
            }
        } else if (sharing && peer != null && session != null) {
            sendLease(peer!!)
        }
        changed()
    }

    fun command(command: RemoteCommand): Boolean {
        if (!canControl || pending.size >= 16) return false
        val target = peer!!
        val id = UUID.randomUUID().toString()
        val nextSequence = sequence + 1
        val encodedFrames = runCatching {
            val payload = RemoteCommandCodec.encode(command)
            encodeFrames(target, "command") {
                addProperty("session", session)
                addProperty("lease", lease)
                addProperty("sequence", nextSequence)
                addProperty("messageId", id)
                add("payload", JsonParser.parseString(payload))
            }
        }.getOrNull() ?: return false
        // Validation and complete framing must succeed before command bookkeeping changes.
        pending[id] = nowMs() + 3000
        sequence = nextSequence
        encodedFrames.forEach { send(target, it, leaseDeadline) }
        return true
    }

    fun receive(from: Peer, frame: ByteArray) {
        if (!connected || from !in peers) return
        val bytes = frames.receive(from.id, frame) ?: return
        try {
            val json = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
            if (json.get("version")?.asInt != 1 || json.get("recipient")?.asString != localId) return
            when (json.get("type")?.asString) {
                "hello" -> {
                    if (role != SessionRole.HOST || !sharing || hostGeometry == null ||
                        json.get("role")?.asString != "controller" || (peer != null && peer != from)) return
                    val nonce = json.get("hello")?.asString ?: return
                    if (nonce.length !in 1..64) return
                    if (peer != from || remoteHello != nonce) {
                        peer = from
                        remoteHello = nonce
                        session = UUID.randomUUID().toString()
                        gate.begin(session!!, from.id)
                    }
                    transmit(from, "screen") {
                        addProperty("session", session)
                        addProperty("hello", remoteHello)
                        add("geometry", gson.toJsonTree(hostGeometry))
                    }
                    sendLease(from)
                }
                "screen" -> {
                    if (role != SessionRole.CONTROLLER || (peer != null && peer != from) ||
                        json.get("hello")?.asString != hello) return
                    val dimensions = gson.fromJson(json.get("geometry"), ScreenGeometry::class.java)
                    if (dimensions.width !in 1..16384 || dimensions.height !in 1..16384) return
                    val newSession = json.get("session")?.asString ?: return
                    if (newSession.length !in 1..64) return
                    if (session != newSession) {
                        lease = null
                        pending.clear()
                        sequence = 0
                    }
                    session = newSession
                    peer = from
                    geometry = dimensions
                }
                "lease" -> {
                    if (role != SessionRole.CONTROLLER || from != peer || session == null ||
                        json.get("session")?.asString != session || json.get("hello")?.asString != hello) return
                    val incoming = json.get("lease")?.asString ?: return
                    if (incoming.length !in 1..64) return
                    lease = incoming
                    // A transport-delayed lease can still expire sooner on the receiver. It can never extend it.
                    leaseDeadline = nowMs() + 1500
                }
                "command" -> {
                    if (role != SessionRole.HOST || !sharing || from != peer) return
                    val command = RemoteCommandCodec.decode(json.get("payload")?.toString() ?: return) ?: return
                    val id = json.get("messageId")?.asString ?: return
                    if (id.length !in 1..64 || !gate.accept(json.get("session").asString, from.id,
                            json.get("lease").asString, json.get("sequence").asLong, id)) return
                    val dispatched = execute(command)
                    transmit(from, "result") {
                        addProperty("session", session)
                        addProperty("messageId", id)
                        addProperty("status", if (dispatched) "dispatched" else "rejected")
                    }
                }
                "result" -> {
                    if (role != SessionRole.CONTROLLER || from != peer ||
                        json.get("session")?.asString != session) return
                    val id = json.get("messageId")?.asString ?: return
                    if (pending.remove(id) != null) result(id, json.get("status")?.asString ?: "unknown")
                }
            }
            changed()
        } catch (_: Exception) {
            // Invalid remote input must not crash the application or dispatch gestures.
        }
    }

    private fun sendLease(target: Peer) {
        val issued = gate.issueLease()
        transmit(target, "lease") {
            addProperty("session", session)
            addProperty("hello", remoteHello)
            addProperty("lease", issued)
        }
    }

    private fun transmit(target: Peer, type: String, deadline: Long? = null, fill: JsonObject.() -> Unit) {
        encodeFrames(target, type, fill).forEach { send(target, it, deadline) }
    }

    private fun encodeFrames(target: Peer, type: String, fill: JsonObject.() -> Unit): List<ByteArray> {
        val json = JsonObject().apply {
            addProperty("version", 1)
            addProperty("type", type)
            addProperty("recipient", target.id)
            fill()
        }
        return frames.encode(json.toString().toByteArray(Charsets.UTF_8))
    }

    private fun resetHandshake() {
        hello = UUID.randomUUID().toString()
        peer = null
        remoteHello = null
        session = null
        lease = null
        geometry = null
        sequence = 0
        gate.close()
        frames.clear()
        pending.keys.toList().forEach { result(it, "unknown") }
        pending.clear()
    }
}
