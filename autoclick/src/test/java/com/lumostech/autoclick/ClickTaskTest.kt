package com.lumostech.autoclick

import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickRecordingProtection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ClickTaskTest {
    private val task = ClickTask("task-1", 10, 30, setOf(Calendar.MONDAY), listOf(ClickCounterPoint(100f, 200f, 0)),
        protection = ClickRecordingProtection(1080, 2400, 0, listOf("com.example.target")))

    @Test
    fun legacyTaskCannotDispatchWithoutRecordedEnvironment() = runBlocking {
        var dispatched = false
        val result = ClickTaskRunner.run(task.copy(protection = null), Calendar.MONDAY, true) { dispatched = true; true }
        assertFalse("A legacy task must require re-recording before any gesture", dispatched)
        assertEquals("录制缺少环境信息，请重新录制", result.message)
    }

    private fun time(hour: Int, minute: Int): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(2026, Calendar.OCTOBER, 5, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun schedulesTodayWhenTargetHasNotPassed() {
        assertEquals(30 * 60_000L, task.initialDelay(time(10, 0), TimeZone.getTimeZone("UTC")))
    }

    @Test
    fun schedulesTomorrowWhenTargetHasPassed() {
        assertEquals((23 * 60 + 30) * 60_000L, task.initialDelay(time(11, 0), TimeZone.getTimeZone("UTC")))
    }

    @Test
    fun rejectsEmptyDaysAndEmptyPoints() {
        assertFalse(task.copy(days = emptySet()).isValid())
        assertFalse(task.copy(points = emptyList()).isValid())
        assertFalse(task.copy(hour = 24).isValid())
        assertFalse(task.copy(days = setOf(8)).isValid())
    }

    @Test
    fun unavailableServiceIsFailureWithoutCallingExecutor() = runBlocking {
        val result = ClickTaskRunner.run(task, Calendar.MONDAY, false) { error("Must not execute") }
        assertEquals(ClickTaskOutcome.SERVICE_UNAVAILABLE, result)
    }

    @Test
    fun skippedDayIsDistinguishedFromExecutedTask() = runBlocking {
        assertEquals(ClickTaskOutcome.SKIPPED_DAY, ClickTaskRunner.run(task, Calendar.TUESDAY, true) { error("Must not execute") })
    }

    @Test
    fun successRequiresCompletedGestureSequence() = runBlocking {
        assertEquals(ClickTaskOutcome.GESTURE_FAILED, ClickTaskRunner.run(task, Calendar.MONDAY, true) { false })
        assertEquals(ClickTaskOutcome.COMPLETED, ClickTaskRunner.run(task, Calendar.MONDAY, true) { points -> points == task.points })
    }
}
