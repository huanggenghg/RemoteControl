package com.lumostech.autoclick

import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickRecordingProtection
import org.junit.Assert.*
import org.junit.Test

class ClickTaskActionsTest {
    private val legacy = ClickTask("old", 9, 0, setOf(2), listOf(ClickCounterPoint(20f, 30f, 0)), enabled = false)
    private fun action(task: ClickTask = legacy, action: ClickTaskAction = ClickTaskAction.EDIT_TIME,
                       saving: Boolean = false, executing: Boolean = false, connected: Boolean = true,
                       recording: Boolean = false) = taskActionState(task, action, saving, executing, connected, recording)
    @Test fun legacyTaskCanEditAndRecoverButCannotExecute() {
        assertTrue(action().enabled)
        assertTrue(action(action = ClickTaskAction.RECOVER).enabled)
        assertFalse(action(action = ClickTaskAction.ENABLE).enabled)
        assertFalse(action(action = ClickTaskAction.TRIAL).enabled)
    }
    @Test fun protectedDisabledTaskKeepsOrdinaryActions() {
        val task = legacy.copy(protection = ClickRecordingProtection(100, 100, 0, listOf("target")))
        for (value in listOf(ClickTaskAction.EDIT_TIME, ClickTaskAction.ENABLE, ClickTaskAction.TRIAL))
            assertTrue(action(task, value).enabled)
    }
    @Test fun temporaryBlocksExplainActualReason() {
        assertEquals("正在保存，请稍候", action(saving = true).reason)
        assertEquals("任务正在准备或执行，请结束后再操作", action(executing = true).reason)
        assertEquals("无障碍服务未连接，请恢复后再操作", action(connected = false).reason)
        assertEquals("正在重新录制，请先保存或取消恢复", action(recording = true).reason)
    }
    @Test fun recordingCanResumeButCannotReplaceSource() {
        assertTrue(action(action = ClickTaskAction.RECOVER, recording = true).enabled)
        assertFalse(action(action = ClickTaskAction.DELETE, recording = true).enabled)
    }
}
