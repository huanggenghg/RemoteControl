package com.lumostech.autoclick.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.window.DialogProperties
import com.lumostech.autoclick.AccessibilityGatePhase

@Composable
internal fun AccessibilityGateDialog(phase: AccessibilityGatePhase, onSettings: () -> Unit, onExit: () -> Unit) {
    val title = when (phase) {
        AccessibilityGatePhase.CHECKING -> "正在检查无障碍服务"
        AccessibilityGatePhase.ENABLE -> "请先开启 AutoClick 无障碍服务"
        AccessibilityGatePhase.CONNECTING -> "正在连接服务"
        AccessibilityGatePhase.RECONNECT -> "无障碍服务未连接"
        AccessibilityGatePhase.READY -> return
    }
    val description = when (phase) {
        AccessibilityGatePhase.ENABLE -> "自动点击需要无障碍服务。请进入系统设置，找到 AutoClick 并开启服务。"
        AccessibilityGatePhase.RECONNECT -> "服务已开启，但系统尚未连接。请进入无障碍设置，关闭后重新开启 AutoClick 服务，再返回应用检查。"
        AccessibilityGatePhase.CONNECTING -> "系统正在连接已开启的服务，最多等待 10 秒。连接成功后即可使用。"
        else -> "正在确认系统授权和服务连接，请稍候。"
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
    AlertDialog(
        onDismissRequest = onExit,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        title = { Text(title) },
        text = { Text(description) },
        confirmButton = {
            if (phase == AccessibilityGatePhase.ENABLE || phase == AccessibilityGatePhase.RECONNECT) {
                TextButton(onClick = onSettings) { Text(if (phase == AccessibilityGatePhase.ENABLE) "去开启" else "去设置") }
            }
        },
        dismissButton = { TextButton(onClick = onExit) { Text("退出应用") } }
    )
}
