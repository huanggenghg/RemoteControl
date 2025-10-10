package com.lumostech.accessibilitycore

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.ViewConfiguration
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

    private val location = IntArray(2) // 小窗口位置坐标

    // --- 【新增】长按逻辑相关变量 ---
    private var longPressRunnable: Runnable? = null
    private val longPressTimeout = 2000L // 获取系统默认的长按超时时间
    private var isLongPressed = false // 标志位，防止长按后还触发拖动或点击

    // --- 【新增】拖动判断相关变量 ---
    private var isDragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop // 系统建议的最小滑动距离

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
                // 重置所有状态标志
                isDragging = false
                isLongPressed = false

                lastWmParamsX = wmParams!!.x
                lastWmParamsY = wmParams!!.y
                mTouchStartX = event.rawX
                mTouchStartY = event.rawY
                Log.i("startP", "startX$mTouchStartX====startY$mTouchStartY")
                Log.i(
                    "startP",
                    "lastWmParamsX$lastWmParamsX====lastWmParamsY$lastWmParamsY"
                )

                // 【新增】启动长按检测
                longPressRunnable = Runnable {
                    isLongPressed = true // 标记已经触发了长按
                    Log.d(TAG, "长按事件触发！")
                    // 在这里执行你的长按逻辑
                    performLongClick()
                }
                // 延迟 longPressTimeout 毫秒后执行长按任务
                postDelayed(longPressRunnable, longPressTimeout)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = x - mTouchStartX
                val dy = y - mTouchStartY

                // 检查是否超过滑动阈值
                if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    isDragging = true // 判定为拖动
                    // 【新增】一旦开始拖动，就取消长按检测
                    removeCallbacks(longPressRunnable)
                }

                if (isDragging && !isLongPressed) {
                    // 如果正在拖动，并且没有触发过长按，才更新位置
                    updateViewPosition()
                }
            }

            MotionEvent.ACTION_UP -> {
                // 【新增】手指抬起时，无论如何都要取消长按检测
                removeCallbacks(longPressRunnable)

                if (isLongPressed) {
                    // 如果已经触发了长按，则不执行任何后续操作
                    // just reset the flag
                    isLongPressed = false
                } else if (isDragging) {
                    // 如果是拖动结束
                    handleEdgeAdsorption(event)
                } else {
                    // 如果既不是长按，也不是拖动，那就是一次“点击”
                    Log.d(TAG, "点击事件触发！")
                    // 如果有子View，最好调用 performClick() 来确保无障碍服务能识别
                    performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                // 【新增】事件被取消时（例如被父View拦截），也要取消长按检测
                removeCallbacks(longPressRunnable)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        if (childCount == 1) {
            (getChildAt(0) as? ClickCounterIconView)?.updateClickCountView()
            return true
        }
        return super.performClick()
    }

    override fun performLongClick(): Boolean {
        if (childCount == 1) {
            (getChildAt(0) as? ClickCounterIconView)?.dispatchClickPointEvent()
            return true
        }
        return super.performLongClick()
    }

    private fun handleEdgeAdsorption(event: MotionEvent) {
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
