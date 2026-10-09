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
    fun permitsOnlyTheExclusiveFiveSecondStartWindow() {
        assertEquals(ClickScheduleStatus.EARLY, ClickSchedulePolicy.evaluate(task, time(minute = 29), zone).status)
        assertEquals(ClickScheduleStatus.READY, ClickSchedulePolicy.evaluate(task, time(), zone).status)
        assertEquals(ClickScheduleStatus.READY, ClickSchedulePolicy.evaluate(task, time(second = 4), zone).status)
        assertEquals(ClickScheduleStatus.LATE, ClickSchedulePolicy.evaluate(task, time(second = 5), zone).status)
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
    @Test fun movingAnExecutedTaskLaterStillSkipsTheConsumedDate() {
        val edited = task.copy(hour = 16, minute = 0, days = (1..7).toSet())
        assertEquals(time(day = 6, hour = 16, minute = 0),
            ClickSchedulePolicy.nextOccurrence(edited, time(hour = 11), time(hour = 9)))
        assertEquals(time(day = 12, hour = 16, minute = 0),
            ClickSchedulePolicy.nextOccurrence(edited.copy(days = setOf(Calendar.MONDAY)), time(hour = 11), time(hour = 9)))
    }

    @Test fun unconsumedFutureTimeCanRunTodayButEqualOrPastTimeCannot() {
        val edited = task.copy(hour = 16, minute = 0, days = (1..7).toSet())
        assertEquals(time(hour = 16, minute = 0), ClickSchedulePolicy.nextOccurrence(edited, time(hour = 11), null))
        assertEquals(time(day = 6, hour = 16, minute = 0),
            ClickSchedulePolicy.nextOccurrence(edited, time(hour = 16, minute = 0), null))
    }

    @Test fun consumedDateUsesSavedZoneAndBlocksClockRollback() {
        val consumed = time(hour = 23, minute = 59)
        assertTrue(ClickSchedulePolicy.isConsumedDate(time(hour = 23, minute = 59, second = 59), consumed, zone))
        assertTrue(ClickSchedulePolicy.isConsumedDate(time(day = 4), consumed, zone))
        assertFalse(ClickSchedulePolicy.isConsumedDate(time(day = 6, hour = 0, minute = 0), consumed, zone))
        assertFalse(ClickSchedulePolicy.isConsumedDate(consumed, null, zone))
        val edited = task.copy(hour = 1, minute = 0, days = (1..7).toSet())
        assertEquals(time(day = 6, hour = 1, minute = 0),
            ClickSchedulePolicy.nextOccurrence(edited, time(day = 4), consumed))
    }
}
