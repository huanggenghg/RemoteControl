package com.lumostech.autoclick

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.lumostech.accessibilitybase.utils.Logger
import com.lumostech.accessibilitycore.AccessibilityCoreService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class ClickPeriodicWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = ClickTaskStore(applicationContext)
        val task = store.load()
        val taskId = inputData.getString(TASK_ID)
        if (task == null || task.id != taskId) return Result.failure(output("任务不存在或已被替换"))
        if (!task.enabled) return Result.success(output(ClickTaskOutcome.DISABLED.message))
        var claimed = false
        var occurrenceAt: Long? = null
        var run: ClickExecutionSession.Handle? = null
        var runMessage = "定时执行未完成"
        fun finish(message: String, success: Boolean, reason: ClickOutcomeReason = ClickOutcomeReason.UNKNOWN): Result {
            occurrenceAt?.let { store.recordOccurrenceOutcome(task.id, it, claimed, message, reason) }
                ?: store.recordOutcome(task.id, message, reason)
            Logger.i(TAG, message)
            runMessage = message
            return if (success) Result.success(output(message)) else Result.failure(output(message))
        }
        return try {
            val protection = task.protection
                ?: return finish(ClickTaskOutcome.NEEDS_RECORDING.message, false, ClickOutcomeReason.NEEDS_RECORDING)
            val schedule = ClickSchedulePolicy.evaluate(task, System.currentTimeMillis())
            occurrenceAt = schedule.scheduledAt.takeIf { it != 0L }
            ClickTaskController(applicationContext).alignNextOccurrence(task.id, id, System.currentTimeMillis())
            if (schedule.scheduledAt != 0L && store.wasConsumed(task.id, schedule.scheduledAt)) {
                return Result.success(output("本次计划已开始过，未重复执行"))
            }
            if (schedule.status != ClickScheduleStatus.READY) {
                return finish(schedule.status.message, schedule.status != ClickScheduleStatus.INVALID,
                    when (schedule.status) {
                        ClickScheduleStatus.LATE -> ClickOutcomeReason.START_EXPIRED
                        ClickScheduleStatus.TIME_ZONE_CHANGED -> ClickOutcomeReason.TIME_CHANGED
                        else -> ClickOutcomeReason.UNKNOWN
                    })
            }
            val waitWall = System.currentTimeMillis()
            val waitElapsed = SystemClock.elapsedRealtime()
            var waitAbort: String? = null
            var waitReason = ClickOutcomeReason.UNKNOWN
            var staleWhileWaiting = false
            fun canWait(): Boolean {
                val current = store.load()
                val now = System.currentTimeMillis()
                val currentSchedule = ClickSchedulePolicy.evaluate(task, now)
                staleWhileWaiting = current?.id != task.id || !current.enabled
                waitAbort = when {
                    staleWhileWaiting -> "任务已停用、删除或替换"
                    !ClickSchedulePolicy.clockUnchanged(waitWall, waitElapsed, now, SystemClock.elapsedRealtime()) ->
                        "等待服务期间系统时间已改变，本次未执行"
                    currentSchedule.status != ClickScheduleStatus.READY -> currentSchedule.status.message
                    currentSchedule.scheduledAt != schedule.scheduledAt -> "日期已改变，本次未执行"
                    else -> null
                }
                waitReason = when {
                    staleWhileWaiting -> ClickOutcomeReason.UNKNOWN
                    waitAbort == null -> ClickOutcomeReason.UNKNOWN
                    currentSchedule.status == ClickScheduleStatus.LATE -> ClickOutcomeReason.START_EXPIRED
                    else -> ClickOutcomeReason.TIME_CHANGED
                }
                return waitAbort == null
            }
            val readiness = withContext(Dispatchers.Main.immediate) { ClickServiceConnection.readiness(applicationContext) }
            if (readiness == ClickServiceReadiness.CONNECTING) {
                Logger.i(TAG, "等待无障碍服务连接，最多 10 秒")
            }
            val service = ClickServiceConnection.await(applicationContext, ::canWait)
            if (service == null) {
                withContext(Dispatchers.Main.immediate) { canWait() }
                if (staleWhileWaiting) return Result.success(output(waitAbort!!))
                if (waitAbort != null) return finish(waitAbort!!, false, waitReason)
                val currentReadiness = withContext(Dispatchers.Main.immediate) { ClickServiceConnection.readiness(applicationContext) }
                return finish(if (currentReadiness == ClickServiceReadiness.DISABLED) ClickTaskOutcome.SERVICE_UNAVAILABLE.message
                    else ClickTaskOutcome.SERVICE_CONNECTION_TIMEOUT.message, false,
                    if (currentReadiness == ClickServiceReadiness.DISABLED) ClickOutcomeReason.ACCESSIBILITY_DISABLED
                    else ClickOutcomeReason.CONNECTION_TIMEOUT)
            }
            withContext(Dispatchers.Main.immediate) { service.enableProtectedRecording() }
            run = ClickExecutionSession.acquire(service, task, manual = false)
                ?: return finish("其他点击正在执行、已紧急停用或停止按钮不可用，本次未开始", true)
            val power = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            val keyguard = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            var nextPoint = 0
            var stopReason: String? = null
            var stopCode = ClickOutcomeReason.UNKNOWN
            // Keep the reconnect anchor across overlay acquisition and gestures.
            val startWallTime = waitWall
            val startElapsedTime = waitElapsed

            fun canContinue(): Boolean {
                val current = store.load()
                val now = System.currentTimeMillis()
                stopCode = ClickOutcomeReason.UNKNOWN
                val reason = when {
                    current?.id != task.id || !current.enabled -> "任务已停用、删除或替换"
                    AccessibilityCoreService.accessibilityCoreService !== service -> {
                        stopCode = if (ClickServiceConnection.readiness(applicationContext) == ClickServiceReadiness.DISABLED)
                            ClickOutcomeReason.ACCESSIBILITY_DISABLED else ClickOutcomeReason.UNKNOWN
                        ClickTaskOutcome.SERVICE_UNAVAILABLE.message
                    }
                    !ClickSchedulePolicy.clockUnchanged(startWallTime, startElapsedTime, now, SystemClock.elapsedRealtime()) ||
                        !ClickSchedulePolicy.isSameOccurrence(task, schedule.scheduledAt, now) -> {
                        stopCode = ClickOutcomeReason.TIME_CHANGED
                        "日期、时间或时区已改变"
                    }
                    nextPoint == 0 && ClickSchedulePolicy.evaluate(task, now).status != ClickScheduleStatus.READY -> {
                        stopCode = ClickOutcomeReason.START_EXPIRED
                        "已超过计划时间 15 分钟"
                    }
                    else -> task.points.getOrNull(nextPoint)?.let { run!!.failureAt(it) }
                        ?: protection.failureAt(nextPoint, service.currentClickEnvironment(), power.isInteractive, keyguard.isKeyguardLocked,
                            task.points.getOrNull(nextPoint))?.let {
                            stopCode = ClickOutcomeReason.fromProtection(it)
                            it.message
                        }
                }
                if (reason != null) {
                    stopReason = "$reason，已停止（已完成 $nextPoint 个点击）"
                    return false
                }
                return true
            }
            if (!withContext(Dispatchers.Main.immediate) { canContinue() }) return finish(stopReason!!, false, stopCode)
            when (store.claimExecution(task.id, schedule.scheduledAt)) {
                ClickExecutionClaim.ALREADY_CONSUMED -> return Result.success(output("本次计划已开始过，未重复执行"))
                ClickExecutionClaim.STALE_TASK -> return Result.failure(output("任务已停用、删除或替换"))
                ClickExecutionClaim.STORAGE_FAILED -> return finish("无法保存执行记录，未执行点击", false)
                ClickExecutionClaim.CLAIMED -> claimed = true
            }
            val completed = withTimeoutOrNull(9 * 60_000L) {
                service.executeClickSequence(task.points) {
                    if (canContinue()) {
                        nextPoint++
                        run!!.update("定时执行 $nextPoint / ${task.points.size}")
                        true
                    } else false
                }
            } == true
            val message = stopReason ?: if (completed) ClickTaskOutcome.COMPLETED.message else ClickTaskOutcome.GESTURE_FAILED.message
            finish(message, completed, if (stopReason != null) stopCode else if (completed) ClickOutcomeReason.COMPLETED else ClickOutcomeReason.DISPATCH_FAILED)
        } catch (e: CancellationException) {
            runMessage = "执行已取消，本次计划不会自动重放"
            if (claimed) occurrenceAt?.let { store.recordOccurrenceOutcome(task.id, it, true, "执行已取消，本次计划不会自动重放", ClickOutcomeReason.CANCELLED) }
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Click task failed", e)
            finish(if (claimed) "执行异常，未完成；本次计划不会自动重放" else "执行检查异常，未执行", false)
        } finally {
            withContext(NonCancellable) {
                run?.let { ClickExecutionSession.release(it, runMessage) }
                try {
                    ClickTaskController(applicationContext).alignNextOccurrence(task.id, id, System.currentTimeMillis())
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to realign next occurrence", e)
                }
            }
        }
    }

    private fun output(message: String): Data = Data.Builder().putString("outcome", message).build()

    companion object {
        const val TAG = "ClickPeriodicWorker"
        const val TASK_ID = "task_id"
    }
}
