package com.lumostech.autoclick

import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickRecordingProtection
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ClickTaskPresentationTest {
    private val zone = "Asia/Shanghai"
    private val task = ClickTask("status-test", 9, 0, (1..7).toSet(),
        listOf(ClickCounterPoint(300f, 300f, 0)),
        protection = ClickRecordingProtection(1080, 2400, 0, listOf("com.test")), timeZoneId = zone)

    private fun at(day: Int = 7, hour: Int = 9, minute: Int = 0): Long = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
        clear(); set(2026, Calendar.OCTOBER, day, hour, minute, 0)
    }.timeInMillis

    private fun result(now: Long, due: Long? = at(), state: ScheduledWorkState = ScheduledWorkState.ENQUEUED,
                       saved: ClickTask? = task, consumed: Boolean = false, run: ClickRunState = ClickRunState(),
                       record: ClickExecutionRecord? = null, currentZone: String = zone): ClickTaskPresentation =
        present(saved, ScheduledWorkSnapshot(saved?.id ?: "missing", state, due), run, consumed, record, now, currentZone)

    @Test fun showsTodayFuturePlan() {
        assertEquals("下次：今天（周三）09:00", result(at(hour = 8)).title)
    }

    @Test fun pastClockTimeDoesNotMislabelTomorrowQueueAsDelayed() {
        val view = result(at(minute = 1), due = at(day = 8))
        assertEquals("下次：明天（周四）09:00", view.title)
        assertEquals(at(day = 8), view.nextAt)
    }

    @Test fun selectedWeekdaysSkipWeekend() {
        val weekdays = task.copy(days = (2..6).toSet())
        assertEquals("下次：10月12日（周一）09:00", result(at(day = 9, hour = 10), at(day = 12), saved = weekdays).title)
    }

    @Test fun disabledTaskDoesNotAdvertisePendingExecution() {
        val view = result(at(hour = 8), saved = task.copy(enabled = false))
        assertEquals("任务已停用", view.title)
        assertNull(view.nextAt)
    }

    @Test fun elapsedUnderMinuteHasNoZeroMinuteDelay() {
        assertEquals("尚未开始 · 已到计划时间", result(at() + 30_000).title)
    }

    @Test fun lateQueueShowsElapsedAndExistingDeadline() {
        val view = result(at(minute = 1))
        assertEquals("尚未开始 · 已延后 1 分钟", view.title)
        assertEquals("09:15 后将跳过本次", view.detail)
    }

    @Test fun exactCutoffStillUsesInclusiveExistingWindow() {
        assertTrue(result(at(minute = 15)).title.startsWith("尚未开始"))
        assertEquals("本次已超时", result(at(minute = 15) + 1).title)
    }

    @Test fun expiredQueueShowsNextSelectedPlan() {
        val view = result(at(minute = 16))
        assertEquals("本次已超时", view.title)
        assertEquals(at(day = 8), view.nextAt)
    }

    @Test fun previousDayQueueIsExpiredInsteadOfTodaysLateOccurrence() {
        assertEquals("本次已超时", result(at(), due = at(day = 6)).title)
    }

    @Test fun runningWorkerDoesNotFormatMissingWorkManagerTime() {
        assertEquals("执行准备中", result(at(minute = 16), due = null, state = ScheduledWorkState.RUNNING).title)
    }

    @Test fun activeScheduledSequenceTakesPriorityOverExpiredStartWindow() {
        assertEquals("定时执行 2 / 3", result(at(minute = 16), run = ClickRunState(active = true, message = "定时执行 2 / 3")).title)
    }

    @Test fun manualTrialDoesNotReplaceScheduledPlan() {
        assertEquals("下次：今天（周三）09:00", result(at(hour = 8), run = ClickRunState(active = true, manual = true, message = "试运行倒计时")).title)
    }

    @Test fun consumedOccurrenceNeverLooksLikeItWillBeReplayed() {
        val record = ClickExecutionRecord(task.id, task.scheduleId, at(), at(minute = 1), ClickOutcomeReason.UNKNOWN, "未知结果")
        val view = result(at(minute = 1), consumed = true, record = record)
        assertEquals(at(day = 8), view.nextAt)
        assertFalse(view.title.startsWith("尚未开始"))
        assertTrue(view.detail.contains("不自动重放"))
    }

    @Test fun missingOrUnknownScheduleDoesNotInventTime() {
        assertNull(result(at(hour = 8), state = ScheduledWorkState.MISSING).nextAt)
        assertNull(result(at(hour = 8), due = null).nextAt)
        assertEquals("正在读取任务状态", result(at(hour = 8), state = ScheduledWorkState.LOADING).title)
    }

    @Test fun changedTimeZoneRequiresExplicitResave() {
        val view = result(at(hour = 8), currentZone = "UTC")
        assertEquals("时区已改变，需重新设置任务", view.title)
        assertEquals(RecoveryHelp.RESAVE_TIME_ZONE, view.recoveryHelp)
    }

    @Test fun staleTaskWorkCannotSupplyNextPlan() {
        val view = present(task, ScheduledWorkSnapshot("old-task", ScheduledWorkState.ENQUEUED, at()),
            ClickRunState(), false, null, at(hour = 8), zone)
        assertNull(view.nextAt)
    }

    @Test fun yesterdayResultDoesNotDeclareTodayFinished() {
        val record = ClickExecutionRecord(task.id, task.scheduleId, at(day = 6), at(day = 6), ClickOutcomeReason.COMPLETED, "完成")
        assertTrue(result(at(minute = 1), record = record).title.startsWith("尚未开始"))
    }

    @Test fun currentFailedResultSelectsNextPlanWithoutAutomaticRetry() {
        val record = ClickExecutionRecord(task.id, task.scheduleId, at(), at(minute = 1), ClickOutcomeReason.SCREEN_LOCKED, "未执行")
        val view = result(at(minute = 2), record = record)
        assertEquals(at(day = 8), view.nextAt)
        assertEquals(RecoveryHelp.EXECUTION_PREPARATION, view.recoveryHelp)
    }

    @Test fun unknownHistoricalReasonDoesNotInventRecoveryAction() {
        val record = ClickExecutionRecord(task.id, task.scheduleId, at(day = 6), at(day = 6), ClickOutcomeReason.UNKNOWN, "未知结果")
        assertEquals(RecoveryHelp.NONE, result(at(hour = 8), record = record).recoveryHelp)
    }

    @Test fun earlyUnknownHistoryCannotCloseAnUnconsumedUpcomingPlan() {
        val record = ClickExecutionRecord(task.id, task.scheduleId, at(), at(hour = 8), ClickOutcomeReason.UNKNOWN, "尚未到计划时间，已跳过")
        assertEquals(at(), result(at(hour = 8), record = record).nextAt)
        assertEquals("尚未开始 · 已到计划时间", result(at(), record = record).title)
        assertEquals("尚未开始 · 已延后 1 分钟", result(at(minute = 1), record = record).title)
    }

    @Test fun unknownReasonAfterDueDoesNotProveOccurrenceHasEnded() {
        val record = ClickExecutionRecord(task.id, task.scheduleId, at(), at(minute = 1), ClickOutcomeReason.UNKNOWN, "未知结果")
        assertTrue(result(at(minute = 2), record = record).title.startsWith("尚未开始"))
    }

    @Test fun clockRollbackResultBeforeDueDoesNotCloseReanchoredPlan() {
        val record = ClickExecutionRecord(task.id, task.scheduleId, at(), at(hour = 8), ClickOutcomeReason.TIME_CHANGED, "时间已改变")
        assertTrue(result(at(minute = 1), record = record).title.startsWith("尚未开始"))
    }

    @Test fun resultTimestampInFutureDoesNotProvePlanHasEndedNow() {
        val record = ClickExecutionRecord(task.id, task.scheduleId, at(), at(minute = 10), ClickOutcomeReason.COMPLETED, "完成")
        assertTrue(result(at(minute = 1), record = record).title.startsWith("尚未开始"))
    }

    @Test fun absentTaskShowsCreationGuidance() {
        assertEquals("还没有定时任务", result(at(), saved = null).title)
    }
}
