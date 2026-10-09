package com.lumostech.autoclick

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickRecordingProtection
import org.junit.Before
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ClickAlarmStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ClickTaskStore(context)
    private val task = ClickTask("epoch", 9, 0, (1..7).toSet(), listOf(ClickCounterPoint(200f, 200f, 0)),
        protection = ClickRecordingProtection(1080, 2400, 0, listOf(context.packageName)))
    private val event = ClickAlarmOccurrence(task.id, task.scheduleId, 1000)
    @Before fun awaitStartup() = runBlocking { (context.applicationContext as AutoclickApp).awaitStartup() }
    @After fun cleanup() { store.clear() }
    private fun prepare() {
        store.clear(); assertTrue(store.save(task))
        assertTrue(store.saveAlarmState(task.id, ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMED, next = event)))
        assertTrue(store.reserveAlarm(event, 1000))
    }
    @Test fun nextSurvivesClaimAndFinishAndDuplicatesCannotOverwrite() {
        prepare()
        val next = event.copy(scheduledAt = 86401000)
        assertTrue(store.saveAlarmState(task.id, store.alarmState()!!.copy(next = next)))
        assertFalse(store.reserveAlarm(event, 1001))
        assertEquals(ClickExecutionClaim.CLAIMED, store.claimAlarm(event))
        assertTrue(store.recordFirstDispatch(event, 1234))
        assertTrue(store.finishAlarm(event, ClickOutcomeReason.COMPLETED, "completed"))
        assertEquals(next, store.alarmState()!!.next)
        assertEquals(1000L, store.closedThrough())
        assertFalse(store.finishAlarm(event, ClickOutcomeReason.UNKNOWN, "duplicate"))
        assertEquals("completed", store.lastExecutionResult()!!.message)
        assertEquals(1234L, store.startTrace()!!.firstDispatchAt)
    }
    @Test fun skippedWatermarkSurvivesEnableGenerationAndClearsOnlyForNewSchedule() {
        prepare(); assertTrue(store.finishAlarm(event, ClickOutcomeReason.START_EXPIRED, "skipped"))
        assertTrue(store.save(task.copy(id = "new")))
        assertEquals(1000L, store.closedThrough())
        assertEquals("skipped", store.lastExecutionResult()!!.message)
        assertNull(store.alarmState())
        assertTrue(store.save(task.copy(id = "other", scheduleId = "other")))
        assertEquals(0L, store.closedThrough()); assertNull(store.startTrace())
    }
    @Test fun smallWallClockRollbackRetainsValidFirstDispatchTrace() {
        val trace = ClickStartTrace(event, 5000, 4500)
        assertEquals(trace, ClickStartTrace.decode(trace.encode()))
    }
    @Test fun snapshotCodecRejectsUnknownAndMismatchedIdentity() {
        val state = ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMED, event)
        assertEquals(state, ClickAlarmState.decode(state.encode()))
        assertNull(ClickAlarmState.decode(state.encode().replace("ARMED", "FUTURE")))
        assertNull(ClickAlarmState.decode(state.copy(taskId = "different").encode()))
        assertEquals(event, ClickAlarmOccurrence.decode(event.encode()))
        val trace = ClickStartTrace(event, 1000, 1001)
        assertEquals(trace, ClickStartTrace.decode(trace.encode()))
    }
}
