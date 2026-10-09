package com.lumostech.autoclick

import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickRecordingProtection
import org.junit.Assert.*
import org.junit.Test

class ExactTimingPresentationTest {
    private val task = ClickTask("epoch", 9, 0, (1..7).toSet(), listOf(ClickCounterPoint(200f, 200f, 0)),
        protection = ClickRecordingProtection(1080, 2400, 0, listOf("test")), timeZoneId = "UTC", scheduleId = "schedule")
    private val due = 9 * 3_600_000L
    private val event = ClickAlarmOccurrence(task.id, task.scheduleId, due)
    private val alarm = ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMED, next = event)
    private fun view(state: ClickAlarmState? = alarm, run: ClickRunState = ClickRunState(),
                     permission: Boolean = true, now: Long = due - 1000, record: ClickExecutionRecord? = null) =
        presentExact(task, state, run, record, permission, now, "UTC")
    @Test fun onlyActualArmedIdentityProvidesFuture() {
        assertEquals(due, view().nextAt)
        assertNull(view(alarm.copy(status = ClickAlarmStatus.ARMING)).nextAt)
        assertNull(view(alarm.copy(taskId = "stale")).nextAt)
        assertNull(view(state = null).nextAt)
    }
    @Test fun permissionAndMigrationNeverAdvertiseEnabledFuture() {
        assertEquals("定时权限未开启", view(permission = false).title)
        assertNull(view(permission = false).nextAt)
        assertEquals("请启用定时任务", view(alarm.copy(status = ClickAlarmStatus.NEEDS_ENABLE, next = null)).title)
    }
    @Test fun currentOwnedRunWinsOverFutureFailureButStaleOrManualRunDoesNot() {
        val failed = alarm.copy(status = ClickAlarmStatus.SCHEDULE_FAILED, next = null, active = event, phase = ClickAlarmPhase.CLAIMED)
        val run = ClickRunState(active = true, message = "定时执行 1 / 2", taskId = task.id, scheduleId = task.scheduleId)
        assertEquals(run.message, view(failed, run).title)
        assertTrue(view(failed, run).detail.contains("下次"))
        assertEquals("定时安排失败，请重新启用", view(failed, run.copy(taskId = "old")).title)
        assertEquals("定时安排失败，请重新启用", view(failed, run.copy(manual = true)).title)
    }
    @Test fun expiredUnconfirmedEventNeverInventsFutureFromConfigOrHistory() {
        val histories = listOf(
            ClickExecutionRecord(task.id, task.scheduleId, event.scheduledAt, 9000, ClickOutcomeReason.TIME_CHANGED, "clock"),
            ClickExecutionRecord(task.id, task.scheduleId, event.scheduledAt, 16000, ClickOutcomeReason.COMPLETED, "future"),
            ClickExecutionRecord(task.id, task.scheduleId, event.scheduledAt - 86400000, 9000, ClickOutcomeReason.COMPLETED, "yesterday"),
            ClickExecutionRecord(task.id, task.scheduleId, event.scheduledAt, 14000, ClickOutcomeReason.UNKNOWN, "unknown")
        )
        for (record in histories) {
            val result = view(now = due + 5000, record = record)
            assertNull(result.nextAt)
            assertTrue(result.title.contains("错过"))
        }
    }
    @Test fun preparingEndsAtExclusiveDeadlineAndHistoryStaysSeparate() {
        val preparing = alarm.copy(active = event, phase = ClickAlarmPhase.PREPARING, next = event.copy(scheduledAt = 86410000))
        assertEquals("执行准备中", view(preparing, now = due + 4999).title)
        assertTrue(view(preparing, now = due + 5000).title.contains("错过"))
    }
}
