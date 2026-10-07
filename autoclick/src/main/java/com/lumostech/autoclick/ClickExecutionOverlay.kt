package com.lumostech.autoclick

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnLayout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import com.lumostech.accessibilitycore.AccessibilityCoreService
import com.lumostech.accessibilitycore.ClickCounterPoint

internal class ClickExecutionOverlay(private val service: AccessibilityCoreService, onStop: () -> Unit) {
    private val density = service.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val status = TextView(service).apply {
        textSize = 14f
        setTextColor(Color.rgb(30, 45, 42))
        gravity = Gravity.CENTER
        isSingleLine = true
    }
    val view = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(4), dp(8), dp(4))
        background = GradientDrawable().apply {
            setColor(Color.rgb(246, 249, 246))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.rgb(128, 143, 136))
        }
        addView(status, LinearLayout.LayoutParams(-1, dp(28)))
        addView(Button(service).apply {
            text = "停止并停用"
            textSize = 16f
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.rgb(150, 55, 48))
            setOnClickListener { onStop() }
        }, LinearLayout.LayoutParams(-1, dp(52)))
    }

    fun show(task: ClickTask): Boolean {
        val protection = task.protection ?: return false
        val width = dp(200).coerceAtMost(protection.width)
        val height = dp(88)
        val position = StopControlPlacement.find(protection.width, protection.height, width, height, task.points) ?: return false
        return service.showExecutionControl(view, position.x, position.y, width, height)
    }

    fun update(message: String) { status.text = message }

    suspend fun awaitReady(): Boolean = withTimeoutOrNull(2_000) {
        suspendCancellableCoroutine { continuation ->
            view.doOnLayout { if (continuation.isActive) continuation.resume(it.isAttachedToWindow && it.width > 0 && it.height > 0) }
        }
    } == true

    fun failureAt(point: ClickCounterPoint): String? {
        if (!view.isAttachedToWindow || view.width == 0 || view.height == 0 || view.visibility != View.VISIBLE) {
            return "停止按钮不可用"
        }
        val bounds = Rect()
        if (!view.getGlobalVisibleRect(bounds)) return "停止按钮不可用"
        val location = IntArray(2).also { view.getLocationOnScreen(it) }
        bounds.set(location[0], location[1], location[0] + view.width, location[1] + view.height)
        return if (bounds.contains(point.x.toInt(), point.y.toInt())) "点击位置被停止按钮遮挡" else null
    }

    fun close() { service.hideExecutionControl(view) }
}
