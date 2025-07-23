package com.lumostech.remotecontrol

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.lumostech.remotecontrol.bean.Bean

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
        clickOnTarget: Bean,
        sourceDim: ScreenDimensions,
    ): Bean? {
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

        return Bean(sourceX, sourceY)
    }
}