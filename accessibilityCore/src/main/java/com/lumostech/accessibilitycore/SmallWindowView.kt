package com.lumostech.accessibilitycore

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.min

class SmallWindowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private val screenHeight: Int
    private val screenWidth: Int
    private val statusHeight: Int //状态栏高度

    //MotionEvent.ACTION_DOWN的开始坐标
    private var mTouchStartX = 0f
    private var mTouchStartY = 0f

    //onTouchEvent实时坐标获取
    private var x = 0f
    private var y = 0f


    var actionUpX: Int = 0
        private set
    var actionUpY: Int = 0
        private set
    private var wm: WindowManager? = null
    var wmParams: WindowManager.LayoutParams? = null

    fun setwmParamsFlags(flags: Int) {
        wmParams!!.flags = flags
        wm!!.updateViewLayout(this, wmParams)
    }

    private val location = IntArray(2) // 小窗口位置坐标

    private fun calcPointRange(event: MotionEvent): Boolean {
        this.getLocationOnScreen(location)
        val width = measuredWidth
        val height = measuredHeight
        val curX = event.rawX
        val curY = event.rawY
        return curX >= location[0] && curX <= location[0] + width && curY >= location[1] && curY <= location[1] + height
    }


    var isRange: Boolean = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            isRange = calcPointRange(event)
            Log.e(TAG, "onTouchEvent: isRange = $isRange")
        }
        if (!isRange) {
            return super.onTouchEvent(event)
        }
        x = event.rawX
        y = event.rawY
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastWmParamsX = wmParams!!.x
                lastWmParamsY = wmParams!!.y
                mTouchStartX = event.rawX
                mTouchStartY = event.rawY
                Log.i("startP", "startX$mTouchStartX====startY$mTouchStartY")
                Log.i(
                    "startP",
                    "lastWmParamsX$lastWmParamsX====lastWmParamsY$lastWmParamsY"
                )
            }

            MotionEvent.ACTION_MOVE -> updateViewPosition()
            MotionEvent.ACTION_UP -> {
                if (isHorizontalScreen(wm!!)) {
                    if (wmParams!!.y <= 0) {
                        wmParams!!.y =
                            if (abs(wmParams!!.y) <= screenWidth / 2 - width / 2) wmParams!!.y else -screenWidth / 2 + width / 2
                    } else {
                        wmParams!!.y = min(wmParams!!.y, (screenWidth / 2) - width / 2)
                    }
                    if (wmParams!!.x <= 0) {
                        wmParams!!.x =
                            if (abs(wmParams!!.x) <= screenHeight / 2 - height / 2) wmParams!!.x else -screenHeight / 2 + height / 2
                    } else {
                        wmParams!!.x = min(wmParams!!.x, (screenHeight / 2) - height / 2)
                    }
                } else {
                    if (wmParams!!.x <= 0) {
                        wmParams!!.x =
                            if (abs(wmParams!!.x) <= screenWidth / 2 - width / 2) wmParams!!.x else -screenWidth / 2 + width / 2
                    } else {
                        wmParams!!.x = min(wmParams!!.x, (screenWidth / 2) - width / 2)
                    }
                    if (wmParams!!.y <= 0) {
                        wmParams!!.y =
                            if (abs(wmParams!!.y) <= screenHeight / 2 - height / 2) wmParams!!.y else -screenHeight / 2 + height / 2
                    } else {
                        wmParams!!.y = min(wmParams!!.y, (screenHeight / 2) - height / 2)
                    }
                }

                actionUpX = (event.rawX + width / 2 - event.x).toInt()
                actionUpY = (event.rawY + height / 2 - event.y).toInt()
                wm!!.updateViewLayout(this, wmParams)
            }

            else -> {}
        }
        return super.dispatchTouchEvent(event)
    }

    private var lastWmParamsX = 0
    private var lastWmParamsY = 0

    init {
        statusHeight = getStatusHeight(context)
        val dm = resources.displayMetrics
        screenHeight = dm.heightPixels
        screenWidth = dm.widthPixels
        wm = context.getSystemService("window") as WindowManager?
        val floatType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        wmParams = WindowManager.LayoutParams(-2, -2, floatType, 8, -3)
    }

    private fun updateViewPosition() {
//        wmParams.gravity = Gravity.NO_GRAVITY;
        //更新浮动窗口位置参数
        val dx = (x - mTouchStartX).toInt()
        val dy = (y - mTouchStartY).toInt()
        wmParams!!.x = lastWmParamsX + dx
        wmParams!!.y = lastWmParamsY + dy
        Log.i(
            "winParams",
            "lastWmParamsX:" + lastWmParamsX + "x : " + wmParams!!.x + "y :" + wmParams!!.y + "  dx:" + dx + "  dy :" + dy
        )
        wm!!.updateViewLayout(this, wmParams)
        //刷新显示
    }

    private fun isHorizontalScreen(windowManager: WindowManager): Boolean {
        val angle = windowManager.defaultDisplay.rotation
        //如果屏幕旋转90°或者270°是判断为横屏，横屏规避不展示
        return angle == Surface.ROTATION_90 || angle == Surface.ROTATION_270
    }


    companion object {
        private const val TAG = "SmallWindowView"

        /**
         * 获得状态栏的高度
         *
         * @param context
         * @return
         */
        @SuppressLint("PrivateApi")
        fun getStatusHeight(context: Context): Int {
            var statusHeight = -1
            try {
                val clazz = Class.forName("com.android.internal.R\$dimen")
                val `object`: Any = clazz.newInstance()
                @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS") val height =
                    clazz.getField("status_bar_height")
                        .get(`object`).toString().toInt()
                statusHeight = context.resources.getDimensionPixelSize(height)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return statusHeight
        }
    }
}
