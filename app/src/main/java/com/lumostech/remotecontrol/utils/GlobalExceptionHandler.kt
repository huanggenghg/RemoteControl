package com.lumostech.remotecontrol.utils

import android.content.Context
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 全局异常处理器
 * 捕获未处理的异常，记录日志，并可选择上报或重启应用
 */
class GlobalExceptionHandler private constructor(
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
    private val applicationContext: Context
) : Thread.UncaughtExceptionHandler {

    companion object {
        @Volatile
        private var instance: GlobalExceptionHandler? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
                        instance = GlobalExceptionHandler(defaultHandler, context.applicationContext)
                        Thread.setDefaultUncaughtExceptionHandler(instance)
                    }
                }
            }
        }

        fun getInstance(): GlobalExceptionHandler? = instance
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // 记录异常信息
            Logger.e("GlobalExceptionHandler", "未捕获异常 in thread: ${thread.name}", throwable)
            
            // 收集设备信息
            val deviceInfo = buildString {
                appendLine("=== 崩溃报告 ===")
                appendLine("线程: ${thread.name}")
                appendLine("时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                appendLine("Android 版本: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("产品: ${Build.PRODUCT}")
                appendLine("品牌: ${Build.BRAND}")
                appendLine("")
                appendLine("异常信息:")
                appendLine(throwable.message ?: "Unknown error")
                appendLine("")
                appendLine("堆栈跟踪:")
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                append(sw.toString())
            }

            Logger.e("CrashReport", deviceInfo)

            // 这里可以添加上报逻辑（发送到服务器）
            // CrashReportUploader.upload(deviceInfo)

            // 保存到本地文件（可选）
            saveCrashReport(deviceInfo, throwable)

        } catch (e: Exception) {
            Logger.e("GlobalExceptionHandler", "处理异常时发生错误", e)
        } finally {
            // 调用默认处理器，通常会重启应用
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashReport(report: String, throwable: Throwable) {
        try {
            val crashDir = java.io.File(applicationContext.externalCacheDir, "crashes")
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val file = java.io.File(crashDir, "crash_$timestamp.txt")
            file.writeText(report)
            Logger.i("GlobalExceptionHandler", "崩溃报告已保存: ${file.absolutePath}")
        } catch (e: Exception) {
            Logger.e("GlobalExceptionHandler", "保存崩溃报告失败", e)
        }
    }
}
