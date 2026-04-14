package com.lumostech.accessibilitycore

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import com.lumostech.accessibilitybase.AccessibilityBaseEvent
import com.lumostech.remotecontrol.utils.Logger


@SuppressLint("AccessibilityPolicy")
class AccessibilityCoreService : AccessibilityService(), AccessibilityBaseEvent, LifecycleOwner {
    private var pkgNameMutableLiveData: MutableLiveData<String> = MutableLiveData()

    private lateinit var windowManager: WindowManager
    private var floatRootView: SmallWindowView? = null//悬浮窗View
    private var floatCustomView: View? = null
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
        super.onCreate()
        initObserve()
    }

    /**
     * 打开关闭的订�?
     */
    private fun initObserve() {
        ViewModelMain.isShowFloatWindow.observe(this, {
            if (it) {
                if (floatRootView == null) {
                    floatRootView =
                        LayoutInflater.from(this)
                            .inflate(R.layout.float_window, null) as SmallWindowView
                }
                ViewModelMain.isShowCustomFloatWindow.postValue(false)
                FloatWindowUtils.showWindow(this, floatRootView!!)
            } else {
                floatRootView?.let { view ->
                    FloatWindowUtils.removeWindow(this, view)
                    floatRootView = null
                }
            }
        })
        ViewModelMain.isShowCustomFloatWindow.observe(this, {
            if (it) {
                floatCustomView?.let { view ->
                    ViewModelMain.isShowFloatWindow.postValue(false)
                    FloatWindowUtils.showWindow(this, view)
                }
            } else {
                floatCustomView?.let { view ->
                    FloatWindowUtils.removeWindow(this, view)
                    floatCustomView = null
                }
            }
        })
    }

    override fun setFloatCustomView(floatCustomView: View) {
        this.floatCustomView = floatCustomView
    }

    override fun dispatchClickPointsEvent() {
        Logger.i("TAG", "dispatchClickPointsEvent: ")
        if (floatRootView?.getClickPointList().isNullOrEmpty()) {
            Logger.i("TAG", "dispatchClickPointsEvent: getClickPointList isNullOrEmpty")
            return
        }
        floatRootView?.getClickPointList()?.forEach { clickCounterPoint ->
            Logger.i(
                "TAG",
                "dispatchClickPointsEvent: clickCounterPoint:x:${clickCounterPoint.x} y:${clickCounterPoint.y} delay:${clickCounterPoint.delay}"
            )
        }
        for (clickPoint in floatRootView?.getClickPointList()!!) {
            mainHandler.postDelayed({
                dispatchGestureClick(
                    clickPoint.x,
                    clickPoint.y
                )
            }, clickPoint.delay)
        }
    }

    override fun dispatchGestureClick(x: Float, y: Float) {
        execDispatchGestureClick(x, y)
    }


    override fun dispatchGestureClick(
        x: Float,
        y: Float,
        onComplete: () -> Unit
    ) {
        execDispatchGestureClick(x, y, onComplete)
    }

    private fun execDispatchGestureClick(
        x: Float,
        y: Float,
        onComplete: (() -> Unit)? = null
    ) {
        Logger.i(TAG, "execDispatchGestureClick: x:$x y:$y")
        val path = Path()
        path.moveTo(x, y)
        path.lineTo(x + 1, y + 1)
        val result = dispatchGesture(
            GestureDescription.Builder().addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    20
                )
            ).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    super.onCompleted(gestureDescription)
                    onComplete?.invoke()
                    Logger.i(TAG, "execDispatchGestureClick: onCompleted")
                }
            },
            null
        )
        Logger.i(TAG, "execDispatchGestureClick: result:$result")
    }

    override fun dispatchScrollUp(distance: Float, duration: Long) {
        dispatchScroll(true, distance, duration)
    }

    override fun dispatchScrollDown(distance: Float, duration: Long) {
        dispatchScroll(false, distance, duration)
    }

    override fun dispatchScrollLeft(distance: Float, duration: Long) {
        dispatchXScroll(true, distance, duration)
    }

    override fun dispatchScrollRight(distance: Float, duration: Long) {
        dispatchXScroll(false, distance, duration)
    }

    private fun dispatchXScroll(isScrollingLeft: Boolean, distance: Float, duration: Long) {
        val diff = if (isScrollingLeft) -distance else distance
        val centerX = resources.displayMetrics.widthPixels / 2
        val centerY = resources.displayMetrics.heightPixels / 2

        dispatchSmoothGesture(
            centerX.toFloat(), centerY.toFloat(),
            centerX + diff, centerY.toFloat(),
            duration
        )
    }

    private fun dispatchScroll(isScrollingUp: Boolean, distance: Float, duration: Long) {
        val diff = if (isScrollingUp) -distance else distance
        val centerX = resources.displayMetrics.widthPixels / 2
        val centerY = resources.displayMetrics.heightPixels / 2

        dispatchSmoothGesture(
            centerX.toFloat(), centerY.toFloat(),
            centerX.toFloat(), centerY + diff,
            duration
        )
    }

    private fun dispatchSmoothGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)
            dispatchGesture(
                GestureDescription.Builder().addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0,
                        duration
                    )
                ).build(),
                null,
                null
            )
            return
        }

        // Split into segments (Calculus-like approach)
        // 20ms per step seems reasonable for smooth animation (50fps)
        val stepDuration = 20L
        val steps = (duration / stepDuration).toInt().coerceAtLeast(1)
        val stepX = (endX - startX) / steps
        val stepY = (endY - startY) / steps

        executeGestureStep(0, steps, startX, startY, stepX, stepY, stepDuration)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun executeGestureStep(
        currentStep: Int,
        totalSteps: Int,
        lastX: Float,
        lastY: Float,
        stepX: Float,
        stepY: Float,
        stepDuration: Long
    ) {
        if (currentStep >= totalSteps) return

        val nextX = lastX + stepX
        val nextY = lastY + stepY

        val path = Path()
        path.moveTo(lastX, lastY)
        path.lineTo(nextX, nextY)

        val isLastStep = currentStep == totalSteps - 1
        // If not the last step, continue the gesture
        val willContinue = !isLastStep

        val stroke = GestureDescription.StrokeDescription(path, 0, stepDuration, willContinue)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                executeGestureStep(
                    currentStep + 1,
                    totalSteps,
                    nextX,
                    nextY,
                    stepX,
                    stepY,
                    stepDuration
                )
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Logger.w(TAG, "Gesture cancelled at step $currentStep")
            }
        }, null)
    }

    override fun dispatchSoftInput(inputText: String) {
        Logger.i(TAG, "dispatchSoftInput: $inputText")
        execInputText(inputText)
    }

    override fun dispatchBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun dispatchHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun dispatchRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * 输入文本
     */
    private fun execInputText(text: String?) {
        if (rootInActiveWindow == null) {
            Logger.w(TAG, "inputText: $rootInActiveWindow, return")
            return
        }

        val info = rootInActiveWindow.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (info == null) {
            Logger.e(TAG, "execInputText: not focus node!")
            return
        }
        //粘贴�?
        val clipboard: ClipboardManager =
            getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("label", text)
        clipboard.setPrimaryClip(clip)

        val arguments = Bundle()
        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text
        )
        info.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        info.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    override fun onRebind(intent: Intent) {
        super.onRebind(intent)
        Logger.e(TAG, "onRebind: ")
    }


    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
        accessibilityCoreService = null
        Logger.e(TAG, "onDestroy: ")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Logger.e(TAG, "onStartCommand: ")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onUnbind(intent: Intent): Boolean {
        Logger.e(TAG, "onUnbind: ")
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        return super.onUnbind(intent)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Logger.e(TAG, "onKeyEvent: $event")
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        Logger.e(TAG, "onAccessibilityEvent: $event")
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.packageName != null && event.className != null) {
                pkgNameMutableLiveData.value = event.packageName.toString()
            }
        }
    }

    override fun onInterrupt() {
        Logger.e(TAG, "onInterrupt: ")
    }

    override fun onServiceConnected() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        super.onServiceConnected()
        Logger.d(TAG, "onServiceConnected: ")
        val config = AccessibilityServiceInfo()
        config.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        config.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

        config.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

        serviceInfo = config
        accessibilityCoreService = this
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    interface OnPointLongClickListener {
        fun onPointLongClick()
    }

    companion object {
        const val TAG: String = "MyService"

        @SuppressLint("StaticFieldLeak")
        var accessibilityCoreService: AccessibilityCoreService? = null
        var onPointLongClickListener: OnPointLongClickListener? = null
        val isStart: Boolean
            get() = accessibilityCoreService != null
    }
}
