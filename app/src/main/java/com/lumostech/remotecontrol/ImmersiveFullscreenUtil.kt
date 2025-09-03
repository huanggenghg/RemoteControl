package com.lumostech.remotecontrol

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object ImmersiveFullscreenUtil {

    /**
     * 开启真正全屏：
     * - 边到边显示，内容延伸进状态栏/导航栏/刘海区域
     * - 隐藏状态栏与系统导航栏（手势上滑短暂呼出）
     * - 适配异形屏（shortEdges）
     *
     * lightBarIcons:
     *   - true：浅色背景时使用深色图标
     *   - false：深色背景时使用浅色图标
     *   - null：不修改当前图标样式
     */
    @JvmStatic
    fun enableTrueFullscreen(activity: Activity, lightBarIcons: Boolean? = null) {
        val window = activity.window
        // 内容边到边
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 透明系统栏颜色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }

        // 异形屏短边延伸
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        val decorView = window.decorView
        val controller = WindowInsetsControllerCompat(window, decorView)

        // 隐藏系统栏，滑动可短暂呼出
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        // 可选：设置系统栏图标明暗
        lightBarIcons?.let { light ->
            controller.isAppearanceLightStatusBars = light
            controller.isAppearanceLightNavigationBars = light
        }

        // 旧版兜底（防止个别 ROM 对 InsetsController 处理不一致）
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    /**
     * 退出全屏，恢复系统栏显示（保留边到边可选）
     */
    @JvmStatic
    fun disableTrueFullscreen(activity: Activity, keepEdgeToEdge: Boolean = false) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, !keepEdgeToEdge)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())

        if (!keepEdgeToEdge && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }
}