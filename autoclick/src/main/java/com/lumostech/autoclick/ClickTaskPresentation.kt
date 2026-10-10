package com.lumostech.autoclick

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class RecoveryHelp { NONE, EXECUTION_PREPARATION, RECORD_AGAIN, RESAVE_TIME_ZONE, REENABLE }
data class ClickTaskPresentation(val title: String, val detail: String = "", val nextAt: Long? = null,
                                 val recoveryHelp: RecoveryHelp = RecoveryHelp.NONE,
                                 val timingPermissionRequired: Boolean = false, val scheduledEnabled: Boolean = false,
                                 val editingBlocked: Boolean = false)

fun presentExact(task: ClickTask?, alarm: ClickAlarmState?, run: ClickRunState, record: ClickExecutionRecord?,
                 permissionGranted: Boolean, now: Long, currentZone: String): ClickTaskPresentation {
    if (task == null) return ClickTaskPresentation("还没有定时任务")
    val history = record?.takeIf { it.scheduleId == task.scheduleId }
    val help = when (history?.reason) {
        ClickOutcomeReason.NEEDS_RECORDING, ClickOutcomeReason.DISPLAY_CHANGED -> RecoveryHelp.RECORD_AGAIN
        ClickOutcomeReason.SCREEN_LOCKED, ClickOutcomeReason.APP_CHANGED, ClickOutcomeReason.TARGET_UNAVAILABLE -> RecoveryHelp.EXECUTION_PREPARATION
        ClickOutcomeReason.SCHEDULING_FAILED -> RecoveryHelp.REENABLE
        else -> RecoveryHelp.NONE
    }
    val state = alarm?.takeIf { it.isValid() && it.taskId == task.id && it.scheduleId == task.scheduleId }
    if (!permissionGranted) return ClickTaskPresentation("定时权限未开启", "开启后请返回并启用任务",
        timingPermissionRequired = true)
    if (state?.status == ClickAlarmStatus.PERMISSION_REQUIRED)
        return ClickTaskPresentation("权限已开启，请启用任务", recoveryHelp = RecoveryHelp.REENABLE)
    if (task.timeZoneId != currentZone || state?.status == ClickAlarmStatus.TIME_ZONE_CHANGED)
        return ClickTaskPresentation("时区已改变，需重新设置任务", recoveryHelp = RecoveryHelp.RESAVE_TIME_ZONE)
    if (task.protection == null) return ClickTaskPresentation("旧版任务需要重新录制，才能启用", recoveryHelp = RecoveryHelp.RECORD_AGAIN)
    val active = state?.active
    val futureFailure = if (state?.status == ClickAlarmStatus.SCHEDULE_FAILED) "下次定时安排失败，请重新启用" else ""
    if (task.enabled && active != null && run.active && !run.manual && run.taskId == task.id && run.scheduleId == task.scheduleId)
        return ClickTaskPresentation(run.message, futureFailure, recoveryHelp = help, scheduledEnabled = true)
    if (task.enabled && active != null && state.phase == ClickAlarmPhase.PREPARING) {
        if (ExactClickPolicy.evaluate(task, active.scheduledAt, now, currentZone).status == ClickScheduleStatus.READY)
            return ClickTaskPresentation("执行准备中", futureFailure, recoveryHelp = help, scheduledEnabled = true)
        return ClickTaskPresentation("已错过启动时间，本次跳过", recoveryHelp = help)
    }
    if (state?.status == ClickAlarmStatus.NEEDS_ENABLE)
        return ClickTaskPresentation("请启用定时任务", recoveryHelp = RecoveryHelp.REENABLE)
    if (state?.status == ClickAlarmStatus.SCHEDULE_FAILED)
        return ClickTaskPresentation("定时安排失败，请重新启用", recoveryHelp = RecoveryHelp.REENABLE)
    if (!task.enabled) return ClickTaskPresentation("任务已停用", recoveryHelp = help)
    val next = state?.next
    if (state?.status == ClickAlarmStatus.ARMED && next != null) {
        if (next.scheduledAt > now) return ClickTaskPresentation(formatNextPlan(task, next.scheduledAt, now),
            nextAt = next.scheduledAt, recoveryHelp = help, scheduledEnabled = true)
        if (now - next.scheduledAt >= ExactClickPolicy.START_WINDOW_MS)
            return ClickTaskPresentation("已错过启动时间，本次跳过", "下一次安排尚未确认", recoveryHelp = help)
        return ClickTaskPresentation("执行准备中", recoveryHelp = help, scheduledEnabled = true)
    }
    return ClickTaskPresentation("暂无法确认下一次计划", recoveryHelp = RecoveryHelp.REENABLE)
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
