package com.lumostech.autoclick

import org.junit.Assert.*
import org.junit.Test

class LegacyTaskRecoverySessionTest {
    private val session = LegacyTaskRecoverySession("session", "source", "plan", "replacement", 9, 30, setOf(2), LegacyRecoveryPhase.PAUSED)
    @Test fun validPausedSessionRetainsBothTaskIdentities() {
        assertTrue(session.isValid())
        assertEquals("replacement", session.copy(phase = LegacyRecoveryPhase.SAVING).replacementTaskId)
        assertEquals("source", session.copy(hour = 10).sourceTaskId)
    }
    @Test fun invalidScheduleOrIdentityCannotStartRecovery() {
        assertFalse(session.copy(recoveryId = "").isValid())
        assertFalse(session.copy(hour = 24).isValid())
        assertFalse(session.copy(days = emptySet()).isValid())
        assertFalse(session.copy(days = setOf(8)).isValid())
    }
}
