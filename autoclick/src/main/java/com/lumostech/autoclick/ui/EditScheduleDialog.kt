package com.lumostech.autoclick.ui

import android.view.LayoutInflater
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumostech.autoclick.ClickTask
import com.lumostech.autoclick.R
import com.lumostech.autoclick.databinding.LayoutConfirmBinding

@Composable
fun EditScheduleDialog(task: ClickTask, saving: Boolean, saveError: String?,
                       onDismiss: () -> Unit, onSave: (Int, Int, Set<Int>) -> Unit) {
    var hour by rememberSaveable(task.id) { mutableIntStateOf(task.hour) }
    var minute by rememberSaveable(task.id) { mutableIntStateOf(task.minute) }
    var days by rememberSaveable(task.id) { mutableStateOf(task.days.sorted()) }
    var validationError by rememberSaveable(task.id) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val panelWidth = with(LocalDensity.current) { scheduleEditorWidthPixels(context.resources).toDp() }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(
        dismissOnBackPress = !saving, dismissOnClickOutside = !saving, usePlatformDefaultWidth = false
    )) {
        AndroidView(modifier = Modifier.width(panelWidth), factory = { viewContext ->
            LayoutConfirmBinding.inflate(LayoutInflater.from(viewContext)).apply {
                configureScheduleEditor(hour, minute, days.toSet()) { selection ->
                    hour = selection.hour
                    minute = selection.minute
                    days = selection.days.sorted()
                    validationError = null
                }
            }.root
        }, update = { view ->
            LayoutConfirmBinding.bind(view).apply {
                dialogTitle.text = "修改任务时间"
                dialogMessage.text = view.context.getString(R.string.schedule_saved_points, task.points.size)
                setScheduleTimeZone(task.timeZoneId, warnIfChanged = true)
                scheduleHint.text = if (task.enabled) "新时间已过时等下一次；当天已开始过的任务不会再次自动执行。" else
                    "保存后仍保持停用，需要执行时请再启用任务。"
                showScheduleError(validationError ?: saveError)
                setScheduleBusy(saving)
                confirm.text = if (saving) "正在保存…" else "保存修改"
                cancel.text = "取消"
                confirm.setOnClickListener {
                    if (!saving) {
                        val selection = readScheduleSelection()
                        validationError = selection.validationError
                        if (validationError == null) onSave(selection.hour, selection.minute, selection.days)
                    }
                }
                cancel.setOnClickListener { if (!saving) onDismiss() }
            }
        })
    }
}
