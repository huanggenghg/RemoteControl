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
import java.util.TimeZone

class ConfirmEventHandler(
    private val binding: LayoutConfirmBinding,
    private val scope: CoroutineScope,
    private val controller: ClickTaskController,
    private val onTimingPermissionRequired: () -> Unit = {},
    private val recoveryViewModel: LegacyTaskRecoveryViewModel? = null
) {
    fun onConfirmClick(view: View) {
        val days = binding.weekdaysPicker.selectedDays.toSet()
        val service = AccessibilityCoreService.accessibilityCoreService
        val snapshot = service?.getRecordedSnapshot()
        val points = snapshot?.points.orEmpty()
        if (days.isEmpty() || points.isEmpty()) {
            Toast.makeText(view.context, if (days.isEmpty()) "请至少选择一个执行星期" else "请先录制点击位置", Toast.LENGTH_SHORT).show()
            return
        }
        val protection = snapshot?.protection
        if (protection == null) {
            Toast.makeText(view.context, ClickTaskOutcome.NEEDS_RECORDING.message, Toast.LENGTH_LONG).show()
            return
        }
        val session = recoveryViewModel?.session
        if (session != null) {
            if (recoveryViewModel.busy) return
            if (snapshot?.sessionId != session.recoveryId) {
                showRecordingError(view, "当前录制不属于本次恢复，请重新确认")
                return
            }
            val candidate = ClickTask(session.replacementTaskId, binding.timePicker.hour, binding.timePicker.minute,
                days, points, enabled = false, protection = protection, timeZoneId = TimeZone.getDefault().id,
                scheduleId = session.replacementTaskId)
            binding.confirm.isEnabled = false
            binding.cancel.isEnabled = false
            binding.confirm.text = "正在保存…"
            recoveryViewModel.save(candidate, snapshot.sessionId)
            return
        }
        if (snapshot?.sessionId != null) {
            showRecordingError(view, "这是恢复录制草稿，请先进入任务恢复")
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
                val result = controller.save(task)
                ViewModelMain.isShowFloatWindow.value = false
                ViewModelMain.isShowCustomFloatWindow.value = false
                Toast.makeText(view.context, if (result == ClickTaskSaveResult.ENABLED)
                    "任务已保存，须在计划时间后 5 秒内开始" else "任务已保存，开启定时权限后请启用", Toast.LENGTH_LONG).show()
                if (result == ClickTaskSaveResult.SAVED_NEEDS_PERMISSION) onTimingPermissionRequired()
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
        recoveryViewModel?.session?.let {
            if (recoveryViewModel.busy) return
            recoveryViewModel.updateTime(binding.timePicker.hour, binding.timePicker.minute, binding.weekdaysPicker.selectedDays.toSet())
        }
        ViewModelMain.isShowCustomFloatWindow.value = false
        ViewModelMain.isShowFloatWindow.value = true
    }

    private fun showRecordingError(view: View, message: String) = Toast.makeText(view.context, message, Toast.LENGTH_LONG).show()
}
