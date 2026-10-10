package com.lumostech.autoclick

import org.json.JSONArray
import org.json.JSONObject

enum class LegacyRecoveryPhase { RECORDING, PAUSED, SAVING }
data class LegacyTaskRecoverySession(val recoveryId: String, val sourceTaskId: String, val sourceScheduleId: String,
    val replacementTaskId: String, val hour: Int, val minute: Int, val days: Set<Int>, val phase: LegacyRecoveryPhase) {
    fun isValid(): Boolean = listOf(recoveryId, sourceTaskId, sourceScheduleId, replacementTaskId).all { it.isNotBlank() } &&
        hour in 0..23 && minute in 0..59 && days.isNotEmpty() && days.all { it in 1..7 }
    fun encode(): String {
        require(isValid())
        return JSONObject().put("version", 1).put("recoveryId", recoveryId).put("sourceTaskId", sourceTaskId)
            .put("sourceScheduleId", sourceScheduleId).put("replacementTaskId", replacementTaskId)
            .put("hour", hour).put("minute", minute).put("days", JSONArray(days.sorted())).put("phase", phase.name).toString()
    }
    companion object {
        fun decode(value: String): LegacyTaskRecoverySession? = runCatching {
            val json = JSONObject(value)
            require(json.getInt("version") == 1)
            val days = json.getJSONArray("days")
            LegacyTaskRecoverySession(json.getString("recoveryId"), json.getString("sourceTaskId"),
                json.getString("sourceScheduleId"), json.getString("replacementTaskId"), json.getInt("hour"),
                json.getInt("minute"), (0 until days.length()).map { days.getInt(it) }.toSet(),
                LegacyRecoveryPhase.valueOf(json.getString("phase"))).also { require(it.isValid()) }
        }.getOrNull()
    }
}
enum class LegacyTaskRecoveryResult { SAVED_DISABLED, ALREADY_SAVED }
