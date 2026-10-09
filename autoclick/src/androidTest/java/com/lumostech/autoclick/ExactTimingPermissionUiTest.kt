package com.lumostech.autoclick

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickRecordingProtection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Calendar

class ExactTimingPermissionUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val f = ExactTimingFixture()
    private val phase = InstrumentationRegistry.getArguments().getString("permissionPhase")
    @Before fun setup() { f.setup(clearTask = phase == null || phase == "seed") }
    @After fun cleanup() { if (phase == null) f.cleanup() }
    private fun task(): ClickTask {
        val date = Calendar.getInstance().apply { add(Calendar.MINUTE, 2) }
        return ClickTask("permission-fixture", date.get(Calendar.HOUR_OF_DAY), date.get(Calendar.MINUTE), (1..7).toSet(),
            listOf(ClickCounterPoint(300f, 300f, 0)),
            protection = ClickRecordingProtection(1080, 2424, 0, listOf(f.context.packageName)))
    }
    private fun waitText(text: String) {
        compose.waitUntil(5000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun editWithoutEnabling(screenshot: String) {
        val before = f.store.load()!!
        compose.onNodeWithText("修改时间").performScrollTo().performClick()
        compose.onNodeWithTag("schedule-minute").performTextReplacement(((before.minute + 1) % 60).toString())
        compose.onNodeWithText("保存修改").performScrollTo().performClick()
        compose.waitUntil(5_000) { f.store.load()?.id != before.id }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("修改任务时间").fetchSemanticsNodes().isEmpty() }
        assertFalse(f.store.load()!!.enabled)
        assertNull(f.store.alarmState()!!.next)
        assertEquals(before.points, f.store.load()!!.points)
        f.shell("screencap -p /sdcard/autoclick-$screenshot.png")
    }
    @Test fun grantStillNeedsExplicitEnableAndKeepsTrialAvailable() {
        org.junit.Assume.assumeTrue(phase == null)
        val task = task().copy(enabled = false)
        f.store.save(task)
        f.store.saveAlarmState(task.id, ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.NEEDS_ENABLE))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitText("请启用定时任务")
            compose.onNodeWithText("5 秒后试运行").performScrollTo().assertIsEnabled()
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            assertFalse(f.store.load()!!.enabled); assertNull(f.store.alarmState()!!.next)
            compose.onNode(hasText("开始录制") or hasText("继续录制")).performScrollTo().assertIsEnabled()
        }
    }
    @Test fun hostDrivenPermissionRoundtrip() {
        org.junit.Assume.assumeTrue(phase != null)
        when (phase) {
            "seed" -> runBlocking {
                assertTrue(AndroidClickAlarmPlatform(f.context).canSchedule())
                assertEquals(ClickTaskSaveResult.ENABLED, ClickTaskController(f.context).save(task()))
                assertNotNull(f.store.alarmState()!!.next)
            }
            "lost" -> {
                assertFalse(AndroidClickAlarmPlatform(f.context).canSchedule())
                ActivityScenario.launch(MainActivity::class.java).use {
                    waitText("定时权限未开启")
                    compose.onNodeWithText("启用任务").performScrollTo().performClick()
                    waitText("开启定时权限")
                    compose.onNodeWithText("暂不").performClick()
                    compose.onNodeWithText("5 秒后试运行").performScrollTo().assertIsEnabled()
                    assertFalse(f.store.load()!!.enabled); assertNull(f.store.alarmState()!!.next)
                    editWithoutEnabling("edit-permission")
                }
            }
            "granted" -> {
                assertTrue(AndroidClickAlarmPlatform(f.context).canSchedule())
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    waitText("请启用")
                    scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                    scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                    assertFalse(f.store.load()!!.enabled); assertNull(f.store.alarmState()!!.next)
                    compose.onNodeWithText("5 秒后试运行").performScrollTo().assertIsEnabled()
                    editWithoutEnabling("edit-regranted")
                    f.shell("screencap -p /sdcard/autoclick-task-status.png")
                }
            }
            else -> error("Unknown permission phase")
        }
    }
}
