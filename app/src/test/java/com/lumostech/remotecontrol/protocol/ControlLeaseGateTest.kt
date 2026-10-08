package com.lumostech.remotecontrol.protocol

import org.junit.Assert.*
import org.junit.Test

class ControlLeaseGateTest {
    @Test fun duplicateExpiredAndOldSessionCommandsAreRejected() {
        var now = 100L
        val gate = ControlLeaseGate { now }
        gate.begin("session-a", "controller")
        val lease = gate.issueLease()
        assertTrue(gate.accept("session-a", "controller", lease, 1, "m1"))
        assertFalse(gate.accept("session-a", "controller", lease, 1, "m1"))
        assertFalse(gate.accept("session-a", "intruder", lease, 2, "m2"))
        now += 3001
        assertFalse(gate.accept("session-a", "controller", lease, 2, "m2"))
        gate.begin("session-b", "controller")
        assertFalse(gate.accept("session-a", "controller", lease, 3, "m3"))
    }

    @Test fun renewalDoesNotResetSequenceAndCloseRevokesAllLeases() {
        val gate = ControlLeaseGate { 0 }
        gate.begin("s", "p")
        assertTrue(gate.accept("s", "p", gate.issueLease(), 20, "a"))
        val second = gate.issueLease()
        assertFalse(gate.accept("s", "p", second, 19, "b"))
        gate.close()
        assertFalse(gate.accept("s", "p", second, 21, "c"))
    }
}
