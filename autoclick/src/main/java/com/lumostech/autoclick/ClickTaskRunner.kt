package com.lumostech.autoclick

import com.lumostech.accessibilitycore.ClickCounterPoint

enum class ClickTaskOutcome(val message: String) {
    INVALID("任务配置无效，请重新录制"),
    DISABLED("任务已停用"),
    SKIPPED_DAY("今天不在执行星期内，已跳过"),
    SERVICE_UNAVAILABLE("无障碍服务未开启，未执行"),
    SERVICE_CONNECTION_TIMEOUT("无障碍服务已开启但未连接，等待超时，未执行"),
    NEEDS_RECORDING("录制缺少环境信息，请重新录制"),
    GESTURE_FAILED("点击被拒绝、取消或超时，未完成"),
    COMPLETED("全部点击手势已完成")
}

object ClickTaskRunner {
    suspend fun run(
        task: ClickTask,
        today: Int,
        serviceAvailable: Boolean,
        execute: suspend (List<ClickCounterPoint>) -> Boolean
    ): ClickTaskOutcome = when {
        !task.isValid() -> ClickTaskOutcome.INVALID
        !task.enabled -> ClickTaskOutcome.DISABLED
        today !in task.days -> ClickTaskOutcome.SKIPPED_DAY
        !serviceAvailable -> ClickTaskOutcome.SERVICE_UNAVAILABLE
        task.protection == null -> ClickTaskOutcome.NEEDS_RECORDING
        execute(task.points) -> ClickTaskOutcome.COMPLETED
        else -> ClickTaskOutcome.GESTURE_FAILED
    }
}
