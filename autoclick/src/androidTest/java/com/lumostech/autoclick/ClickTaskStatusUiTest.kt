package com.lumostech.autoclick

import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.lumostech.accessibilitycore.AccessibilityCoreService
import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickRecordingProtection
import com.lumostech.accessibilitycore.ClickSequenceStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ClickTaskStatusUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = ClickTaskStore(context)
    private lateinit var automation: UiAutomation
    private var originalServices = "null"
    private var originalEnabled = "0"
    private lateinit var task: ClickTask
    private var due = 0L

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    @Before fun prepare() {
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        originalServices = shell("settings --user 0 get secure enabled_accessibility_services")
        originalEnabled = shell("settings --user 0 get secure accessibility_enabled")
        val component = "${context.packageName}/${AccessibilityCoreService::class.java.name}"
        val services = originalServices.split(':').filter { it != "null" && it.isNotBlank() }
        shell("settings --user 0 put secure enabled_accessibility_services ${(services + component).distinct().joinToString(":")}")
        shell("settings --user 0 put secure accessibility_enabled 1")
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        compose.waitUntil(10_000) { AccessibilityCoreService.accessibilityCoreService != null }
        runBlocking { ClickTaskController(context).delete() }
        val target = Calendar.getInstance().apply { add(Calendar.MINUTE, 2); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        due = target.timeInMillis
        task = ClickTask("status-ui", target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE), (1..7).toSet(),
            listOf(ClickCounterPoint(300f, 300f, 0)),
            protection = ClickRecordingProtection(1080, 2424, 0, listOf(context.packageName)))
        runBlocking { ClickTaskController(context).save(task) }
    }

    @After fun restore() {
        WorkManager.getInstance(context).cancelUniqueWork(ClickPeriodicWorker.TAG).result.get(10, TimeUnit.SECONDS)
        runBlocking { ClickTaskController(context).delete() }
        if (originalServices == "null" || originalServices.isBlank()) shell("settings --user 0 delete secure enabled_accessibility_services")
        else shell("settings --user 0 put secure enabled_accessibility_services $originalServices")
        shell("settings --user 0 put secure accessibility_enabled ${if (originalEnabled == "1") "1" else "0"}")
    }

    private fun waitFor(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("uiScreenshots") == "true")
            shell("screencap -p /sdcard/autoclick-$name.png")
    }

    @Test fun largeFontAndLandscapeKeepTaskActionsReachable() {
        val font = shell("settings --user 0 get system font_scale")
        val rotation = shell("settings --user 0 get system user_rotation")
        val automatic = shell("settings --user 0 get system accelerometer_rotation")
        try {
            shell("settings --user 0 put system font_scale 1.5")
            for (rotation in listOf(UiAutomation.ROTATION_FREEZE_0, UiAutomation.ROTATION_FREEZE_90)) {
                automation.setRotation(rotation)
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    compose.waitUntil(5_000) {
                        var ready = false
                        scenario.onActivity { ready = it.resources.configuration.fontScale >= 1.49f &&
                            (rotation != UiAutomation.ROTATION_FREEZE_90 ||
                                it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) }
                        ready
                    }
                    waitFor("当前状态")
                    for (text in listOf("当前状态", "最近结果", "停用任务"))
                        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
                    capture(if (rotation == UiAutomation.ROTATION_FREEZE_0) "task-large-font" else "task-landscape")
                }
            }
        } finally {
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            for ((key, value) in listOf("user_rotation" to rotation, "accelerometer_rotation" to automatic)) {
                if (value == "null") shell("settings --user 0 delete system $key")
                else shell("settings --user 0 put system $key $value")
            }
            if (font == "null") shell("settings --user 0 delete system font_scale")
            else shell("settings --user 0 put system font_scale $font")
        }
    }

    @Test fun nextPlanAndHistoricalFailureAreSeparate() {
        assertTrue(store.recordOccurrenceOutcome(task.id, due - 86_400_000, false,
            "上次连接超时，未执行", ClickOutcomeReason.CONNECTION_TIMEOUT))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("当前状态")
            compose.onNodeWithText("当前状态").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("最近结果").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("上次连接超时，未执行", substring = true).performScrollTo().assertIsDisplayed()
            capture("task-status")
        }
    }

    @Test fun recoveryExplanationDoesNotClearTaskOrDraft() {
        val drafts = ClickSequenceStore(context)
        drafts.save(task.points, task.protection)
        assertTrue(store.recordOccurrenceOutcome(task.id, due - 86_400_000, false,
            "屏幕尺寸或方向已改变", ClickOutcomeReason.DISPLAY_CHANGED))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("重新录制说明")
            compose.onNodeWithText("重新录制说明").performScrollTo().performClick()
            compose.onNodeWithText("查看现有录制入口", substring = true).performScrollTo().assertIsDisplayed()
            capture("recovery-help")
            assertEquals(task, store.load())
            assertEquals(task.points, drafts.load())
            compose.onNodeWithText("收起说明").performScrollTo().performClick()
        }
    }

    @Test fun disabledScheduleDoesNotShowWaitingTime() {
        runBlocking { ClickTaskController(context).setEnabled(false) }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("当前状态")
            compose.onNodeWithText("当前状态").performScrollTo()
            assertTrue(compose.onAllNodesWithText("任务已停用").fetchSemanticsNodes().isNotEmpty())
            assertTrue(compose.onAllNodesWithText("下次：", substring = true).fetchSemanticsNodes().isEmpty())
        }
    }

    @Test fun returningToForegroundRefreshesStatusAndRetainsHistory() {
        assertTrue(store.recordOccurrenceOutcome(task.id, due - 86_400_000, false,
            "之前页面不符", ClickOutcomeReason.APP_CHANGED))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor("当前状态")
            scenario.moveToState(Lifecycle.State.CREATED)
            runBlocking { ClickTaskController(context).setEnabled(false) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitFor("任务已停用")
            compose.onNodeWithText("之前页面不符", substring = true).performScrollTo().assertIsDisplayed()
            assertEquals(ClickOutcomeReason.APP_CHANGED, store.lastExecutionRecord()?.reason)
        }
    }

    @Test fun claimedProgressIsNotDisplayedAsRecentResult() {
        assertEquals(ClickExecutionClaim.CLAIMED, store.claimExecution(task.id, due))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("最近结果")
            compose.onNodeWithText("尚无执行结果").performScrollTo().assertIsDisplayed()
            assertTrue(compose.onAllNodesWithText("正在执行点击").fetchSemanticsNodes().isEmpty())
        }
    }

    @Test fun unknownHistoryDoesNotOfferGuessedRecovery() {
        assertTrue(store.recordOccurrenceOutcome(task.id, due - 86_400_000, false, "未知原因", ClickOutcomeReason.UNKNOWN))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("最近结果")
            compose.onNodeWithText("最近结果").performScrollTo()
            assertTrue(compose.onAllNodesWithText("重新录制说明").fetchSemanticsNodes().isEmpty())
            assertTrue(compose.onAllNodesWithText("执行前准备").fetchSemanticsNodes().isEmpty())
        }
    }
}
