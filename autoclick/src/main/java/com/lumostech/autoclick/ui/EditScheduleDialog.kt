package com.lumostech.autoclick.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumostech.autoclick.ClickTask
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditScheduleDialog(task: ClickTask, saving: Boolean, saveError: String?,
                       onDismiss: () -> Unit, onSave: (Int, Int, Set<Int>) -> Unit) {
    var hour by rememberSaveable(task.id) { mutableStateOf(String.format(Locale.ROOT, "%02d", task.hour)) }
    var minute by rememberSaveable(task.id) { mutableStateOf(String.format(Locale.ROOT, "%02d", task.minute)) }
    var days by rememberSaveable(task.id) { mutableStateOf(task.days.sorted()) }
    var validationError by rememberSaveable(task.id) { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(
        dismissOnBackPress = !saving, dismissOnClickOutside = !saving, usePlatformDefaultWidth = false
    )) {
        Surface(shape = MaterialTheme.shapes.large, color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant),
            modifier = Modifier.padding(20.dp).widthIn(max = 520.dp).fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("修改任务时间", style = MaterialTheme.typography.headlineSmall)
                Text("沿用保存的 ${task.points.size} 个点击和间隔。", style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant)
                Text("执行时间 · 24 小时制", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = hour, onValueChange = {
                        hour = it.filter(Char::isDigit).take(2); validationError = null
                    }, label = { Text("时（00–23）") }, singleLine = true, enabled = !saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("schedule-hour"))
                    OutlinedTextField(value = minute, onValueChange = {
                        minute = it.filter(Char::isDigit).take(2); validationError = null
                    }, label = { Text("分（00–59）") }, singleLine = true, enabled = !saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("schedule-minute"))
                }
                Text("重复日期", style = MaterialTheme.typography.labelLarge)
                val weekdayLabels = listOf(2 to "周一", 3 to "周二", 4 to "周三", 5 to "周四", 6 to "周五", 7 to "周六", 1 to "周日")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    weekdayLabels.forEach { (day, label) ->
                        FilterChip(selected = day in days, enabled = !saving, onClick = {
                            days = if (day in days) days - day else (days + day).sorted()
                            validationError = null
                        }, label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primaryContainer,
                                selectedLabelColor = colors.onPrimaryContainer),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("schedule-day-$day"))
                    }
                }
                Text("保存时区：${task.timeZoneId}", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (task.timeZoneId != TimeZone.getDefault().id) {
                    Text("当前设备时区已改变。本次修改仍使用原时区；请通过录制设置重新保存任务后再启用。",
                        color = colors.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(if (task.enabled) "新时间已过时等下一次；当天已开始过的任务不会再次自动执行。" else
                    "保存后仍保持停用，需要执行时请再启用任务。", color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                (validationError ?: saveError)?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }
                Button(onClick = {
                    val h = hour.toIntOrNull()
                    val m = minute.toIntOrNull()
                    when {
                        h == null || h !in 0..23 || m == null || m !in 0..59 -> validationError = "请输入有效的时间（00:00–23:59）"
                        days.isEmpty() -> validationError = "请至少选择一个执行星期"
                        else -> onSave(h, m, days.toSet())
                    }
                }, enabled = !saving, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (saving) "正在保存…" else "保存修改")
                }
                TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("取消")
                }
            }
        }
    }
}
