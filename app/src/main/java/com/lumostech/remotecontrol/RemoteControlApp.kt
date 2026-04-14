package com.lumostech.remotecontrol

import android.app.Application
import com.lumostech.remotecontrol.utils.GlobalExceptionHandler
import com.lumostech.remotecontrol.utils.Logger

/**
 * RemoteControl 应用主类
 * 负责初始化全局组件（日志、异常处理等）
 */
class RemoteControlApp : Application() {

    companion object {
        lateinit var instance: RemoteControlApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化日志系统
        initLogger()

        // 初始化全局异常处理器
        initExceptionHandler()

        Logger.i("RemoteControlApp", "应用启动成功")
    }

    private fun initLogger() {
        // 根据构建类型决定是否启用日志
        // 这里默认启用，可以通过 BuildConfig.DEBUG 控制
        Logger.isEnabled = BuildConfig.DEBUG
        Logger.tagPrefix = "RemoteControl"
        Logger.i("Logger", "日志系统已初始化 (enabled=${Logger.isEnabled})")
    }

    private fun initExceptionHandler() {
        GlobalExceptionHandler.init(this)
        Logger.i("GlobalExceptionHandler", "全局异常处理器已注册")
    }
}
