package com.lumostech.autoclick

import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.util.Calendar
import java.util.TimeZone

/** Only reads state; collection is owned and cancelled by the foreground Activity. */
internal class ClickTaskStatusObserver(context: Context) {
    private val store = ClickTaskStore(context.applicationContext)
    private val manager = WorkManager.getInstance(context.applicationContext)

    suspend fun observe(onUpdate: (ClickTaskPresentation, ClickExecutionRecord?) -> Unit) {
        val changes = callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
            store.observe(listener)
            trySend(Unit)
            awaitClose { store.stopObserving(listener) }
        }
        val clock = flow {
            while (true) { emit(System.currentTimeMillis()); delay(1_000) }
        }
        combine(manager.getWorkInfosForUniqueWorkFlow(ClickPeriodicWorker.TAG), changes,
            ClickExecutionSession.state, clock) { infos, _, run, now ->
            val task = store.load()
            val work = task?.let { scheduledWorkSnapshot(it, infos) }
            val consumed = task != null && work?.plannedAt != null && store.wasConsumed(task.id, work.plannedAt)
            val record = store.lastExecutionRecord()
            present(task, work, run, consumed, record, now, TimeZone.getDefault().id) to record
        }.collect { (presentation, record) -> onUpdate(presentation, record) }
    }
}

internal fun scheduledWorkSnapshot(task: ClickTask, infos: List<WorkInfo>): ScheduledWorkSnapshot {
    val matches = infos.filter { !it.state.isFinished && it.periodicityInfo != null &&
        task.id in it.tags && ClickPeriodicWorker.TAG in it.tags }
    if (matches.isEmpty()) return ScheduledWorkSnapshot(task.id, ScheduledWorkState.MISSING)
    val info = matches.singleOrNull() ?: return ScheduledWorkSnapshot(task.id, ScheduledWorkState.UNKNOWN)
    if (info.state == WorkInfo.State.RUNNING) return ScheduledWorkSnapshot(task.id, ScheduledWorkState.RUNNING)
    val hint = info.nextScheduleTimeMillis
    if (info.state != WorkInfo.State.ENQUEUED || hint <= 0 || hint == Long.MAX_VALUE)
        return ScheduledWorkSnapshot(task.id, ScheduledWorkState.UNKNOWN)
    val date = Calendar.getInstance(TimeZone.getTimeZone(task.timeZoneId)).apply {
        timeInMillis = hint
        set(Calendar.HOUR_OF_DAY, task.hour)
        set(Calendar.MINUTE, task.minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (date.get(Calendar.DAY_OF_WEEK) !in task.days)
        return ScheduledWorkSnapshot(task.id, ScheduledWorkState.UNKNOWN)
    return ScheduledWorkSnapshot(task.id, ScheduledWorkState.ENQUEUED, date.timeInMillis)
}
