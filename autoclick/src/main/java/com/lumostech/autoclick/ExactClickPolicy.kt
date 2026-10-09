package com.lumostech.autoclick

import java.util.Calendar
import java.util.TimeZone

object ExactClickPolicy {
    const val START_WINDOW_MS = 5_000L

    fun evaluate(task: ClickTask, due: Long, now: Long, currentZone: String): ClickScheduleDecision {
        if (!task.isValid() || due <= 0) return ClickScheduleDecision(ClickScheduleStatus.INVALID, due)
        if (task.timeZoneId != currentZone) return ClickScheduleDecision(ClickScheduleStatus.TIME_ZONE_CHANGED, due)
        val date = Calendar.getInstance(TimeZone.getTimeZone(task.timeZoneId)).apply { timeInMillis = due }
        val valid = date.get(Calendar.DAY_OF_WEEK) in task.days && date.get(Calendar.HOUR_OF_DAY) == task.hour &&
            date.get(Calendar.MINUTE) == task.minute && date.get(Calendar.SECOND) == 0 && date.get(Calendar.MILLISECOND) == 0
        if (!valid) return ClickScheduleDecision(ClickScheduleStatus.INVALID, due)
        return ClickScheduleDecision(when {
            now < due -> ClickScheduleStatus.EARLY
            now - due >= START_WINDOW_MS -> ClickScheduleStatus.LATE
            else -> ClickScheduleStatus.READY
        }, due)
    }

    fun remaining(due: Long, now: Long): Long =
        if (now < due) 0 else (START_WINDOW_MS - (now - due)).coerceAtLeast(0)

    fun startupOpen(due: Long, receivedAt: Long, receivedElapsed: Long, nowWall: Long, nowElapsed: Long): Boolean {
        val budget = remaining(due, receivedAt)
        val spent = nowElapsed - receivedElapsed
        return budget > 0 && remaining(due, nowWall) > 0 && spent >= 0 && spent < budget &&
            ClickSchedulePolicy.clockUnchanged(receivedAt, receivedElapsed, nowWall, nowElapsed)
    }

    fun next(task: ClickTask, now: Long, closedThrough: Long, consumedAt: Long? = null): Long =
        ClickSchedulePolicy.nextOccurrence(task, maxOf(now, closedThrough), consumedAt)
}
