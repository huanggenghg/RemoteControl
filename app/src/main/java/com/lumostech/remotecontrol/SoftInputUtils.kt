package com.lumostech.remotecontrol

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lumostech.accessibilitycore.ClickPoint
import com.lumostech.remotecontrol.SoftInputUtils.targetDim

object SoftInputUtils {
    @SuppressLint("ServiceCast")
    fun showSoftInput(view: View) {
        val inputManager: InputMethodManager =
            view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.showSoftInput(view, 0)
    }

    @SuppressLint("ServiceCast")
    fun hideSoftInput(view: View) {
        val inputManager: InputMethodManager =
            view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    // 定义一个简单的数据类来表示屏幕尺寸
    data class ScreenDimensions(var width: Int, var height: Int)

    val targetDim = ScreenDimensions(0, 0)

    /**
     * 将目标设备（显示投屏的设备）上的点击坐标，映射回源设备（被投屏的设备）的真实坐标。
     *
     * 此函数假设投屏内容以 "Aspect Fit" 模式（保持宽高比，居中显示）进行显示。
     *
     * @param clickOnTarget 在目标设备屏幕上点击的坐标。
     * @param sourceDim 源设备（被投屏设备）的分辨率。
     * @param targetDim 目标设备（显示投屏、接收点击的设备）的分辨率。
     * @return 映射到源设备上的坐标 Point(x, y)；如果点击位置在黑边上，则返回 null。
     */
    fun mapCoordinatesFromTargetToSource(
        clickOnTarget: ClickPoint,
        sourceDim: ScreenDimensions,
    ): ClickPoint? {
        if (targetDim.width == 0 || targetDim.height == 0) return null

        // 1. 计算缩放比例，取宽、高缩放比中较小的一个，以确保内容能完整显示
        val scaleX = targetDim.width.toFloat() / sourceDim.width
        val scaleY = targetDim.height.toFloat() / sourceDim.height
        val scale = scaleX.coerceAtMost(scaleY)

        if (scale <= 0) return null // 防止除零或无效尺寸

        // 2. 计算投屏内容在目标设备上的实际显示尺寸
        val displayWidth = sourceDim.width * scale
        val displayHeight = sourceDim.height * scale

        // 3. 计算投屏内容在目标设备上为了居中而产生的偏移量（黑边的大小）
        val offsetX = (targetDim.width - displayWidth) / 2
        val offsetY = (targetDim.height - displayHeight) / 2

        // 4. 检查点击是否在有效的显示区域内
        if (clickOnTarget.x < offsetX || clickOnTarget.x > (offsetX + displayWidth) ||
            clickOnTarget.y < offsetY || clickOnTarget.y > (offsetY + displayHeight)
        ) {
            // 点击在了黑边上，是无效点击
            return null
        }

        // 5. 将点击坐标从目标屏幕坐标系转换到源屏幕坐标系
        // 5.1. 减去偏移量，得到在投屏显示区域内的相对坐标
        val relativeX = clickOnTarget.x - offsetX
        val relativeY = clickOnTarget.y - offsetY

        // 5.2. 按缩放比例反向换算，得到源设备上的坐标
        val sourceX = relativeX / scale
        val sourceY = relativeY / scale

        return ClickPoint(sourceX, sourceY)
    }

    // 真实屏幕高度（px），包含状态栏、导航栏、DisplayCutout
    @JvmStatic
    fun getRealScreenHeightPx(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(WindowManager::class.java)
            wm.maximumWindowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).run {
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION")
                defaultDisplay.getRealMetrics(dm)
                dm.heightPixels
            }
        }
    }

    // 若需要 dp，可用此便捷方法（一般仅用于展示/日志）
    @JvmStatic
    fun getRealScreenHeightDp(context: Context): Int {
        val px = getRealScreenHeightPx(context)
        val density = context.resources.displayMetrics.density
        return (px / density).toInt()
    }

    /**
     * 沉浸模式（全屏模式）
     * 设置全屏的方法
     * 参数window在activity或AppCompatActivity都带有
     */
    fun immersionFull(window: Window) {
        hideSystemBars(window)
        useSpecialScreen(window)
    }

    /**
     * 隐藏状态栏，显示内容上移到状态栏
     */
    private fun hideSystemBars(window: Window) {
        //占满全屏，activity绘制将状态栏也加入绘制范围。
        //如此即使使用BEHAVIOR_SHOW_BARS_BY_SWIPE或BEHAVIOR_SHOW_BARS_BY_TOUCH
        //也不会因为状态栏的显示而导致activity的绘制区域出现变形
        //使用刘海屏也需要这一句进行全屏处理
        WindowCompat.setDecorFitsSystemWindows(window, false)
        //隐藏状态栏和导航栏 以及交互
        WindowInsetsControllerCompat(window, window.decorView).let {
            //隐藏状态栏和导航栏
            //用于WindowInsetsCompat.Type.systemBars()隐藏两个系统栏
            //用于WindowInsetsCompat.Type.statusBars()仅隐藏状态栏
            //用于WindowInsetsCompat.Type.navigationBars()仅隐藏导航栏
            it.hide(WindowInsetsCompat.Type.systemBars())
            //交互效果
            //BEHAVIOR_SHOW_BARS_BY_SWIPE 下拉状态栏操作可能会导致activity画面变形
            //BEHAVIOR_SHOW_BARS_BY_TOUCH 未测试到与BEHAVIOR_SHOW_BARS_BY_SWIPE的明显差异
            //BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE 下拉或上拉的屏幕交互操作会显示状态栏和导航栏
            it.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * 扩展使用刘海屏
     */
    private fun useSpecialScreen(window: Window) {
        //允许window 的内容可以上移到刘海屏状态栏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
    }
}