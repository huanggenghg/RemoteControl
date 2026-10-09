package com.lumostech.autoclick.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
fun ExactTimingPermissionDialog(onSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("开启定时权限") },
        text = { Text("定时点击需要开启闹钟和提醒权限。开启后请返回并启用任务；录制和手动试运行仍可使用。") },
        confirmButton = { TextButton(onClick = onSettings) { Text("去开启") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("暂不") } })
}
