package com.lumostech.autoclick

import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import android.content.pm.ActivityInfo
import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.lumostech.accessibilitycore.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.io.File

class LegacyTaskRecoveryUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = ClickTaskStore(context)
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var automation: UiAutomation
    private var services = "null"
    private var enabled = "0"
    private var fontScale = "1.0"
    private var overlayOp = "allow"
    private lateinit var model: LegacyTaskRecoveryViewModel
    private val source = ClickTask("legacy-ui", 9, 30, setOf(2, 4), listOf(ClickCounterPoint(100f, 200f, 0)),
        enabled = false, scheduleId = "legacy-ui-plan")
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
        .bufferedReader().use { it.readText().trim() }
    @Before fun prepare() {
        runBlocking { (context.applicationContext as AutoclickApp).awaitStartup() }
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        services = shell("settings --user 0 get secure enabled_accessibility_services")
        enabled = shell("settings --user 0 get secure accessibility_enabled")
        fontScale = shell("settings --user 0 get system font_scale")
        overlayOp = Regex("SYSTEM_ALERT_WINDOW: (\\w+)").find(shell("appops get ${context.packageName} SYSTEM_ALERT_WINDOW"))?.groupValues?.get(1) ?: "default"
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        val component = "${context.packageName}/${AccessibilityCoreService::class.java.name}"
        shell("settings --user 0 put secure enabled_accessibility_services ${(services.split(':').filter { it != "null" && it.isNotBlank() } + component).distinct().joinToString(":")}")
        shell("settings --user 0 put secure accessibility_enabled 1")
        compose.waitUntil(10_000) { AccessibilityCoreService.accessibilityCoreService != null }
        store.clear(); LegacyTaskRecoveryStore(context).clear(); assertTrue(store.save(source))
        ClickSequenceStore(context).save(listOf(ClickCounterPoint(400f, 500f, 0)))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { model = ViewModelProvider(it)[LegacyTaskRecoveryViewModel::class.java] }
        compose.waitUntil(5_000) { !model.busy }
    }
    @After fun cleanup() {
        scenario?.close()
        runBlocking { ClickTaskController(context).delete() }
        LegacyTaskRecoveryStore(context).clear()
        instrumentation.runOnMainSync { ViewModelMain.isShowFloatWindow.value = false; ViewModelMain.isShowCustomFloatWindow.value = false }
        if (services == "null") shell("settings --user 0 delete secure enabled_accessibility_services")
        else shell("settings --user 0 put secure enabled_accessibility_services $services")
        shell("settings --user 0 put secure accessibility_enabled ${if (enabled == "1") "1" else "0"}")
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $overlayOp")
        if (fontScale == "null") shell("settings --user 0 delete system font_scale")
        else shell("settings --user 0 put system font_scale $fontScale")
    }
    @Test fun legacyButtonsExplainReasonAndExplanationCancelKeepsTaskAndDraft() {
        compose.onNodeWithText("旧版任务需要重新录制，才能启用。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("修改时间").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("启用任务").assertIsNotEnabled()
        compose.onNodeWithText("5 秒后试运行").assertIsNotEnabled()
        val draft = ClickSequenceStore(context).load()
        capture("legacy-reason")
        compose.onNodeWithText("重新录制").performScrollTo().performClick()
        compose.onNodeWithText("重新录制旧版任务").assertIsDisplayed()
        capture("legacy-explanation")
        compose.onNodeWithText("取消").performClick()
        assertEquals(source, store.load())
        assertEquals(draft, ClickSequenceStore(context).load())
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("uiScreenshots") == "true") shell("screencap -p /sdcard/autoclick-$name.png")
    }
    private fun begin() {
        compose.onNodeWithText("重新录制").performScrollTo().performClick()
        compose.onNodeWithText("开始重新录制").performClick()
        compose.waitUntil(5_000) { !model.busy && model.session != null }
        assertNull(model.error)
        assertEquals(model.session!!.recoveryId, ClickSequenceStore(context).loadSnapshot().sessionId)
        assertTrue(ClickSequenceStore(context).load().isEmpty())
        assertEquals(source, store.load())
    }
    private fun overlay(): View = AccessibilityCoreService::class.java.getDeclaredField("floatRootView")
        .apply { isAccessible = true }.get(AccessibilityCoreService.accessibilityCoreService) as View
    private fun panel(): View = AccessibilityCoreService::class.java.getDeclaredField("floatCustomView")
        .apply { isAccessible = true }.get(AccessibilityCoreService.accessibilityCoreService) as View
    private fun tap(view: View, long: Boolean = false) {
        val position = IntArray(2)
        var width = 0; var height = 0
        instrumentation.runOnMainSync { assertTrue(view.isAttachedToWindow); view.getLocationOnScreen(position); width = view.width; height = view.height }
        val x = position[0] + width / 2; val y = position[1] + height / 2
        shell(if (long) "input swipe $x $y $x $y 1200" else "input tap $x $y")
    }
    private fun candidate(): ClickTask {
        val session = model.session!!
        val snapshot = AccessibilityCoreService.accessibilityCoreService!!.getRecordedSnapshot()
        return ClickTask(session.replacementTaskId, session.hour, session.minute, session.days, snapshot.points,
            enabled = false, protection = snapshot.protection, scheduleId = session.replacementTaskId)
    }
    private fun recordOne() {
        compose.waitUntil(5_000) { overlay().isAttachedToWindow }
        tap(overlay())
        compose.waitUntil(5_000) { AccessibilityCoreService.accessibilityCoreService!!.getRecordedSnapshot().points.size == 1 }
        assertNotNull(ClickSequenceStore(context).loadSnapshot().protection)
    }

    @Test fun legacyTimeAndWeekdaysCanBeEditedWithoutClearingDraft() {
        val draft = ClickSequenceStore(context).load()
        compose.onNodeWithText("修改时间").performScrollTo().performClick()
        compose.onNodeWithTag("schedule-hour").performTextReplacement("18")
        compose.onNodeWithTag("schedule-minute").performTextReplacement("20")
        compose.onNodeWithTag("schedule-day-2").performClick()
        compose.onNodeWithText("保存修改").performClick()
        compose.waitUntil(5_000) { store.load()?.hour == 18 }
        val edited = store.load()!!
        assertEquals(setOf(4), edited.days)
        assertFalse(edited.enabled); assertNull(edited.protection)
        assertEquals(source.scheduleId, edited.scheduleId)
        assertEquals(source.points, edited.points)
        assertEquals(draft, ClickSequenceStore(context).load())
    }

    @Test fun pauseAndContinuePreserveTheNewRecordingAndOriginalTask() {
        begin(); recordOne()
        val before = ClickSequenceStore(context).loadSnapshot()
        compose.onNodeWithText("暂停恢复").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.session?.phase == LegacyRecoveryPhase.PAUSED && !model.busy }
        assertEquals(before, ClickSequenceStore(context).loadSnapshot())
        assertEquals(source, store.load())
        compose.onNodeWithText("继续恢复").performScrollTo().performClick()
        compose.onNodeWithText("继续录制").performClick()
        compose.waitUntil(5_000) { model.session?.phase == LegacyRecoveryPhase.RECORDING && !model.busy }
        assertEquals(before, ClickSequenceStore(context).loadSnapshot())
    }

    @Test fun rotatingPanelPreservesTimeDraftAndLargeFontCanReachSave() {
        begin(); recordOne()
        tap(overlay(), long = true)
        compose.waitUntil(5_000) { ViewModelMain.isShowCustomFloatWindow.value == true }
        instrumentation.runOnMainSync { panel().findViewById<android.widget.TimePicker>(R.id.timePicker).hour = 16 }
        compose.waitUntil(5_000) { model.session?.hour == 16 }
        scenario!!.recreate()
        scenario!!.onActivity { it.onPointLongClick() }
        instrumentation.runOnMainSync { assertEquals(16, panel().findViewById<android.widget.TimePicker>(R.id.timePicker).hour) }
        capture("legacy-panel")
        shell("settings --user 0 put system font_scale 1.5")
        scenario!!.recreate()
        scenario!!.onActivity { it.onPointLongClick() }
        instrumentation.runOnMainSync { (panel() as ScrollView).fullScroll(View.FOCUS_DOWN) }
        capture("legacy-large-font")
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(5_000) { context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        scenario!!.onActivity { it.onPointLongClick() }
        instrumentation.runOnMainSync {
            (panel() as ScrollView).fullScroll(View.FOCUS_DOWN)
            assertTrue(panel().findViewById<View>(R.id.confirm).isEnabled)
        }
        capture("legacy-landscape")
        assertEquals(source, store.load())
    }

    @Test fun duplicateSaveAndRotationKeepOneConfirmedReplacement() {
        begin(); recordOne()
        val selected = candidate()
        val recordedId = model.session!!.recoveryId
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val slow = ClickTaskController(store, AndroidClickAlarmPlatform(context), {}, canEdit = {
            entered.countDown(); withContext(Dispatchers.IO) { check(release.await(10, TimeUnit.SECONDS)) }; true
        })
        val field = LegacyTaskRecoveryViewModel::class.java.getDeclaredField("controller").apply { isAccessible = true }
        field.set(model, slow)
        instrumentation.runOnMainSync { model.save(selected, recordedId); model.save(selected, recordedId) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(source, store.load())
            scenario!!.recreate()
            instrumentation.runOnMainSync { assertFalse("Saving must not expose the recording overlay after rotation", ViewModelMain.isShowFloatWindow.value == true) }
            scenario!!.onActivity { assertSame(model, ViewModelProvider(it)[LegacyTaskRecoveryViewModel::class.java]); it.onPointLongClick() }
            instrumentation.runOnMainSync { assertFalse(panel().findViewById<View>(R.id.confirm).isEnabled) }
        } finally { release.countDown() }
        compose.waitUntil(5_000) { !model.saving }
        assertEquals(selected, store.load())
        assertTrue(store.wasRecovered(recordedId, selected.scheduleId))
        assertNull(store.alarmState()!!.next)
    }

    @Test fun deniedOverlayPermissionDoesNotClearOriginalOrDraft() {
        val draft = ClickSequenceStore(context).loadSnapshot()
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW deny")
        compose.onNodeWithText("重新录制").performScrollTo().performClick()
        compose.onNodeWithText("开始重新录制").performClick()
        compose.waitUntil(5_000) { automation.rootInActiveWindow?.packageName?.toString() == "com.android.settings" }
        assertEquals(source, store.load())
        assertEquals(draft, ClickSequenceStore(context).loadSnapshot())
        assertNull(LegacyTaskRecoveryStore(context).load())
        shell("input keyevent KEYCODE_BACK")
    }

    @Test fun failedSaveRetainsOriginalAndDraftForAConfirmedRetry() {
        begin(); recordOne()
        val draft = ClickSequenceStore(context).loadSnapshot()
        val selected = candidate()
        val journal = MemoryRecoveryJournal().apply { failWrite = true }
        val prefs = context.getSharedPreferences("autoclick_task", android.content.Context.MODE_PRIVATE)
        val failed = ClickTaskController(ClickTaskStore(RecoverableTaskPreferences(prefs, journal)), AndroidClickAlarmPlatform(context), {})
        val field = LegacyTaskRecoveryViewModel::class.java.getDeclaredField("controller").apply { isAccessible = true }
        field.set(model, failed)
        instrumentation.runOnMainSync { model.save(selected, draft.sessionId) }
        compose.waitUntil(5_000) { !model.saving && model.error != null }
        assertEquals(source, store.load()); assertEquals(draft, ClickSequenceStore(context).loadSnapshot())
        assertEquals(LegacyRecoveryPhase.PAUSED, LegacyTaskRecoveryStore(context).load()!!.phase)
        field.set(model, ClickTaskController(context))
        instrumentation.runOnMainSync { model.resume(source) }
        compose.waitUntil(5_000) { !model.busy && model.session!!.phase == LegacyRecoveryPhase.RECORDING }
        instrumentation.runOnMainSync { model.save(selected, draft.sessionId) }
        compose.waitUntil(5_000) { !model.saving && model.session == null }
        assertEquals(selected, store.load()); assertFalse(store.load()!!.enabled)
    }

    @Test fun changedSourcePausesRecoveryWithoutRebindingOrClearingDraft() {
        begin(); recordOne()
        val draft = ClickSequenceStore(context).loadSnapshot()
        val changed = source.copy(id = "external-change", hour = 15)
        assertTrue(store.save(changed))
        compose.waitUntil(5_000) { model.session?.phase == LegacyRecoveryPhase.PAUSED && model.error != null }
        assertEquals(source.id, model.session!!.sourceTaskId)
        assertEquals(draft, ClickSequenceStore(context).loadSnapshot())
        instrumentation.runOnMainSync { model.save(candidate(), draft.sessionId) }
        compose.waitUntil(5_000) { !model.saving && model.error != null }
        assertEquals(changed, store.load()); assertEquals(draft, ClickSequenceStore(context).loadSnapshot())
    }

    @Test fun unconfirmedStorageKeepsLastKnownTaskAndBlocksOperations() {
        val file = File(context.filesDir, "legacy-task-recovery-commit.json")
        file.writeText("unconfirmed")
        try {
            compose.waitUntil(5_000) { compose.onAllNodesWithText("保存状态未确认").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("09:30").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("修改时间").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("删除任务").assertIsNotEnabled()
            compose.onNodeWithText("还没有定时任务").assertDoesNotExist()
        } finally { file.delete() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("保存状态未确认").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("修改时间").assertIsEnabled()
        assertEquals(source, store.load())
    }

    @Test fun savingPanelUpdatesWhileTargetAppIsInForeground() {
        begin()
        instrumentation.context.startActivity(Intent(instrumentation.context, RecoveryTargetActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("reset", true))
        compose.waitUntil(5_000) { AccessibilityCoreService.accessibilityCoreService!!.currentClickEnvironment()?.packageName == instrumentation.context.packageName }
        recordOne(); tap(overlay(), long = true)
        compose.waitUntil(5_000) { ViewModelMain.isShowCustomFloatWindow.value == true }
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val slow = ClickTaskController(store, AndroidClickAlarmPlatform(context), {}, canEdit = {
            entered.countDown(); withContext(Dispatchers.IO) { check(release.await(10, TimeUnit.SECONDS)) }; true
        })
        LegacyTaskRecoveryViewModel::class.java.getDeclaredField("controller").apply { isAccessible = true }.set(model, slow)
        instrumentation.runOnMainSync { (panel() as ScrollView).fullScroll(View.FOCUS_DOWN) }
        tap(panel().findViewById(R.id.confirm))
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                assertFalse("Background panel must reflect the active save", panel().findViewById<View>(R.id.confirm).isEnabled)
                assertFalse(panel().findViewById<View>(R.id.cancel).isEnabled)
            }
            assertEquals(source, store.load())
        } finally { release.countDown() }
        compose.waitUntil(5_000) { !model.saving }
        assertFalse(store.load()!!.enabled)
        assertNotEquals(source.id, store.load()!!.id)
    }

    @Test fun recordedRecoverySavesDisabledRejectsOldAlarmsAndTrialClicksOnce() {
        begin()
        val received = AtomicInteger()
        scenario!!.onActivity { activity -> activity.setContentView(Button(activity).apply {
            text = "旧版恢复验收：等待试运行"
            setOnClickListener { text = "旧版恢复验收：已点击 ${received.incrementAndGet()} 次" }
        }) }
        recordOne()
        tap(overlay(), long = true)
        compose.waitUntil(5_000) { ViewModelMain.isShowCustomFloatWindow.value == true }
        instrumentation.runOnMainSync { (panel() as ScrollView).fullScroll(View.FOCUS_DOWN) }
        tap(panel().findViewById(R.id.confirm))
        compose.waitUntil(5_000) { store.load()?.protection != null && !model.saving }
        val saved = store.load()!!
        assertFalse(saved.enabled)
        assertNotEquals(source.scheduleId, saved.scheduleId)
        assertNull(store.alarmState()!!.next)
        val oldEvent = ClickAlarmOccurrence(source.id, source.scheduleId, System.currentTimeMillis())
        repeat(2) { context.sendBroadcast(AndroidClickAlarmPlatform(context).eventIntent(oldEvent)) }
        Thread.sleep(1500)
        assertEquals(0, received.get()); assertNull(store.lastExecutionResult())
        instrumentation.runOnMainSync { assertTrue(ClickExecutionSession.startTrial(context, saved)) }
        compose.waitUntil(20_000) { !ClickExecutionSession.state.value.active && received.get() == 1 }
        assertEquals(1, received.get()); assertFalse(store.load()!!.enabled)
        assertNull(store.consumedAt(saved.id)); assertNull(store.alarmState()!!.next)
        capture("legacy-trial-result")
        android.util.Log.i("AutoclickQuickCheck", "legacy recoveredDisabled=true oldDeliveries=2 automaticClicks=0 realTrialClicks=${received.get()}")
        scenario!!.recreate()
        compose.onNodeWithText("启用任务").performScrollTo().performClick()
        compose.waitUntil(5_000) { store.load()?.enabled == true && store.alarmState()?.next != null }
        assertTrue(store.alarmState()!!.next!!.scheduledAt > System.currentTimeMillis())
        assertEquals(saved.scheduleId, store.load()!!.scheduleId)
    }
}
