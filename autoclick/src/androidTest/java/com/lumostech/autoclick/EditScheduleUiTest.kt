package com.lumostech.autoclick

import android.app.UiAutomation
import android.content.pm.ActivityInfo
import android.os.ParcelFileDescriptor
import android.widget.Button
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.work.WorkManager
import androidx.work.Data
import androidx.work.testing.TestListenableWorkerBuilder
import com.lumostech.accessibilitycore.AccessibilityCoreService
import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickRecordingProtection
import com.lumostech.accessibilitycore.ClickSequenceStore
import com.lumostech.accessibilitycore.ClickEnvironment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class EditScheduleUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = ClickTaskStore(context)
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var automation: UiAutomation
    private var originalServices = "null"
    private var originalEnabled = "0"
    private var originalFontScale = "1.0"
    private val task = ClickTask("edit-ui", 9, 35, setOf(Calendar.MONDAY, Calendar.WEDNESDAY),
        listOf(ClickCounterPoint(100f, 200f, 0)), enabled = false,
        protection = ClickRecordingProtection(1080, 2424, 0, listOf("com.example.saved")))

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    @Before fun prepare() {
        runBlocking { (context.applicationContext as AutoclickApp).awaitStartup() }
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        originalServices = shell("settings --user 0 get secure enabled_accessibility_services")
        originalEnabled = shell("settings --user 0 get secure accessibility_enabled")
        originalFontScale = shell("settings --user 0 get system font_scale")
        val component = "${context.packageName}/${AccessibilityCoreService::class.java.name}"
        val services = originalServices.split(':').filter { it != "null" && it.isNotBlank() }
        shell("settings --user 0 put secure enabled_accessibility_services ${(services + component).distinct().joinToString(":")}")
        shell("settings --user 0 put secure accessibility_enabled 1")
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        compose.waitUntil(10_000) { AccessibilityCoreService.accessibilityCoreService != null }
        store.clear()
        assertTrue(store.save(task))
        ClickSequenceStore(context).save(listOf(ClickCounterPoint(400f, 500f, 0)))
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun restore() {
        scenario?.close()
        WorkManager.getInstance(context).cancelUniqueWork(ClickPeriodicWorker.TAG).result.get(10, TimeUnit.SECONDS)
        runBlocking { ClickTaskController(context).delete() }
        if (originalServices == "null" || originalServices.isBlank()) shell("settings --user 0 delete secure enabled_accessibility_services")
        else shell("settings --user 0 put secure enabled_accessibility_services $originalServices")
        shell("settings --user 0 put secure accessibility_enabled ${if (originalEnabled == "1") "1" else "0"}")
        if (originalFontScale == "null") shell("settings --user 0 delete system font_scale")
        else shell("settings --user 0 put system font_scale $originalFontScale")
    }

    private fun openEditor() {
        compose.waitUntil(5_000) { compose.onAllNodesWithText("修改时间").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("修改时间").performScrollTo().performClick()
        compose.onNodeWithText("修改任务时间").assertIsDisplayed()
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("uiScreenshots") == "true")
            shell("screencap -p /sdcard/autoclick-$name.png")
    }

    @Test fun prefilledValuesAndCancelLeaveTaskAndDraftUntouched() {
        val draft = ClickSequenceStore(context).load()
        openEditor()
        compose.onNodeWithTag("schedule-hour").assertTextContains("09")
        compose.onNodeWithTag("schedule-minute").assertTextContains("35")
        compose.onNodeWithTag("schedule-day-2").assertIsSelected()
        compose.onNodeWithTag("schedule-day-4").assertIsSelected()
        capture("edit-normal")
        compose.onNodeWithTag("schedule-hour").performTextReplacement("18")
        compose.onNodeWithText("取消").performScrollTo().performClick()
        compose.onNodeWithText("修改任务时间").assertDoesNotExist()
        assertEquals(task, store.load())
        assertEquals(draft, ClickSequenceStore(context).load())
    }

    @Test fun savesTimeAndWeekdaysWithoutEnablingOrReplacingRecording() {
        val draft = ClickSequenceStore(context).load()
        openEditor()
        compose.onNodeWithTag("schedule-hour").performTextReplacement("18")
        compose.onNodeWithTag("schedule-minute").performTextReplacement("20")
        compose.onNodeWithTag("schedule-day-2").performClick()
        compose.onNodeWithTag("schedule-day-4").performClick()
        compose.onNodeWithTag("schedule-day-6").performClick()
        compose.onNodeWithText("保存修改").performScrollTo().performClick()
        compose.waitUntil(5_000) { store.load()?.hour == 18 }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("修改任务时间").fetchSemanticsNodes().isEmpty() }
        val edited = ClickTaskStore(context).load()!!
        assertEquals(20, edited.minute)
        assertEquals(setOf(Calendar.FRIDAY), edited.days)
        assertFalse(edited.enabled)
        assertEquals(task.points, edited.points)
        assertEquals(task.protection, edited.protection)
        assertEquals(draft, ClickSequenceStore(context).load())
        compose.onNodeWithText("18:20").performScrollTo().assertIsDisplayed()
        capture("edit-disabled")
        scenario!!.recreate()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("18:20").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(edited, store.load())
    }

    @Test fun emptyWeekdaysAndInvalidTimeCannotBeSaved() {
        openEditor()
        compose.onNodeWithTag("schedule-day-2").performClick()
        compose.onNodeWithTag("schedule-day-4").performClick()
        compose.onNodeWithText("保存修改").performScrollTo().performClick()
        compose.onNodeWithText("请至少选择一个执行星期").performScrollTo().assertIsDisplayed()
        capture("edit-error")
        assertEquals(task, store.load())
        compose.onNodeWithTag("schedule-day-2").performClick()
        compose.onNodeWithTag("schedule-hour").performTextReplacement("24")
        compose.onNodeWithText("保存修改").performScrollTo().performClick()
        compose.onNodeWithText("请输入有效的时间（00:00–23:59）").performScrollTo().assertIsDisplayed()
        assertEquals(task, store.load())
    }

    @Test fun rotationKeepsDraftAndLargeFontAndLandscapeRemainUsable() {
        openEditor()
        compose.onNodeWithTag("schedule-hour").performTextReplacement("17")
        scenario!!.recreate()
        compose.onNodeWithTag("schedule-hour").assertTextContains("17")
        shell("settings --user 0 put system font_scale 1.5")
        scenario!!.recreate()
        compose.onNodeWithText("保存修改").performScrollTo().assertIsDisplayed()
        capture("edit-large-font")
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(5_000) { context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        compose.onNodeWithText("保存修改").performScrollTo().assertIsDisplayed()
        capture("edit-landscape")
        compose.onNodeWithText("取消").performScrollTo().performClick()
        assertEquals(task, store.load())
    }

    @Test fun replacedTaskAndLostServiceCloseUnsubmittedEditor() {
        openEditor()
        assertTrue(store.save(task.copy(id = "replacement-ui")))
        compose.waitUntil(5_000) { compose.onAllNodesWithText("修改任务时间").fetchSemanticsNodes().isEmpty() }
        openEditor()
        val service = AccessibilityCoreService.accessibilityCoreService!!
        try {
            compose.runOnIdle { AccessibilityCoreService.accessibilityCoreService = null }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("修改任务时间").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("正在连接服务").assertIsDisplayed()
        } finally { compose.runOnIdle { AccessibilityCoreService.accessibilityCoreService = service } }
        assertEquals("replacement-ui", store.load()!!.id)
        assertEquals(task.hour, store.load()!!.hour)
    }

    @Test fun rotatingDuringSaveRetainsOneOperationAndDisablesDuplicateSave() {
        runBlocking { ClickTaskController(context).save(task.copy(enabled = true)) }
        val originalAlarm = store.alarmState()!!.next!!
        openEditor()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val platform = RecordingAlarmPlatform().apply {
            beforeSchedule = {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
        }
        val slow = ClickTaskController(store, platform, {})
        lateinit var model: EditScheduleViewModel
        scenario!!.onActivity {
            model = ViewModelProvider(it)[EditScheduleViewModel::class.java]
            model.save(slow, 18, 15, setOf(Calendar.FRIDAY))
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            scenario!!.recreate()
            scenario!!.onActivity { assertSame(model, ViewModelProvider(it)[EditScheduleViewModel::class.java]) }
            compose.onNodeWithText("正在保存…").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("取消").performScrollTo().assertIsNotEnabled()
        } finally {
            release.countDown()
            AndroidClickAlarmPlatform(context).cancel(originalAlarm)
        }
        compose.waitUntil(5_000) { !model.saving }
        compose.onNodeWithText("修改任务时间").assertDoesNotExist()
        assertEquals(18, store.load()!!.hour)
        assertEquals(setOf(Calendar.FRIDAY), store.load()!!.days)
        assertEquals(1, platform.scheduled.size)
        assertEquals(store.load()!!.id, platform.scheduled.single().taskId)
        assertEquals(platform.scheduled.single(), store.alarmState()!!.next)
    }

    @Test fun editedEnabledScheduleClicksOnceAndConsumedDateStaysBlocked() {
        val service = AccessibilityCoreService.accessibilityCoreService!!
        var environment: ClickEnvironment? = null
        instrumentation.runOnMainSync { service.enableProtectedRecording() }
        var previousEnvironment = ""
        try {
            compose.waitUntil(10_000) {
                instrumentation.runOnMainSync {
                    environment = service.currentClickEnvironment()
                    val root = service.rootInActiveWindow
                    try {
                        val diagnostic = "environment=$environment rootPackage=${root?.packageName} " +
                            "window=${root?.window} serviceFlags=${service.serviceInfo.flags}"
                        if (diagnostic != previousEnvironment) {
                            android.util.Log.i("AutoclickQuickCheck", diagnostic)
                            previousEnvironment = diagnostic
                        }
                    } finally { root?.recycle() }
                }
                environment?.packageName == context.packageName
            }
        } finally {
            capture("edit-environment-diagnostic")
        }
        val env = environment!!
        val originalTime = Calendar.getInstance().apply { add(Calendar.MINUTE, 5) }
        val original = task.copy(hour = originalTime.get(Calendar.HOUR_OF_DAY), minute = originalTime.get(Calendar.MINUTE),
            enabled = true, days = (1..7).toSet(), points = listOf(ClickCounterPoint(300f, 300f, 0)),
            protection = ClickRecordingProtection(env.width, env.height, env.rotation, listOf(context.packageName)))
        runBlocking { ClickTaskController(context).save(original) }
        val oldEvent = store.alarmState()!!.next!!
        openEditor()
        val due = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 1); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis - System.currentTimeMillis() < 10_000) add(Calendar.MINUTE, 1)
        }
        compose.onNodeWithTag("schedule-hour").performTextReplacement(due.get(Calendar.HOUR_OF_DAY).toString())
        compose.onNodeWithTag("schedule-minute").performTextReplacement(due.get(Calendar.MINUTE).toString())
        compose.onNodeWithText("保存修改").performScrollTo().performClick()
        compose.waitUntil(5_000) { store.load()?.id != original.id }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("修改任务时间").fetchSemanticsNodes().isEmpty() }
        val edited = store.load()!!
        assertFalse(store.reserveAlarm(oldEvent, System.currentTimeMillis()))
        val count = AtomicInteger()
        val clickedAt = AtomicLong()
        scenario!!.onActivity { activity -> activity.setContentView(Button(activity).apply {
            text = "编辑时间验收：等待定时点击"
            setOnClickListener {
                clickedAt.set(System.currentTimeMillis())
                text = "编辑时间验收：已点击 ${count.incrementAndGet()} 次"
            }
        }) }
        assertEquals(0, count.get())
        android.util.Log.i("AutoclickQuickCheck", "edit scheduledAt=${due.timeInMillis}, replacedAlarm=${oldEvent.scheduledAt}")
        compose.waitUntil(90_000) {
            count.get() == 1 && store.lastOutcome() == ClickTaskOutcome.COMPLETED.message && !ClickExecutionSession.state.value.active
        }
        val trace = checkNotNull(store.startTrace())
        assertEquals(due.timeInMillis, trace.occurrence.scheduledAt)
        val firstDispatch = checkNotNull(trace.firstDispatchAt)
        assertTrue(firstDispatch >= due.timeInMillis)
        assertTrue(firstDispatch - due.timeInMillis < 5_000)
        assertTrue(clickedAt.get() >= due.timeInMillis)
        assertEquals(1, count.get())
        capture("edit-result")
        val record = store.lastExecutionRecord()
        scenario!!.recreate()
        openEditor()
        val later = (due.clone() as Calendar).apply { add(Calendar.MINUTE, 2) }
        compose.onNodeWithTag("schedule-hour").performTextReplacement(later.get(Calendar.HOUR_OF_DAY).toString())
        compose.onNodeWithTag("schedule-minute").performTextReplacement(later.get(Calendar.MINUTE).toString())
        compose.onNodeWithText("保存修改").performScrollTo().performClick()
        compose.waitUntil(5_000) { store.load()?.id != edited.id }
        val updated = ClickTaskStore(context).load()!!
        val newOccurrenceToday = (due.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, updated.hour); set(Calendar.MINUTE, updated.minute)
        }.timeInMillis
        assertTrue(store.wasConsumed(updated.id, newOccurrenceToday))
        assertEquals(ClickExecutionClaim.ALREADY_CONSUMED, store.claimExecution(updated.id, newOccurrenceToday))
        assertEquals(ClickSchedulePolicy.nextOccurrence(updated, System.currentTimeMillis(), due.timeInMillis),
            store.alarmState()!!.next!!.scheduledAt)
        assertFalse(store.reserveAlarm(ClickAlarmOccurrence(updated.id, updated.scheduleId, newOccurrenceToday), System.currentTimeMillis()))
        assertEquals(record, store.lastExecutionRecord())
        val stale = TestListenableWorkerBuilder<ClickPeriodicWorker>(context)
            .setInputData(Data.Builder().putString(ClickPeriodicWorker.TASK_ID, edited.id).build()).build()
        runBlocking { stale.doWork() }
        assertEquals(1, count.get())
        android.util.Log.i("AutoclickQuickCheck", "edit realClicks=${count.get()}, firstDispatchLateMs=${firstDispatch - due.timeInMillis}, buttonLateMs=${clickedAt.get() - due.timeInMillis}, consumedDateBlocked=true, staleWorkerRejected=true")
    }
}
