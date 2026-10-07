package com.lumostech.autoclick

import android.view.View
import android.widget.Toast
import com.lumostech.accessibilitycore.AccessibilityCoreService
import com.lumostech.accessibilitycore.ViewModelMain
import com.lumostech.autoclick.databinding.LayoutConfirmBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

class ConfirmEventHandler(
    private val binding: LayoutConfirmBinding,
    private val scope: CoroutineScope,
    private val controller: ClickTaskController
) {
    fun onConfirmClick(view: View) {
        val days = binding.weekdaysPicker.selectedDays.toSet()
        val service = AccessibilityCoreService.accessibilityCoreService
        val points = service?.getRecordedClickPoints().orEmpty()
        if (days.isEmpty() || points.isEmpty()) {
            Toast.makeText(view.context, if (days.isEmpty()) "请至少选择一个执行星期" else "请先录制点击位置", Toast.LENGTH_SHORT).show()
            return
        }
        val protection = service?.getRecordedProtection()
        if (protection == null) {
            Toast.makeText(view.context, ClickTaskOutcome.NEEDS_RECORDING.message, Toast.LENGTH_LONG).show()
            return
        }
        val task = ClickTask(UUID.randomUUID().toString(), binding.timePicker.hour, binding.timePicker.minute, days, points,
            protection = protection)
        if (!task.isValid()) {
            Toast.makeText(view.context, "录制最多 200 个点击，时长不超过 8 分钟", Toast.LENGTH_LONG).show()
            return
        }
        binding.confirm.isEnabled = false
        binding.cancel.isEnabled = false
        scope.launch {
            try {
                controller.save(task)
                ViewModelMain.isShowFloatWindow.value = false
                ViewModelMain.isShowCustomFloatWindow.value = false
                Toast.makeText(view.context, "任务已保存，延后超过 15 分钟将跳过", Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(view.context, "保存失败：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.confirm.isEnabled = true
                binding.cancel.isEnabled = true
            }
        }
    }

    fun onCancelClick(view: View) {
        ViewModelMain.isShowCustomFloatWindow.value = false
        ViewModelMain.isShowFloatWindow.value = true
    }
}
