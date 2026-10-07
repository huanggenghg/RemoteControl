package com.lumostech.autoclick

import com.lumostech.accessibilitycore.ClickProtectionFailure
import org.json.JSONObject

enum class ClickOutcomeReason {
    UNKNOWN, COMPLETED, ACCESSIBILITY_DISABLED, CONNECTION_TIMEOUT,
    NEEDS_RECORDING, SCREEN_LOCKED, APP_CHANGED, DISPLAY_CHANGED,
    TARGET_UNAVAILABLE, TIME_CHANGED, START_EXPIRED,
    DISPATCH_FAILED, CANCELLED, SCHEDULING_FAILED;

    companion object {
        fun fromProtection(failure: ClickProtectionFailure): ClickOutcomeReason = when (failure) {
            ClickProtectionFailure.SCREEN_LOCKED -> SCREEN_LOCKED
            ClickProtectionFailure.APP_CHANGED -> APP_CHANGED
            ClickProtectionFailure.DISPLAY_CHANGED -> DISPLAY_CHANGED
            ClickProtectionFailure.INVALID_RECORDING -> NEEDS_RECORDING
            else -> TARGET_UNAVAILABLE
        }
    }
}

data class ClickExecutionRecord(
    val taskId: String,
    val scheduleId: String,
    val occurrenceAt: Long?,
    val recordedAt: Long,
    val reason: ClickOutcomeReason,
    val message: String
) {
    fun encode(): String = JSONObject().apply {
        put("taskId", taskId)
        put("scheduleId", scheduleId)
        put("occurrenceAt", occurrenceAt ?: JSONObject.NULL)
        put("recordedAt", recordedAt)
        put("reason", reason.name)
        put("message", message)
    }.toString()

    companion object {
        fun decode(encoded: String?): ClickExecutionRecord? = runCatching {
            val json = JSONObject(encoded ?: return null)
            ClickExecutionRecord(json.getString("taskId"), json.getString("scheduleId"),
                if (json.isNull("occurrenceAt")) null else json.getLong("occurrenceAt"),
                json.getLong("recordedAt"),
                runCatching { ClickOutcomeReason.valueOf(json.getString("reason")) }.getOrDefault(ClickOutcomeReason.UNKNOWN),
                json.getString("message"))
                .takeIf { it.taskId.isNotBlank() && it.scheduleId.isNotBlank() && it.recordedAt > 0 }
        }.getOrNull()
    }
}
