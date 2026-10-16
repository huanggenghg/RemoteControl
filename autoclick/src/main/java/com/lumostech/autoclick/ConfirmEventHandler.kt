package com.lumostech.autoclick

import android.view.View
import android.widget.Toast
import com.lumostech.accessibilitycore.AccessibilityCoreService
import com.lumostech.accessibilitycore.ViewModelMain
import com.lumostech.autoclick.databinding.LayoutConfirmBinding
import com.lumostech.autoclick.ui.readScheduleSelection
import com.lumostech.autoclick.ui.setScheduleBusy
import com.lumostech.autoclick.ui.showScheduleError
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
    private var saving = false

    fun onConfirmClick(view: View) {
        if (saving || recoveryViewModel?.busy == true) return
        val selection = binding.readScheduleSelection()
        selection.validationError?.let { binding.showScheduleError(it); return }
        binding.showScheduleError(null)
        val days = selection.days
        val service = AccessibilityCoreService.accessibilityCoreService
        val snapshot = service?.getRecordedSnapshot()
        val points = snapshot?.points.orEmpty()
        if (points.isEmpty()) {
            Toast.makeText(view.context, "请先录制点击位置", Toast.LENGTH_SHORT).show()
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
            val candidate = ClickTask(session.replacementTaskId, selection.hour, selection.minute,
                days, points, enabled = false, protection = protection, timeZoneId = TimeZone.getDefault().id,
                scheduleId = session.replacementTaskId)
            binding.setScheduleBusy(true)
            binding.confirm.text = "正在保存…"
            recoveryViewModel.save(candidate, snapshot.sessionId)
            return
        }
        if (snapshot?.sessionId != null) {
            showRecordingError(view, "这是恢复录制草稿，请先进入任务恢复")
            return
        }
        val task = ClickTask(UUID.randomUUID().toString(), selection.hour, selection.minute, days, points,
            protection = protection)
        if (!task.isValid()) {
            Toast.makeText(view.context, "录制最多 200 个点击，时长不超过 8 分钟", Toast.LENGTH_LONG).show()
            return
        }
        saving = true
        binding.setScheduleBusy(true)
        binding.confirm.text = "正在保存…"
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
                binding.showScheduleError("保存失败：${e.message}")
            } finally {
                saving = false
                binding.setScheduleBusy(false)
                binding.confirm.text = "保存任务"
            }
        }
    }

    fun onCancelClick(view: View) {
        if (saving || recoveryViewModel?.busy == true) return
        recoveryViewModel?.session?.let {
            if (recoveryViewModel.busy) return
            val selection = binding.readScheduleSelection()
            recoveryViewModel.updateTime(selection.hour, selection.minute, selection.days)
        }
        ViewModelMain.isShowCustomFloatWindow.value = false
        ViewModelMain.isShowFloatWindow.value = true
    }

    private fun showRecordingError(view: View, message: String) = Toast.makeText(view.context, message, Toast.LENGTH_LONG).show()
}
