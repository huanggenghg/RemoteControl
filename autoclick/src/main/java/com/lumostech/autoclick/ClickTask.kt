package com.lumostech.autoclick

import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickSequenceCodec
import com.lumostech.accessibilitycore.ClickRecordingProtection
import java.util.Calendar
import java.util.TimeZone

data class ClickTask(
    val id: String,
    val hour: Int,
    val minute: Int,
    val days: Set<Int>,
    val points: List<ClickCounterPoint>,
    val enabled: Boolean = true,
    val protection: ClickRecordingProtection? = null,
    val timeZoneId: String = TimeZone.getDefault().id,
    val scheduleId: String = id
) {
    fun isValid(): Boolean = id.isNotBlank() && hour in 0..23 && minute in 0..59 &&
        days.isNotEmpty() && days.all { it in Calendar.SUNDAY..Calendar.SATURDAY } &&
        ClickSequenceCodec.isValid(points) && scheduleId.isNotBlank() && timeZoneId in TIME_ZONE_IDS &&
        (protection == null || protection.isValid(points))

    fun initialDelay(now: Long, timeZone: TimeZone = TimeZone.getTimeZone(timeZoneId)): Long {
        require(isValid())
        val due = Calendar.getInstance(timeZone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis < now) add(Calendar.DAY_OF_YEAR, 1)
        }
        return due.timeInMillis - now
    }

    companion object {
        private val TIME_ZONE_IDS = TimeZone.getAvailableIDs().toSet()
    }
}
