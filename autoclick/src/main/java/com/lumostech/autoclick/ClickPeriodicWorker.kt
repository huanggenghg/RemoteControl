package com.lumostech.autoclick

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.lumostech.accessibilitycore.AccessibilityCoreService
import java.util.Calendar


class ClickPeriodicWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        // 执行后台任务逻辑
        Log.d(TAG, "doWork:")
        val targetDays = inputData.getIntArray(TARGET_DAYS_OF_WEEK)
        if (targetDays == null || targetDays.isEmpty()) {
            Log.i(TAG, "doWork: targetDays is null or empty.")
            return Result.success()
        }

        if (isInTargetDay(targetDays)) {
            Log.i(TAG, "doWork: isInTargetDay.")
            AccessibilityCoreService.accessibilityCoreService?.dispatchClickPointsEvent()
        } else {
            Log.i(TAG, "doWork: is not inTargetDay.")
        }
        return Result.success()
    }

    private fun isInTargetDay(targetDays: IntArray): Boolean {
        val calendar: Calendar = Calendar.getInstance()
        val dayOfWeek: Int = calendar.get(Calendar.DAY_OF_WEEK)
        var isTargetDay = false
        Log.i(TAG, "isInTargetDay: targetDays=${targetDays.contentToString()}")
        Log.i(TAG, "isInTargetDay: dayOfWeek=$dayOfWeek")
        for (targetDay in targetDays) {
            if (dayOfWeek == targetDay) {
                isTargetDay = true
                break
            }
        }
        return isTargetDay
    }

    companion object {
        const val TAG = "ClickPeriodicWorker"
        const val TARGET_DAYS_OF_WEEK = "TARGET_DAYS_OF_WEEK"
    }

}
