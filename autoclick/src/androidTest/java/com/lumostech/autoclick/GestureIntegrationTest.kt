package com.lumostech.autoclick

import android.app.UiAutomation
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.view.View
import android.graphics.Rect
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.lumostech.accessibilitycore.AccessibilityCoreService
import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickEnvironment
import com.lumostech.accessibilitycore.ClickRecordingProtection
import com.lumostech.accessibilitycore.ClickSequenceStore
import com.lumostech.accessibilitycore.ViewModelMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.Calendar
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext
import java.util.concurrent.LinkedBlockingQueue

/** Runs real Android accessibility gestures on a dedicated emulator/test device. */
@RunWith(AndroidJUnit4::class)
class GestureIntegrationTest {
    private enum class ScheduledResult { COMPLETED, STOPPED }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var automation: UiAutomation
    private var originalServices = ""
    private var originalEnabled = "0"

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    @Before
    fun enableService() {
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        originalServices = shell("settings --user 0 get secure enabled_accessibility_services")
        originalEnabled = shell("settings --user 0 get secure accessibility_enabled")
        val component = "${context.packageName}/${AccessibilityCoreService::class.java.name}"
        val combined = if (originalServices == "null" || originalServices.isBlank()) component else "$originalServices:$component"
        shell("settings --user 0 put secure enabled_accessibility_services $combined")
        shell("settings --user 0 put secure accessibility_enabled 1")
        assertEquals(combined, shell("settings --user 0 get secure enabled_accessibility_services"))
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        val end = System.currentTimeMillis() + 10_000
        while (AccessibilityCoreService.accessibilityCoreService == null && System.currentTimeMillis() < end) Thread.sleep(100)
        assertNotNull("Accessibility service must connect", AccessibilityCoreService.accessibilityCoreService)
        ClickExecutionSession.allowScheduled()
    }

    @After
    fun restoreService() {
        if (ClickExecutionSession.state.value.active) {
            instrumentation.runOnMainSync { ClickExecutionSession.emergencyStop(context) }
            awaitCondition("Test cleanup must release execution session", 5_000) { !ClickExecutionSession.state.value.active }
            awaitCondition("Test cleanup must finish disabling task", 5_000) { ClickTaskStore(context).load()?.enabled != true }
        }
        instrumentation.runOnMainSync {
            ViewModelMain.isShowFloatWindow.value = false
            ViewModelMain.isShowCustomFloatWindow.value = false
            AccessibilityCoreService.accessibilityCoreService?.startRecording()
        }
        WorkManager.getInstance(context).cancelUniqueWork(ClickPeriodicWorker.TAG).result.get(10, TimeUnit.SECONDS)
        runBlocking { ClickTaskController(context).delete() }
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        if (originalServices == "null" || originalServices.isBlank()) shell("settings --user 0 delete secure enabled_accessibility_services")
        else shell("settings --user 0 put secure enabled_accessibility_services $originalServices")
        shell("settings --user 0 put secure accessibility_enabled ${if (originalEnabled == "1") "1" else "0"}")
    }

    @Test
    fun overlaySwitchKeepsRecordedSequence() {
        val service = AccessibilityCoreService.accessibilityCoreService!!
        instrumentation.runOnMainSync {
            service.startRecording()
            service.recordClick(120f, 240f)
            ViewModelMain.isShowFloatWindow.value = true
        }
        instrumentation.waitForIdleSync()
        val snapshot = service.getRecordedClickPoints()
        instrumentation.runOnMainSync {
            service.setFloatCustomView(View(service))
            ViewModelMain.isShowCustomFloatWindow.value = true
        }
        instrumentation.waitForIdleSync()
        assertEquals(snapshot, service.getRecordedClickPoints())
        instrumentation.runOnMainSync {
            ViewModelMain.isShowCustomFloatWindow.value = false
            ViewModelMain.isShowFloatWindow.value = true
        }
        instrumentation.waitForIdleSync()
        assertEquals(snapshot, service.getRecordedClickPoints())
    }

    @Test
    fun activityRecreationClosesStaleConfirmationOverlay() {
        instrumentation.runOnMainSync {
            val service = AccessibilityCoreService.accessibilityCoreService!!
            service.startRecording()
            service.recordClick(120f, 240f)
        }
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(AccessibilityCoreService.CONFIGURE_CLICKS, true)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            instrumentation.waitForIdleSync()
            assertTrue(ViewModelMain.isShowCustomFloatWindow.value == true)
            scenario.recreate()
            instrumentation.waitForIdleSync()
            assertFalse("Old confirmation must not retain a cancelled Activity scope", ViewModelMain.isShowCustomFloatWindow.value == true)
            assertEquals(1, AccessibilityCoreService.accessibilityCoreService!!.getRecordedClickPoints().size)
        }
    }

    @Test fun scheduledAlarmWaitsWithinRemainingBudgetForConnection() {
        val count = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContentView(Button(activity).apply { setOnClickListener { count.incrementAndGet() } }) }
            val task = protectedTask("reconnect", listOf(ClickCounterPoint(300f, 300f, 0)))
            val fixture = ExactTimingFixture(); val due = fixture.due(task); val event = fixture.seed(task, due)
            val clock = fixture.clock(due); val service = AccessibilityCoreService.accessibilityCoreService!!
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                instrumentation.runOnMainSync { AccessibilityCoreService.accessibilityCoreService = null }
                val job = scope.launch { ClickAlarmDispatcher.dispatch(context, event, clock) }
                Thread.sleep(500); assertTrue(job.isActive); assertEquals(0, count.get())
                assertFalse(ClickTaskStore(context).wasConsumed(task.id, due))
                instrumentation.runOnMainSync { AccessibilityCoreService.accessibilityCoreService = service }
                awaitCondition("reconnected service must deliver actual click", 5000) {
                    count.get() == 1 && ClickTaskStore(context).lastExecutionResult()?.reason == ClickOutcomeReason.COMPLETED
                }
            } finally { instrumentation.runOnMainSync { AccessibilityCoreService.accessibilityCoreService = service }; scope.cancel() }
        }
    }

    @Test fun stoppingTaskDuringReconnectWaitDispatchesNothing() {
        val count = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContentView(Button(activity).apply { setOnClickListener { count.incrementAndGet() } }) }
            val task = protectedTask("stop-reconnect", listOf(ClickCounterPoint(300f, 300f, 0)))
            val fixture = ExactTimingFixture(); val due = fixture.due(task); val event = fixture.seed(task, due)
            val clock = fixture.clock(due); val service = AccessibilityCoreService.accessibilityCoreService!!
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                instrumentation.runOnMainSync { AccessibilityCoreService.accessibilityCoreService = null }
                val job = scope.launch { ClickAlarmDispatcher.dispatch(context, event, clock) }
                awaitCondition("event must be preparing") { ClickTaskStore(context).alarmState()?.active == event }
                runBlocking { ClickTaskController(context).emergencyStop() }
                awaitCondition("disabled event must stop waiting promptly", 3000) { job.isCompleted }
                assertEquals(0, count.get()); assertFalse(ClickTaskStore(context).wasConsumed(task.id, due))
                assertFalse(ClickExecutionSession.state.value.active)
            } finally { instrumentation.runOnMainSync { AccessibilityCoreService.accessibilityCoreService = service }; scope.cancel() }
        }
    }

    @Test
    fun disabledAccessibilityServiceDoesNotWaitOrConsume() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val task = protectedTask("disabled-service", listOf(ClickCounterPoint(300f, 300f, 0)))
            assertTrue(ClickTaskStore(context).save(task))
            val component = "${context.packageName}/${AccessibilityCoreService::class.java.name}"
            val enabled = shell("settings --user 0 get secure enabled_accessibility_services")
            val remaining = enabled.split(':').filter { it != component }.joinToString(":")
            if (remaining.isBlank()) shell("settings --user 0 delete secure enabled_accessibility_services")
            else shell("settings --user 0 put secure enabled_accessibility_services $remaining")
            awaitCondition("Settings must report a disabled service", 5_000) {
                ClickServiceConnection.readiness(context) == ClickServiceReadiness.DISABLED
            }
            val started = System.currentTimeMillis()
            assertEquals(ScheduledResult.STOPPED, runScheduled(task))
            assertTrue("Disabled service must not use the ten-second reconnect window", System.currentTimeMillis() - started < 5_000)
            assertEquals(ClickTaskOutcome.SERVICE_UNAVAILABLE.message, ClickTaskStore(context).lastOutcome())
            assertFalse(ClickTaskStore(context).wasConsumed(task.id,
                ClickSchedulePolicy.evaluate(task, System.currentTimeMillis()).scheduledAt))
        }
    }

    @Test fun scheduledExecutorCompletesRealAndroidGestures() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val task = protectedTask("real-gestures", listOf(ClickCounterPoint(120f, 200f, 0), ClickCounterPoint(360f, 200f, 100)))
            assertTrue(ClickTaskStore(context).save(task))
            assertEquals(ScheduledResult.COMPLETED, runScheduled(task))
            assertEquals(ClickTaskOutcome.COMPLETED.message, ClickTaskStore(context).lastOutcome())
        }
    }

    private fun foregroundEnvironment(): ClickEnvironment {
        instrumentation.runOnMainSync { AccessibilityCoreService.accessibilityCoreService!!.enableProtectedRecording() }
        var environment: ClickEnvironment? = null
        val end = System.currentTimeMillis() + 10_000
        while (environment?.packageName != context.packageName && System.currentTimeMillis() < end) {
            instrumentation.runOnMainSync { environment = AccessibilityCoreService.accessibilityCoreService!!.currentClickEnvironment() }
            if (environment?.packageName != context.packageName) Thread.sleep(100)
        }
        assertEquals(context.packageName, environment?.packageName)
        return environment!!
    }

    private fun protectedTask(id: String, points: List<ClickCounterPoint>): ClickTask {
        val env = foregroundEnvironment()
        val now = Calendar.getInstance()
        return ClickTask(id, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), (1..7).toSet(), points,
            protection = ClickRecordingProtection(env.width, env.height, env.rotation, List(points.size) { env.packageName }))
    }

    /** Fixed wall anchor exercises real gestures and protections, not real alarm timeliness. */
    private fun runScheduled(task: ClickTask): ScheduledResult {
        val fixture = ExactTimingFixture(); val due = fixture.due(task)
        val event = if (ClickTaskStore(context).alarmState() == null) fixture.seed(task, due)
            else ClickAlarmOccurrence(task.id, task.scheduleId, due)
        runBlocking { ClickAlarmDispatcher.dispatch(context, event, fixture.clock(due)) }
        awaitCondition("scheduled execution must terminate", 20000) { ClickTaskStore(context).alarmState()?.active == null }
        return if (ClickTaskStore(context).lastExecutionResult()?.reason == ClickOutcomeReason.COMPLETED)
            ScheduledResult.COMPLETED else ScheduledResult.STOPPED
    }

    @Test
    fun protectedRecordingRetainsEnvironmentWhenOverlayIsCreated() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val expected = foregroundEnvironment()
            instrumentation.runOnMainSync {
                val service = AccessibilityCoreService.accessibilityCoreService!!
                service.enableProtectedRecording()
                service.startRecording()
                ViewModelMain.isShowFloatWindow.value = true
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                AccessibilityCoreService.accessibilityCoreService!!.recordClick(120f, 240f)
            }
            val restored = ClickSequenceStore(context).loadProtection()!!
            assertEquals(expected.width, restored.width)
            assertEquals(expected.rotation, restored.rotation)
            assertEquals(listOf(context.packageName), restored.packages)
        }
    }

    @Test
    fun duplicateWorkerDoesNotClickTwiceOrOverwriteCompletedStatus() {
        val count = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContentView(Button(activity).apply { setOnClickListener { count.incrementAndGet() } })
            }
            val task = protectedTask("duplicate", listOf(ClickCounterPoint(300f, 300f, 0)))
            assertTrue(ClickTaskStore(context).save(task))
            assertEquals(ScheduledResult.COMPLETED, runScheduled(task))
            awaitCondition("The actual button must receive the completed gesture", 5_000) { count.get() == 1 }
            assertEquals(1, count.get())
            assertEquals(ScheduledResult.COMPLETED, runScheduled(task))
            awaitCondition("The actual button must receive the completed gesture", 5_000) { count.get() == 1 }
            assertEquals(1, count.get())
            assertEquals(ClickTaskOutcome.COMPLETED.message, ClickTaskStore(context).lastOutcome())
        }
    }

    @Test
    fun switchingApplicationsAfterFirstClickStopsRemainingPoints() {
        // Warm the external Activity before measuring the protected sequence; a cold
        // Settings launch can take several seconds on a resource-limited emulator.
        shell("am start -W -a android.settings.SETTINGS")
        val count = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContentView(Button(activity).apply {
                    setOnClickListener { count.incrementAndGet(); activity.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                })
            }
            val task = protectedTask("app-change", listOf(ClickCounterPoint(300f, 300f, 0), ClickCounterPoint(300f, 300f, 5000)))
            assertTrue(ClickTaskStore(context).save(task))
            assertEquals(ScheduledResult.STOPPED, runScheduled(task))
            assertEquals(1, count.get())
            assertTrue(ClickTaskStore(context).lastOutcome().contains("当前应用与录制时不一致"))
            assertTrue(ClickTaskStore(context).lastOutcome().contains("已完成 1 个点击"))
        }
    }

    @Test
    fun screenOffAfterFirstClickStopsRemainingPoints() {
        val count = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContentView(Button(activity).apply {
                    setOnClickListener { count.incrementAndGet(); shell("input keyevent KEYCODE_SLEEP") }
                })
            }
            val task = protectedTask("screen-off", listOf(ClickCounterPoint(300f, 300f, 0), ClickCounterPoint(300f, 300f, 1500)))
            assertTrue(ClickTaskStore(context).save(task))
            assertEquals(ScheduledResult.STOPPED, runScheduled(task))
            assertEquals(1, count.get())
            assertTrue(ClickTaskStore(context).lastOutcome().contains("屏幕关闭或锁定"))
        }
    }

    @Test
    fun displayMismatchCannotConsumeOrExecuteOccurrence() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val task = protectedTask("display-change", listOf(ClickCounterPoint(300f, 300f, 0)))
            val changed = task.copy(protection = task.protection!!.copy(rotation = (task.protection.rotation + 1) % 4))
            assertTrue(ClickTaskStore(context).save(changed))
            assertEquals(ScheduledResult.STOPPED, runScheduled(changed))
            assertTrue(ClickTaskStore(context).lastOutcome().contains("屏幕尺寸或方向已改变"))
            assertFalse(ClickTaskStore(context).wasConsumed(changed.id,
                ClickSchedulePolicy.evaluate(changed, System.currentTimeMillis()).scheduledAt))
        }
    }



    @Test fun currentCompletionPreservesIndependentFutureAlarm() {
        val count = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContentView(Button(activity).apply { setOnClickListener { count.incrementAndGet() } }) }
            val task = protectedTask("future-alarm", listOf(ClickCounterPoint(300f, 300f, 0)))
            assertTrue(ClickTaskStore(context).save(task))
            assertEquals(ScheduledResult.COMPLETED, runScheduled(task))
            val completedAt = System.currentTimeMillis(); val completedCount = count.get()
            awaitCondition("The actual button must receive the completed gesture", 5_000) { count.get() == 1 }
            android.util.Log.i("AutoclickQuickCheck", "gestureCompleteButtonCount=$completedCount actualButtonCount=${count.get()} deliveryWaitMs=${System.currentTimeMillis()-completedAt}")
            assertEquals(1, count.get())
            val next = ClickTaskStore(context).alarmState()!!.next!!
            assertTrue(next.scheduledAt > System.currentTimeMillis())
            assertEquals(ClickAlarmStatus.ARMED, ClickTaskStore(context).alarmState()!!.status)
        }
    }

    @Test
    fun tappingRecordingOverlayCapturesUnderlyingTargetApplication() {
        ActivityScenario.launch(MainActivity::class.java).use {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val service = AccessibilityCoreService.accessibilityCoreService!!
            instrumentation.runOnMainSync { service.enableProtectedRecording(); service.startRecording() }
            val end = System.currentTimeMillis() + 10_000
            var targetReady = false
            while (!targetReady && System.currentTimeMillis() < end) {
                instrumentation.runOnMainSync { targetReady = service.currentClickEnvironment()?.packageName == "com.android.settings" }
                if (!targetReady) Thread.sleep(100)
            }
            assertTrue("Settings target must be foreground before recording", targetReady)
            instrumentation.runOnMainSync { ViewModelMain.isShowFloatWindow.value = true }
            instrumentation.waitForIdleSync()
            val bounds = Rect()
            val overlayEnd = System.currentTimeMillis() + 5_000
            while (bounds.isEmpty && System.currentTimeMillis() < overlayEnd) {
                instrumentation.runOnMainSync {
                    service.windows.firstOrNull { window -> window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
                        ?.getBoundsInScreen(bounds)
                }
                if (bounds.isEmpty) Thread.sleep(100)
            }
            assertFalse("Recording overlay must become accessible before tapping", bounds.isEmpty)
            shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
            val recordedEnd = System.currentTimeMillis() + 5_000
            while (service.getRecordedClickPoints().isEmpty() && System.currentTimeMillis() < recordedEnd) Thread.sleep(100)
            assertEquals(listOf("com.android.settings"), service.getRecordedProtection()?.packages)
            assertEquals(1, service.getRecordedClickPoints().size)
        }
    }

    private fun awaitCondition(message: String, timeout: Long = 20_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeout
        while (!condition() && System.currentTimeMillis() < end) Thread.sleep(100)
        val satisfied = condition()
        if (!satisfied) captureExecutionDiagnostics(message, failed = true)
        assertTrue(message, satisfied)
    }

    private fun captureExecutionDiagnostics(stage: String, failed: Boolean = false) {
        var environment: ClickEnvironment? = null
        instrumentation.runOnMainSync {
            environment = AccessibilityCoreService.accessibilityCoreService?.currentClickEnvironment()
        }
        android.util.Log.i("AutoclickQuickCheck",
            "stage=$stage, state=${ClickExecutionSession.state.value}, environment=$environment")
        if (failed) shell("screencap -p /sdcard/autoclick-failure-execution.png")
    }

    @Test
    fun trialRunsDisabledScheduleWithoutConsumingItsOccurrence() {
        val count = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContentView(Button(activity).apply {
                setOnClickListener {
                    android.util.Log.i("AutoclickQuickCheck", "trialButtonClicks=${count.incrementAndGet()}")
                }
            }) }
            val task = protectedTask("manual-trial", listOf(ClickCounterPoint(300f, 300f, 0))).copy(enabled = false,
                days = setOf((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) % 7) + 1))
            assertTrue(ClickTaskStore(context).save(task))
            instrumentation.runOnMainSync { assertTrue(ClickExecutionSession.startTrial(context, task)) }
            awaitCondition("Trial should finish") { !ClickExecutionSession.state.value.active }
            captureExecutionDiagnostics("trial-finished, buttonClicks=${count.get()}",
                failed = !ClickExecutionSession.state.value.message.contains("全部点击手势已完成"))
            assertTrue("Trial outcome: ${ClickExecutionSession.state.value}",
                ClickExecutionSession.state.value.message.contains("全部点击手势已完成"))
            awaitCondition("The actual button must receive the trial click", 5_000) { count.get() == 1 }
            assertFalse(ClickTaskStore(context).wasConsumed(task.id,
                ClickSchedulePolicy.evaluate(task, System.currentTimeMillis()).scheduledAt))
            assertFalse(ClickTaskStore(context).load()!!.enabled)
            val trialResult = ClickExecutionSession.state.value.lastTrialResult
            assertTrue(trialResult.contains("全部点击手势已完成"))
            val scheduled = task.copy(enabled = true, days = (1..7).toSet())
            assertTrue(ClickTaskStore(context).save(scheduled))
            assertEquals(ScheduledResult.COMPLETED, runScheduled(scheduled))
            awaitCondition("The actual button must receive the scheduled click", 5_000) { count.get() == 2 }
            assertEquals(2, count.get())
            assertEquals("A scheduled run must retain the independent trial result", trialResult,
                ClickExecutionSession.state.value.lastTrialResult)
        }
    }

    @Test
    fun emergencyStopDuringCountdownDispatchesNothingAndDisablesSchedule() {
        val count = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContentView(Button(activity).apply { setOnClickListener { count.incrementAndGet() } }) }
            val task = protectedTask("stop-countdown", listOf(ClickCounterPoint(300f, 300f, 0)))
            runBlocking { ClickTaskController(context).save(task) }
            instrumentation.runOnMainSync {
                assertTrue(ClickExecutionSession.startTrial(context, task))
                ClickExecutionSession.emergencyStop(context)
            }
            awaitCondition("Emergency stop must persist disabled state", 5_000) { ClickTaskStore(context).load()?.enabled == false }
            awaitCondition("Countdown cancellation must release its lease", 3_000) { !ClickExecutionSession.state.value.active }
            assertEquals(0, count.get())
            assertTrue(ClickTaskStore(context).lastOutcome().contains("已紧急停止"))
        }
    }

    @Test
    fun floatingEmergencyStopInterruptsLongIntervalBeforeNextClick() {
        val count = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContentView(Button(activity).apply { setOnClickListener { count.incrementAndGet() } }) }
            val task = protectedTask("stop-interval", listOf(ClickCounterPoint(300f, 300f, 0), ClickCounterPoint(300f, 300f, 8000)))
            runBlocking { ClickTaskController(context).save(task) }
            instrumentation.runOnMainSync { assertTrue(ClickExecutionSession.startTrial(context, task)) }
            awaitCondition("First real button click must arrive") { count.get() == 1 }
            val bounds = Rect()
            instrumentation.runOnMainSync {
                serviceStopWindowBounds(bounds)
            }
            assertFalse("Stop control must remain visible during the interval", bounds.isEmpty)
            val startedStop = System.currentTimeMillis()
            shell("input tap ${bounds.centerX()} ${bounds.bottom - (26 * context.resources.displayMetrics.density).toInt()}")
            awaitCondition("Stop should interrupt the wait promptly", 3_000) { !ClickExecutionSession.state.value.active }
            assertTrue(System.currentTimeMillis() - startedStop < 4_000)
            awaitCondition("Stop should disable future schedule", 5_000) { ClickTaskStore(context).load()?.enabled == false }
            assertEquals(1, count.get())
        }
    }

    private fun serviceStopWindowBounds(bounds: Rect) {
        AccessibilityCoreService.accessibilityCoreService!!.windows.firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
        }?.getBoundsInScreen(bounds)
    }

    @Test
    fun cancellationWhileReturningAcquiredLeaseReleasesTheStopWindow() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val task = protectedTask("cancel-acquire", listOf(ClickCounterPoint(300f, 300f, 0)))
            val queue = LinkedBlockingQueue<Runnable>()
            val dispatcher = object : CoroutineDispatcher() {
                override fun dispatch(context: CoroutineContext, block: Runnable) { queue.add(block) }
            }
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            val job = scope.launch {
                val handle = ClickExecutionSession.acquire(AccessibilityCoreService.accessibilityCoreService!!, task, false)
                if (handle != null) ClickExecutionSession.release(handle)
            }
            try {
                queue.poll(5, TimeUnit.SECONDS)!!.run()
                val returning = queue.poll(5, TimeUnit.SECONDS)!!
                assertTrue("The lease must exist before the dispatcher resumes", ClickExecutionSession.state.value.active)
                job.cancel()
                returning.run()
                awaitCondition("A discarded acquired handle must still be cleaned", 3_000) { !ClickExecutionSession.state.value.active }
            } finally {
                scope.cancel()
                while (queue.isNotEmpty()) queue.poll()?.run()
                if (ClickExecutionSession.state.value.active) {
                    instrumentation.runOnMainSync { ClickExecutionSession.emergencyStop(context) }
                }
            }
        }
    }

}
