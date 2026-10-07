package com.lumostech.autoclick.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.lumostech.accessibilitycore.ClickCounterIconView
import com.lumostech.autoclick.R
import kotlin.math.min

/** Autoclick-only presentation; touch handling remains in the shared overlay. */
class RecordingIconView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ClickCounterIconView(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val primary = ContextCompat.getColor(context, R.color.ac_primary)
    private val foreground = ContextCompat.getColor(context, R.color.ac_on_primary)

    init {
        contentDescription = "录制按钮：轻点记录位置，长按设置定时任务"
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        paint.style = Paint.Style.FILL
        paint.color = primary
        canvas.drawCircle(cx, cy, size * .46f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = foreground
        paint.strokeWidth = size * .024f
        canvas.drawCircle(cx, cy, size * .43f, paint)
        paint.strokeWidth = size * .035f
        paint.strokeCap = Paint.Cap.ROUND
        val inner = size * .32f
        val outer = size * .38f
        canvas.drawLine(cx, cy - outer, cx, cy - inner, paint)
        canvas.drawLine(cx, cy + inner, cx, cy + outer, paint)
        canvas.drawLine(cx - outer, cy, cx - inner, cy, paint)
        canvas.drawLine(cx + inner, cy, cx + outer, cy, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = min(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 17f, resources.displayMetrics), size * .34f)
        val baseline = cy - (paint.ascent() + paint.descent()) / 2
        canvas.drawText(getClickPointList().size.toString(), cx, baseline, paint)
    }
}
