package com.lumostech.autoclick

import android.content.Context
import android.util.Log
import android.view.View
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.lumostech.accessibilitycore.ViewModelMain
import com.lumostech.autoclick.databinding.LayoutConfirmBinding
import java.util.Calendar
import java.util.concurrent.TimeUnit


class ConfirmEventHandler(private val layoutConfirmBinding: LayoutConfirmBinding) {
    fun onConfirmClick(view: View) {
        removeFloatWindow()
        Log.i(
            "ConfirmEventHandler",
            "onConfirmClick: ${layoutConfirmBinding.timePicker.hour}:${layoutConfirmBinding.timePicker.minute}"
        )
        layoutConfirmBinding.weekdaysPicker.selectedDaysText.map {
            Log.i(
                "ConfirmEventHandler",
                "onConfirmClick: selectedDay: $it"
            )
        }
        setClickPeriodicWorker(view.context)
    }

    fun onCancelClick(view: View) {
        removeFloatWindow()
    }

    private fun setClickPeriodicWorker(context: Context) {
        val data = Data.Builder()
            .putIntArray(
                ClickPeriodicWorker.TARGET_DAYS_OF_WEEK,
                layoutConfirmBinding.weekdaysPicker.selectedDays.toIntArray()
            )
            .build()

        val currentDate: Calendar = Calendar.getInstance()
        val dueDate: Calendar = currentDate.clone() as Calendar

        dueDate.set(Calendar.HOUR_OF_DAY, layoutConfirmBinding.timePicker.hour)
        dueDate.set(Calendar.MINUTE, layoutConfirmBinding.timePicker.minute)
        dueDate.set(Calendar.SECOND, 0)
        // 如果当前时间已经过了今天的设置的时刻，则将执行日期设置为明天
        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }
        val initialDelay = dueDate.getTimeInMillis() - currentDate.getTimeInMillis()
        val build: PeriodicWorkRequest =
            PeriodicWorkRequest.Builder(ClickPeriodicWorker::class.java, 1, TimeUnit.DAYS)
                .addTag(ClickPeriodicWorker.TAG)
                .setInputData(data)
                .setInitialDelay(
                    initialDelay, // 设置初始延迟，实现大致触发
                    TimeUnit.MILLISECONDS
                )
                .build()
        val instance = WorkManager.getInstance(context)
        instance.enqueueUniquePeriodicWork(
            ClickPeriodicWorker.TAG,
            ExistingPeriodicWorkPolicy.REPLACE,
            build
        )
    }

    private fun removeFloatWindow() {
        ViewModelMain.isShowFloatWindow.postValue(false)
        ViewModelMain.isShowCustomFloatWindow.postValue(false)
    }
}