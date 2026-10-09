package com.lumostech.autoclick

import android.content.Context
import androidx.lifecycle.lifecycleScope
import com.lumostech.accessibilitycore.AccessibilityCoreService
import kotlinx.coroutines.*

internal object ClickAlarmDispatcher {
    suspend fun dispatch(context: Context, occurrence: ClickAlarmOccurrence, clock: ClickClock = SystemClickClock,
                         receivedAt: Long = clock.wallMillis(), receivedElapsed: Long = clock.elapsedMillis()) {
        val store = ClickTaskStore(context)
        val controller = ClickTaskController(context)
        (context.applicationContext as? AutoclickApp)?.awaitStartup()
        val task = controller.acceptAlarm(occurrence, receivedAt) ?: return
        var handedOff = false
        var reason = ClickOutcomeReason.CONNECTION_TIMEOUT
        var message = ClickTaskOutcome.SERVICE_CONNECTION_TIMEOUT.message
        fun open() = ExactClickPolicy.startupOpen(occurrence.scheduledAt, receivedAt, receivedElapsed,
            clock.wallMillis(), clock.elapsedMillis()) && store.load()?.let { occurrence.matches(it) && it.enabled } == true
        try {
            if (!AndroidClickAlarmPlatform(context).canSchedule()) {
                reason = ClickOutcomeReason.EXACT_PERMISSION_REQUIRED; message = "定时权限未开启，本次跳过"; return
            }
            if (!open()) { reason = ClickOutcomeReason.START_EXPIRED; message = "已错过启动时间，本次跳过"; return }
            val remaining = minOf(ExactClickPolicy.remaining(occurrence.scheduledAt, clock.wallMillis()),
                ExactClickPolicy.remaining(occurrence.scheduledAt, receivedAt) - (clock.elapsedMillis() - receivedElapsed))
            val service = ClickServiceConnection.await(context, ::open, remaining)
            if (service == null) {
                if (!open()) { reason = ClickOutcomeReason.START_EXPIRED; message = "已错过启动时间，本次跳过" }
                else if (ClickServiceConnection.readiness(context) == ClickServiceReadiness.DISABLED) {
                    reason = ClickOutcomeReason.ACCESSIBILITY_DISABLED; message = ClickTaskOutcome.SERVICE_UNAVAILABLE.message
                }
                return
            }
            withContext(Dispatchers.Main.immediate) {
                if (!open() || AccessibilityCoreService.accessibilityCoreService !== service) return@withContext
                val job = service.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    ScheduledClickExecutor(context, store, controller, clock).execute(service, task, occurrence, receivedAt, receivedElapsed)
                }
                handedOff = !job.isCancelled
            }
            if (!handedOff && !open()) { reason = ClickOutcomeReason.START_EXPIRED; message = "已错过启动时间，本次跳过" }
        } catch (cancelled: CancellationException) {
            reason = ClickOutcomeReason.CANCELLED; message = "执行已取消，本次计划不会自动重放"; throw cancelled
        } finally {
            if (!handedOff) withContext(NonCancellable) {
                withTimeoutOrNull(1_000) { controller.finishAlarm(occurrence, reason, message) }
            }
        }
    }
}
