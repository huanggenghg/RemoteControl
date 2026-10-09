package com.lumostech.autoclick

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import com.lumostech.accessibilitybase.utils.Logger
import com.lumostech.accessibilitycore.AccessibilityCoreService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.TimeZone

internal class ScheduledClickExecutor(
    private val context: Context,
    private val store: ClickTaskStore,
    private val controller: ClickTaskController,
    private val clock: ClickClock = SystemClickClock
) {
    suspend fun execute(service: AccessibilityCoreService, task: ClickTask, occurrence: ClickAlarmOccurrence,
                        receivedAt: Long, receivedElapsed: Long) {
        var handle: ClickExecutionSession.Handle? = null
        var firstDispatchAt: Long? = null
        var nextPoint = 0
        var code = ClickOutcomeReason.UNKNOWN
        var message = "执行检查异常，未执行"
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        fun startupOpen(): Boolean = ExactClickPolicy.startupOpen(occurrence.scheduledAt, receivedAt,
            receivedElapsed, clock.wallMillis(), clock.elapsedMillis()) &&
            ExactClickPolicy.evaluate(task, occurrence.scheduledAt, clock.wallMillis(), TimeZone.getDefault().id).status == ClickScheduleStatus.READY
        fun fail(reason: ClickOutcomeReason, text: String): Boolean { code = reason; message = text; return false }
        fun canContinue(): Boolean {
            val current = store.load()
            val now = clock.wallMillis()
            if (current?.id != task.id || current.scheduleId != task.scheduleId || !current.enabled || store.alarmState()?.active != occurrence)
                return fail(ClickOutcomeReason.CANCELLED, "任务已停用、删除或替换")
            if (AccessibilityCoreService.accessibilityCoreService !== service)
                return fail(ClickOutcomeReason.ACCESSIBILITY_DISABLED, ClickTaskOutcome.SERVICE_UNAVAILABLE.message)
            if (!ClickSchedulePolicy.clockUnchanged(receivedAt, receivedElapsed, now, clock.elapsedMillis()) ||
                !ClickSchedulePolicy.isSameOccurrence(task, occurrence.scheduledAt, now))
                return fail(ClickOutcomeReason.TIME_CHANGED, "日期、时间或时区已改变，已停止（已完成 $nextPoint 个点击）")
            if (firstDispatchAt == null && !startupOpen())
                return fail(ClickOutcomeReason.START_EXPIRED, "已错过启动时间，本次跳过")
            val point = task.points.getOrNull(nextPoint)
                ?: return fail(ClickOutcomeReason.NEEDS_RECORDING, "录制位置无效")
            handle?.failureAt(point)?.let { return fail(ClickOutcomeReason.STOP_CONTROL_UNAVAILABLE, "$it，已停止（已完成 $nextPoint 个点击）") }
            val protection = task.protection ?: return fail(ClickOutcomeReason.NEEDS_RECORDING, ClickTaskOutcome.NEEDS_RECORDING.message)
            protection.failureAt(nextPoint, service.currentClickEnvironment(), power.isInteractive, keyguard.isKeyguardLocked, point)?.let {
                return fail(ClickOutcomeReason.fromProtection(it), "${it.message}，已停止（已完成 $nextPoint 个点击）")
            }
            return true
        }
        try {
            if (!startupOpen()) { fail(ClickOutcomeReason.START_EXPIRED, "已错过启动时间，本次跳过"); return }
            if (ClickExecutionSession.state.value.active) { fail(ClickOutcomeReason.EXECUTION_BUSY, "其他点击正在执行，本次跳过"); return }
            service.enableProtectedRecording()
            val remaining = minOf(ExactClickPolicy.remaining(occurrence.scheduledAt, clock.wallMillis()),
                ExactClickPolicy.remaining(occurrence.scheduledAt, receivedAt) - (clock.elapsedMillis() - receivedElapsed))
            handle = ClickExecutionSession.acquire(service, task, manual = false, readyTimeoutMs = remaining)
            if (handle == null) {
                if (!startupOpen()) fail(ClickOutcomeReason.START_EXPIRED, "已错过启动时间，本次跳过")
                else fail(ClickOutcomeReason.STOP_CONTROL_UNAVAILABLE, "停止按钮不可用，本次未开始")
                return
            }
            if (!canContinue()) return
            when (store.claimAlarm(occurrence)) {
                ClickExecutionClaim.CLAIMED -> Unit
                ClickExecutionClaim.STORAGE_FAILED -> { fail(ClickOutcomeReason.UNKNOWN, "无法保存执行记录，未执行点击"); return }
                else -> { fail(ClickOutcomeReason.CANCELLED, "本次计划已失效，未重复执行"); return }
            }
            val completed = withTimeoutOrNull(9 * 60_000L) {
                service.executeClickSequence(task.points, beforeDispatch = {
                    val now = clock.wallMillis()
                    val elapsed = clock.elapsedMillis()
                    if (firstDispatchAt == null &&
                        (!ExactClickPolicy.startupOpen(occurrence.scheduledAt, receivedAt, receivedElapsed, now, elapsed) ||
                            ExactClickPolicy.evaluate(task, occurrence.scheduledAt, now, TimeZone.getDefault().id).status != ClickScheduleStatus.READY)) {
                        fail(ClickOutcomeReason.START_EXPIRED, "已错过启动时间，本次跳过")
                    } else {
                        if (firstDispatchAt == null) firstDispatchAt = now
                        nextPoint++
                        true
                    }
                }) {
                    if (canContinue()) { handle!!.update("定时执行 ${nextPoint + 1} / ${task.points.size}"); true } else false
                }
            } == true
            if (completed) { code = ClickOutcomeReason.COMPLETED; message = ClickTaskOutcome.COMPLETED.message }
            else if (code == ClickOutcomeReason.UNKNOWN) { code = ClickOutcomeReason.DISPATCH_FAILED; message = ClickTaskOutcome.GESTURE_FAILED.message }
        } catch (cancelled: CancellationException) {
            code = ClickOutcomeReason.CANCELLED; message = "执行已取消，本次计划不会自动重放"
            throw cancelled
        } catch (error: Exception) {
            Logger.e("ScheduledClickExecutor", "Protected execution failed", error)
        } finally {
            withContext(NonCancellable) {
                try {
                    firstDispatchAt?.let { if (!store.recordFirstDispatch(occurrence, it))
                        Logger.w("ScheduledClickExecutor", "Unable to persist first dispatch trace") }
                    controller.finishAlarm(occurrence, code, message)
                } finally { handle?.let { ClickExecutionSession.release(it, message) } }
            }
        }
    }
}
