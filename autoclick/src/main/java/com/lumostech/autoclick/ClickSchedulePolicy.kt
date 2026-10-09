package com.lumostech.autoclick

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

enum class ClickScheduleStatus(val message: String) {
    READY("等待执行"),
    EARLY("尚未到计划时间，已跳过"),
    LATE("已错过启动时间，本次跳过"),
    WRONG_DAY("今天不在执行星期内，已跳过"),
    TIME_ZONE_CHANGED("时区已改变，请重新设置任务"),
    INVALID("任务配置无效，请重新录制")
}

data class ClickScheduleDecision(val status: ClickScheduleStatus, val scheduledAt: Long = 0)

object ClickSchedulePolicy {
    fun clockUnchanged(startWall: Long, startElapsed: Long, nowWall: Long, nowElapsed: Long): Boolean =
        abs((nowWall - startWall) - (nowElapsed - startElapsed)) <= 2_000L

    fun isConsumedDate(candidate: Long, consumedAt: Long?, timeZoneId: String): Boolean {
        if (consumedAt == null) return false
        if (candidate <= consumedAt) return true
        val zone = TimeZone.getTimeZone(timeZoneId)
        val a = Calendar.getInstance(zone).apply { timeInMillis = candidate }
        val b = Calendar.getInstance(zone).apply { timeInMillis = consumedAt }
        return a.get(Calendar.ERA) == b.get(Calendar.ERA) &&
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    fun nextOccurrence(task: ClickTask, now: Long, consumedAt: Long? = null): Long {
        require(task.isValid())
        val date = Calendar.getInstance(TimeZone.getTimeZone(task.timeZoneId)).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 12)
        }
        while (true) {
            val due = (date.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, task.hour)
                set(Calendar.MINUTE, task.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // Calendar normalizes nonexistent local times across a DST gap.
            // Such a value would fail the occurrence identity check; skip that date.
            if (due.timeInMillis > now && due.get(Calendar.DAY_OF_WEEK) in task.days &&
                due.get(Calendar.HOUR_OF_DAY) == task.hour && due.get(Calendar.MINUTE) == task.minute &&
                !isConsumedDate(due.timeInMillis, consumedAt, task.timeZoneId)) return due.timeInMillis
            date.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    fun evaluate(task: ClickTask, now: Long, currentTimeZoneId: String = TimeZone.getDefault().id): ClickScheduleDecision {
        if (!task.isValid()) return ClickScheduleDecision(ClickScheduleStatus.INVALID)
        val today = Calendar.getInstance(TimeZone.getTimeZone(task.timeZoneId)).apply { timeInMillis = now }
        val due = (today.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, task.hour)
            set(Calendar.MINUTE, task.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (task.timeZoneId != currentTimeZoneId) return ClickScheduleDecision(ClickScheduleStatus.TIME_ZONE_CHANGED, due)
        if (today.get(Calendar.DAY_OF_WEEK) !in task.days) return ClickScheduleDecision(ClickScheduleStatus.WRONG_DAY, due)
        val lateness = now - due
        val status = when {
            lateness < 0 -> ClickScheduleStatus.EARLY
            lateness >= ExactClickPolicy.START_WINDOW_MS -> ClickScheduleStatus.LATE
            else -> ClickScheduleStatus.READY
        }
        return ClickScheduleDecision(status, due)
    }

    fun isSameOccurrence(task: ClickTask, scheduledAt: Long, now: Long, currentTimeZoneId: String = TimeZone.getDefault().id): Boolean {
        if (task.timeZoneId != currentTimeZoneId || now < scheduledAt) return false
        val zone = TimeZone.getTimeZone(task.timeZoneId)
        val due = Calendar.getInstance(zone).apply { timeInMillis = scheduledAt }
        val current = Calendar.getInstance(zone).apply { timeInMillis = now }
        return due.get(Calendar.ERA) == current.get(Calendar.ERA) &&
            due.get(Calendar.YEAR) == current.get(Calendar.YEAR) &&
            due.get(Calendar.DAY_OF_YEAR) == current.get(Calendar.DAY_OF_YEAR)
    }
}
