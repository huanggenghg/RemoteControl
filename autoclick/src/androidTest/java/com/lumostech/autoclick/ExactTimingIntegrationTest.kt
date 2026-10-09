package com.lumostech.autoclick

import android.content.Intent
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import com.lumostech.accessibilitycore.AccessibilityCoreService
import com.lumostech.accessibilitycore.ClickCounterPoint
import kotlinx.coroutines.runBlocking
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ExactTimingIntegrationTest {
    private val f = ExactTimingFixture()
    @Before fun setup() = f.setup()
    @After fun cleanup() = f.cleanup()
    private fun button(scenario: ActivityScenario<MainActivity>, count: AtomicInteger, at: AtomicLong = AtomicLong()) {
        scenario.onActivity { activity -> activity.setContentView(Button(activity).apply {
            text = "定时验证：等待点击"
            setOnClickListener { at.set(System.currentTimeMillis()); text = "已点击 ${count.incrementAndGet()} 次" }
        }) }
    }
    @Test fun quickExactTaskFiresAtNextMinute() {
        val count = AtomicInteger(); val at = AtomicLong()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            button(scenario, count, at)
            val base = f.task("exact-next-minute", listOf(ClickCounterPoint(300f, 300f, 0)))
            val date = Calendar.getInstance().apply {
                add(Calendar.MINUTE, 1); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis - System.currentTimeMillis() < 5000) add(Calendar.MINUTE, 1)
            }
            val task = base.copy(hour = date.get(Calendar.HOUR_OF_DAY), minute = date.get(Calendar.MINUTE))
            runBlocking { assertEquals(ClickTaskSaveResult.ENABLED, ClickTaskController(f.context).save(task)) }
            val event = f.store.alarmState()!!.next!!
            assertEquals(date.timeInMillis, event.scheduledAt)
            assertEquals(0, count.get())
            f.waitFor("real exact alarm and button completion", 90000) {
                count.get() == 1 && f.store.lastExecutionResult()?.reason == ClickOutcomeReason.COMPLETED
            }
            val trace = f.store.startTrace()!!; val first = trace.firstDispatchAt!!; val due = event.scheduledAt
            assertTrue(first >= due && first - due < 5000)
            assertTrue(at.get() >= due && at.get() - due < 5000)
            assertTrue(f.store.alarmState()!!.next!!.scheduledAt > due)
            f.context.sendBroadcast(AndroidClickAlarmPlatform(f.context).eventIntent(event))
            Thread.sleep(2000); assertEquals(1, count.get()); assertEquals(trace, f.store.startTrace())
            android.util.Log.i("AutoclickQuickCheck", "scheduledAt=$due receivedAt=${trace.receivedAt} firstDispatchAt=$first buttonReceivedAt=${at.get()} firstLateMs=${first-due} buttonLateMs=${at.get()-due} realClicks=${count.get()} duplicateClicks=0")
            f.shell("screencap -p /sdcard/autoclick-quick-scheduled.png")
        }
    }
    @Test fun realClockExpiredEventSkipsWithoutButtonClickAndKeepsFuture() {
        val count = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            button(scenario, count)
            val past = Calendar.getInstance().apply { add(Calendar.MINUTE, -1) }
            val task = f.task("expired", listOf(ClickCounterPoint(300f, 300f, 0))).copy(
                hour = past.get(Calendar.HOUR_OF_DAY), minute = past.get(Calendar.MINUTE))
            val event = f.seed(task, f.due(task))
            runBlocking { ClickAlarmDispatcher.dispatch(f.context, event) }
            assertEquals(0, count.get()); assertNull(f.store.startTrace()!!.firstDispatchAt)
            assertEquals(ClickOutcomeReason.START_EXPIRED, f.store.lastExecutionResult()!!.reason)
            assertTrue(f.store.closedThrough() >= event.scheduledAt)
            assertTrue(f.store.alarmState()!!.next!!.scheduledAt > System.currentTimeMillis())
        }
    }
    @Test fun stopControlPreparationConsumesOriginalStartupBudget() {
        val count = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            button(scenario, count)
            val task = f.task("overlay-budget", listOf(ClickCounterPoint(300f, 300f, 0)))
            val due = f.due(task); val event = f.seed(task, due)
            runBlocking { assertNotNull(ClickTaskController(f.context).acceptAlarm(event, due + 100)) }
            val service = AccessibilityCoreService.accessibilityCoreService!!
            var job: kotlinx.coroutines.Job? = null
            f.instrumentation.runOnMainSync {
                val clock = f.clock(due, 4800)
                job = service.lifecycleScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    ScheduledClickExecutor(f.context, f.store, ClickTaskController(f.context), clock)
                        .execute(service, task, event, clock.wallMillis(), clock.elapsedMillis())
                }
                assertTrue(ClickExecutionSession.state.value.active)
                // Hold the first layout frame beyond the original remaining 200 ms.
                Thread.sleep(300)
            }
            f.waitFor("late stop-control preparation must close without dispatch") { job!!.isCompleted }
            assertEquals(0, count.get())
            assertNull(f.store.startTrace()!!.firstDispatchAt)
            assertEquals(ClickOutcomeReason.START_EXPIRED, f.store.lastExecutionResult()!!.reason)
            assertTrue(f.store.closedThrough() >= due)
            assertNotNull(f.store.alarmState()!!.next)
        }
    }

    @Test fun firstRecordedDelayCannotExtendStartupWindow() {
        val count = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            button(scenario, count)
            val task = f.task("first-delay", listOf(ClickCounterPoint(300f, 300f, 5100)))
            val due = f.due(task); val event = f.seed(task, due)
            runBlocking { ClickAlarmDispatcher.dispatch(f.context, event, f.clock(due)) }
            f.waitFor("delayed first point must terminate") { f.store.alarmState()?.active == null }
            assertEquals(0, count.get()); assertNull(f.store.startTrace()!!.firstDispatchAt)
            assertTrue(f.store.wasConsumed(task.id, due))
            assertEquals(ClickOutcomeReason.START_EXPIRED, f.store.lastExecutionResult()!!.reason)
        }
    }
    @Test fun firstTimelyClickAllowsSecondPointAfterStartupWindow() {
        val count = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            button(scenario, count)
            val task = f.task("later-second", listOf(ClickCounterPoint(300f, 300f, 0), ClickCounterPoint(300f, 300f, 5100)))
            val due = f.due(task); val event = f.seed(task, due)
            runBlocking { ClickAlarmDispatcher.dispatch(f.context, event, f.clock(due)) }
            f.waitFor("sequence may continue after first window") { f.store.alarmState()?.active == null }
            f.waitFor("both button click callbacks must be delivered", 5000) { count.get() == 2 }
            assertEquals("result=${f.store.lastExecutionResult()}; trace=${f.store.startTrace()}", 2, count.get()); assertEquals(ClickOutcomeReason.COMPLETED, f.store.lastExecutionResult()!!.reason)
        }
    }
    @Test fun sharedFinalHookRunsAfterRecordedDelayAndCanRejectRealGesture() {
        val count = AtomicInteger(); var called = false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            button(scenario, count); f.environment()
            val start = android.os.SystemClock.elapsedRealtime()
            val done = runBlocking {
                AccessibilityCoreService.accessibilityCoreService!!.executeClickSequence(
                    listOf(ClickCounterPoint(300f, 300f, 200)), beforeDispatch = {
                        called = true; assertTrue(android.os.SystemClock.elapsedRealtime() - start >= 200); false
                    })
            }
            assertFalse(done); assertTrue(called); assertEquals(0, count.get())
        }
    }
    @Test fun busyTrialSkipsOccurrenceWithoutQueuingOrCancellingFuture() {
        val count = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            button(scenario, count)
            val task = f.task("busy", listOf(ClickCounterPoint(300f, 300f, 0)))
            val due = f.due(task); val event = f.seed(task, due)
            f.instrumentation.runOnMainSync { assertTrue(ClickExecutionSession.startTrial(f.context, task)) }
            runBlocking { ClickAlarmDispatcher.dispatch(f.context, event, f.clock(due)) }
            f.waitFor("busy event must finish") { f.store.alarmState()?.active == null }
            assertEquals(ClickOutcomeReason.EXECUTION_BUSY, f.store.lastExecutionResult()!!.reason)
            assertEquals(0, count.get()); assertNotNull(f.store.alarmState()!!.next)
            assertTrue(ClickExecutionSession.state.value.manual)
            f.instrumentation.runOnMainSync { ClickExecutionSession.emergencyStop(f.context) }
            f.waitFor("trial cleanup") { !ClickExecutionSession.state.value.active }
        }
    }
    @Test fun connectionConsumesOriginalWindowAndCannotStartLate() {
        val count = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            button(scenario, count)
            val task = f.task("connection-expired", listOf(ClickCounterPoint(300f, 300f, 0)))
            val due = f.due(task); val event = f.seed(task, due)
            val service = AccessibilityCoreService.accessibilityCoreService!!
            f.instrumentation.runOnMainSync { AccessibilityCoreService.accessibilityCoreService = null }
            try { runBlocking { ClickAlarmDispatcher.dispatch(f.context, event, f.clock(due, 4800)) } }
            finally { f.instrumentation.runOnMainSync { AccessibilityCoreService.accessibilityCoreService = service } }
            assertEquals(0, count.get()); assertFalse(f.store.wasConsumed(task.id, due))
            assertEquals(ClickOutcomeReason.START_EXPIRED, f.store.lastExecutionResult()!!.reason)
        }
    }
    @Test fun systemServiceDisconnectCancelsOwnedSequenceAndPreservesFuture() {
        val count = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContentView(Button(activity).apply {
                setOnClickListener {
                    count.incrementAndGet()
                    val enabled = f.shell("settings --user 0 get secure enabled_accessibility_services")
                    val own = "${f.context.packageName}/${AccessibilityCoreService::class.java.name}"
                    val other = enabled.split(':').filter { it != own }.joinToString(":")
                    if (other.isBlank()) f.shell("settings --user 0 delete secure enabled_accessibility_services")
                    else f.shell("settings --user 0 put secure enabled_accessibility_services $other")
                }
            }) }
            val task = f.task("service-destroy", listOf(ClickCounterPoint(300f, 300f, 0), ClickCounterPoint(300f, 300f, 4000)))
            val due = f.due(task); val event = f.seed(task, due)
            runBlocking { ClickAlarmDispatcher.dispatch(f.context, event, f.clock(due)) }
            f.waitFor("service lifecycle must end execution") { count.get() == 1 && !ClickExecutionSession.state.value.active }
            assertEquals(ClickOutcomeReason.CANCELLED, f.store.lastExecutionResult()!!.reason)
            assertNull(AccessibilityCoreService.accessibilityCoreService)
            assertNotNull(f.store.alarmState()!!.next)
            assertEquals(1, count.get())
        }
    }

    @Test fun emergencyStopAfterFirstDispatchRetainsPartialTrace() {
        val count = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContentView(Button(activity).apply {
                setOnClickListener { count.incrementAndGet(); ClickExecutionSession.emergencyStop(f.context) }
            }) }
            val task = f.task("partial-stop", listOf(ClickCounterPoint(300f, 300f, 0), ClickCounterPoint(300f, 300f, 4000)))
            val due = f.due(task); val event = f.seed(task, due)
            runBlocking { ClickAlarmDispatcher.dispatch(f.context, event, f.clock(due)) }
            f.waitFor("stop must clean active execution") { count.get() == 1 && !ClickExecutionSession.state.value.active && f.store.load()?.enabled == false }
            assertEquals(1, count.get()); assertNotNull(f.store.startTrace()!!.firstDispatchAt)
            assertEquals(due, f.store.closedThrough())
        }
    }

    @Test fun failedConsumptionCommitDispatchesNoGesture() {
        val count = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            button(scenario, count)
            val task = f.task("failed-consumption", listOf(ClickCounterPoint(300f, 300f, 0)))
            val due = f.due(task); val event = f.seed(task, due)
            val clock = f.clock(due)
            val controller = ClickTaskController(f.context)
            runBlocking { assertNotNull(controller.acceptAlarm(event, clock.wallMillis())) }
            val prefs = f.context.getSharedPreferences("autoclick_task", android.content.Context.MODE_PRIVATE)
            val failing = ClickTaskStore(FailingCommitPreferences(prefs))
            val service = AccessibilityCoreService.accessibilityCoreService!!
            var job: kotlinx.coroutines.Job? = null
            f.instrumentation.runOnMainSync {
                job = service.lifecycleScope.launch { ScheduledClickExecutor(f.context, failing, controller, clock)
                    .execute(service, task, event, clock.wallMillis(), clock.elapsedMillis()) }
            }
            f.waitFor("failed commit must close without dispatch") { job!!.isCompleted }
            assertEquals(0, count.get()); assertNull(f.store.startTrace()!!.firstDispatchAt)
            assertTrue(f.store.wasConsumed(task.id, due))
            assertEquals("无法保存执行记录，未执行点击", f.store.lastExecutionResult()!!.message)
            assertEquals(ClickExecutionClaim.ALREADY_CONSUMED, failing.claimAlarm(event))
        }
    }

}
