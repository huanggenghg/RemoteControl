package com.lumostech.accessibilitybase.utils

import android.util.Log

/**
 * 统一日志工具类
 * 支持多级别日志、生产环境过滤、堆栈跟踪
 */
object Logger {
    private const val TAG_PREFIX = "RemoteControl"
    private const val DEBUG = true // 设置为 false 可在发布版本中禁用日志

    enum class Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
        ASSERT
    }

    /**
     * 设置是否启用日志（用于发布版本）
     */
    var isEnabled = DEBUG

    /**
     * 设置全局日志标签前缀
     */
    var tagPrefix = TAG_PREFIX

    /**
     * 生成日志标签（自动使用调用类名）
     */
    private fun generateTag(caller: Any?): String {
        val stackTrace = Throwable().stackTrace
        // 找到调用者的类名（跳过前几层框架调用）
        val index = stackTrace.indexOfFirst { it.className.startsWith("com.lumostech.") && !it.className.contains("Logger") }
        if (index != -1) {
            val className = stackTrace[index].className.substringAfterLast('.')
            return "$tagPrefix/$className"
        }
        return tagPrefix
    }

    // ========== 基础日志方法 ==========

    fun v(message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        val tag = generateTag(null)
        if (throwable != null) {
            Log.v(tag, message, throwable)
        } else {
            Log.v(tag, message)
        }
    }

    fun d(message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        val tag = generateTag(null)
        if (throwable != null) {
            Log.d(tag, message, throwable)
        } else {
            Log.d(tag, message)
        }
    }

    fun i(message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        val tag = generateTag(null)
        if (throwable != null) {
            Log.i(tag, message, throwable)
        } else {
            Log.i(tag, message)
        }
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        val tag = generateTag(null)
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        val tag = generateTag(null)
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    // ========== 带自定义标签的方法 ==========

    fun v(tag: String, message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        val fullTag = "$tagPrefix/$tag"
        if (throwable != null) {
            Log.v(fullTag, message, throwable)
        } else {
            Log.v(fullTag, message)
        }
    }

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        val fullTag = "$tagPrefix/$tag"
        if (throwable != null) {
            Log.d(fullTag, message, throwable)
        } else {
            Log.d(fullTag, message)
        }
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        val fullTag = "$tagPrefix/$tag"
        if (throwable != null) {
            Log.i(fullTag, message, throwable)
        } else {
            Log.i(fullTag, message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        val fullTag = "$tagPrefix/$tag"
        if (throwable != null) {
            Log.w(fullTag, message, throwable)
        } else {
            Log.w(fullTag, message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        val fullTag = "$tagPrefix/$tag"
        if (throwable != null) {
            Log.e(fullTag, message, throwable)
        } else {
            Log.e(fullTag, message)
        }
    }

    // ========== 便捷方法 ==========

    fun logObject(obj: Any?, level: Level = Level.DEBUG) {
        if (!isEnabled) return
        val message = if (obj != null) obj.toString() else "null"
        when (level) {
            Level.VERBOSE -> v(message)
            Level.DEBUG -> d(message)
            Level.INFO -> i(message)
            Level.WARN -> w(message)
            Level.ERROR -> e(message)
            Level.ASSERT -> e("ASSERT: $message")
        }
    }

    fun logException(throwable: Throwable, tag: String = "Exception") {
        e(tag, throwable.message ?: "Unknown error", throwable)
    }

    fun logStackTrace(throwable: Throwable) {
        val sw = java.io.StringWriter()
        val pw = java.io.PrintWriter(sw)
        throwable.printStackTrace(pw)
        e(sw.toString())
    }
}
