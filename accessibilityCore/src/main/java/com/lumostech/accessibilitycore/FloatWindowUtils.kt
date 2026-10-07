package com.lumostech.accessibilitycore

import com.lumostech.accessibilitybase.utils.Logger

import android.app.Activity
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.net.toUri

object FloatWindowUtils {
    const val REQUEST_FLOAT_CODE = 1001

    /**
     * 判断悬浮窗权限权�?
     */
    private fun commonROMPermissionCheck(context: Context?): Boolean {
        var result = true
        try {
            val clazz: Class<*> = Settings::class.java
            val canDrawOverlays =
                clazz.getDeclaredMethod("canDrawOverlays", Context::class.java)
            result = canDrawOverlays.invoke(null, context) as Boolean
        } catch (e: Exception) {
            Logger.e("ServiceUtils", "Overlay permission check failed", e)
        }
        return result
    }

    /**
     * 检查悬浮窗权限是否开�?
     */
    fun checkSuspendedWindowPermission(context: Activity, block: () -> Unit) {
        if (commonROMPermissionCheck(context)) {
            block()
        } else {
            Toast.makeText(context, "请开启悬浮窗权限", Toast.LENGTH_SHORT).show()
            context.startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = "package:${context.packageName}".toUri()
            }, REQUEST_FLOAT_CODE)
        }
    }


    fun showWindow(context: Context, view: View, widthPixels: Int? = null) {
        if (view.isAttachedToWindow) {
            Logger.w(TAG, "showWindow: view.isAttachedToWindow, return.")
            return
        }
        val windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
        val outMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(outMetrics)
        val layoutParam = WindowManager.LayoutParams()
        layoutParam.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                //刘海屏延伸到刘海里面
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            } else {
                type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            flags =
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            width = widthPixels?.coerceIn(1, outMetrics.widthPixels) ?: WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            format = PixelFormat.TRANSPARENT
        }
        try {
            windowManager.addView(view, layoutParam)
        } catch (e: Exception) {
            Logger.e(TAG, "showWindow: windowManager.addView exception: ${e.message}")
        }
    }

    fun removeWindow(context: Context, view: View) {
        if (!view.isAttachedToWindow) {
            Logger.w(TAG, "removeWindow: view.isAttachedToWindow false, return.")
            return
        }
        val windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            Logger.e(TAG, "removeWindow: windowManager.removeView exception: ${e.message}")
        }
    }

    private const val TAG = "Utils"
}
