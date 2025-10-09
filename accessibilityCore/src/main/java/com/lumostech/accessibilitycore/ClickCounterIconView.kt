package com.lumostech.accessibilitycore

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * 一个自定义的“瞄准镜”样式图标View，并包含点击计数功能。
 *
 * 功能：
 * 1. 绘制一个带四个方向圆角标记的黑色圆环。
 * 2. 内部绘制一个绿松石色的实心圆。
 * 3. 在中心圆上显示点击次数。
 * 4. 每次被点击时，计数器加一并刷新视图。
 */
class ClickCounterIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 内部点击计数器
    private var clickCount = 0

    // 绘制黑色圆环的画笔
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE // 样式为描边
    }

    // 绘制中心绿松石色圆圈的画笔
    private val centerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1FFAA9") // 绿松石色
        style = Paint.Style.FILL
    }

    // 绘制文字的画笔
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK // 文字颜色为黑色
        textAlign = Paint.Align.CENTER // 文字水平居中
        isFakeBoldText = true // 文字加粗，更清晰
    }

    // 绘制圆环外侧圆角标记的画笔
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL // 实心填充
    }

    // 用于计算文字边界
    private val textBounds = Rect()

    // 可复用的RectF对象，用于绘制圆角矩形
    private val markerRect = RectF()

    init {
        // 设置点击监听器
        setOnClickListener {
            clickCount++
            invalidate() // 请求重绘View
        }

        // 初始化时设置一个合适的默认尺寸 (e.g., 60dp)
        // 这样在WindowManager中用WRAP_CONTENT时，它会有一个默认大小
        val defaultSize = (60 * resources.displayMetrics.density).toInt()
        minimumHeight = defaultSize
        minimumWidth = defaultSize
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // --- 1. 计算通用尺寸 ---
        val size = min(width, height).toFloat()
        val centerX = width / 2f
        val centerY = height / 2f

        // 动态设置描边/标记宽度
        val strokeWidth = size / 12f // 稍微加粗一点描边
        ringPaint.strokeWidth = strokeWidth

        // **【关键修正 1】** 重新定义半径计算方式
        // 标记的长度也需要考虑在内，以防止标记被View边界裁剪
        val markerLength = strokeWidth * 1.4f // 标记的长度
        // 外半径需要为标记线留出空间
        val outerRadius = (size / 2f) - markerLength
        // 中心圆半径，确保它在黑色圆环内部
        val centerRadius = outerRadius - strokeWidth * 2

        // **【关键修正 2】** 更改绘制顺序，先画中心内容，再画外部装饰

        // --- 2. 绘制中心的绿松石色圆圈 ---
        canvas.drawCircle(centerX, centerY, centerRadius, centerCirclePaint)

        // --- 3. 在圆圈中心绘制点击次数 ---
        val textToShow = clickCount.toString()
        // 文字大小根据中心圆半径计算，确保不会超出
        textPaint.textSize = centerRadius * 1.2f
        textPaint.getTextBounds(textToShow, 0, textToShow.length, textBounds)
        val textY = centerY - textBounds.exactCenterY()
        canvas.drawText(textToShow, centerX, textY, textPaint)

        // --- 4. 绘制黑色圆环 ---
        // **【关键修正 3】** 圆环应该画在中心圆的外侧
        val ringRadius = outerRadius - (strokeWidth / 2f)
        canvas.drawCircle(centerX, centerY, ringRadius, ringPaint)

        // --- 5. 绘制四个方向的凸起标记（带圆角） ---
        val markerThickness = strokeWidth // 标记的厚度
        val cornerRadius = markerThickness / 2f // 圆角半径，使其两端是完美的半圆形

        // 向上 (270度) 的标记
        markerRect.set(
            centerX - markerThickness / 2,
            centerY - ringRadius - (strokeWidth/2), // 从圆环外边缘开始
            centerX + markerThickness / 2,
            centerY - ringRadius + markerLength - (strokeWidth/2) // 向上延伸
        )
        // **【修正】** 坐标计算错误，应该是从圆环外边缘开始向上画，这里简化并修正了逻辑
        markerRect.set(
            centerX - markerThickness / 2,
            centerY - ringRadius - markerLength,
            centerX + markerThickness / 2,
            centerY - ringRadius
        )
        canvas.drawRoundRect(markerRect, cornerRadius, cornerRadius, markerPaint)


        // 向下 (90度) 的标记
        markerRect.set(
            centerX - markerThickness / 2,
            centerY + ringRadius,
            centerX + markerThickness / 2,
            centerY + ringRadius + markerLength
        )
        canvas.drawRoundRect(markerRect, cornerRadius, cornerRadius, markerPaint)

        // 向左 (180度) 的标记
        markerRect.set(
            centerX - ringRadius - markerLength,
            centerY - markerThickness / 2,
            centerX - ringRadius,
            centerY + markerThickness / 2
        )
        canvas.drawRoundRect(markerRect, cornerRadius, cornerRadius, markerPaint)

        // 向右 (0度) 的标记
        markerRect.set(
            centerX + ringRadius,
            centerY - markerThickness / 2,
            centerX + ringRadius + markerLength,
            centerY + markerThickness / 2
        )
        canvas.drawRoundRect(markerRect, cornerRadius, cornerRadius, markerPaint)
    }

    /**
     * 公开方法，用于从外部重置计数器
     */
    fun resetCount() {
        clickCount = 0
        invalidate() // 请求重绘以显示 "0"
    }
}
