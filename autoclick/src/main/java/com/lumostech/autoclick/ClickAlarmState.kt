package com.lumostech.autoclick

import org.json.JSONObject

enum class ClickAlarmStatus { NEEDS_ENABLE, PERMISSION_REQUIRED, TIME_ZONE_CHANGED, ARMING, ARMED, SCHEDULE_FAILED }
enum class ClickAlarmPhase { PREPARING, CLAIMED }
enum class ClickTaskSaveResult { ENABLED, SAVED_NEEDS_PERMISSION }
enum class ClickRecoveryEvent { STARTUP, BOOT, PACKAGE_REPLACED, TIME_CHANGED, ZONE_CHANGED, PERMISSION_CHANGED }

data class ClickAlarmOccurrence(val taskId: String, val scheduleId: String, val scheduledAt: Long, val version: Int = 2) {
    fun isValid(): Boolean = version == 2 && taskId.isNotBlank() && scheduleId.isNotBlank() && scheduledAt > 0
    fun matches(task: ClickTask): Boolean = isValid() && task.id == taskId && task.scheduleId == scheduleId
    fun encode(): String = JSONObject().apply {
        put("version", version); put("taskId", taskId); put("scheduleId", scheduleId); put("scheduledAt", scheduledAt)
    }.toString()
    companion object {
        fun decode(encoded: String?): ClickAlarmOccurrence? = runCatching {
            val json = JSONObject(encoded ?: return null)
            ClickAlarmOccurrence(json.getString("taskId"), json.getString("scheduleId"),
                json.getLong("scheduledAt"), json.getInt("version")).takeIf { it.isValid() }
        }.getOrNull()
    }
}

data class ClickAlarmState(val taskId: String, val scheduleId: String, val status: ClickAlarmStatus,
                           val next: ClickAlarmOccurrence? = null, val active: ClickAlarmOccurrence? = null,
                           val phase: ClickAlarmPhase? = null, val version: Int = 2) {
    fun isValid(): Boolean = version == 2 && taskId.isNotBlank() && scheduleId.isNotBlank() &&
        listOfNotNull(next, active).all { it.isValid() && it.taskId == taskId && it.scheduleId == scheduleId } &&
        ((active == null) == (phase == null)) && (status != ClickAlarmStatus.ARMED || next != null)
    fun encode(): String = JSONObject().apply {
        put("version", version); put("taskId", taskId); put("scheduleId", scheduleId); put("status", status.name)
        put("next", next?.let { JSONObject(it.encode()) } ?: JSONObject.NULL)
        put("active", active?.let { JSONObject(it.encode()) } ?: JSONObject.NULL)
        put("phase", phase?.name ?: JSONObject.NULL)
    }.toString()
    companion object {
        fun decode(encoded: String?): ClickAlarmState? = runCatching {
            val json = JSONObject(encoded ?: return null)
            fun event(key: String): ClickAlarmOccurrence? = if (json.isNull(key)) null
                else requireNotNull(ClickAlarmOccurrence.decode(json.getJSONObject(key).toString()))
            ClickAlarmState(json.getString("taskId"), json.getString("scheduleId"), ClickAlarmStatus.valueOf(json.getString("status")),
                event("next"), event("active"), if (json.isNull("phase")) null else ClickAlarmPhase.valueOf(json.getString("phase")),
                json.getInt("version")).takeIf { it.isValid() }
        }.getOrNull()
    }
}

data class ClickStartTrace(val occurrence: ClickAlarmOccurrence, val receivedAt: Long, val firstDispatchAt: Long? = null) {
    fun encode(): String = JSONObject().apply {
        put("occurrence", JSONObject(occurrence.encode())); put("receivedAt", receivedAt)
        put("firstDispatchAt", firstDispatchAt ?: JSONObject.NULL)
    }.toString()
    companion object {
        fun decode(encoded: String?): ClickStartTrace? = runCatching {
            val json = JSONObject(encoded ?: return null)
            ClickStartTrace(requireNotNull(ClickAlarmOccurrence.decode(json.getJSONObject("occurrence").toString())),
                json.getLong("receivedAt"), if (json.isNull("firstDispatchAt")) null else json.getLong("firstDispatchAt"))
                .takeIf { it.receivedAt > 0 && (it.firstDispatchAt == null ||
                    (it.firstDispatchAt >= it.occurrence.scheduledAt &&
                        it.firstDispatchAt - it.occurrence.scheduledAt < ExactClickPolicy.START_WINDOW_MS)) }
        }.getOrNull()
    }
}
