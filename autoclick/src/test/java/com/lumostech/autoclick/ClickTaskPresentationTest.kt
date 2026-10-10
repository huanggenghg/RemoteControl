package com.lumostech.autoclick

import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickRecordingProtection
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ClickTaskPresentationTest {
    @Test fun legacyTaskExplainsWhyItCannotBeEnabled() {
        val legacy = ClickTask("legacy", 9, 0, setOf(2),
            listOf(com.lumostech.accessibilitycore.ClickCounterPoint(200f, 200f, 0)), enabled = false)
        val view = presentExact(legacy, null, ClickRunState(), null, true, 1000, legacy.timeZoneId)
        assertEquals("旧版任务需要重新录制，才能启用", view.title)
        assertNull(view.nextAt)
    }
    private val zone = "Asia/Shanghai"
    private val task = ClickTask("status-test", 9, 0, (1..7).toSet(), listOf(ClickCounterPoint(300f, 300f, 0)),
        protection = ClickRecordingProtection(1080, 2400, 0, listOf("com.test")), timeZoneId = zone)
    private fun at(day: Int = 7, hour: Int = 9, minute: Int = 0): Long = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
        clear(); set(2026, Calendar.OCTOBER, day, hour, minute, 0)
    }.timeInMillis
    private fun alarm(due: Long = at()) = ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMED,
        ClickAlarmOccurrence(task.id, task.scheduleId, due))
    private fun result(now: Long, state: ClickAlarmState? = alarm(), saved: ClickTask? = task,
                       run: ClickRunState = ClickRunState(), record: ClickExecutionRecord? = null, currentZone: String = zone) =
        presentExact(saved, state, run, record, true, now, currentZone)
    @Test fun showsTodayFuturePlan() { assertEquals("下次：今天（周三）09:00", result(at(hour = 8)).title) }
    @Test fun pastClockTimeDoesNotMislabelTomorrowQueueAsDelayed() {
        val view = result(at(minute = 1), alarm(at(day = 8)))
        assertEquals("下次：明天（周四）09:00", view.title); assertEquals(at(day = 8), view.nextAt)
    }
    @Test fun selectedWeekdaysSkipWeekend() {
        assertEquals("下次：10月12日（周一）09:00", result(at(day = 9, hour = 10), alarm(at(day = 12)), task.copy(days = (2..6).toSet())).title)
    }
    @Test fun disabledTaskDoesNotAdvertisePendingExecution() {
        val view = result(at(hour = 8), saved = task.copy(enabled = false))
        assertEquals("任务已停用", view.title); assertNull(view.nextAt)
    }
    @Test fun withinWindowShowsPreparationWithoutMinuteDelay() {
        assertEquals("执行准备中", result(at() + 4999).title)
    }
    @Test fun exactCutoffIsExclusive() {
        assertEquals("已错过启动时间，本次跳过", result(at() + 5000).title)
    }
    @Test fun expiredQueueDoesNotInventNextSelectedPlan() {
        assertNull(result(at(minute = 16)).nextAt)
    }
    @Test fun previousDayQueueDoesNotBecomeTodaysOccurrence() {
        assertEquals("已错过启动时间，本次跳过", result(at(), alarm(at(day = 6))).title)
    }
    @Test fun preparingHasNoInventedNextTime() {
        val state = alarm().copy(active = alarm().next, phase = ClickAlarmPhase.PREPARING)
        assertEquals("执行准备中", result(at(), state).title); assertNull(result(at(), state).nextAt)
    }
    @Test fun activeScheduledSequenceTakesPriorityOverExpiredStartWindow() {
        val run = ClickRunState(active = true, message = "定时执行 2 / 3", taskId = task.id, scheduleId = task.scheduleId)
        val state = alarm(at(day = 8)).copy(active = alarm().next, phase = ClickAlarmPhase.CLAIMED)
        assertEquals(run.message, result(at(minute = 16), state, run = run).title)
    }
    @Test fun manualTrialDoesNotReplaceScheduledPlan() {
        assertEquals("下次：今天（周三）09:00", result(at(hour = 8), run = ClickRunState(active = true, manual = true)).title)
    }
    @Test fun recordedResultDoesNotOverrideConfirmedFuture() {
        val record = ClickExecutionRecord(task.id, task.scheduleId, at(), at(), ClickOutcomeReason.COMPLETED, "done")
        assertEquals(at(day = 8), result(at(minute = 1), alarm(at(day = 8)), record = record).nextAt)
    }
    @Test fun missingScheduleDoesNotInventTime() { assertNull(result(at(hour = 8), state = null).nextAt) }
    @Test fun armingScheduleDoesNotAdvertiseSuccess() {
        assertNull(result(at(hour = 8), alarm().copy(status = ClickAlarmStatus.ARMING)).nextAt)
    }
    @Test fun changedTimeZoneRequiresExplicitResave() {
        val view = result(at(hour = 8), currentZone = "UTC")
        assertEquals("时区已改变，需重新设置任务", view.title); assertEquals(RecoveryHelp.RESAVE_TIME_ZONE, view.recoveryHelp)
    }
    @Test fun staleTaskCannotSupplyNextPlan() { assertNull(result(at(hour = 8), alarm().copy(taskId = "old")).nextAt) }
    @Test fun yesterdayResultDoesNotDeclareTodayFinished() {
        val r = ClickExecutionRecord(task.id, task.scheduleId, at(day = 6), at(day = 6), ClickOutcomeReason.COMPLETED, "done")
        assertEquals("执行准备中", result(at(), record = r).title)
    }
    @Test fun currentFailureSelectsRecoveryButNeverInventsFuture() {
        val r = ClickExecutionRecord(task.id, task.scheduleId, at(), at(), ClickOutcomeReason.SCREEN_LOCKED, "failed")
        val view = result(at(minute = 1), record = r)
        assertNull(view.nextAt); assertEquals(RecoveryHelp.EXECUTION_PREPARATION, view.recoveryHelp)
    }
    @Test fun unknownHistoricalReasonDoesNotInventRecovery() {
        val r = ClickExecutionRecord(task.id, task.scheduleId, at(), at(), ClickOutcomeReason.UNKNOWN, "unknown")
        assertEquals(RecoveryHelp.NONE, result(at(hour = 8), record = r).recoveryHelp)
    }
    @Test fun earlyUnknownHistoryCannotCloseUnconsumedUpcomingPlan() {
        val r = ClickExecutionRecord(task.id, task.scheduleId, at(), at(hour = 8), ClickOutcomeReason.UNKNOWN, "early")
        assertEquals(at(), result(at(hour = 8), record = r).nextAt)
        assertEquals("执行准备中", result(at(), record = r).title)
    }
    @Test fun unknownReasonAfterDueDoesNotProveOccurrenceEnded() {
        val r = ClickExecutionRecord(task.id, task.scheduleId, at(), at() + 1000, ClickOutcomeReason.UNKNOWN, "unknown")
        assertEquals("执行准备中", result(at() + 2000, record = r).title)
    }
    @Test fun clockRollbackResultBeforeDueDoesNotCloseReanchoredPlan() {
        val r = ClickExecutionRecord(task.id, task.scheduleId, at(), at(hour = 8), ClickOutcomeReason.TIME_CHANGED, "clock")
        assertEquals("执行准备中", result(at() + 1000, record = r).title)
    }
    @Test fun resultTimestampInFutureDoesNotProveEndedNow() {
        val r = ClickExecutionRecord(task.id, task.scheduleId, at(), at(minute = 10), ClickOutcomeReason.COMPLETED, "done")
        assertEquals("执行准备中", result(at() + 1000, record = r).title)
    }
    @Test fun absentTaskShowsCreationGuidance() { assertEquals("还没有定时任务", result(at(), saved = null).title) }
}
