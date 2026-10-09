package com.lumostech.autoclick

import android.content.Context
import androidx.work.WorkManager
import com.lumostech.accessibilitybase.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.TimeZone
import java.util.UUID

class ClickTaskController internal constructor(
    private val store: ClickTaskStore,
    private val platform: ClickAlarmPlatform,
    private val cancelLegacy: () -> Unit,
    private val clock: ClickClock = SystemClickClock,
    private val canEdit: suspend () -> Boolean = { true }
) {
    constructor(context: Context) : this(ClickTaskStore(context), AndroidClickAlarmPlatform(context), {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(ClickPeriodicWorker.TAG).result.get()
    }, canEdit = { withContext(Dispatchers.Main.immediate) {
        ClickServiceConnection.readiness(context.applicationContext) == ClickServiceReadiness.CONNECTED
    } })

    suspend fun updateSchedule(expectedTaskId: String, hour: Int, minute: Int,
                               days: Set<Int>): ClickScheduleEditResult = withContext(Dispatchers.IO) {
        val selected = days.toSet()
        mutationLock.withLock {
            val current = checkNotNull(store.load()) { "任务已删除，请重新打开任务设置" }
            check(current.id == expectedTaskId) { "任务已改变，请重新打开任务设置" }
            require(hour in 0..23 && minute in 0..59) { "请输入有效的时间" }
            require(selected.isNotEmpty() && selected.all { it in 1..7 }) { "请至少选择一个有效的执行星期" }
            require(current.protection != null) { ClickTaskOutcome.NEEDS_RECORDING.message }
            check(store.alarmState()?.active == null && !ClickExecutionSession.state.value.active) {
                "定时准备或点击正在执行，请结束后再修改"
            }
            check(canEdit()) { "无障碍服务未连接，请恢复后再修改" }
            if (current.hour == hour && current.minute == minute && current.days == selected)
                return@withLock ClickScheduleEditResult.UNCHANGED
            ClickExecutionSession.withScheduleEdit { edit ->
                val latest = checkNotNull(store.load()) { "任务已删除，请重新打开任务设置" }
                check(latest.id == expectedTaskId) { "任务已改变，请重新打开任务设置" }
                val old = store.alarmState()
                check(old?.active == null) { "定时任务已开始准备，请结束后再修改" }
                val decision = ClickScheduleEditPolicy.decide(latest, old, platform.canSchedule(), TimeZone.getDefault().id)
                val updated = latest.copy(id = UUID.randomUUID().toString(), hour = hour,
                    minute = minute, days = selected, enabled = decision.enabled)
                val pending = ClickAlarmState(updated.id, updated.scheduleId, decision.status)
                edit.checkActive()
                check(canEdit()) { "无障碍服务已断开，请恢复后再修改" }
                try {
                    cancelLegacy()
                    old?.next?.let { platform.cancel(it) }
                    edit.checkActive()
                    check(store.save(updated, decision.result.message, alarmState = pending)) { "无法保存任务" }
                    currentCoroutineContext().ensureActive()
                    edit.checkActive()
                    if (updated.enabled) {
                        armNext(updated, null)
                        check(platform.canSchedule()) { "定时权限已关闭，任务未启用" }
                        check(canEdit()) { "无障碍服务已断开，请恢复后重新启用任务" }
                        edit.allowScheduled()
                    } else {
                        edit.checkActive()
                    }
                    // Platform registration is synchronous and need not observe caller cancellation.
                    currentCoroutineContext().ensureActive()
                    decision.result
                } catch (failure: Exception) {
                    val revoked = !edit.isActive()
                    edit.halt()
                    withContext(NonCancellable) cleanup@ {
                        val lostPermission = runCatching { !platform.canSchedule() }.getOrDefault(false)
                        val status = if (lostPermission) ClickAlarmStatus.PERMISSION_REQUIRED else ClickAlarmStatus.SCHEDULE_FAILED
                        val toCancel = listOfNotNull(old?.next, store.alarmState()?.next).distinct()
                        var cancelled = true
                        for (event in toCancel) {
                            try { platform.cancel(event) } catch (cleanup: Exception) {
                                cancelled = false; failure.addSuppressed(cleanup)
                            }
                        }
                        try { cancelLegacy() } catch (cleanup: Exception) {
                            cancelled = false; failure.addSuppressed(cleanup)
                        }
                        val disabled = updated.copy(enabled = false)
                        val message = if (lostPermission) ClickScheduleEditResult.PERMISSION_REQUIRED.message
                            else "时间配置已保留，定时安排失败，请重新启用"
                        val saved = try {
                            store.save(disabled, message,
                                alarmState = ClickAlarmState(disabled.id, disabled.scheduleId, status))
                        } catch (cleanup: Exception) { failure.addSuppressed(cleanup); false }
                        if (failure is CancellationException) throw failure
                        if (saved && cancelled && lostPermission && !revoked)
                            return@cleanup ClickScheduleEditResult.PERMISSION_REQUIRED
                        throw IllegalStateException(if (saved && cancelled) message
                            else "调度状态未确认，请重新停用或启用", failure)
                    }
                }
            }
        }
    }

    suspend fun save(task: ClickTask): ClickTaskSaveResult = withContext(Dispatchers.IO) {
        mutationLock.withLock {
            require(task.protection != null) { ClickTaskOutcome.NEEDS_RECORDING.message }
            require(task.timeZoneId == TimeZone.getDefault().id) { "时区已改变，请重新保存任务" }
            stopCurrent("任务已替换")
            cancelLegacy()
            check(store.save(task.copy(enabled = false))) { "无法保存任务" }
            if (!platform.canSchedule()) {
                saveState(task, ClickAlarmStatus.PERMISSION_REQUIRED, false)
                ClickTaskSaveResult.SAVED_NEEDS_PERMISSION
            } else {
                // A newly saved schedule has a new identity; enable/resume rotates the generation below.
                check(store.save(task.copy(enabled = true))) { "无法启用任务" }
                armNext(task.copy(enabled = true))
                ClickExecutionSession.allowScheduled()
                ClickTaskSaveResult.ENABLED
            }
        }
    }

    suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        mutationLock.withLock {
            val task = checkNotNull(store.load()) { "没有已保存的任务" }
            if (!enabled) {
                disable(task, "任务已停用")
            } else {
                require(task.protection != null) { ClickTaskOutcome.NEEDS_RECORDING.message }
                require(task.timeZoneId == TimeZone.getDefault().id && store.alarmState()?.status != ClickAlarmStatus.TIME_ZONE_CHANGED) {
                    "时区已改变，请重新保存任务"
                }
                stopCurrent("任务已重新启用")
                cancelLegacy()
                if (!platform.canSchedule()) {
                    saveState(task, ClickAlarmStatus.PERMISSION_REQUIRED, false)
                } else {
                    val updated = task.copy(id = UUID.randomUUID().toString(), enabled = true)
                    check(store.save(updated)) { "无法更新任务" }
                    armNext(updated, null)
                    ClickExecutionSession.allowScheduled()
                }
            }
        }
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        mutationLock.withLock {
            try { stopCurrent("任务已删除") } finally {
                try { cancelLegacy() } finally { check(store.clear()) { "无法删除任务" } }
            }
        }
    }

    suspend fun emergencyStop() = withContext(Dispatchers.IO) {
        mutationLock.withLock { store.load()?.let { disable(it, "已紧急停止，任务已停用") } ?: cancelLegacy() }
    }

    private suspend fun disable(task: ClickTask, message: String) {
        // Cancelling a running sequence and cancelling its future alarm are independent of disk success.
        try { stopCurrent(message) } finally {
            try { check(store.save(task.copy(enabled = false), message)) { "无法停用任务" } }
            finally { cancelLegacy() }
        }
    }

    private suspend fun stopCurrent(message: String) {
        ClickExecutionSession.cancelScheduled(message)
        val state = store.alarmState() ?: return
        try {
            state.active?.let { check(store.finishAlarm(it, ClickOutcomeReason.CANCELLED, message)) { "无法终结当前执行" } }
        } finally {
            state.next?.let { platform.cancel(it) }
        }
    }

    private fun saveState(task: ClickTask, status: ClickAlarmStatus, enabled: Boolean? = null) {
        check(store.saveAlarmState(task.id, ClickAlarmState(task.id, task.scheduleId, status), enabled)) { "无法保存定时状态" }
    }

    private fun armNext(task: ClickTask, active: ClickAlarmState? = store.alarmState()) {
        val due = ExactClickPolicy.next(task, clock.wallMillis(), store.closedThrough(), store.consumedAt(task.id))
        val o = ClickAlarmOccurrence(task.id, task.scheduleId, due)
        val pending = ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMING,
            next = o, active = active?.active, phase = active?.phase)
        check(store.saveAlarmState(task.id, pending)) { "无法保存定时安排" }
        try {
            platform.schedule(o)
            check(store.saveAlarmState(task.id, pending.copy(status = ClickAlarmStatus.ARMED))) { "无法确认定时安排" }
        } catch (error: Exception) {
            runCatching { platform.cancel(o) }.exceptionOrNull()?.let { error.addSuppressed(it) }
            if (!store.saveAlarmState(task.id, pending.copy(status = ClickAlarmStatus.SCHEDULE_FAILED, next = null))) {
                error.addSuppressed(IllegalStateException("定时失败状态未能保存"))
            }
            throw error
        }
    }

    internal suspend fun acceptAlarm(o: ClickAlarmOccurrence, receivedAt: Long): ClickTask? {
        var reserved = false
        try {
            return withContext(Dispatchers.IO) {
                mutationLock.withLock {
                    val task = store.load() ?: return@withLock null
                    val state = store.alarmState() ?: return@withLock null
                    if (!task.enabled || !o.matches(task) || state.status != ClickAlarmStatus.ARMED || state.next != o) return@withLock null
                    // Startup may have already closed this overdue event without arranging anything.
                    // Its matching system delivery can advance the future, but can never claim it again.
                    if (state.active == null && o.scheduledAt <= store.closedThrough()) {
                        platform.cancel(o)
                        if (platform.canSchedule()) armNext(task) else saveState(task, ClickAlarmStatus.PERMISSION_REQUIRED, false)
                        return@withLock null
                    }
                    val decision = ExactClickPolicy.evaluate(task, o.scheduledAt, receivedAt, TimeZone.getDefault().id)
                    if (decision.status == ClickScheduleStatus.EARLY) {
                        if (platform.canSchedule()) platform.schedule(o)
                        return@withLock null
                    }
                    if (decision.status !in listOf(ClickScheduleStatus.READY, ClickScheduleStatus.LATE)) return@withLock null
                    if (!store.reserveAlarm(o, receivedAt)) return@withLock null
                    reserved = true
                    try {
                        platform.cancel(o)
                        if (platform.canSchedule()) armNext(task)
                        else {
                            val current = store.alarmState()!!
                            check(store.saveAlarmState(task.id, current.copy(status = ClickAlarmStatus.PERMISSION_REQUIRED))) { "无法保存权限状态" }
                        }
                    } catch (cancelled: CancellationException) {
                        store.finishAlarm(o, ClickOutcomeReason.CANCELLED, "执行已取消，本次计划不会自动重放")
                        throw cancelled
                    } catch (error: Exception) {
                        Logger.e("ClickTaskController", "Unable to arrange next occurrence", error)
                        // Own the current event even if arranging tomorrow failed; the dispatcher closes it on all paths.
                    }
                    task
                }
            }
        } catch (cancelled: CancellationException) {
            // Prompt cancellation can discard the IO return after reserve succeeded.
            if (reserved) withContext(kotlinx.coroutines.NonCancellable) {
                kotlinx.coroutines.withTimeoutOrNull(1_000) {
                    finishAlarm(o, ClickOutcomeReason.CANCELLED, "执行已取消，本次计划不会自动重放")
                }
            }
            throw cancelled
        }
    }

    internal suspend fun finishAlarm(o: ClickAlarmOccurrence, reason: ClickOutcomeReason, message: String) = withContext(Dispatchers.IO) {
        mutationLock.withLock {
            val state = store.alarmState()
            if (state?.active == o) check(store.finishAlarm(o, reason, message)) { "无法保存执行结果" }
        }
    }

    suspend fun reconcile(event: ClickRecoveryEvent = ClickRecoveryEvent.STARTUP) = withContext(Dispatchers.IO) {
        mutationLock.withLock {
            val task = store.load() ?: run { cancelLegacy(); return@withLock }
            var state = store.alarmState()
            if (state == null) {
                cancelLegacy()
                check(store.save(task.copy(enabled = false), if (task.protection == null)
                    ClickTaskOutcome.NEEDS_RECORDING.message else "定时方式已更新，请启用任务")) { "无法迁移旧任务" }
                saveState(task, ClickAlarmStatus.NEEDS_ENABLE, false)
                return@withLock
            }
            if (event == ClickRecoveryEvent.PERMISSION_CHANGED || !platform.canSchedule()) {
                stopCurrent("定时权限已改变，本次已停止")
                saveState(task, if (platform.canSchedule()) ClickAlarmStatus.NEEDS_ENABLE else ClickAlarmStatus.PERMISSION_REQUIRED, false)
                return@withLock
            }
            if (event == ClickRecoveryEvent.ZONE_CHANGED || task.timeZoneId != TimeZone.getDefault().id) {
                stopCurrent("时区已改变，本次已停止")
                saveState(task, ClickAlarmStatus.TIME_ZONE_CHANGED, false)
                return@withLock
            }
            // A foreground Activity can reconcile while a service-owned sequence is still running.
            val run = ClickExecutionSession.state.value
            val live = run.active && !run.manual && run.taskId == task.id && run.scheduleId == task.scheduleId
            if (event == ClickRecoveryEvent.STARTUP && live) return@withLock
            state.active?.let {
                val timeChanged = event == ClickRecoveryEvent.TIME_CHANGED
                ClickExecutionSession.cancelScheduled(if (timeChanged) "系统时间已改变" else "恢复时发现未完成的定时执行")
                check(store.finishAlarm(it, if (timeChanged) ClickOutcomeReason.TIME_CHANGED else ClickOutcomeReason.CANCELLED,
                    if (timeChanged) "系统时间已改变，本次已停止" else "上次执行已中断，本次计划不会自动重放")) { "无法终结中断执行" }
            }
            state = store.alarmState()!!
            if (event == ClickRecoveryEvent.STARTUP) {
                if (!task.enabled) return@withLock
                val next = state.next
                if (state.status == ClickAlarmStatus.ARMED && next != null) {
                    val decision = ExactClickPolicy.evaluate(task, next.scheduledAt, clock.wallMillis(), TimeZone.getDefault().id)
                    if (decision.status in listOf(ClickScheduleStatus.EARLY, ClickScheduleStatus.READY)) return@withLock
                    if (decision.status == ClickScheduleStatus.LATE) {
                        closeMissedNext(task, state)
                        // Keep the exact closed identity and explicit enable intent for BOOT/UPDATE
                        // or the already pending late broadcast. Ordinary startup does not arm.
                        check(store.saveAlarmState(task.id, state.copy(active = null, phase = null))) { "无法保存过期安排身份" }
                        return@withLock
                    }
                }
                state.next?.let { platform.cancel(it) }
                saveState(task, ClickAlarmStatus.SCHEDULE_FAILED)
                return@withLock
            }
            if (!task.enabled || state.status in listOf(ClickAlarmStatus.PERMISSION_REQUIRED,
                    ClickAlarmStatus.NEEDS_ENABLE, ClickAlarmStatus.TIME_ZONE_CHANGED)) return@withLock
            if (event == ClickRecoveryEvent.TIME_CHANGED) ClickExecutionSession.cancelScheduled("系统时间已改变")
            closeMissedNext(task, state)
            state.next?.let { platform.cancel(it) }
            armNext(task, null)
            ClickExecutionSession.allowScheduled()
        }
    }

    private fun closeMissedNext(task: ClickTask, state: ClickAlarmState) {
        val next = state.next ?: return
        if (ExactClickPolicy.evaluate(task, next.scheduledAt, clock.wallMillis(), TimeZone.getDefault().id).status in
                listOf(ClickScheduleStatus.READY, ClickScheduleStatus.LATE) &&
            store.reserveAlarm(next, clock.wallMillis())) {
            check(store.finishAlarm(next, ClickOutcomeReason.START_EXPIRED,
                if (clock.wallMillis() - next.scheduledAt >= ExactClickPolicy.START_WINDOW_MS) "已错过启动时间，本次跳过"
                else "恢复调度时已跳过本次，不自动补执行")) { "无法保存跳过结果" }
        }
    }

    companion object { private val mutationLock = Mutex() }
}
