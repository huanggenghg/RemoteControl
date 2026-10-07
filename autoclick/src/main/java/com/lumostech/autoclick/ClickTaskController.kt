package com.lumostech.autoclick

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.UUID

class ClickTaskController internal constructor(private val store: ClickTaskStore, private val workManager: WorkManager) {
    constructor(context: Context) : this(ClickTaskStore(context), WorkManager.getInstance(context.applicationContext))

    suspend fun save(task: ClickTask) = withContext(Dispatchers.IO) {
        mutationLock.withLock {
            require(task.protection != null) { ClickTaskOutcome.NEEDS_RECORDING.message }
            check(store.save(task)) { "无法保存任务" }
            try {
                enqueue(task)
                ClickExecutionSession.allowScheduled()
            } catch (e: Exception) {
                store.save(task.copy(enabled = false), "调度失败，请重新启用", ClickOutcomeReason.SCHEDULING_FAILED)
                throw e
            }
        }
    }

    suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        mutationLock.withLock {
            val task = checkNotNull(store.load()) { "没有已保存的任务" }
            if (enabled) require(task.protection != null) { ClickTaskOutcome.NEEDS_RECORDING.message }
            val updated = task.copy(id = if (enabled) UUID.randomUUID().toString() else task.id, enabled = enabled)
            check(store.save(updated, if (enabled) "等待下次执行" else "任务已停用")) { "无法更新任务" }
            try {
                if (enabled) {
                    enqueue(updated)
                    ClickExecutionSession.allowScheduled()
                }
                else workManager.cancelUniqueWork(ClickPeriodicWorker.TAG).result.get()
            } catch (e: Exception) {
                store.save(updated.copy(enabled = false), "调度失败，请重新启用", ClickOutcomeReason.SCHEDULING_FAILED)
                throw e
            }
        }
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        mutationLock.withLock {
            check(store.clear()) { "无法删除任务" }
            workManager.cancelUniqueWork(ClickPeriodicWorker.TAG).result.get()
        }
    }

    suspend fun emergencyStop() = withContext(Dispatchers.IO) {
        mutationLock.withLock {
            try {
                store.load()?.let { task ->
                    check(store.save(task.copy(enabled = false), "已紧急停止，任务已停用")) { "无法停用任务" }
                }
            } finally {
                // Persistence failure must not skip the independent cancellation.
                workManager.cancelUniqueWork(ClickPeriodicWorker.TAG).result.get()
            }
        }
    }

    /** Preferences and WorkManager use separate transactions; repair interrupted saves on startup. */
    suspend fun reconcile() = withContext(Dispatchers.IO) {
        mutationLock.withLock {
            val task = store.load()
            if (task != null && task.protection == null) {
                check(store.save(task.copy(enabled = false), ClickTaskOutcome.NEEDS_RECORDING.message)) { "无法停用旧任务" }
                workManager.cancelUniqueWork(ClickPeriodicWorker.TAG).result.get()
            } else if (task == null || !task.enabled) {
                workManager.cancelUniqueWork(ClickPeriodicWorker.TAG).result.get()
            } else {
                val exists = workManager.getWorkInfosForUniqueWork(ClickPeriodicWorker.TAG).get()
                    .any { !it.state.isFinished && task.id in it.tags && SCHEDULE_VERSION_TAG in it.tags }
                if (!exists) {
                    try {
                        enqueue(task)
                    } catch (e: Exception) {
                        store.save(task.copy(enabled = false), "恢复调度失败，请重新启用", ClickOutcomeReason.SCHEDULING_FAILED)
                        throw e
                    }
                }
            }
        }
    }

    private fun enqueue(task: ClickTask) {
        val now = System.currentTimeMillis()
        val request = PeriodicWorkRequestBuilder<ClickPeriodicWorker>(1, TimeUnit.DAYS)
            .setInputData(Data.Builder().putString(ClickPeriodicWorker.TASK_ID, task.id).build())
            .setInitialDelay(ClickSchedulePolicy.nextOccurrence(task, now) - now, TimeUnit.MILLISECONDS)
            .addTag(ClickPeriodicWorker.TAG)
            .addTag(task.id)
            .addTag(SCHEDULE_VERSION_TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(ClickPeriodicWorker.TAG, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request).result.get()
    }

    /** Update the exact periodic work, never a replaced task or a one-time test/manual work. */
    suspend fun alignNextOccurrence(taskId: String, workId: UUID, now: Long) = withContext(Dispatchers.IO) {
        mutationLock.withLock {
            val task = store.load()
            if (task?.id != taskId || !task.enabled || task.protection == null) return@withLock
            val info = workManager.getWorkInfoById(workId).get() ?: return@withLock
            if (info.state.isFinished || info.periodicityInfo == null || taskId !in info.tags) return@withLock
            val next = ClickSchedulePolicy.nextOccurrence(task, now)
            val request = PeriodicWorkRequestBuilder<ClickPeriodicWorker>(1, TimeUnit.DAYS)
                .setId(workId)
                .setInputData(Data.Builder().putString(ClickPeriodicWorker.TASK_ID, task.id).build())
                .addTag(ClickPeriodicWorker.TAG).addTag(task.id).addTag(SCHEDULE_VERSION_TAG)
                .setNextScheduleTimeOverride(next).build()
            workManager.updateWork(request).get()
        }
    }

    companion object {
        private val mutationLock = Mutex()
        private const val SCHEDULE_VERSION_TAG = "autoclick-wall-clock-v1"
    }
}
