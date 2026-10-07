package com.lumostech.autoclick

enum class ClickServiceReadiness(val title: String, val hint: String) {
    DISABLED("无障碍未开启", "请在系统设置中开启 AutoClick 无障碍服务，才能录制和执行点击。"),
    CONNECTING("无障碍已开启，等待连接", "系统尚未连接服务。定时触发时最多等待 10 秒，超时后本次不执行。"),
    CONNECTED("无障碍已连接", "可在后台执行点击；请保持屏幕解锁，并打开目标页面。");

    companion object {
        fun from(enabled: Boolean, connected: Boolean): ClickServiceReadiness = when {
            !enabled -> DISABLED
            connected -> CONNECTED
            else -> CONNECTING
        }
    }
}
