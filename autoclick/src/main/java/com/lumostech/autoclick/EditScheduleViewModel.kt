package com.lumostech.autoclick

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumostech.accessibilitybase.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Owns one save across Activity recreation without retaining a view or Activity. */
class EditScheduleViewModel : ViewModel() {
    var candidate by mutableStateOf<ClickTask?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var resultMessage by mutableStateOf<String?>(null)
        private set

    fun open(task: ClickTask) {
        if (saving) return
        candidate = task
        error = null
    }

    fun dismiss(message: String? = null) {
        if (saving) return
        candidate = null
        if (message != null) resultMessage = error ?: message
        error = null
    }

    fun clearResult() { resultMessage = null }

    fun save(controller: ClickTaskController, hour: Int, minute: Int, days: Set<Int>) {
        val snapshot = candidate ?: return
        if (saving) return
        saving = true
        error = null
        viewModelScope.launch {
            try {
                val result = controller.updateSchedule(snapshot.id, hour, minute, days)
                candidate = null
                resultMessage = result.message
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Logger.e("EditSchedule", "Cannot update schedule", failure)
                error = "保存失败：${failure.message}"
            } finally {
                saving = false
            }
        }
    }
}
