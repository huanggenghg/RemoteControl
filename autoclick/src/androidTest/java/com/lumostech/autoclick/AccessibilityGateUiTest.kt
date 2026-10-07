package com.lumostech.autoclick

import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.lumostech.accessibilitycore.AccessibilityCoreService
import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickSequenceStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AccessibilityGateUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var automation: UiAutomation
    private var originalServices = "null"
    private var originalEnabled = "0"
    private val component = "${context.packageName}/${AccessibilityCoreService::class.java.name}"

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    @Before fun prepare() {
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        originalServices = shell("settings --user 0 get secure enabled_accessibility_services")
        originalEnabled = shell("settings --user 0 get secure accessibility_enabled")
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        WorkManager.getInstance(context).cancelUniqueWork(ClickPeriodicWorker.TAG).result.get(10, TimeUnit.SECONDS)
        ClickTaskStore(context).clear()
        disableService()
    }

    @After fun restore() {
        if (originalServices == "null" || originalServices.isBlank()) shell("settings --user 0 delete secure enabled_accessibility_services")
        else shell("settings --user 0 put secure enabled_accessibility_services $originalServices")
        shell("settings --user 0 put secure accessibility_enabled ${if (originalEnabled == "1") "1" else "0"}")
    }

    private fun disableService() {
        val others = shell("settings --user 0 get secure enabled_accessibility_services")
            .split(':').filter { it != component && it != "null" && it.isNotBlank() }.joinToString(":")
        if (others.isBlank()) shell("settings --user 0 delete secure enabled_accessibility_services")
        else shell("settings --user 0 put secure enabled_accessibility_services $others")
        compose.waitUntil(5_000) { ClickServiceConnection.readiness(context) == ClickServiceReadiness.DISABLED }
    }

    private fun enableService() {
        val others = shell("settings --user 0 get secure enabled_accessibility_services")
            .split(':').filter { it != "null" && it.isNotBlank() }
        shell("settings --user 0 put secure enabled_accessibility_services ${(others + component).distinct().joinToString(":")}")
        shell("settings --user 0 put secure accessibility_enabled 1")
        compose.waitUntil(10_000) { AccessibilityCoreService.accessibilityCoreService != null }
    }

    private fun waitFor(text: String, timeout: Long = 5_000) {
        compose.waitUntil(timeout) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("uiScreenshots") == "true")
            shell("screencap -p /sdcard/autoclick-$name.png")
    }

    @Test fun disabledServiceAutomaticallyBlocksHome() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("请先开启 AutoClick 无障碍服务")
            compose.onNodeWithText("开始录制").assertDoesNotExist()
            capture("gate-enable")
            shell("input tap 20 200")
            waitFor("请先开启 AutoClick 无障碍服务")
        }
    }

    @Test fun exitKeepsSavedTaskAndDraft() {
        val store = ClickTaskStore(context)
        val task = ClickTask("gate-exit", 9, 0, (1..7).toSet(), listOf(ClickCounterPoint(300f, 300f, 0)), enabled = false)
        assertTrue(store.save(task))
        ClickSequenceStore(context).save(task.points)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor("请先开启 AutoClick 无障碍服务")
            compose.onNodeWithText("退出应用").performClick()
            compose.waitUntil(3_000) { scenario.state == Lifecycle.State.DESTROYED }
        }
        assertEquals(task, store.load())
        assertEquals(task.points, ClickSequenceStore(context).load())
    }

    @Test fun backExitsInsteadOfBypassingGate() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor("请先开启 AutoClick 无障碍服务")
            shell("input keyevent KEYCODE_BACK")
            compose.waitUntil(3_000) { scenario.state == Lifecycle.State.DESTROYED }
        }
    }

    @Test fun settingsReturnRechecksAndOpensHomeOnlyAfterConnection() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("请先开启 AutoClick 无障碍服务")
            compose.onNodeWithText("去开启").performClick()
            compose.waitUntil(5_000) { automation.rootInActiveWindow?.packageName?.toString() == "com.android.settings" }
            enableService()
            shell("input keyevent KEYCODE_BACK")
            waitFor("开始录制", 10_000)
            compose.onNodeWithText("退出应用").assertDoesNotExist()
        }
    }

    @Test fun reconnectDeadlineSurvivesActivityRecreationAndRecovery() {
        enableService()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor("开始录制")
            val service = AccessibilityCoreService.accessibilityCoreService!!
            try {
                instrumentation.runOnMainSync { AccessibilityCoreService.accessibilityCoreService = null }
                waitFor("正在连接服务")
                capture("gate-connecting")
                Thread.sleep(1_500)
                scenario.recreate()
                waitFor("无障碍服务未连接", 10_000)
                compose.onNodeWithText("开始录制").assertDoesNotExist()
                capture("gate-reconnect")
            } finally {
                instrumentation.runOnMainSync { AccessibilityCoreService.accessibilityCoreService = service }
            }
            waitFor("开始录制")
            compose.onNodeWithText("去设置").assertDoesNotExist()
        }
    }

    @Test fun foregroundGrantRemovalBlocksAlreadyOpenHome() {
        enableService()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("开始录制")
            disableService()
            waitFor("请先开启 AutoClick 无障碍服务")
            compose.onNodeWithText("开始录制").assertDoesNotExist()
        }
    }
}
