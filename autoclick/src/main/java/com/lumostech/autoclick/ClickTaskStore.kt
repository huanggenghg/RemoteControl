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
    constructor(context: Context) : this(RecoverableTaskPreferences.forContext(context))

    fun load(): ClickTask? = synchronized(lock) {
        (preferences as? RecoverableTaskPreferences)?.ensureReadable()
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
        }.getOrNull().also {
            if (it == null && (preferences as? RecoverableTaskPreferences)?.hasRecoverySnapshot() == true)
                throw RecoveryStorageException()
        }
    }

    fun save(task: ClickTask, message: String = "等待下次执行", reason: ClickOutcomeReason? = null,
             controlError: String? = null, alarmState: ClickAlarmState? = null): Boolean = synchronized(lock) {
        commitValues(saveValues(task, message, reason, controlError, alarmState))
    }

    private fun saveValues(task: ClickTask, message: String, reason: ClickOutcomeReason? = null,
                           controlError: String? = null, alarmState: ClickAlarmState? = null): Map<String, Any> {
        require(task.isValid())
        alarmState?.let { require(it.isValid() && it.taskId == task.id && it.scheduleId == task.scheduleId) }
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
        return copyPreferenceValues(preferences.all).toMutableMap().apply {
            put("task", json.toString()); put("outcome", message)
            remove("outcome_time"); remove("control_error")
            if (!sameSchedule) listOf("consumed_at", "execution_record", "execution_pending", "exact_alarm",
                "closed_at", "start_trace", "recovered_session_id").forEach(::remove)
            legacyRecord?.let { put("execution_record", it.encode()) }
            reason?.let {
                put("execution_record", ClickExecutionRecord(task.id, task.scheduleId, null,
                    System.currentTimeMillis(), it, message).encode())
                put("execution_pending", false)
            }
            controlError?.let { put("control_error", JSONObject().put("taskId", task.id).put("message", it).toString()) }
            alarmState?.let { put("exact_alarm", it.encode()) }
        }
    }

    private fun commitValues(values: Map<String, Any>): Boolean {
        val editor = preferences.edit().clear()
        values.forEach { (key, value) -> when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        } }
        return editor.commit()
    }

    fun replaceLegacyTask(expectedTaskId: String, task: ClickTask, recoveryId: String): Boolean = synchronized(lock) {
        val old = load() ?: return false
        if (old.id != expectedTaskId || old.protection != null || old.enabled || recoveryId.isBlank()) return false
        require(task.isValid() && task.protection != null && !task.enabled && task.id == task.scheduleId)
        val values = saveValues(task, "任务已恢复，请先试运行，再手动启用", alarmState =
            ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.NEEDS_ENABLE)).toMutableMap()
        values["recovered_session_id"] = recoveryId
        checkNotNull(preferences as? RecoverableTaskPreferences) { "恢复保存需要可靠存储" }
            .commitRecovery(expectedTaskId, values, recoveryId)
    }

    fun saveLegacySchedule(expectedTaskId: String, task: ClickTask, message: String): Boolean = synchronized(lock) {
        val old = load() ?: return false
        if (old.id != expectedTaskId || old.protection != null) return false
        require(task.isValid() && task.protection == null && !task.enabled && old.scheduleId == task.scheduleId &&
            old.points == task.points && old.timeZoneId == task.timeZoneId)
        val values = saveValues(task, message, alarmState = ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.NEEDS_ENABLE))
        checkNotNull(preferences as? RecoverableTaskPreferences) { "时间修改需要可靠存储" }
            .commitRecovery(expectedTaskId, values, "schedule-edit-${task.id}")
    }

    fun wasRecovered(recoveryId: String, replacementTaskId: String): Boolean = synchronized(lock) {
        load()?.scheduleId == replacementTaskId && preferences.getString("recovered_session_id", null) == recoveryId
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
        val task = load() ?: return false
        task.id == taskId && ClickSchedulePolicy.isConsumedDate(scheduledAt, consumedAt(taskId), task.timeZoneId)
    }

    fun consumedAt(taskId: String): Long? = synchronized(lock) {
        if (load()?.id == taskId && preferences.contains("consumed_at")) preferences.getLong("consumed_at", 0) else null
    }

    fun controlError(taskId: String): String? = synchronized(lock) {
        runCatching {
            val value = JSONObject(preferences.getString("control_error", null) ?: return null)
            value.getString("message").takeIf { load()?.id == taskId && value.getString("taskId") == taskId }
        }.getOrNull()
    }

    fun alarmState(): ClickAlarmState? = synchronized(lock) {
        val task = load() ?: return null
        ClickAlarmState.decode(preferences.getString("exact_alarm", null))
            ?.takeIf { it.taskId == task.id && it.scheduleId == task.scheduleId }
    }

    fun startTrace(): ClickStartTrace? = synchronized(lock) {
        ClickStartTrace.decode(preferences.getString("start_trace", null))
            ?.takeIf { it.occurrence.scheduleId == load()?.scheduleId }
    }

    fun closedThrough(): Long = synchronized(lock) {
        maxOf(preferences.getLong("consumed_at", 0), preferences.getLong("closed_at", 0))
    }

    fun saveAlarmState(taskId: String, state: ClickAlarmState, enabled: Boolean? = null): Boolean = synchronized(lock) {
        val task = load() ?: return false
        if (task.id != taskId || state.taskId != taskId || state.scheduleId != task.scheduleId || !state.isValid()) return false
        val editor = preferences.edit().putString("exact_alarm", state.encode())
        if (enabled != null) {
            val json = JSONObject(preferences.getString("task", null)!!).put("enabled", enabled)
            editor.putString("task", json.toString())
        }
        editor.commit()
    }

    fun reserveAlarm(occurrence: ClickAlarmOccurrence, receivedAt: Long): Boolean = synchronized(lock) {
        val task = load() ?: return false
        val state = alarmState() ?: return false
        if (!occurrence.matches(task) || !task.enabled || task.protection == null ||
            state.status != ClickAlarmStatus.ARMED || state.next != occurrence || state.active != null ||
            occurrence.scheduledAt <= closedThrough() || wasConsumed(task.id, occurrence.scheduledAt) || receivedAt <= 0) return false
        // Until the separate platform call confirms a next event, no future alarm is advertised.
        preferences.edit().putString("exact_alarm", state.copy(status = ClickAlarmStatus.ARMING,
            next = null, active = occurrence, phase = ClickAlarmPhase.PREPARING).encode())
            .putString("start_trace", ClickStartTrace(occurrence, receivedAt).encode()).commit()
    }

    fun claimAlarm(occurrence: ClickAlarmOccurrence): ClickExecutionClaim = synchronized(lock) {
        val task = load() ?: return ClickExecutionClaim.STALE_TASK
        if (!occurrence.matches(task) || !task.enabled || task.protection == null) return ClickExecutionClaim.STALE_TASK
        if (occurrence.scheduledAt <= closedThrough() || wasConsumed(task.id, occurrence.scheduledAt)) return ClickExecutionClaim.ALREADY_CONSUMED
        val state = alarmState() ?: return ClickExecutionClaim.STALE_TASK
        if (state.active != occurrence || state.phase != ClickAlarmPhase.PREPARING) return ClickExecutionClaim.STALE_TASK
        val legacy = if (lastExecutionRecord() == null) lastExecutionResult() else null
        val editor = preferences.edit().putLong("consumed_at", occurrence.scheduledAt)
            .putBoolean("execution_pending", true)
            .putString("exact_alarm", state.copy(phase = ClickAlarmPhase.CLAIMED).encode())
        legacy?.let { editor.putString("execution_record", it.encode()) }
        if (editor.commit()) ClickExecutionClaim.CLAIMED else ClickExecutionClaim.STORAGE_FAILED
    }

    fun finishAlarm(occurrence: ClickAlarmOccurrence, reason: ClickOutcomeReason, message: String): Boolean = synchronized(lock) {
        val task = load() ?: return false
        val state = alarmState() ?: return false
        if (!occurrence.matches(task) || state.active != occurrence) return false
        val consumed = preferences.getLong("consumed_at", 0)
        if (state.phase == ClickAlarmPhase.CLAIMED && consumed != occurrence.scheduledAt) return false
        if (state.phase != ClickAlarmPhase.CLAIMED && consumed >= occurrence.scheduledAt) return false
        val time = System.currentTimeMillis()
        preferences.edit().putLong("closed_at", maxOf(preferences.getLong("closed_at", 0), occurrence.scheduledAt))
            .putString("execution_record", ClickExecutionRecord(task.id, task.scheduleId, occurrence.scheduledAt,
                time, reason, message).encode()).putBoolean("execution_pending", false)
            .putString("outcome", message).putLong("outcome_time", time)
            .putString("exact_alarm", state.copy(active = null, phase = null).encode()).commit()
    }

    fun recordFirstDispatch(occurrence: ClickAlarmOccurrence, firstDispatchAt: Long): Boolean = synchronized(lock) {
        val task = load() ?: return false
        val state = alarmState() ?: return false
        val trace = startTrace() ?: return false
        if (!occurrence.matches(task) || state.active != occurrence || state.phase != ClickAlarmPhase.CLAIMED ||
            trace.occurrence != occurrence || trace.firstDispatchAt != null) return false
        preferences.edit().putString("start_trace", trace.copy(firstDispatchAt = firstDispatchAt).encode()).commit()
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
        if (!claimed && wasConsumed(taskId, scheduledAt)) return false
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
