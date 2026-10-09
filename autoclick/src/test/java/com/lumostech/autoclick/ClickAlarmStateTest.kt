package com.lumostech.autoclick

import org.junit.Assert.*
import org.junit.Test

class ClickAlarmStateTest {
    private val event = ClickAlarmOccurrence("epoch", "schedule", 1000)
    @Test fun identityIncludesGenerationOccurrenceAndVersion() {
        assertNotEquals(event, event.copy(taskId = "new"))
        assertNotEquals(event, event.copy(scheduledAt = 2000))
        assertFalse(event.copy(version = 9).isValid())
        assertFalse(event.copy(scheduledAt = -1).isValid())
    }
    @Test fun independentNextAndActiveRequireMatchingIdentityAndPhase() {
        val state = ClickAlarmState("epoch", "schedule", ClickAlarmStatus.ARMED,
            next = event.copy(scheduledAt = 2000), active = event, phase = ClickAlarmPhase.CLAIMED)
        assertTrue(state.isValid())
        assertFalse(state.copy(next = event.copy(taskId = "other")).isValid())
        assertFalse(state.copy(phase = null).isValid())
        assertFalse(state.copy(active = null).isValid())
        assertFalse(state.copy(next = null).isValid())
        assertTrue(state.copy(next = null, status = ClickAlarmStatus.SCHEDULE_FAILED).isValid())
    }
}
