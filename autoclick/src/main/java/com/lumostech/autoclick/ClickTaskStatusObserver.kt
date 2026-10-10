package com.lumostech.autoclick

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.util.TimeZone

/** Reads only; the foreground Activity owns collection and unregisters on cancellation. */
internal class ClickTaskStatusObserver(context: Context) {
    private val store = ClickTaskStore(context.applicationContext)
    private val platform = AndroidClickAlarmPlatform(context.applicationContext)
    suspend fun observe(onUpdate: (ClickTaskPresentation, ClickExecutionRecord?) -> Unit) {
        var lastConfirmed: Pair<ClickTaskPresentation, ClickExecutionRecord?> = ClickTaskPresentation("正在读取任务状态") to null
        val changes = callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
            store.observe(listener); trySend(Unit)
            awaitClose { store.stopObserving(listener) }
        }
        val clock = flow { while (true) { emit(System.currentTimeMillis()); delay(1000) } }
        combine(changes, ClickExecutionSession.state, clock) { _, run, now ->
            try {
            val task = store.load(); val alarm = store.alarmState(); val record = store.lastExecutionResult()
            var view = presentExact(task, alarm, run, record, platform.canSchedule(), now, TimeZone.getDefault().id)
            if (alarm?.status == ClickAlarmStatus.NEEDS_ENABLE && view.title == "请启用定时任务" &&
                store.lastOutcome() == "定时方式已更新，请启用任务") view = view.copy(title = store.lastOutcome())
            view = view.copy(editingBlocked = run.active || alarm?.active != null)
            (view to record).also { lastConfirmed = it }
            } catch (failure: RecoveryStorageException) {
                lastConfirmed.first.copy(title = "保存状态未确认", detail = "原任务与恢复记录已保留，请稍候重试",
                    nextAt = null, editingBlocked = true) to lastConfirmed.second
            }
        }.collect { (view, record) -> onUpdate(view, record) }
    }
}
