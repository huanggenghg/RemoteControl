package com.lumostech.remotecontrol.protocol

import java.util.UUID

/** The receiver owns the clock. Remote device clock skew cannot extend a command lease. */
class ControlLeaseGate(private val nowMs: () -> Long) {
    private var session: String? = null
    private var peer: String? = null
    private var lastSequence = 0L
    private val leases = mutableMapOf<String, Long>()
    private val received = LinkedHashSet<String>()

    @Synchronized fun begin(sessionId: String, peerId: String) {
        close()
        session = sessionId
        peer = peerId
    }

    @Synchronized fun issueLease(): String {
        check(session != null)
        val now = nowMs()
        leases.entries.removeAll { it.value <= now }
        return UUID.randomUUID().toString().also { leases[it] = now + LEASE_MS }
    }

    @Synchronized fun accept(sessionId: String, peerId: String, lease: String,
                             sequence: Long, messageId: String): Boolean {
        if (sessionId != session || peerId != peer || messageId.isBlank() ||
            (leases[lease] ?: Long.MIN_VALUE) <= nowMs() ||
            sequence <= lastSequence || messageId in received) return false
        lastSequence = sequence
        received += messageId
        if (received.size > 1024) received.remove(received.first())
        return true
    }

    @Synchronized fun close() {
        session = null
        peer = null
        lastSequence = 0
        leases.clear()
        received.clear()
    }

    companion object { const val LEASE_MS = 3000L }
}
