package com.lumostech.remotecontrol.utils

import android.content.Context
import android.widget.Toast

/**
 * UI 错误显示工具
 * 在界面上显示错误信息，同时记录日志
 */
object UiErrorHandler {

    /**
     * 在 Toast 中显示错误
     */
    fun showToast(context: Context, throwable: Throwable, duration: Int = Toast.LENGTH_LONG) {
        val message = NetworkErrorHandler.getErrorMessage(throwable)
        Logger.w("UiErrorHandler", "显示错误 Toast: $message", throwable)
        Toast.makeText(context, message, duration).show()
    }

    /**
     * 显示错误信息到 Snackbar（需要传入 view）
     */
    fun showSnackbar(
        view: android.view.View,
        throwable: Throwable,
        actionLabel: String? = null,
        action: (() -> Unit)? = null
    ) {
        val message = NetworkErrorHandler.getErrorMessage(throwable)
        Logger.w("UiErrorHandler", "显示 Snackbar: $message", throwable)

        val snackbar = com.google.android.material.snackbar.Snackbar.make(view, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
        if (actionLabel != null && action != null) {
            snackbar.setAction(actionLabel) { action() }
        }
        snackbar.show()
    }

    /**
     * 显示简单消息（非错误）
     */
    fun showMessage(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Logger.d("UiErrorHandler", "显示消息: $message")
        Toast.makeText(context, message, duration).show()
    }

    /**
     * 处理异常并显示（便捷方法）
     */
    fun handleException(
        context: Context,
        throwable: Throwable,
        showToast: Boolean = true,
        customMessage: String? = null
    ) {
        val message = customMessage ?: NetworkErrorHandler.getErrorMessage(throwable)
        Logger.e("UiErrorHandler", message, throwable)

        if (showToast) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
