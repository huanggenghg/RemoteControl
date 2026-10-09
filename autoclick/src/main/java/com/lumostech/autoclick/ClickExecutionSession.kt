package com.lumostech.autoclick

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.lifecycle.lifecycleScope
import com.lumostech.accessibilitybase.utils.Logger
import com.lumostech.accessibilitycore.AccessibilityCoreService
import com.lumostech.accessibilitycore.ClickCounterPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ClickRunState(
    val active: Boolean = false, val manual: Boolean = false, val message: String = "尚未执行",
    val lastTrialResult: String = "", val stopError: String? = null,
    val taskId: String? = null, val scheduleId: String? = null
)

/** Main-thread UI/job ownership; the gate itself also protects starts on worker threads. */
object ClickExecutionSession {
    private val gate = ClickExecutionGate()
    private val mutableState = MutableStateFlow(ClickRunState())
    val state = mutableState.asStateFlow()
    private val stopScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var active: Handle? = null

    class Handle internal constructor(internal val lease: Long, internal val job: Job,
                                     internal val overlay: ClickExecutionOverlay, val manual: Boolean) {
        internal var message = if (manual) "试运行准备中" else "定时执行准备中"
        fun failureAt(point: ClickCounterPoint): String? =
            if (!gate.canContinue(lease)) "已紧急停止" else overlay.failureAt(point)
        fun update(message: String) {
            this.message = message
            overlay.update(message)
            mutableState.value = mutableState.value.copy(active = true, manual = manual, message = message)
        }
    }

    fun allowScheduled() = gate.allowScheduled()

    internal class ScheduleEdit internal constructor(private val lease: Long) {
        fun isActive() = gate.canEdit(lease)
        fun checkActive() = check(isActive()) { "修改期间已停止，请重新打开任务设置" }
        fun allowScheduled() = check(gate.allowScheduledAfterEdit(lease)) { "修改期间已停止，任务未启用" }
        fun halt() = gate.stop()
    }

    internal suspend fun <T> withScheduleEdit(block: suspend (ScheduleEdit) -> T): T {
        val lease = checkNotNull(gate.tryBeginEdit()) { "任务正在执行，请结束后再修改" }
        return try { block(ScheduleEdit(lease)) } finally { gate.finishEdit(lease) }
    }

    suspend fun acquire(service: AccessibilityCoreService, task: ClickTask, manual: Boolean, readyTimeoutMs: Long? = null): Handle? {
        val job = checkNotNull(currentCoroutineContext()[Job])
        return withContext(Dispatchers.Main.immediate) {
            val handle = acquireOnMain(service, task, manual, job) ?: return@withContext null
            try {
                val ready = if (readyTimeoutMs == null) handle.overlay.awaitReady()
                    else if (readyTimeoutMs <= 0) false else withTimeoutOrNull(readyTimeoutMs) { handle.overlay.awaitReady() } == true
                if (ready) handle else {
                    release(handle, "停止按钮不可用，未执行点击")
                    null
                }
            } catch (e: CancellationException) {
                release(handle, "执行已取消")
                throw e
            }
        }
    }

    private fun acquireOnMain(service: AccessibilityCoreService, task: ClickTask, manual: Boolean, job: Job): Handle? {
        val lease = gate.tryStart(manual) ?: return null
        val overlay = ClickExecutionOverlay(service) { emergencyStop(service) }
        if (!overlay.show(task)) {
            gate.finish(lease)
            mutableState.value = mutableState.value.copy(active = false, manual = manual, message = "停止按钮无法安全放置，未执行点击")
            return null
        }
        return Handle(lease, job, overlay, manual).also { handle ->
            active = handle
            mutableState.value = mutableState.value.copy(taskId = task.id, scheduleId = task.scheduleId)
            handle.update(handle.message)
            // Own cleanup before handing the lease back across dispatchers. A
            // cancelled withContext return can discard the caller's result.
            job.invokeOnCompletion {
                stopScope.launch {
                    if (active === handle) release(handle, if (!gate.canContinue(handle.lease))
                        "已紧急停止，不自动重放" else "执行已取消，不自动重放")
                }
            }
        }
    }

    suspend fun release(handle: Handle, message: String = handle.message) = withContext(NonCancellable + Dispatchers.Main.immediate) {
        if (active !== handle) return@withContext
        handle.overlay.close()
        gate.finish(handle.lease)
        active = null
        mutableState.value = mutableState.value.copy(active = false, manual = handle.manual, message = message,
            lastTrialResult = if (handle.manual) message else mutableState.value.lastTrialResult)
    }

    suspend fun cancelScheduled(reason: String) = withContext(Dispatchers.Main.immediate) {
        active?.takeIf { !it.manual }?.let { it.message = reason; it.job.cancel(CancellationException(reason)) }
    }

    /** Invoked by explicit home/overlay actions; cancellation precedes asynchronous persistence. */
    fun emergencyStop(context: Context) {
        gate.stop()
        mutableState.value = mutableState.value.copy(stopError = null)
        active?.let { it.message = "已紧急停止"; it.job.cancel(CancellationException("Emergency stop")) }
        val application = context.applicationContext
        stopScope.launch {
            try {
                ClickTaskController(application).emergencyStop()
            } catch (e: Exception) {
                Logger.e("ClickExecutionSession", "Unable to persist emergency stop", e)
                mutableState.value = mutableState.value.copy(stopError = "点击已停止，但停用或取消任务失败，请重试停止")
            }
        }
    }

    /** Called on Main. The service lifecycle, not the Activity, owns the explicit trial. */
    fun startTrial(context: Context, task: ClickTask): Boolean {
        if (active != null) return false
        val service = AccessibilityCoreService.accessibilityCoreService
        if (service == null || !task.isValid() || task.protection == null) {
            mutableState.value = mutableState.value.copy(message = "无障碍服务或录制环境不可用，请重新录制")
            return false
        }
        val snapshot = task.copy(points = task.points.map { it.copy() },
            protection = task.protection.copy(packages = task.protection.packages.toList()))
        val application = context.applicationContext
        lateinit var handle: Handle
        val job = service.lifecycleScope.launch(start = CoroutineStart.LAZY) { runTrial(application, service, snapshot, handle) }
        if (job.isCancelled) return false
        handle = acquireOnMain(service, snapshot, manual = true, job) ?: run { job.cancel(); return false }
        handle.update("5 秒后试运行，请切回目标页面")
        return job.start()
    }

    private suspend fun runTrial(context: Context, service: AccessibilityCoreService, task: ClickTask, handle: Handle) {
        var result = "试运行未完成"
        var completedPoints = 0
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val startedWall = System.currentTimeMillis()
        val startedElapsed = SystemClock.elapsedRealtime()
        try {
            service.enableProtectedRecording()
            for (seconds in 5 downTo 1) { handle.update("$seconds 秒后试运行"); delay(1_000) }
            val completed = withTimeoutOrNull(9 * 60_000L) {
                service.executeClickSequence(task.points) {
                    val point = task.points[completedPoints]
                    val reason = when {
                        ClickTaskStore(context).load()?.id != task.id -> "任务已删除或替换"
                        AccessibilityCoreService.accessibilityCoreService !== service -> "无障碍服务不可用"
                        !ClickSchedulePolicy.clockUnchanged(startedWall, startedElapsed, System.currentTimeMillis(), SystemClock.elapsedRealtime()) -> "系统时间已改变"
                        else -> handle.failureAt(point) ?: task.protection!!.failureAt(completedPoints,
                            service.currentClickEnvironment(), power.isInteractive, keyguard.isKeyguardLocked, point)?.message
                    }
                    if (reason != null) { result = "$reason，试运行已停止（已完成 $completedPoints 个点击）"; false }
                    else { completedPoints++; handle.update("试运行 $completedPoints / ${task.points.size}"); true }
                }
            } == true
            if (completed) result = "试运行：${ClickTaskOutcome.COMPLETED.message}"
            else if (result == "试运行未完成") result = "试运行：${ClickTaskOutcome.GESTURE_FAILED.message}"
        } catch (e: CancellationException) {
            result = if (!gate.canContinue(handle.lease)) "已紧急停止，试运行不自动重放" else "试运行已取消，不自动重放"
            throw e
        } catch (e: Exception) {
            Logger.e("ClickExecutionSession", "Trial failed", e)
            result = "试运行异常，未完成"
        } finally {
            release(handle, result)
        }
    }
}
