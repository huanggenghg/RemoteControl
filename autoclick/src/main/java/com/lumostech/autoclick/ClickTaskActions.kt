package com.lumostech.autoclick

enum class ClickTaskAction { EDIT_TIME, ENABLE, DISABLE, TRIAL, RECOVER, DELETE }
data class ClickTaskActionState(val enabled: Boolean, val reason: String? = null)
fun taskActionState(task: ClickTask?, action: ClickTaskAction, saving: Boolean, executing: Boolean,
                    connected: Boolean, recordingRecovery: Boolean): ClickTaskActionState {
    fun blocked(reason: String) = ClickTaskActionState(false, reason)
    if (task == null) return blocked("还没有定时任务")
    if (saving) return blocked("正在保存，请稍候")
    if (executing) return blocked("任务正在准备或执行，请结束后再操作")
    if (!connected) return blocked("无障碍服务未连接，请恢复后再操作")
    if (recordingRecovery && action != ClickTaskAction.RECOVER)
        return blocked("正在重新录制，请先保存或取消恢复")
    if (task.protection == null && action in setOf(ClickTaskAction.ENABLE, ClickTaskAction.TRIAL))
        return blocked("旧版任务需要重新录制，才能启用或试运行")
    if (action == ClickTaskAction.RECOVER && task.protection != null)
        return blocked("当前任务已有完整录制")
    return ClickTaskActionState(true)
}
