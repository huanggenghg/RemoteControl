package com.lumostech.remotecontrol

import android.app.Application
import android.content.Context
import android.provider.Settings
import com.lumostech.remotecontrol.utils.GlobalExceptionHandler
import com.lumostech.remotecontrol.utils.Logger
import com.lumostech.accessibilitycore.AccessibilityCoreService

class MyApp : Application() {
    private var context: Context? = null

    override fun onCreate() {
        super.onCreate()
        context = this
        
        // 初始化日志系统
        initLogger()
        
        // 初始化全局异常处理器
        GlobalExceptionHandler.init(this)
        
        Logger.i(TAG, "应用启动 - 版本: ${getAppVersion()}")
        
        try {
            setMyServiceEnable()
        } catch (e: Exception) {
            Logger.e(TAG, "设置无障碍服务失败", e)
        }
    }

    private fun initLogger() {
        // 根据 BuildConfig.DEBUG 决定是否启用详细日志
        Logger.isEnabled = BuildConfig.DEBUG
        Logger.tagPrefix = "RemoteControl"
        Logger.d(TAG, "日志系统已初始化 (DEBUG=${BuildConfig.DEBUG})")
    }

    private fun getAppVersion(): String {
        return try {
            val pmi = packageManager.getPackageInfo(packageName, 0)
            "${pmi.versionName} (${pmi.versionCode})"
        } catch (e: Exception) {
            Logger.e(TAG, "获取版本信息失败", e)
            "unknown"
        }
    }

    /**
     * 需要授予权限 android.permission.WRITE_SECURE_SETTINGS
     * adb shell pm grant 包名 android.permission.WRITE_SECURE_SETTINGS
     */
    private fun setMyServiceEnable() {
        val name = packageName + "/" + AccessibilityCoreService::class.java.name

        val string = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val stringBuffer = StringBuffer(string)
        if (!string.contains(name)) {
            val s = stringBuffer.append(":").append(name).toString()
            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, s
            )
            Logger.i(TAG, "无障碍服务已启用: $name")
        }
    }

    companion object {
        const val TAG: String = "MyApp"
        var remoteScreenAdaptedWidth = -1
        var remoteScreenAdaptedHeight = -1
    }
}
