package com.lumostech.autoclick

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickRecordingProtection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LegacyTaskRecoveryIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val raw = context.getSharedPreferences("legacy-recovery-fixture", Context.MODE_PRIVATE)
    private val journal = MemoryRecoveryJournal()
    private val preferences = RecoverableTaskPreferences(raw, journal)
    private val store = ClickTaskStore(preferences)
    private val source = ClickTask("legacy", 9, 30, setOf(2, 4), listOf(ClickCounterPoint(300f, 400f, 0)),
        enabled = false, scheduleId = "legacy-plan")
    private val platform = object : ClickAlarmPlatform {
        var scheduled = 0
        var permission = false
        override fun canSchedule() = permission
        override fun schedule(occurrence: ClickAlarmOccurrence) { scheduled++ }
        override fun cancel(occurrence: ClickAlarmOccurrence) {}
    }
    private val controller = ClickTaskController(store, platform, {})
    private val session = LegacyTaskRecoverySession("recovery", source.id, source.scheduleId,
        "replacement", 10, 15, setOf(3, 5), LegacyRecoveryPhase.RECORDING)
    private fun candidate() = source.copy(id = session.replacementTaskId, scheduleId = session.replacementTaskId,
        hour = 10, minute = 15, days = setOf(3, 5),
        protection = ClickRecordingProtection(1080, 1920, 0, listOf("test.target")))
    @Before fun prepare() { raw.edit().clear().commit(); assertTrue(store.save(source)) }
    @After fun cleanup() { raw.edit().clear().commit() }

    @Test fun legacyTimeEditPreservesRecordingAndKeepsDisabled() = runBlocking {
        raw.edit().putLong("consumed_at", 123).putLong("outcome_time", 456).putString("outcome", "以前的结果").commit()
        controller.updateSchedule(source.id, 10, 15, setOf(3, 5))
        val saved = store.load()!!
        assertEquals(10, saved.hour)
        assertEquals(15, saved.minute)
        assertEquals(setOf(3, 5), saved.days)
        assertEquals(source.points, saved.points)
        assertEquals(source.scheduleId, saved.scheduleId)
        assertNull(saved.protection)
        assertFalse(saved.enabled)
        assertEquals(0, platform.scheduled)
        assertEquals(123L, store.consumedAt(saved.id))
        assertEquals("以前的结果", store.lastExecutionResult()!!.message)
    }

    @Test fun recoverySavesNewDisabledPlanExactlyOnce() = runBlocking {
        raw.edit().putLong("consumed_at", 123).putLong("outcome_time", 456).putString("outcome", "以前的结果").commit()
        assertEquals(LegacyTaskRecoveryResult.SAVED_DISABLED, controller.saveRecoveredRecording(session, candidate(), session.recoveryId))
        assertEquals(candidate(), store.load())
        assertEquals(ClickAlarmStatus.NEEDS_ENABLE, store.alarmState()!!.status)
        assertNull(store.alarmState()!!.next)
        assertNull(store.consumedAt(candidate().id))
        assertNull(store.lastExecutionResult())
        assertEquals(LegacyTaskRecoveryResult.ALREADY_SAVED, controller.saveRecoveredRecording(session, candidate(), session.recoveryId))
        assertEquals(0, platform.scheduled)
    }

    @Test fun failedRecoveryPreservesEveryOriginalPreference() = runBlocking {
        raw.edit().putLong("consumed_at", 123).putString("unknown_history", "keep").commit()
        val before = raw.all.toMap()
        journal.failWrite = true
        expectFailure { controller.saveRecoveredRecording(session, candidate(), session.recoveryId) }
        assertEquals(before, raw.all)
        assertEquals(source, store.load())
    }

    @Test fun mismatchedRecordingAndChangedSourceCannotReplaceTask() = runBlocking {
        expectFailure { controller.saveRecoveredRecording(session, candidate(), "other") }
        assertEquals(source, store.load())
        store.save(source.copy(id = "changed"))
        expectFailure { controller.saveRecoveredRecording(session, candidate(), session.recoveryId) }
        assertEquals("changed", store.load()!!.id)
    }

    @Test fun missingProtectionAndDisconnectedServicePreserveSource() = runBlocking {
        expectFailure { controller.saveRecoveredRecording(session, candidate().copy(protection = null), session.recoveryId) }
        val disconnected = ClickTaskController(store, platform, {}, canEdit = { false })
        expectFailure { disconnected.saveRecoveredRecording(session, candidate(), session.recoveryId) }
        assertEquals(source, store.load())
    }

    @Test fun unchangedLegacyScheduleDoesNotRewriteOrCreateAlarm() = runBlocking {
        val before = raw.all.toMap()
        assertEquals(ClickScheduleEditResult.UNCHANGED, controller.updateSchedule(source.id, source.hour, source.minute, source.days))
        assertEquals(before, raw.all)
        assertEquals(0, platform.scheduled)
    }

    @Test fun failedLegacyTimeEditPreservesOriginalTimeAndHistory() = runBlocking {
        val before = raw.all.toMap()
        journal.failWrite = true
        expectFailure { controller.updateSchedule(source.id, 10, 15, setOf(3, 5)) }
        assertEquals(before, raw.all)
    }

    @Test fun oldAlarmCannotClaimRecoveredTask() = runBlocking {
        val old = ClickAlarmOccurrence(source.id, source.scheduleId, System.currentTimeMillis())
        controller.saveRecoveredRecording(session, candidate(), session.recoveryId)
        assertNull(controller.acceptAlarm(old, old.scheduledAt))
        assertNull(controller.acceptAlarm(old, old.scheduledAt))
        assertEquals(candidate(), store.load())
        assertNull(store.lastExecutionResult())
    }
    @Test fun stopDuringFinalServiceCheckPreventsReplacement() = runBlocking {
        var checks = 0
        val stopped = ClickTaskController(store, platform, {}, canEdit = {
            checks++
            if (checks == 2) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { ClickExecutionSession.emergencyStop(context) }
            true
        })
        expectFailure { stopped.saveRecoveredRecording(session, candidate(), session.recoveryId) }
        assertEquals(source, store.load())
        assertNull(journal.value)
    }
    @Test fun completedRecoveryStillMatchesAfterManualEnableRotatesCallbackId() = runBlocking {
        controller.saveRecoveredRecording(session, candidate(), session.recoveryId)
        platform.permission = true
        controller.setEnabled(true)
        val enabled = store.load()!!
        assertTrue(enabled.enabled)
        assertNotEquals(candidate().id, enabled.id)
        assertEquals(candidate().scheduleId, enabled.scheduleId)
        assertTrue(store.wasRecovered(session.recoveryId, session.replacementTaskId))
        assertEquals(LegacyTaskRecoveryResult.ALREADY_SAVED, controller.saveRecoveredRecording(session, candidate(), session.recoveryId))
        assertEquals(enabled, store.load())
        assertEquals(1, platform.scheduled)
    }

    private suspend fun expectFailure(action: suspend () -> Unit) {
        try { action(); fail("Expected operation to be rejected") } catch (expected: IllegalStateException) {
            assertNotNull(expected.message)
        } catch (expected: IllegalArgumentException) { assertNotNull(expected.message) }
    }
}
