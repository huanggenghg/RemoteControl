package com.lumostech.accessibilitycore

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import com.lumostech.accessibilitybase.AccessibilityBaseEvent
import java.util.Objects.isNull


@SuppressLint("AccessibilityPolicy")
class AccessibilityCoreService : AccessibilityService(), AccessibilityBaseEvent, LifecycleOwner {
    private var pkgNameMutableLiveData: MutableLiveData<String> = MutableLiveData()

    private lateinit var windowManager: WindowManager
    private var floatRootView: SmallWindowView? = null//悬浮窗View
    private val lifecycleRegistry = LifecycleRegistry(this)

    init {
        Log.e(TAG, "MyService: ")
    }

    override fun onCreate() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
        super.onCreate()
        initObserve()
    }

    /**
     * 打开关闭的订阅
     */
    private fun initObserve() {
        ViewModelMain.isShowWindow.observe(this, {
            if (it) {
                showWindow()
            } else {
                if (!isNull(floatRootView)) {
                    if (!isNull(floatRootView?.windowToken)) {
                        if (!isNull(windowManager)) {
                            windowManager.removeView(floatRootView)
                        }
                    }
                }
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showWindow() {
        // 设置LayoutParam
        // 获取WindowManager服务
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val outMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(outMetrics)
        var layoutParam = WindowManager.LayoutParams()
        layoutParam.apply {
            //显示的位置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                //刘海屏延伸到刘海里面
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            } else {
                type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            flags =
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            format = PixelFormat.TRANSPARENT
        }
        floatRootView = LayoutInflater.from(this).inflate(R.layout.float_window, null) as SmallWindowView
        windowManager.addView(floatRootView, layoutParam)
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

    private fun execDispatchGestureClick(x: Float,
                                         y: Float,
                                         onComplete: (() -> Unit)? = null) {
        val path = Path()
        path.moveTo(x, y)
        path.lineTo(x + 1, y + 1)
        dispatchGesture(
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
                }
            },
            null
        )
    }

    override fun dispatchScrollUp() {
        dispatchScroll(true)
    }

    override fun dispatchScrollDown() {
        dispatchScroll(false)
    }

    override fun dispatchScrollLeft() {
        dispatchXScroll(true)
    }

    override fun dispatchScrollRight() {
        dispatchXScroll(false)
    }

    private fun dispatchXScroll(isScrollingLeft: Boolean) {
        val diff = if (isScrollingLeft) -50F else 50F
        val centerX = resources.displayMetrics.widthPixels / 2
        val centerY = resources.displayMetrics.widthPixels / 2

        val path = Path()
        path.moveTo(centerX.toFloat(), centerY.toFloat())
        path.lineTo(centerX + diff, centerY.toFloat())
        dispatchGesture(
            GestureDescription.Builder().addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    20
                )
            ).build(),
            null,
            null
        )
    }

    private fun dispatchScroll(isScrollingUp: Boolean) {
        val diff = if (isScrollingUp) -50F else 50F
        val centerX = resources.displayMetrics.widthPixels / 2
        val centerY = resources.displayMetrics.widthPixels / 2

        val path = Path()
        path.moveTo(centerX.toFloat(), centerY.toFloat())
        path.lineTo(centerX.toFloat(), centerY + diff)
        dispatchGesture(
            GestureDescription.Builder().addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    20
                )
            ).build(),
            null,
            null
        )
    }

    override fun dispatchSoftInput(inputText: String) {
        Log.e(TAG, "dispatchSoftInput: $inputText")
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
            Log.w(TAG, "inputText: $rootInActiveWindow, return")
            return
        }

        val info = rootInActiveWindow.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (info == null) {
            Log.e(TAG, "execInputText: not focus node!")
            return
        }
        //粘贴板
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
        Log.e(TAG, "onRebind: ")
    }


    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
        accessibilityCoreService = null
        Log.e(TAG, "onDestroy: ")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.e(TAG, "onStartCommand: ")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.e(TAG, "onUnbind: ")
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        return super.onUnbind(intent)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.e(TAG, "onKeyEvent: $event")
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        Log.e(TAG, "onAccessibilityEvent: $event")
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.packageName != null && event.className != null) {
                pkgNameMutableLiveData.value = event.packageName.toString()
            }
        }
    }

    override fun onInterrupt() {
        Log.e(TAG, "onInterrupt: ")
    }

    override fun onServiceConnected() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected: ")
        val config = AccessibilityServiceInfo()
        config.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        config.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

        config.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

        serviceInfo = config
        accessibilityCoreService = this
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    companion object {
        const val TAG: String = "MyService"
        @SuppressLint("StaticFieldLeak")
        var accessibilityCoreService: AccessibilityCoreService? = null

        val isStart: Boolean
            get() = accessibilityCoreService != null
    }
}