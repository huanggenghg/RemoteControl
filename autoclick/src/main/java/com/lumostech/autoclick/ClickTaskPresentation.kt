package com.lumostech.autoclick

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class ScheduledWorkState { LOADING, MISSING, ENQUEUED, RUNNING, UNKNOWN }
data class ScheduledWorkSnapshot(val taskId: String, val state: ScheduledWorkState, val plannedAt: Long? = null)
enum class RecoveryHelp { NONE, EXECUTION_PREPARATION, RECORD_AGAIN, RESAVE_TIME_ZONE, REENABLE }
data class ClickTaskPresentation(val title: String, val detail: String = "", val nextAt: Long? = null,
                                 val recoveryHelp: RecoveryHelp = RecoveryHelp.NONE)

fun present(task: ClickTask?, work: ScheduledWorkSnapshot?, runState: ClickRunState, consumed: Boolean,
            lastRecord: ClickExecutionRecord?, now: Long, currentTimeZoneId: String): ClickTaskPresentation {
    if (task == null) return ClickTaskPresentation("还没有定时任务")
    val record = lastRecord?.takeIf { it.scheduleId == task.scheduleId }
    val help = when (record?.reason) {
        ClickOutcomeReason.NEEDS_RECORDING, ClickOutcomeReason.DISPLAY_CHANGED -> RecoveryHelp.RECORD_AGAIN
        ClickOutcomeReason.SCREEN_LOCKED, ClickOutcomeReason.APP_CHANGED,
        ClickOutcomeReason.TARGET_UNAVAILABLE -> RecoveryHelp.EXECUTION_PREPARATION
        ClickOutcomeReason.SCHEDULING_FAILED -> RecoveryHelp.REENABLE
        else -> RecoveryHelp.NONE
    }
    if (!task.enabled) return ClickTaskPresentation("任务已停用", recoveryHelp = help)
    if (task.timeZoneId != currentTimeZoneId)
        return ClickTaskPresentation("时区已改变，需重新设置任务", recoveryHelp = RecoveryHelp.RESAVE_TIME_ZONE)
    if (task.protection == null)
        return ClickTaskPresentation("录制信息不完整，需重新录制", recoveryHelp = RecoveryHelp.RECORD_AGAIN)
    if (runState.active && !runState.manual) return ClickTaskPresentation(runState.message, recoveryHelp = help)
    if (work != null && work.taskId != task.id)
        return ClickTaskPresentation("暂无法确认下一次计划", recoveryHelp = help)
    if (work == null || work.state == ScheduledWorkState.LOADING)
        return ClickTaskPresentation("正在读取任务状态", recoveryHelp = help)
    if (work.state == ScheduledWorkState.RUNNING) return ClickTaskPresentation("执行准备中", recoveryHelp = help)
    val due = work.plannedAt
    if (work.state != ScheduledWorkState.ENQUEUED || due == null || due <= 0 || due == Long.MAX_VALUE)
        return ClickTaskPresentation("暂无法确认下一次计划", recoveryHelp = help)
    if (due > now) return ClickTaskPresentation(formatNextPlan(task, due, now), nextAt = due, recoveryHelp = help)
    val next = ClickSchedulePolicy.nextOccurrence(task, now)
    // An early or clock-shifted diagnostic may refer to a future occurrence.
    // Only a known terminal result recorded within this occurrence proves it ended.
    val ended = record != null && record.occurrenceAt == due &&
        record.reason != ClickOutcomeReason.UNKNOWN && record.recordedAt in due..now
    if (consumed || ended) {
        return ClickTaskPresentation(formatNextPlan(task, next, now),
            if (consumed) "本次已开始过，不自动重放" else "本次已结束，不自动重试", next, help)
    }
    if (!sameDate(due, now, task.timeZoneId) || now - due > ClickSchedulePolicy.MAX_LATENESS_MS) {
        return ClickTaskPresentation("本次已超时", formatNextPlan(task, next, now), next, help)
    }
    val minutes = (now - due) / 60_000
    return ClickTaskPresentation(if (minutes == 0L) "尚未开始 · 已到计划时间" else "尚未开始 · 已延后 $minutes 分钟",
        "${formatTime(task, due + ClickSchedulePolicy.MAX_LATENESS_MS)} 后将跳过本次", recoveryHelp = help)
}

private fun dateAt(time: Long, zone: String): Calendar = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply { timeInMillis = time }

private fun sameDate(a: Long, b: Long, zone: String): Boolean {
    val first = dateAt(a, zone)
    val second = dateAt(b, zone)
    return listOf(Calendar.ERA, Calendar.YEAR, Calendar.DAY_OF_YEAR).all { first.get(it) == second.get(it) }
}

private fun formatTime(task: ClickTask, at: Long): String = SimpleDateFormat("HH:mm", Locale.CHINA).apply {
    timeZone = TimeZone.getTimeZone(task.timeZoneId)
}.format(at)

private fun formatNextPlan(task: ClickTask, due: Long, now: Long): String {
    val date = dateAt(due, task.timeZoneId)
    val today = dateAt(now, task.timeZoneId)
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    val day = when {
        sameDate(due, now, task.timeZoneId) -> "今天"
        sameDate(due, tomorrow.timeInMillis, task.timeZoneId) -> "明天"
        date.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> "${date.get(Calendar.MONTH) + 1}月${date.get(Calendar.DAY_OF_MONTH)}日"
        else -> "${date.get(Calendar.YEAR)}年${date.get(Calendar.MONTH) + 1}月${date.get(Calendar.DAY_OF_MONTH)}日"
    }
    val week = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")[date.get(Calendar.DAY_OF_WEEK) - 1]
    return "下次：$day（$week）${formatTime(task, due)}"
}
