package com.lumostech.autoclick.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumostech.autoclick.ClickTaskPresentation
import com.lumostech.autoclick.RecoveryHelp
import com.lumostech.autoclick.ClickTask
import com.lumostech.autoclick.ClickRunState
import com.lumostech.autoclick.ClickServiceReadiness
import com.lumostech.autoclick.ClickTaskAction
import com.lumostech.autoclick.ClickTaskActionState
import com.lumostech.autoclick.LegacyTaskRecoverySession
import com.lumostech.autoclick.LegacyRecoveryPhase
import com.lumostech.autoclick.taskActionState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoclickScreen(
    pointCount: Int,
    task: ClickTask?,
    outcome: String,
    presentation: ClickTaskPresentation,
    busy: Boolean,
    runState: ClickRunState,
    serviceReadiness: ClickServiceReadiness,
    onAccessibilitySettings: () -> Unit,
    onRecord: (Boolean) -> Unit,
    onToggleTask: () -> Unit,
    onDeleteTask: () -> Unit,
    onTrial: () -> Unit,
    onEditSchedule: () -> Unit,
    onEmergencyStop: () -> Unit,
    onTimingSettings: () -> Unit = {},
    recoverySession: LegacyTaskRecoverySession? = null,
    recoveryError: String? = null,
    storageUnconfirmed: Boolean = false,
    onRecover: () -> Unit = {},
    onPauseRecovery: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    fun action(which: ClickTaskAction): ClickTaskActionState = if (storageUnconfirmed)
        ClickTaskActionState(false, "保存状态未确认，请稍候重试")
    else taskActionState(task, which, busy, runState.active || presentation.editingBlocked,
        serviceReadiness == ClickServiceReadiness.CONNECTED, recoverySession != null)
    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = MaterialTheme.shapes.medium, color = colors.primaryContainer) {
                        Canvas(Modifier.size(48.dp).padding(11.dp)) {
                            drawCircle(colors.primary, radius = size.minDimension * .32f, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                            drawLine(colors.primary, Offset(center.x, 0f), Offset(center.x, size.height), 2.dp.toPx())
                            drawLine(colors.primary, Offset(0f, center.y), Offset(size.width, center.y), 2.dp.toPx())
                        }
                    }
                    Column {
                        Text("AUTOCLICK", color = colors.primary, style = MaterialTheme.typography.labelMedium)
                        Text("自动点击", style = MaterialTheme.typography.headlineLarge)
                    }
                }
                Text("记录点击，按计划重放。", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)

                Panel {
                    Text(serviceReadiness.title, style = MaterialTheme.typography.titleMedium,
                        color = if (serviceReadiness == ClickServiceReadiness.CONNECTED) colors.primary else colors.error)
                    Text(serviceReadiness.hint, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onAccessibilitySettings, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("无障碍设置")
                    }
                    Text("强行停止应用后，请重新打开应用并检查任务，必要时重新启用。", color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }

                Panel {
                    SectionLabel("01", "录制点击")
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(pointCount.toString(), style = MaterialTheme.typography.headlineLarge, color = colors.primary)
                        Text("个点击已录制", modifier = Modifier.padding(bottom = 4.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("打开目标应用，拖动悬浮按钮定位，轻点记录位置和间隔。长按按钮设置定时任务。", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { onRecord(false) }, enabled = !busy && !runState.active && !presentation.editingBlocked && !storageUnconfirmed,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium
                    ) { Text(if (recoverySession != null) "继续恢复录制" else if (pointCount == 0) "开始录制" else "继续录制") }
                    if (pointCount > 0) {
                        TextButton(onClick = { onRecord(true) }, enabled = !busy && !runState.active && !presentation.editingBlocked && !storageUnconfirmed, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("清空并重新录制")
                        }
                    }
                }

                Panel {
                    SectionLabel("02", "定时任务")
                    if (task == null) {
                        Text("还没有定时任务", style = MaterialTheme.typography.titleMedium)
                        Text("录制完成后，长按悬浮按钮，选择执行时间和重复日期。", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(String.format(Locale.getDefault(), "%02d:%02d", task.hour, task.minute), style = MaterialTheme.typography.headlineLarge)
                            Surface(color = if (presentation.scheduledEnabled) colors.primaryContainer else colors.background, shape = MaterialTheme.shapes.small) {
                                Text(if (presentation.scheduledEnabled) "已启用" else "已停用", color = if (presentation.scheduledEnabled) colors.primary else colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                        val labels = mapOf(1 to "周日", 2 to "周一", 3 to "周二", 4 to "周三", 5 to "周四", 6 to "周五", 7 to "周六")
                        val orderedDays = (2..7).toList() + 1
                        Text(orderedDays.filter { it in task.days }.joinToString("、") { labels[it].orEmpty() }, style = MaterialTheme.typography.bodyMedium)
                        Text("${task.points.size} 个点击 · 使用保存时的录制", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Text(if (task.protection == null) "旧版任务需要重新录制，才能启用。" else
                            "已保护应用与屏幕方向 · ${task.timeZoneId} · 5 秒内未开始则跳过", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        if (task.protection == null) {
                            Text("旧录制缺少目标应用、屏幕尺寸和方向信息，无法安全启用或试运行。时间和星期仍可修改。",
                                color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onRecover, enabled = action(ClickTaskAction.RECOVER).enabled,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                Text(if (recoverySession == null) "重新录制" else "继续恢复")
                            }
                        }
                        recoverySession?.let {
                            Text(if (it.phase == LegacyRecoveryPhase.SAVING) "正在保存新的停用任务，原任务在保存成功前保留。"
                                else if (it.phase == LegacyRecoveryPhase.PAUSED) "恢复已暂停，新录制草稿已保留。原任务在新任务保存成功前保留。"
                                else "正在重新录制，原任务在新任务保存成功前保留。", style = MaterialTheme.typography.bodyMedium)
                            if (it.phase == LegacyRecoveryPhase.RECORDING)
                                TextButton(onClick = onPauseRecovery, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("暂停恢复") }
                        }
                        recoveryError?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }
                        HorizontalDivider(color = colors.outlineVariant)
                        Text("当前状态", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Text(presentation.title, style = MaterialTheme.typography.titleMedium)
                        if (presentation.detail.isNotBlank()) {
                            Text(presentation.detail, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (presentation.timingPermissionRequired) {
                            TextButton(onClick = onTimingSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("去开启") }
                        }
                        HorizontalDivider(color = colors.outlineVariant)
                        Text("最近结果", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Text(outcome.ifBlank { "尚无执行结果" }, style = MaterialTheme.typography.bodyMedium)
                        if (task.protection != null) RecoveryExplanation(task.scheduleId, presentation.recoveryHelp)
                        val toggle = action(if (presentation.scheduledEnabled) ClickTaskAction.DISABLE else ClickTaskAction.ENABLE)
                        val edit = action(ClickTaskAction.EDIT_TIME)
                        val trial = action(ClickTaskAction.TRIAL)
                        val delete = action(ClickTaskAction.DELETE)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onEditSchedule, enabled = edit.enabled,
                                shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, colors.outline),
                                modifier = Modifier.heightIn(min = 48.dp)) { Text("修改时间") }
                            OutlinedButton(onClick = onToggleTask, enabled = toggle.enabled, shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, colors.outline), modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(if (presentation.scheduledEnabled) "停用任务" else "启用任务")
                            }
                            TextButton(onClick = onDeleteTask, enabled = delete.enabled,
                                colors = ButtonDefaults.textButtonColors(contentColor = colors.error), modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("删除任务")
                            }
                        }
                        OutlinedButton(onClick = onTrial, enabled = trial.enabled,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) {
                            Text("5 秒后试运行")
                        }
                        listOfNotNull(edit.reason, toggle.reason, trial.reason, delete.reason).distinct().forEach {
                            Text(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("试运行会真实点击，可先停用定时任务再测试，不消耗当天定时次数。", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (runState.active || runState.lastTrialResult.isNotBlank() || task != null || runState.stopError != null) {
                    Panel {
                        SectionLabel("03", "执行控制")
                        if (runState.active || !runState.manual || runState.lastTrialResult.isBlank()) {
                            Text(runState.message, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (runState.lastTrialResult.isNotBlank()) {
                            Text("最近试运行", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            Text(runState.lastTrialResult, style = MaterialTheme.typography.bodyMedium)
                        }
                        runState.stopError?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }
                        Button(onClick = onEmergencyStop, enabled = runState.active || task?.enabled == true || runState.stopError != null,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) {
                            Text("停止并停用")
                        }
                        Text("立即中断当前序列并停用定时任务。已发送的点击无法撤销。", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text("执行前请确认", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    Text("屏幕亮起并解锁，目标页面已打开，无障碍服务已开启。", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Text("每次点击前核对应用、屏幕方向和锁屏状态；同一应用内的页面变化仍需自行确认。", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Text("计划时间后 5 秒内未开始则跳过；请保持亮屏解锁并打开目标页面。已开始的计划不会自动重放。最多 200 个点击，录制时长不超过 8 分钟。", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
private fun SectionLabel(number: String, title: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(number, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun RecoveryExplanation(scheduleId: String, help: RecoveryHelp) {
    if (help == RecoveryHelp.NONE) return
    var expanded by remember(scheduleId, help) { mutableStateOf(false) }
    val label = when (help) {
        RecoveryHelp.EXECUTION_PREPARATION -> "执行前准备"
        RecoveryHelp.RECORD_AGAIN -> "重新录制说明"
        RecoveryHelp.RESAVE_TIME_ZONE -> "重新设置说明"
        RecoveryHelp.REENABLE -> "重新启用说明"
        RecoveryHelp.NONE -> return
    }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(if (expanded) "收起说明" else label)
    }
    if (expanded) {
        val explanation = when (help) {
            RecoveryHelp.EXECUTION_PREPARATION -> "请亮屏解锁，打开录制时的目标应用并确认页面。已结束的本次计划不会自动重试；需要验证时，可使用 5 秒后试运行。"
            RecoveryHelp.RECORD_AGAIN -> "旧录制无法继续安全使用。请向上查看现有录制入口，在目标页面重新录制，再长按悬浮按钮保存任务。展开说明不会清空录制或修改任务。"
            RecoveryHelp.RESAVE_TIME_ZONE -> "当前时区与保存时不同。请通过现有悬浮按钮的长按设置入口重新选择并保存执行时间。展开说明不会修改时间。"
            RecoveryHelp.REENABLE -> "调度未成功建立。准备好目标页面后，可通过现有启用任务按钮重新启用；已开始过的当天计划不会自动重放。"
            RecoveryHelp.NONE -> ""
        }
        Text(explanation, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}
