package com.lumostech.autoclick

import android.content.Context
import android.content.SharedPreferences
import com.lumostech.accessibilitycore.ClickSequenceCodec
import com.lumostech.accessibilitycore.ClickRecordingProtection
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

enum class ClickExecutionClaim { CLAIMED, ALREADY_CONSUMED, STALE_TASK, STORAGE_FAILED }

class ClickTaskStore internal constructor(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("autoclick_task", Context.MODE_PRIVATE))

    fun load(): ClickTask? = synchronized(lock) {
        runCatching {
            val json = JSONObject(preferences.getString("task", null) ?: return null)
            val days = json.getJSONArray("days")
            ClickTask(
                json.getString("id"), json.getInt("hour"), json.getInt("minute"),
                (0 until days.length()).map { days.getInt(it) }.toSet(),
                ClickSequenceCodec.decode(json.getString("points")), json.getBoolean("enabled"),
                protection = if (json.has("protection")) {
                    requireNotNull(ClickRecordingProtection.decode(json.getString("protection")))
                } else null,
                timeZoneId = json.optString("timeZoneId", TimeZone.getDefault().id),
                scheduleId = json.optString("scheduleId", json.getString("id"))
            ).takeIf { it.isValid() }
        }.getOrNull()
    }

    fun save(task: ClickTask, message: String = "等待下次执行", reason: ClickOutcomeReason? = null): Boolean = synchronized(lock) {
        require(task.isValid())
        val previous = load()
        val sameSchedule = previous?.scheduleId == task.scheduleId
        // Preserve a legacy execution result before a control operation replaces
        // its old text/time keys. No cause or occurrence is inferred from prose.
        val legacyRecord = if (sameSchedule && lastExecutionRecord() == null) lastExecutionResult() else null
        val json = JSONObject().apply {
            put("id", task.id)
            put("hour", task.hour)
            put("minute", task.minute)
            put("days", JSONArray(task.days.sorted()))
            put("points", ClickSequenceCodec.encode(task.points))
            put("enabled", task.enabled)
            put("timeZoneId", task.timeZoneId)
            put("scheduleId", task.scheduleId)
            task.protection?.let { put("protection", it.encode()) }
        }
        val editor = preferences.edit().putString("task", json.toString()).putString("outcome", message)
            .remove("outcome_time")
        if (!sameSchedule) editor.remove("consumed_at").remove("execution_record").remove("execution_pending")
        legacyRecord?.let { editor.putString("execution_record", it.encode()) }
        reason?.let { editor.putString("execution_record", ClickExecutionRecord(task.id, task.scheduleId,
            null, System.currentTimeMillis(), it, message).encode()).putBoolean("execution_pending", false) }
        editor.commit()
    }

    /** Commit before any external side effect; an interrupted occurrence is never replayed. */
    fun claimExecution(taskId: String, scheduledAt: Long): ClickExecutionClaim = synchronized(lock) {
        val current = load()
        if (current == null || current.id != taskId || !current.enabled || current.protection == null) {
            return ClickExecutionClaim.STALE_TASK
        }
        if (wasConsumed(taskId, scheduledAt)) return ClickExecutionClaim.ALREADY_CONSUMED
        val legacyRecord = if (lastExecutionRecord() == null) lastExecutionResult() else null
        val editor = preferences.edit().putLong("consumed_at", scheduledAt)
            .putString("outcome", "正在执行点击").putLong("outcome_time", System.currentTimeMillis())
            .putBoolean("execution_pending", true)
        legacyRecord?.let { editor.putString("execution_record", it.encode()) }
        if (editor.commit()) ClickExecutionClaim.CLAIMED
        else {
            // commit() updates memory even when disk persistence fails. Keep its
            // consumed watermark, but replace the running state while still owning
            // this claim under the lock; an unclaimed worker cannot do so later.
            val time = System.currentTimeMillis()
            val message = "无法保存执行记录，未执行点击"
            preferences.edit().putString("outcome", message).putLong("outcome_time", time)
                .putString("execution_record", ClickExecutionRecord(taskId, current.scheduleId, scheduledAt,
                    time, ClickOutcomeReason.UNKNOWN, message).encode()).putBoolean("execution_pending", false).commit()
            ClickExecutionClaim.STORAGE_FAILED
        }
    }

    fun wasConsumed(taskId: String, scheduledAt: Long): Boolean = synchronized(lock) {
        load()?.id == taskId && preferences.contains("consumed_at") && scheduledAt <= preferences.getLong("consumed_at", 0)
    }

    fun clear(): Boolean = synchronized(lock) { preferences.edit().clear().commit() }

    fun recordOutcome(taskId: String, message: String, reason: ClickOutcomeReason = ClickOutcomeReason.UNKNOWN): Boolean = synchronized(lock) {
        val current = load()
        if (current?.id != taskId || !current.enabled) return false
        val time = System.currentTimeMillis()
        preferences.edit().putString("outcome", message).putLong("outcome_time", time)
            .putString("execution_record", ClickExecutionRecord(taskId, current.scheduleId, null, time, reason, message).encode()).putBoolean("execution_pending", false).commit()
    }

    fun recordOccurrenceOutcome(taskId: String, scheduledAt: Long, claimed: Boolean, message: String,
                                reason: ClickOutcomeReason = ClickOutcomeReason.UNKNOWN): Boolean = synchronized(lock) {
        val current = load()
        if (current?.id != taskId || !current.enabled) return false
        val consumed = preferences.contains("consumed_at")
        val last = preferences.getLong("consumed_at", 0)
        if (claimed && (!consumed || last != scheduledAt)) return false
        if (!claimed && consumed && last >= scheduledAt) return false
        val time = System.currentTimeMillis()
        preferences.edit().putString("outcome", message).putLong("outcome_time", time)
            .putString("execution_record", ClickExecutionRecord(taskId, current.scheduleId, scheduledAt, time, reason, message).encode()).putBoolean("execution_pending", false).commit()
    }

    fun lastExecutionRecord(): ClickExecutionRecord? = synchronized(lock) {
        ClickExecutionRecord.decode(preferences.getString("execution_record", null))
            ?.takeIf { it.scheduleId == load()?.scheduleId }
    }

    /** Legacy text is terminal history only while no new claim is in progress. */
    fun lastExecutionResult(): ClickExecutionRecord? = synchronized(lock) {
        lastExecutionRecord() ?: if (!preferences.getBoolean("execution_pending", false) && lastOutcomeTime() > 0) {
            load()?.let { ClickExecutionRecord(it.id, it.scheduleId, null, lastOutcomeTime(),
                ClickOutcomeReason.UNKNOWN, lastOutcome()) }
        } else null
    }

    fun lastOutcome(): String = preferences.getString("outcome", "尚无执行记录") ?: "尚无执行记录"
    fun lastOutcomeTime(): Long = preferences.getLong("outcome_time", 0)

    fun observe(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.registerOnSharedPreferenceChangeListener(listener)

    fun stopObserving(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        private val lock = Any()
    }
}
