package com.lumostech.autoclick

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickSequenceStore
import com.lumostech.accessibilitycore.ClickRecordingProtection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ClickTaskIntegrationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ClickTaskStore(context)
    private val points = listOf(ClickCounterPoint(120f, 240f, 0L), ClickCounterPoint(360f, 480f, 100L))
    private val task = ClickTask("integration-task", 23, 59, (1..7).toSet(), points,
        protection = ClickRecordingProtection(1080, 2400, 0, listOf(context.packageName, context.packageName)))

    @Before
    @After
    fun clearTask() {
        WorkManager.getInstance(context).cancelUniqueWork(ClickPeriodicWorker.TAG).result.get(10, TimeUnit.SECONDS)
        store.clear()
        ClickSequenceStore(context).save(emptyList())
    }

    @Test
    fun restoresTaskAndDraftUsingNewStoreInstances() {
        assertTrue(store.save(task))
        ClickSequenceStore(context).save(points, task.protection)
        assertEquals(task, ClickTaskStore(context).load())
        assertEquals(points, ClickSequenceStore(context).load())
        assertEquals(task.protection, ClickSequenceStore(context).loadProtection())
    }

    @Test
    fun executionResultSurvivesControlStatusAndScheduleResume() {
        assertTrue(store.save(task))
        assertTrue(store.recordOccurrenceOutcome(task.id, currentOccurrence(), false, "上次执行失败"))
        val preferences = context.getSharedPreferences("autoclick_task", Context.MODE_PRIVATE)
        val before = preferences.getString("execution_record", null)
        assertNotNull("Execution result must be stored separately from current control status", before)
        assertTrue(store.save(task.copy(enabled = false), "任务已停用"))
        assertEquals(before, preferences.getString("execution_record", null))
        assertTrue(store.save(task.copy(id = "resumed-task"), "等待下次执行"))
        assertEquals(before, preferences.getString("execution_record", null))
        assertTrue(store.save(task.copy(id = "new-task", scheduleId = "new-task")))
        assertNull(preferences.getString("execution_record", null))
    }

    @Test
    fun typedResultRestoresAndUnknownOrDamagedMetadataFallsBackSafely() {
        assertTrue(store.save(task))
        val due = currentOccurrence()
        assertTrue(store.recordOccurrenceOutcome(task.id, due, false, "屏幕已改变", ClickOutcomeReason.DISPLAY_CHANGED))
        val restored = ClickTaskStore(context).lastExecutionRecord()!!
        assertEquals(task.scheduleId, restored.scheduleId)
        assertEquals(due, restored.occurrenceAt)
        assertEquals(ClickOutcomeReason.DISPLAY_CHANGED, restored.reason)
        val preferences = context.getSharedPreferences("autoclick_task", Context.MODE_PRIVATE)
        preferences.edit().putString("execution_record", restored.encode().replace("DISPLAY_CHANGED", "FUTURE_REASON")).commit()
        assertEquals(ClickOutcomeReason.UNKNOWN, store.lastExecutionRecord()?.reason)
        preferences.edit().putString("execution_record", "{broken").commit()
        assertNull(store.lastExecutionRecord())
        assertEquals("屏幕已改变", store.lastOutcome())
    }

    @Test
    fun legacyResultIsRetainedWhenSameScheduleIsDisabled() {
        assertTrue(store.save(task))
        val preferences = context.getSharedPreferences("autoclick_task", Context.MODE_PRIVATE)
        preferences.edit().putString("outcome", "历史失败原文").putLong("outcome_time", 123_456L).commit()
        assertTrue(store.save(task.copy(enabled = false), "任务已停用"))
        val record = store.lastExecutionRecord()!!
        assertEquals("历史失败原文", record.message)
        assertEquals(123_456L, record.recordedAt)
        assertEquals(ClickOutcomeReason.UNKNOWN, record.reason)
        assertNull(record.occurrenceAt)
    }

    @Test
    fun adapterReadsActualFutureQueueWhenTodaysTimeHasPassed() = runBlocking {
        val past = Calendar.getInstance().apply { add(Calendar.MINUTE, -1) }
        val saved = task.copy(hour = past.get(Calendar.HOUR_OF_DAY), minute = past.get(Calendar.MINUTE))
        ClickTaskController(context).save(saved)
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(ClickPeriodicWorker.TAG).get()
        val snapshot = scheduledWorkSnapshot(saved, infos)
        assertEquals(ScheduledWorkState.ENQUEUED, snapshot.state)
        assertTrue(snapshot.plannedAt!! > System.currentTimeMillis())
        assertEquals(ClickSchedulePolicy.nextOccurrence(saved, System.currentTimeMillis()), snapshot.plannedAt)
        assertTrue(present(saved, snapshot, ClickRunState(), false, null, System.currentTimeMillis(), saved.timeZoneId).title.startsWith("下次："))
        val replaced = saved.copy(id = "replacement", scheduleId = "replacement")
        assertEquals(ScheduledWorkState.MISSING, scheduledWorkSnapshot(replaced, infos).state)
    }

    @Test
    fun runningOrUncertainWorkNeverInventsNextTime() {
        fun info(state: WorkInfo.State, tags: Set<String> = setOf(task.id, ClickPeriodicWorker.TAG)) = WorkInfo(
            java.util.UUID.randomUUID(), state, tags,
            periodicityInfo = WorkInfo.PeriodicityInfo(86_400_000, 86_400_000))
        val running = scheduledWorkSnapshot(task, listOf(info(WorkInfo.State.RUNNING)))
        assertEquals(ScheduledWorkState.RUNNING, running.state)
        assertNull(running.plannedAt)
        assertEquals("执行准备中", present(task, running, ClickRunState(), false, null,
            System.currentTimeMillis(), task.timeZoneId).title)
        assertEquals(ScheduledWorkState.UNKNOWN, scheduledWorkSnapshot(task, listOf(info(WorkInfo.State.ENQUEUED))).state)
        assertEquals(ScheduledWorkState.MISSING, scheduledWorkSnapshot(task, listOf(info(WorkInfo.State.RUNNING, setOf("old-task", ClickPeriodicWorker.TAG)))).state)
        assertEquals(ScheduledWorkState.UNKNOWN, scheduledWorkSnapshot(task, listOf(info(WorkInfo.State.RUNNING), info(WorkInfo.State.RUNNING))).state)
    }

    @Test
    fun claimPreservesLegacyTerminalHistoryAndMarksNewProgressSeparately() {
        assertTrue(store.save(task))
        val preferences = context.getSharedPreferences("autoclick_task", Context.MODE_PRIVATE)
        preferences.edit().putString("outcome", "旧版本执行失败").putLong("outcome_time", 123_456L).commit()
        val due = currentOccurrence()
        assertEquals(ClickExecutionClaim.CLAIMED, store.claimExecution(task.id, due))
        assertEquals("旧版本执行失败", store.lastExecutionRecord()?.message)
        assertTrue(preferences.getBoolean("execution_pending", false))
        assertTrue(store.recordOccurrenceOutcome(task.id, due, true, "completed", ClickOutcomeReason.COMPLETED))
        assertFalse(preferences.getBoolean("execution_pending", false))
        store.clear()
        assertTrue(store.save(task))
        assertEquals(ClickExecutionClaim.CLAIMED, store.claimExecution(task.id, due))
        assertNull(store.lastExecutionRecord())
        assertTrue(preferences.getBoolean("execution_pending", false))
        assertNull(ClickTaskStore(context).lastExecutionResult())
    }

    private fun currentOccurrence(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, task.hour)
        set(Calendar.MINUTE, task.minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun executionClaimSurvivesStoreRestorationAndClockRollback() {
        assertTrue(store.save(task))
        val due = currentOccurrence()
        assertEquals(ClickExecutionClaim.CLAIMED, store.claimExecution(task.id, due))
        assertEquals(ClickExecutionClaim.ALREADY_CONSUMED, ClickTaskStore(context).claimExecution(task.id, due))
        assertEquals(ClickExecutionClaim.ALREADY_CONSUMED, ClickTaskStore(context).claimExecution(task.id, due - 86_400_000L))
        assertEquals(ClickExecutionClaim.CLAIMED, ClickTaskStore(context).claimExecution(task.id, due + 86_400_000L))
    }

    @Test
    fun failedClaimCommitDoesNotShowRunningOrAllowAnotherClaim() {
        assertTrue(store.save(task))
        val preferences = context.getSharedPreferences("autoclick_task", Context.MODE_PRIVATE)
        val failingPreferences = object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val editor = preferences.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { editor.putString(key, value) }
                    override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { editor.putLong(key, value) }
                    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { editor.putBoolean(key, value) }
                    // Android updates memory before returning a disk failure. Retain
                    // those mutations and report failure to exercise the same branch.
                    override fun commit(): Boolean { editor.commit(); return false }
                }
            }
        }
        val failingStore = ClickTaskStore(failingPreferences)
        val due = currentOccurrence()
        assertEquals(ClickExecutionClaim.STORAGE_FAILED, failingStore.claimExecution(task.id, due))
        assertEquals("无法保存执行记录，未执行点击", failingStore.lastOutcome())
        assertEquals("无法保存执行记录，未执行点击", failingStore.lastExecutionRecord()?.message)
        assertEquals(ClickExecutionClaim.ALREADY_CONSUMED, failingStore.claimExecution(task.id, due))
    }

    @Test
    fun emergencyStopStillCancelsWorkWhenDisablingPreferencesFails() = runBlocking {
        ClickTaskController(context).save(task)
        val manager = WorkManager.getInstance(context)
        val preferences = context.getSharedPreferences("autoclick_task", Context.MODE_PRIVATE)
        val failingPreferences = object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val editor = preferences.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { editor.putString(key, value) }
                    override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { editor.putLong(key, value) }
                    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { editor.putBoolean(key, value) }
                    override fun remove(key: String?): SharedPreferences.Editor = apply { editor.remove(key) }
                    override fun commit(): Boolean { editor.commit(); return false }
                }
            }
        }
        try {
            ClickTaskController(ClickTaskStore(failingPreferences), manager).emergencyStop()
            fail("A failed disable commit must be reported")
        } catch (_: IllegalStateException) { }
        assertTrue("Work must still be cancelled after the failed commit",
            manager.getWorkInfosForUniqueWork(ClickPeriodicWorker.TAG).get().all { it.state.isFinished })
    }

    @Test
    fun disableResumeDoesNotReplayTheConsumedDay() = runBlocking {
        val controller = ClickTaskController(context)
        controller.save(task)
        val due = currentOccurrence()
        assertEquals(ClickExecutionClaim.CLAIMED, store.claimExecution(task.id, due))
        controller.setEnabled(false)
        controller.setEnabled(true)
        val resumed = store.load()!!
        assertNotEquals(task.id, resumed.id)
        assertEquals(task.scheduleId, resumed.scheduleId)
        assertEquals(ClickExecutionClaim.STALE_TASK, store.claimExecution(task.id, due))
        assertEquals(ClickExecutionClaim.ALREADY_CONSUMED, store.claimExecution(resumed.id, due))
    }

    @Test
    fun cancelledOccurrenceIsNotAutomaticallyReplayedAndDuplicateCannotOverwriteStatus() {
        assertTrue(store.save(task))
        val due = currentOccurrence()
        assertEquals(ClickExecutionClaim.CLAIMED, store.claimExecution(task.id, due))
        assertTrue(store.recordOutcome(task.id, "执行已取消"))
        assertEquals(ClickExecutionClaim.ALREADY_CONSUMED, store.claimExecution(task.id, due))
        assertEquals("执行已取消", store.lastOutcome())
        assertTrue(store.save(task.copy(id = "new-plan", scheduleId = "new-plan")))
        assertEquals(ClickExecutionClaim.CLAIMED, store.claimExecution("new-plan", due))
    }

    @Test
    fun legacyTaskIsVisibleButStartupDisablesItsSchedule() = runBlocking {
        assertTrue(store.save(task.copy(protection = null)))
        ClickTaskController(context).reconcile()
        assertNotNull(store.load())
        assertFalse(store.load()!!.enabled)
        assertEquals(ClickTaskOutcome.NEEDS_RECORDING.message, store.lastOutcome())
    }

    @Test
    fun unclaimedWorkerCannotOverwriteOwnerAndPreviousDayCannotOverwriteNewOccurrence() {
        assertTrue(store.save(task))
        val due = currentOccurrence()
        assertEquals(ClickExecutionClaim.CLAIMED, store.claimExecution(task.id, due))
        assertFalse(store.recordOccurrenceOutcome(task.id, due, false, "unclaimed package mismatch"))
        assertTrue(store.recordOccurrenceOutcome(task.id, due, true, "completed"))
        assertEquals("completed", store.lastOutcome())
        val record = store.lastExecutionRecord()
        assertFalse(store.recordOccurrenceOutcome(task.id, due, false, "duplicate"))
        assertEquals(record, store.lastExecutionRecord())
        assertEquals(ClickExecutionClaim.CLAIMED, store.claimExecution(task.id, due + 86_400_000L))
        assertFalse(store.recordOccurrenceOutcome(task.id, due, true, "late old result"))
        assertEquals("正在执行点击", store.lastOutcome())
        assertEquals(record, store.lastExecutionRecord())
    }

    @Test
    fun periodicReanchorUpdatesExactWorkAndPreservesWorkerAndScheduleIdentity() = runBlocking {
        val controller = ClickTaskController(context)
        controller.save(task)
        val manager = WorkManager.getInstance(context)
        val original = manager.getWorkInfosForUniqueWork(ClickPeriodicWorker.TAG).get().single { !it.state.isFinished }
        val afterLongExecution = currentOccurrence() + 8 * 60_000L
        controller.alignNextOccurrence(task.id, original.id, afterLongExecution)
        val updated = manager.getWorkInfoById(original.id).get()!!
        assertEquals(original.id, updated.id)
        assertEquals(task.scheduleId, store.load()!!.scheduleId)
        assertEquals(ClickSchedulePolicy.nextOccurrence(task, afterLongExecution), updated.nextScheduleTimeMillis)
    }

    @Test
    fun staleOrDisabledExecutionsCannotOverwriteCurrentStatus() {
        assertTrue(store.save(task.copy(id = "replacement")))
        assertFalse(store.recordOutcome(task.id, "incorrect success"))
        assertTrue(store.save(task.copy(enabled = false), "任务已停用"))
        assertFalse(store.recordOutcome(task.id, "incorrect success"))
        assertEquals("任务已停用", store.lastOutcome())
    }

    @Test
    fun missingServiceReportsFailureInsteadOfSuccess() = runBlocking {
        val now = Calendar.getInstance()
        assertTrue(store.save(task.copy(hour = now.get(Calendar.HOUR_OF_DAY), minute = now.get(Calendar.MINUTE))))
        val worker = TestListenableWorkerBuilder<ClickPeriodicWorker>(context)
            .setInputData(Data.Builder().putString(ClickPeriodicWorker.TASK_ID, task.id).build()).build()
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(ClickTaskOutcome.SERVICE_UNAVAILABLE.message, store.lastOutcome())
    }

    @Test
    fun nonTargetDayIsRecordedAsSkipped() = runBlocking {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val otherDay = if (today == 7) 1 else today + 1
        assertTrue(store.save(task.copy(days = setOf(otherDay))))
        val worker = TestListenableWorkerBuilder<ClickPeriodicWorker>(context)
            .setInputData(Data.Builder().putString(ClickPeriodicWorker.TASK_ID, task.id).build()).build()
        assertTrue(worker.doWork() is ListenableWorker.Result.Success)
        assertEquals(ClickTaskOutcome.SKIPPED_DAY.message, store.lastOutcome())
    }

    @Test
    fun savedTaskCanBeDisabledResumedAndDeleted() = runBlocking {
        val controller = ClickTaskController(context)
        controller.save(task)
        assertEquals(1, WorkManager.getInstance(context).getWorkInfosForUniqueWork(ClickPeriodicWorker.TAG).get().count { it.state == WorkInfo.State.ENQUEUED })
        controller.setEnabled(false)
        assertFalse(store.load()!!.enabled)
        assertTrue(WorkManager.getInstance(context).getWorkInfosForUniqueWork(ClickPeriodicWorker.TAG).get().all { it.state == WorkInfo.State.CANCELLED })
        controller.setEnabled(true)
        assertTrue(store.load()!!.enabled)
        assertNotEquals(task.id, store.load()!!.id)
        assertFalse(store.recordOutcome(task.id, "old cancelled execution"))
        controller.delete()
        assertNull(store.load())
    }

    @Test
    fun openingAppRepairsMissingScheduleAfterInterruptedSave() {
        assertTrue(store.save(task))
        ActivityScenario.launch(MainActivity::class.java).use {
            val end = System.currentTimeMillis() + 10_000
            var scheduled = false
            while (!scheduled && System.currentTimeMillis() < end) {
                scheduled = WorkManager.getInstance(context).getWorkInfosForUniqueWork(ClickPeriodicWorker.TAG).get()
                    .any { !it.state.isFinished && task.id in it.tags }
                if (!scheduled) Thread.sleep(100)
            }
            assertTrue("Opening the app must repair the persisted enabled task", scheduled)
        }
    }
}
