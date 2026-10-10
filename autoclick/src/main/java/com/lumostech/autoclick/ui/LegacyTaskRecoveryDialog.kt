package com.lumostech.autoclick.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LegacyTaskRecoveryDialog(hasDraft: Boolean, onDismiss: () -> Unit, onBegin: () -> Unit, onResume: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("重新录制旧版任务") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("旧录制缺少目标应用、屏幕尺寸和方向信息，因此不能启用或试运行。")
                Text("在目标页面重新录制，长按悬浮按钮确认时间和星期。新任务保存成功前，原任务及其执行历史会保留。")
                Text("新任务保存后保持停用，请先试运行，再手动启用。")
                if (hasDraft) Text("已有恢复草稿。继续会保留已录制点击；重新开始会清空当前录制草稿，原任务仍保留。")
                else Text("开始会清空当前录制草稿。取消不会修改任务或录制。")
            }
        }, confirmButton = {
            Column {
                if (hasDraft) TextButton(onClick = onResume, modifier = Modifier.heightIn(min = 48.dp)) { Text("继续录制") }
                TextButton(onClick = onBegin, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (hasDraft) "清空草稿并重新开始" else "开始重新录制") }
            }
        }, dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消") } })
}
