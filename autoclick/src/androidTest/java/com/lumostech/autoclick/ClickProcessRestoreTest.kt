package com.lumostech.autoclick

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickRecordingProtection
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Run with processRestorePhase=seed, then verify in a separate instrumentation process. */
@RunWith(AndroidJUnit4::class)
class ClickProcessRestoreTest {
    @Test
    fun consumedOccurrenceSurvivesASeparateApplicationProcess() {
        val phase = InstrumentationRegistry.getArguments().getString("processRestorePhase")
        assumeNotNull(phase)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ClickTaskStore(context)
        if (phase == "seed") {
            WorkManager.getInstance(context).cancelUniqueWork(ClickPeriodicWorker.TAG).result.get(10, TimeUnit.SECONDS)
            assertTrue(store.clear())
            val now = Calendar.getInstance()
            val task = ClickTask("process-restore", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), (1..7).toSet(),
                listOf(ClickCounterPoint(120f, 240f, 0)),
                protection = ClickRecordingProtection(1080, 2400, 0, listOf(context.packageName)))
            assertTrue(store.save(task))
            val due = ClickSchedulePolicy.evaluate(task, System.currentTimeMillis()).scheduledAt
            assertEquals(ClickExecutionClaim.CLAIMED, store.claimExecution(task.id, due))
            assertTrue(store.recordOccurrenceOutcome(task.id, due, true, "已开始，模拟进程中断"))
        } else {
            assertEquals("verify", phase)
            val task = store.load()!!
            assertEquals("process-restore", task.id)
            assertNotNull(task.protection)
            val due = ClickSchedulePolicy.evaluate(task, System.currentTimeMillis()).scheduledAt
            assertEquals(ClickExecutionClaim.ALREADY_CONSUMED, store.claimExecution(task.id, due))
            assertEquals("已开始，模拟进程中断", store.lastOutcome())
            WorkManager.getInstance(context).cancelUniqueWork(ClickPeriodicWorker.TAG).result.get(10, TimeUnit.SECONDS)
            assertTrue(store.clear())
        }
    }
}
