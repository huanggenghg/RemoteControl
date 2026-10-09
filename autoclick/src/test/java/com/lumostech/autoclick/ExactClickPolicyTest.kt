package com.lumostech.autoclick

import com.lumostech.accessibilitycore.ClickCounterPoint
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ExactClickPolicyTest {
    private val zone = "Asia/Shanghai"
    private val due = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
        set(2026, Calendar.OCTOBER, 7, 9, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val task = ClickTask("epoch", 9, 0, (1..7).toSet(), listOf(ClickCounterPoint(100f, 100f, 0)), timeZoneId = zone)

    @Test fun firstDispatchWindowHasExclusiveEnd() {
        assertEquals(ClickScheduleStatus.EARLY, ExactClickPolicy.evaluate(task, due, due - 1, zone).status)
        assertEquals(ClickScheduleStatus.READY, ExactClickPolicy.evaluate(task, due, due, zone).status)
        assertEquals(ClickScheduleStatus.READY, ExactClickPolicy.evaluate(task, due, due + 4999, zone).status)
        assertEquals(ClickScheduleStatus.LATE, ExactClickPolicy.evaluate(task, due, due + 5000, zone).status)
    }
    @Test fun oldEventCannotBecomeTodaysOccurrence() {
        assertEquals(ClickScheduleStatus.LATE, ExactClickPolicy.evaluate(task, due, due + 86400000, zone).status)
        assertEquals(ClickScheduleStatus.INVALID, ExactClickPolicy.evaluate(task, due + 1, due + 1, zone).status)
        assertEquals(ClickScheduleStatus.TIME_ZONE_CHANGED, ExactClickPolicy.evaluate(task, due, due, "UTC").status)
    }
    @Test fun smallClockRollbackCannotExtendOriginalBudget() {
        assertFalse(ExactClickPolicy.startupOpen(due, due + 4000, 10000, due + 3500, 11001))
        assertTrue(ExactClickPolicy.startupOpen(due, due, 10000, due + 4999, 14999))
        assertFalse(ExactClickPolicy.startupOpen(due, due, 10000, due + 5000, 15000))
        assertFalse(ExactClickPolicy.startupOpen(due, due, 10000, due + 1000, 9000))
    }
    @Test fun futureIsStrictlyAfterBothNowAndLedger() {
        assertEquals(due + 86400000, ExactClickPolicy.next(task, due - 1000, due))
        assertEquals(due + 2 * 86400000, ExactClickPolicy.next(task, due, due + 86400000))
        assertEquals(due, ExactClickPolicy.next(task, due - 1000, 0))
    }
    @Test fun daylightSavingGapSkipsNonexistentLocalOccurrence() {
        val dst = TimeZone.getTimeZone("America/New_York")
        val saved = task.copy(hour = 2, minute = 30, timeZoneId = dst.id)
        val now = Calendar.getInstance(dst).apply { clear(); set(2026, Calendar.MARCH, 8, 1, 0, 0) }.timeInMillis
        val expected = Calendar.getInstance(dst).apply { clear(); set(2026, Calendar.MARCH, 9, 2, 30, 0) }.timeInMillis
        assertEquals(expected, ExactClickPolicy.next(saved, now, 0))
        assertEquals(ClickScheduleStatus.EARLY, ExactClickPolicy.evaluate(saved, expected, now, dst.id).status)
    }

}
