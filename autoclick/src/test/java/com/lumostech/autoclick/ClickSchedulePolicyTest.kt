package com.lumostech.autoclick

import com.lumostech.accessibilitycore.ClickCounterPoint
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ClickSchedulePolicyTest {
    private val zone = "Asia/Shanghai"
    private val task = ClickTask("schedule", 10, 30, setOf(Calendar.MONDAY), listOf(ClickCounterPoint(1f, 2f, 0)), timeZoneId = zone)

    private fun time(day: Int = 5, hour: Int = 10, minute: Int = 30, second: Int = 0): Long =
        Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
            set(2026, Calendar.OCTOBER, day, hour, minute, second)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun permitsOnlyTheInclusiveFifteenMinuteStartWindow() {
        assertEquals(ClickScheduleStatus.EARLY, ClickSchedulePolicy.evaluate(task, time(minute = 29), zone).status)
        assertEquals(ClickScheduleStatus.READY, ClickSchedulePolicy.evaluate(task, time(), zone).status)
        assertEquals(ClickScheduleStatus.READY, ClickSchedulePolicy.evaluate(task, time(minute = 45), zone).status)
        assertEquals(ClickScheduleStatus.LATE, ClickSchedulePolicy.evaluate(task, time(minute = 45, second = 1), zone).status)
    }

    @Test
    fun rejectsNonTargetDaysAndTimeZoneChanges() {
        assertEquals(ClickScheduleStatus.WRONG_DAY, ClickSchedulePolicy.evaluate(task, time(day = 6), zone).status)
        assertEquals(ClickScheduleStatus.TIME_ZONE_CHANGED, ClickSchedulePolicy.evaluate(task, time(), "UTC").status)
    }

    @Test
    fun midnightDoesNotCatchUpThePreviousDay() {
        val lateTask = task.copy(hour = 23, minute = 59, days = (1..7).toSet())
        assertEquals(ClickScheduleStatus.READY, ClickSchedulePolicy.evaluate(lateTask, time(hour = 23, minute = 59), zone).status)
        assertEquals(ClickScheduleStatus.EARLY, ClickSchedulePolicy.evaluate(lateTask, time(day = 6, hour = 0, minute = 1), zone).status)
    }

    @Test
    fun startedSequenceCanFinishAfterStartWindowButCannotCrossDateOrClockRollback() {
        val due = time()
        assertTrue(ClickSchedulePolicy.isSameOccurrence(task, due, time(minute = 52), zone))
        assertFalse(ClickSchedulePolicy.isSameOccurrence(task, due, time(minute = 29), zone))
        assertFalse(ClickSchedulePolicy.isSameOccurrence(task, due, time(day = 6), zone))
        assertFalse(ClickSchedulePolicy.isSameOccurrence(task, due, time(), "UTC"))
    }

    @Test
    fun detectsClockCorrectionsEvenWhenWallTimeStillIncreasesBetweenClicks() {
        assertTrue(ClickSchedulePolicy.clockUnchanged(100_000, 10_000, 101_000, 11_000))
        assertFalse(ClickSchedulePolicy.clockUnchanged(100_000, 10_000, 200_000, 11_000))
        assertFalse(ClickSchedulePolicy.clockUnchanged(100_000, 10_000, 120_000, 40_000))
    }

    @Test
    fun nextOccurrenceRealignsAfterLongExecutionAndSkipsUnselectedDays() {
        assertEquals(time(day = 12), ClickSchedulePolicy.nextOccurrence(task, time(minute = 38)))
        assertEquals(time(day = 6), ClickSchedulePolicy.nextOccurrence(task.copy(days = (1..7).toSet()), time(minute = 38)))
        assertEquals(time(), ClickSchedulePolicy.nextOccurrence(task, time(minute = 29)))
        assertEquals(time(day = 12), ClickSchedulePolicy.nextOccurrence(task, time()))
    }

    @Test
    fun daylightSavingGapDoesNotCarryNormalizedHourIntoTomorrow() {
        val dstZone = TimeZone.getTimeZone("America/New_York")
        val dstTask = task.copy(hour = 2, minute = 30, days = (1..7).toSet(), timeZoneId = dstZone.id)
        val now = Calendar.getInstance(dstZone).apply {
            set(2026, Calendar.MARCH, 8, 3, 31, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val expected = Calendar.getInstance(dstZone).apply {
            set(2026, Calendar.MARCH, 9, 2, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, ClickSchedulePolicy.nextOccurrence(dstTask, now.timeInMillis))
    }
}
