package com.lumostech.autoclick

import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.lumostech.accessibilitycore.AccessibilityCoreService
import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickEnvironment
import com.lumostech.accessibilitycore.ClickRecordingProtection
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ClickTrialUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var automation: UiAutomation
    private var originalServices = "null"
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
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        compose.waitUntil(10_000) { AccessibilityCoreService.accessibilityCoreService != null }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun cleanup() {
        if (ClickExecutionSession.state.value.active) {
            compose.runOnIdle { ClickExecutionSession.emergencyStop(context) }
            compose.waitUntil(5_000) { !ClickExecutionSession.state.value.active }
        }
        WorkManager.getInstance(context).cancelUniqueWork(ClickPeriodicWorker.TAG).result.get(10, TimeUnit.SECONDS)
        ClickTaskStore(context).clear()
        scenario?.close()
        if (originalServices == "null" || originalServices.isBlank()) shell("settings --user 0 delete secure enabled_accessibility_services")
        else shell("settings --user 0 put secure enabled_accessibility_services $originalServices")
        shell("settings --user 0 put secure accessibility_enabled ${if (originalEnabled == "1") "1" else "0"}")
    }

    private fun captureUi(name: String) {
        if (InstrumentationRegistry.getArguments().getString("uiScreenshots") == "true") shell("screencap -p /sdcard/autoclick-$name.png")
    }

    @Test
    fun homeShowsConnectionStateAndSettingsEntry() {
        val service = AccessibilityCoreService.accessibilityCoreService!!
        compose.waitUntil(5_000) { compose.onAllNodesWithText("无障碍已连接").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("无障碍已连接").assertIsDisplayed()
        try {
            compose.runOnIdle { AccessibilityCoreService.accessibilityCoreService = null }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("正在连接服务").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("正在连接服务").assertIsDisplayed()
        } finally {
            compose.runOnIdle { AccessibilityCoreService.accessibilityCoreService = service }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("无障碍已连接").fetchSemanticsNodes().isNotEmpty() }
        captureUi("service-status")
        compose.onNodeWithText("无障碍设置").performClick()
        compose.waitUntil(5_000) { automation.rootInActiveWindow?.packageName?.toString() == "com.android.settings" }
    }

    @Test
    fun homeTrialConfirmationStartsRealProtectedTrial() {
        var environment: ClickEnvironment? = null
        compose.waitUntil(10_000) {
            compose.runOnIdle {
                AccessibilityCoreService.accessibilityCoreService!!.enableProtectedRecording()
                environment = AccessibilityCoreService.accessibilityCoreService!!.currentClickEnvironment()
            }
            environment?.packageName == context.packageName
        }
        val env = environment!!
        val now = Calendar.getInstance()
        // The screen's empty left margin sends a real gesture without activating
        // its own task controls. Business action success is not inferred.
        val task = ClickTask("home-trial", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), (1..7).toSet(),
            listOf(ClickCounterPoint(20f, 200f, 0)), enabled = false,
            protection = ClickRecordingProtection(env.width, env.height, env.rotation, listOf(env.packageName)))
        assertTrue(ClickTaskStore(context).save(task))
        compose.onNodeWithText("5 秒后试运行").performScrollTo()
        captureUi("trial-home")
        compose.onNodeWithText("5 秒后试运行").performClick()
        compose.waitForIdle()
        captureUi("trial-confirmation")
        compose.onNodeWithText("开始 5 秒倒计时").performClick()
        compose.waitUntil(3_000) { ClickExecutionSession.state.value.active }
        compose.waitForIdle()
        captureUi("trial-countdown")
        compose.waitUntil(20_000) { !ClickExecutionSession.state.value.active }
        assertTrue(ClickExecutionSession.state.value.lastTrialResult.contains("全部点击手势已完成"))
        assertFalse(ClickTaskStore(context).wasConsumed(task.id,
            ClickSchedulePolicy.evaluate(task, System.currentTimeMillis()).scheduledAt))
        compose.onNodeWithText("停止并停用").performScrollTo()
        captureUi("trial-result")
    }
}
