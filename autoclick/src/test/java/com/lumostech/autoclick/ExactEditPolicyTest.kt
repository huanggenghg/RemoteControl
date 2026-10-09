package com.lumostech.autoclick

import com.lumostech.accessibilitycore.ClickCounterPoint
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ExactEditPolicyTest {
    private val zone = "Asia/Shanghai"
    private fun at(day: Int, hour: Int) = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
        set(2026, Calendar.OCTOBER, day, hour, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val task = ClickTask("edit", 16, 0, (1..7).toSet(), listOf(ClickCounterPoint(10f, 10f, 0)), timeZoneId = zone)
    @Test fun consumedDateCannotReplayAfterEditingLater() {
        assertEquals(at(9, 16), ExactClickPolicy.next(task, at(8, 11), at(8, 9), at(8, 9)))
        assertEquals(at(9, 16), ExactClickPolicy.next(task, at(7, 11), 0, at(8, 9)))
    }
    @Test fun skippedEventDoesNotConsumeWholeDateAndEqualTimeIsNotFuture() {
        assertEquals(at(8, 16), ExactClickPolicy.next(task, at(8, 11), at(8, 9), null))
        assertEquals(at(9, 16), ExactClickPolicy.next(task, at(8, 16), 0, null))
    }
    @Test fun disabledEditDoesNotRequirePermissionOrEraseFailureStatus() {
        for (status in listOf(ClickAlarmStatus.NEEDS_ENABLE, ClickAlarmStatus.PERMISSION_REQUIRED, ClickAlarmStatus.SCHEDULE_FAILED)) {
            val decision = ClickScheduleEditPolicy.decide(task.copy(enabled = false),
                ClickAlarmState(task.id, task.scheduleId, status), false, zone)
            assertEquals(ClickScheduleEditResult.DISABLED, decision.result)
            assertFalse(decision.enabled)
            assertEquals(status, decision.status)
        }
    }
    @Test fun permissionAndTimeZoneDecisionsHaveExplicitResults() {
        assertEquals(ClickScheduleEditResult.ENABLED, ClickScheduleEditPolicy.decide(task, null, true, zone).result)
        val denied = ClickScheduleEditPolicy.decide(task, null, false, zone)
        assertFalse(denied.enabled)
        assertEquals(ClickScheduleEditResult.PERMISSION_REQUIRED, denied.result)
        assertEquals(ClickAlarmStatus.PERMISSION_REQUIRED, denied.status)
        for (currentZone in listOf(zone, "UTC")) {
            val changed = ClickScheduleEditPolicy.decide(task,
                ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.TIME_ZONE_CHANGED), true, currentZone)
            assertFalse(changed.enabled)
            assertEquals(ClickScheduleEditResult.TIME_ZONE_CHANGED, changed.result)
        }
    }
}
