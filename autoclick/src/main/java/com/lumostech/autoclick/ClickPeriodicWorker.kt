package com.lumostech.autoclick

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters

/** Compatibility only: persisted legacy work can never dispatch a gesture. */
class ClickPeriodicWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success(
        Data.Builder().putString("outcome", "旧定时入口已停用，请启用新定时任务").build())
    companion object {
        const val TAG = "ClickPeriodicWorker"
        const val TASK_ID = "task_id"
    }
}
