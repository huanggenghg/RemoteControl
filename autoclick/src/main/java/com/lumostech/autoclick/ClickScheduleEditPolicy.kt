package com.lumostech.autoclick

enum class ClickScheduleEditResult(val message: String) {
    UNCHANGED("时间和星期未改变"),
    ENABLED("时间已修改"),
    DISABLED("时间已修改，任务仍停用"),
    LEGACY_DISABLED("时间已修改，旧版任务仍需重新录制"),
    PERMISSION_REQUIRED("时间已修改，定时权限未开启，任务未启用"),
    TIME_ZONE_CHANGED("时间已修改，时区已改变，任务未启用")
}

internal data class ClickScheduleEditDecision(
    val result: ClickScheduleEditResult,
    val enabled: Boolean,
    val status: ClickAlarmStatus
)

internal object ClickScheduleEditPolicy {
    fun decide(task: ClickTask, alarm: ClickAlarmState?, permission: Boolean, currentZone: String): ClickScheduleEditDecision {
        if (task.timeZoneId != currentZone || alarm?.status == ClickAlarmStatus.TIME_ZONE_CHANGED)
            return ClickScheduleEditDecision(ClickScheduleEditResult.TIME_ZONE_CHANGED, false, ClickAlarmStatus.TIME_ZONE_CHANGED)
        if (!task.enabled) {
            val retained = alarm?.status?.takeIf { it in setOf(ClickAlarmStatus.PERMISSION_REQUIRED,
                ClickAlarmStatus.NEEDS_ENABLE, ClickAlarmStatus.SCHEDULE_FAILED) } ?: ClickAlarmStatus.NEEDS_ENABLE
            return ClickScheduleEditDecision(ClickScheduleEditResult.DISABLED, false, retained)
        }
        if (!permission) return ClickScheduleEditDecision(ClickScheduleEditResult.PERMISSION_REQUIRED,
            false, ClickAlarmStatus.PERMISSION_REQUIRED)
        return ClickScheduleEditDecision(ClickScheduleEditResult.ENABLED, true, ClickAlarmStatus.ARMING)
    }
}
