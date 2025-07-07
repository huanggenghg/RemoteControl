package com.lumostech.remotecontrol

import android.content.Context
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
) :
    LinearLayout(context, attrs, defStyleAttr) {
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
    var wm: WindowManager? = null
    var smallWindowParams: WindowManager.LayoutParams? = null


    fun getWmParams(): WindowManager.LayoutParams? {
        return smallWindowParams
    }

    fun setWmParams(wmParams: WindowManager.LayoutParams?) {
        this.smallWindowParams = wmParams
        this.smallWindowParams!!.x = 0
        this.smallWindowParams!!.y = 0
    }

    fun setwmParamsFlags(flags: Int) {
        smallWindowParams!!.flags = flags
        wm!!.updateViewLayout(this, smallWindowParams)
    }


    private val location = IntArray(2) // 小窗口位置坐标

    private fun calcPointRange(event: MotionEvent): Boolean {
        this.getLocationOnScreen(location)
        val width = measuredWidth
        val height = measuredHeight
        val curX = event.rawX
        val curY = event.rawY
        if (curX >= location[0] && curX <= location[0] + width && curY >= location[1] && curY <= location[1] + height) {
            return true
        }
        return false
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
                //                if (wmParams.x > 0) {
//                    isRight = true;
//                }
//                if (wmParams.x < 0) {
//                    isRight = false;
//                }
                lastWmParamsX = smallWindowParams!!.x
                lastWmParamsY = smallWindowParams!!.y
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
                    if (smallWindowParams!!.y <= 0) {
                        smallWindowParams!!.y =
                            if (abs(smallWindowParams!!.y) <= screenWidth / 2 - width / 2) smallWindowParams!!.y else -screenWidth / 2 + width / 2
                    } else {
                        smallWindowParams!!.y = min(smallWindowParams!!.y, (screenWidth / 2) - width / 2)
                    }
                    if (smallWindowParams!!.x <= 0) {
                        smallWindowParams!!.x =
                            if (abs(smallWindowParams!!.x) <= screenHeight / 2 - height / 2) smallWindowParams!!.x else -screenHeight / 2 + height / 2
                    } else {
                        smallWindowParams!!.x = min(smallWindowParams!!.x, (screenHeight / 2) - height / 2)
                    }
                } else {
                    if (smallWindowParams!!.x <= 0) {
                        smallWindowParams!!.x =
                            if (abs(smallWindowParams!!.x) <= screenWidth / 2 - width / 2) smallWindowParams!!.x else -screenWidth / 2 + width / 2
                    } else {
                        smallWindowParams!!.x = min(smallWindowParams!!.x, (screenWidth / 2) - width / 2)
                    }
                    if (smallWindowParams!!.y <= 0) {
                        smallWindowParams!!.y =
                            if (abs(smallWindowParams!!.y) <= screenHeight / 2 - height / 2) smallWindowParams!!.y else -screenHeight / 2 + height / 2
                    } else {
                        smallWindowParams!!.y = min(smallWindowParams!!.y, (screenHeight / 2) - height / 2)
                    }
                }

                actionUpX = (event.rawX + width / 2 - event.x).toInt()
                actionUpY = (event.rawY + height / 2 - event.y).toInt()
                //                wmParams.y = (int) (y - screenHeight / 2);
                wm!!.updateViewLayout(this, smallWindowParams)
            }

            else -> {}
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return super.onTouchEvent(event)
    }

    private var lastWmParamsX = 0
    private var lastWmParamsY = 0

    init {
        statusHeight = getStatusHeight(context)
        val dm = resources.displayMetrics
        screenHeight = dm.heightPixels
        screenWidth = dm.widthPixels
    }

    private fun updateViewPosition() {
//        wmParams.gravity = Gravity.NO_GRAVITY;
        //更新浮动窗口位置参数
        val dx = (x - mTouchStartX).toInt()
        val dy = (y - mTouchStartY).toInt()
        //        if (isRight) {
//            wmParams.x = screenWidth / 2 - dx;
//        } else {
//            wmParams.x = -dx - screenWidth / 2;
//        }
        smallWindowParams!!.x = lastWmParamsX + dx
        smallWindowParams!!.y = lastWmParamsY + dy
        Log.i(
            "winParams",
            "lastWmParamsX:" + lastWmParamsX + "x : " + smallWindowParams!!.x + "y :" + smallWindowParams!!.y + "  dx:" + dx + "  dy :" + dy
        )
        wm!!.updateViewLayout(this, smallWindowParams)
        //刷新显示
    }

    private fun isHorizontalScreen(windowManager: WindowManager): Boolean {
        val angle = windowManager.defaultDisplay.rotation
        if (angle == Surface.ROTATION_90 || angle == Surface.ROTATION_270) {
            //如果屏幕旋转90°或者270°是判断为横屏，横屏规避不展示
            return true
        }
        return false
    }


    companion object {
        private const val TAG = "SmallWindowView"

        /**
         * 获得状态栏的高度
         *
         * @param context
         * @return
         */
        fun getStatusHeight(context: Context): Int {
            var statusHeight = -1
            try {
                val clazz = Class.forName("com.android.internal.R\$dimen")
                val `object` = clazz.newInstance()
                val height = clazz.getField("status_bar_height")[`object`].toString().toInt()
                statusHeight = context.resources.getDimensionPixelSize(height)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return statusHeight
        }
    }
}
