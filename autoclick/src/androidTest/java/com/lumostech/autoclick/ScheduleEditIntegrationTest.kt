package com.lumostech.autoclick

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickRecordingProtection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ScheduleEditIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("autoclick_edit_integration_fixture", Context.MODE_PRIVATE)
    private val store = ClickTaskStore(preferences)
    private val zone = TimeZone.getDefault().id
    private fun at(day: Int, hour: Int) = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
        set(2026, Calendar.OCTOBER, day, hour, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val task = ClickTask("edit-fixture", 9, 0, (1..7).toSet(), listOf(ClickCounterPoint(200f, 200f, 0)),
        protection = ClickRecordingProtection(1080, 2400, 0, listOf(context.packageName)))
    private val old = ClickAlarmOccurrence(task.id, task.scheduleId, at(8, 9))
    private val platform = RecordingAlarmPlatform()
    private var ready = true
    private val clock = object : ClickClock {
        override fun wallMillis() = at(8, 11)
        override fun elapsedMillis() = 1234L
    }
    private val controller get() = ClickTaskController(store, platform, {}, clock, canEdit = { ready })
    @Before fun prepare() = runBlocking {
        (context.applicationContext as AutoclickApp).awaitStartup()
        preferences.edit().clear().commit()
        ClickExecutionSession.allowScheduled()
        assertTrue(store.save(task, alarmState = ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMED, old)))
    }
    @After fun cleanup() { preferences.edit().clear().commit(); ClickExecutionSession.allowScheduled() }
    private fun finish(consumed: Boolean) {
        assertTrue(store.reserveAlarm(old, old.scheduledAt))
        if (consumed) assertEquals(ClickExecutionClaim.CLAIMED, store.claimAlarm(old))
        assertTrue(store.finishAlarm(old, if (consumed) ClickOutcomeReason.COMPLETED else ClickOutcomeReason.START_EXPIRED, "history"))
    }
    @Test fun consumedDateSurvivesEditAndRejectsReserveAndClaim() = runBlocking {
        finish(true)
        val history = store.lastExecutionResult()
        assertEquals(ClickScheduleEditResult.ENABLED, controller.updateSchedule(task.id, 16, 0, task.days))
        val updated = store.load()!!
        assertNotEquals(task.id, updated.id)
        assertEquals(task.scheduleId, updated.scheduleId)
        assertEquals(task.points, updated.points)
        assertEquals(task.protection, updated.protection)
        assertEquals(history, store.lastExecutionResult())
        assertEquals(at(9, 16), platform.scheduled.single().scheduledAt)
        assertEquals(platform.scheduled.single(), store.alarmState()!!.next)
        val sameDay = ClickAlarmOccurrence(updated.id, updated.scheduleId, at(8, 16))
        assertTrue(store.saveAlarmState(updated.id, ClickAlarmState(updated.id, updated.scheduleId, ClickAlarmStatus.ARMED, sameDay)))
        assertFalse(ClickTaskStore(preferences).reserveAlarm(sameDay, sameDay.scheduledAt))
        assertEquals(ClickExecutionClaim.ALREADY_CONSUMED, store.claimAlarm(sameDay))
        assertFalse(store.reserveAlarm(old, old.scheduledAt))
    }
    @Test fun skippedEventAllowsExplicitFutureEditOnSameDate() = runBlocking {
        finish(false)
        assertEquals(ClickScheduleEditResult.ENABLED, controller.updateSchedule(task.id, 16, 0, task.days))
        assertEquals(at(8, 16), platform.scheduled.single().scheduledAt)
        assertNull(store.consumedAt(store.load()!!.id))
    }
    @Test fun enabledEditCancelsOldEventAndAtomicallyChangesAlarmIdentity() = runBlocking {
        assertEquals(ClickScheduleEditResult.ENABLED, controller.updateSchedule(task.id, 16, 0, task.days))
        assertTrue(old in platform.cancelled)
        assertFalse(store.reserveAlarm(old, old.scheduledAt))
        assertEquals(store.load()!!.id, store.alarmState()!!.taskId)
        assertEquals(ClickAlarmStatus.ARMED, store.alarmState()!!.status)
    }
    @Test fun disabledEditNeedsNoExactPermissionAndNeverEnables() = runBlocking {
        store.save(task.copy(enabled = false), alarmState = ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.NEEDS_ENABLE))
        platform.permission = false
        assertEquals(ClickScheduleEditResult.DISABLED, controller.updateSchedule(task.id, 16, 0, task.days))
        assertFalse(store.load()!!.enabled); assertEquals(16, store.load()!!.hour)
        assertTrue(platform.scheduled.isEmpty()); assertNull(store.alarmState()!!.next)
    }
    @Test fun missingPermissionKeepsConfigurationAndRequiresExplicitEnable() = runBlocking {
        platform.permission = false
        assertEquals(ClickScheduleEditResult.PERMISSION_REQUIRED, controller.updateSchedule(task.id, 16, 0, task.days))
        assertEquals(16, store.load()!!.hour); assertFalse(store.load()!!.enabled)
        platform.permission = true
        assertEquals(ClickScheduleEditResult.DISABLED, controller.updateSchedule(store.load()!!.id, 17, 0, task.days))
        assertFalse(store.load()!!.enabled); assertTrue(platform.scheduled.isEmpty())
    }
    @Test fun timeZoneMarkerIsRetainedByEdit() = runBlocking {
        store.saveAlarmState(task.id, ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.TIME_ZONE_CHANGED))
        assertEquals(ClickScheduleEditResult.TIME_ZONE_CHANGED, controller.updateSchedule(task.id, 16, 0, task.days))
        assertFalse(store.load()!!.enabled)
        assertEquals(ClickAlarmStatus.TIME_ZONE_CHANGED, store.alarmState()!!.status)
    }
    @Test fun unchangedAndStaleEditsDoNotSchedule() = runBlocking {
        assertEquals(ClickScheduleEditResult.UNCHANGED, controller.updateSchedule(task.id, task.hour, task.minute, task.days))
        assertEquals(task, store.load()); assertTrue(platform.cancelled.isEmpty())
        assertNotNull(runCatching { controller.updateSchedule("stale", 16, 0, task.days) }.exceptionOrNull())
        assertEquals(task, store.load()); assertTrue(platform.scheduled.isEmpty())
    }
    @Test fun preparingAndClaimedOccurrencesRejectEditing() = runBlocking {
        assertTrue(store.reserveAlarm(old, old.scheduledAt))
        assertNotNull(runCatching { controller.updateSchedule(task.id, 16, 0, task.days) }.exceptionOrNull())
        assertEquals(ClickExecutionClaim.CLAIMED, store.claimAlarm(old))
        assertNotNull(runCatching { controller.updateSchedule(task.id, 16, 0, task.days) }.exceptionOrNull())
        assertEquals(task, store.load()); assertTrue(platform.scheduled.isEmpty())
    }
    @Test fun disconnectedServiceRejectsBeforeWriting() = runBlocking {
        ready = false
        assertNotNull(runCatching { controller.updateSchedule(task.id, 16, 0, task.days) }.exceptionOrNull())
        assertEquals(task, store.load()); assertTrue(platform.cancelled.isEmpty())
    }
    @Test fun serviceLostDuringRegistrationDisablesAndCancels() = runBlocking {
        platform.beforeSchedule = { ready = false }
        assertNotNull(runCatching { controller.updateSchedule(task.id, 16, 0, task.days) }.exceptionOrNull())
        assertFalse(store.load()!!.enabled); assertNull(store.alarmState()!!.next)
        assertTrue(platform.scheduled.single() in platform.cancelled)
    }
    @Test fun platformFailureKeepsHistoryAndDisables() = runBlocking {
        finish(true); val history = store.lastExecutionResult()
        platform.failure = IllegalStateException("schedule failure")
        assertNotNull(runCatching { controller.updateSchedule(task.id, 16, 0, task.days) }.exceptionOrNull())
        assertFalse(store.load()!!.enabled); assertEquals(16, store.load()!!.hour)
        assertEquals(ClickAlarmStatus.SCHEDULE_FAILED, store.alarmState()!!.status)
        assertEquals(history, store.lastExecutionResult()); assertNull(store.alarmState()!!.next)
    }
    @Test fun permissionLostDuringRegistrationHasExplicitResult() = runBlocking {
        platform.beforeSchedule = { platform.permission = false }
        assertEquals(ClickScheduleEditResult.PERMISSION_REQUIRED, controller.updateSchedule(task.id, 16, 0, task.days))
        assertFalse(store.load()!!.enabled); assertNull(store.alarmState()!!.next)
    }
    @Test fun emergencyStopDuringEditCannotBeUndone() = runBlocking {
        platform.beforeSchedule = { ClickExecutionSession.emergencyStop(context) }
        assertNotNull(runCatching { controller.updateSchedule(task.id, 16, 0, task.days) }.exceptionOrNull())
        assertFalse(store.load()!!.enabled); assertNull(store.alarmState()!!.next)
        assertTrue(platform.scheduled.single() in platform.cancelled)
    }
    @Test fun cancellationStillDisablesAndCancels() = runBlocking {
        platform.failure = CancellationException("cancelled during scheduling")
        assertTrue(runCatching { controller.updateSchedule(task.id, 16, 0, task.days) }.exceptionOrNull() is CancellationException)
        assertFalse(store.load()!!.enabled); assertNull(store.alarmState()!!.next)
    }
    @Test fun cancelledCallerCannotLeaveAnEnabledEdit() = runBlocking {
        lateinit var saving: kotlinx.coroutines.Job
        saving = launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            controller.updateSchedule(task.id, 16, 0, task.days)
        }
        platform.beforeSchedule = { saving.cancel() }
        saving.start()
        saving.join()
        assertTrue(saving.isCancelled)
        assertFalse(store.load()!!.enabled)
        assertNull(store.alarmState()!!.next)
        assertTrue(platform.scheduled.single() in platform.cancelled)
    }
    @Test fun failedCommitDoesNotAdvertiseSuccessOrSchedule() = runBlocking {
        val failingStore = ClickTaskStore(FailingCommitPreferences(preferences))
        val failing = ClickTaskController(failingStore, platform, {}, clock)
        val error = runCatching { failing.updateSchedule(task.id, 16, 0, task.days) }.exceptionOrNull()
        assertNotNull(error); assertTrue(error!!.message!!.contains("未确认"))
        assertTrue(platform.scheduled.isEmpty())
    }
    @Test fun explicitEnableAfterEditStillSkipsConsumedDate() = runBlocking {
        finish(true)
        controller.updateSchedule(task.id, 16, 0, task.days)
        controller.setEnabled(false)
        controller.setEnabled(true)
        assertEquals(at(9, 16), platform.scheduled.last().scheduledAt)
    }
    @Test fun stopBeforeEditLeaseCannotBeReopenedByEdit() = runBlocking {
        ClickExecutionSession.emergencyStop(context)
        assertNotNull(runCatching { controller.updateSchedule(task.id, 16, 0, task.days) }.exceptionOrNull())
        assertFalse(store.load()!!.enabled); assertNull(store.alarmState()!!.next)
    }
    @Test fun competingEditLeaseRejectsWithoutWriting() = runBlocking {
        ClickExecutionSession.withScheduleEdit {
            assertNotNull(runCatching { controller.updateSchedule(task.id, 16, 0, task.days) }.exceptionOrNull())
        }
        assertEquals(task, store.load()); assertTrue(platform.scheduled.isEmpty())
    }
    @Test fun readinessLostImmediatelyBeforeWriteRejectsWithoutChangingTask() = runBlocking {
        var checks = 0
        val changing = ClickTaskController(store, platform, {}, clock, canEdit = { ++checks == 1 })
        assertNotNull(runCatching { changing.updateSchedule(task.id, 16, 0, task.days) }.exceptionOrNull())
        assertEquals(task, store.load()); assertTrue(platform.cancelled.isEmpty())
    }
    @Test fun alarmSnapshotRejectsMismatchedIdentityBeforeWriting() {
        assertNotNull(runCatching { store.save(task.copy(id = "new"), alarmState = store.alarmState()) }.exceptionOrNull())
        assertEquals(task, store.load())
    }
}

internal class RecordingAlarmPlatform : ClickAlarmPlatform {
    var permission = true
    var failure: Exception? = null
    var beforeSchedule: (() -> Unit)? = null
    val scheduled = mutableListOf<ClickAlarmOccurrence>()
    val cancelled = mutableListOf<ClickAlarmOccurrence>()
    override fun canSchedule() = permission
    override fun schedule(occurrence: ClickAlarmOccurrence) {
        beforeSchedule?.invoke(); failure?.let { throw it }; scheduled += occurrence
    }
    override fun cancel(occurrence: ClickAlarmOccurrence) { cancelled += occurrence }
}
