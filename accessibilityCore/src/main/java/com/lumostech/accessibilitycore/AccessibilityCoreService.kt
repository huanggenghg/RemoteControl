package com.lumostech.accessibilitycore

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.DisplayMetrics
import android.widget.Toast
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.resume
import android.view.KeyEvent
import android.view.Gravity
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
import com.lumostech.accessibilitybase.utils.Logger


@SuppressLint("AccessibilityPolicy")
class AccessibilityCoreService : AccessibilityService(), AccessibilityBaseEvent, LifecycleOwner {
    private var pkgNameMutableLiveData: MutableLiveData<String> = MutableLiveData()

    private lateinit var windowManager: WindowManager
    private var customWindowWidthPixels: Int? = null
    private var floatRootView: SmallWindowView? = null//悬浮窗View
    private var floatCustomView: View? = null
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val sequenceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sequenceMutex = Mutex()
    private lateinit var recording: ClickRecording
    private lateinit var sequenceStore: ClickSequenceStore
    private var recordingProtection: ClickRecordingProtection? = null
    private var protectedRecording = false
    private var executionControlView: View? = null

    /** Independent from recording/confirmation windows, so replay cannot hide its stop control. */
    fun showExecutionControl(view: View, x: Int, y: Int, width: Int, height: Int): Boolean {
        if (executionControlView != null) return false
        val parameters = WindowManager.LayoutParams(width, height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            this.x = x
            this.y = y
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        return try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(view, parameters)
            executionControlView = view
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Unable to show execution control", e)
            false
        }
    }

    fun hideExecutionControl(view: View) {
        if (executionControlView !== view) return
        FloatWindowUtils.removeWindow(this, view)
        executionControlView = null
    }

    override fun onCreate() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
        super.onCreate()
        sequenceStore = ClickSequenceStore(this)
        recording = ClickRecording(sequenceStore.load())
        recordingProtection = sequenceStore.loadProtection()
        protectedRecording = recordingProtection != null
        ViewModelMain.recordedPointCount.value = recording.snapshot().size
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
                ViewModelMain.isShowCustomFloatWindow.value = false
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
                    ViewModelMain.isShowFloatWindow.value = false
                    FloatWindowUtils.showWindow(this, view, customWindowWidthPixels)
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
        setFloatCustomView(floatCustomView, null)
    }

    fun setFloatCustomView(floatCustomView: View, widthPixels: Int?) {
        this.floatCustomView?.takeIf { it !== floatCustomView }?.let { FloatWindowUtils.removeWindow(this, it) }
        this.floatCustomView = floatCustomView
        customWindowWidthPixels = widthPixels
    }

    fun getRecordedClickPoints(): List<ClickCounterPoint> = recording.snapshot()

    fun enableProtectedRecording() {
        protectedRecording = true
        val config = serviceInfo
        config.flags = config.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = config
    }

    fun getRecordedProtection(): ClickRecordingProtection? = recordingProtection
        ?.let { it.copy(packages = it.packages.toList()) }

    /** Do not use cached accessibility events: they can describe a window that is no longer active. */
    @Suppress("DEPRECATION")
    fun currentClickEnvironment(): ClickEnvironment? {
        val root = rootInActiveWindow ?: return null
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        val display = manager.defaultDisplay
        val metrics = DisplayMetrics().also { display.getRealMetrics(it) }
        return try {
            val foregroundPackage = root.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return null
            val window = root.window
            val displayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window?.displayId ?: return null else 0
            val bounds = Rect().also { root.getBoundsInScreen(it) }
            ClickEnvironment(metrics.widthPixels, metrics.heightPixels, display.rotation, foregroundPackage, displayId,
                bounds.left, bounds.top, bounds.right, bounds.bottom)
        } finally { root.recycle() }
    }

    fun startRecording() {
        recording.clear()
        recordingProtection = null
        sequenceStore.save(emptyList())
        ViewModelMain.recordedPointCount.value = 0
    }

    fun recordClick(x: Float, y: Float) {
        val points = recording.snapshot()
        if (points.size >= ClickSequenceCodec.MAX_POINTS) {
            Toast.makeText(this, "最多录制 ${ClickSequenceCodec.MAX_POINTS} 个点击", Toast.LENGTH_SHORT).show()
            return
        }
        val environment = if (protectedRecording) currentClickEnvironment() else null
        if (protectedRecording) {
            val message = when {
                points.isNotEmpty() && recordingProtection == null -> "旧录制缺少环境信息，请清空并重新录制"
                environment == null -> "无法确认当前应用，未记录点击"
                environment.displayId != 0 -> "请在设备主屏幕录制，未记录点击"
                recordingProtection?.hasSameDisplay(environment) == false -> "屏幕尺寸或方向已改变，请清空并重新录制"
                x < 0 || y < 0 || x >= environment.width || y >= environment.height || !environment.contains(x, y) -> "点击位置不在目标应用窗口内，未记录"
                else -> null
            }
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                return
            }
        }
        recording.record(x, y, SystemClock.elapsedRealtime())
        val updated = recording.snapshot()
        if (!ClickSequenceCodec.isValid(updated)) {
            recording = ClickRecording(points)
            Toast.makeText(this, "录制时长不能超过 8 分钟，请重新录制", Toast.LENGTH_SHORT).show()
            return
        }
        if (environment != null) {
            recordingProtection = ClickRecordingProtection(environment.width, environment.height, environment.rotation,
                recordingProtection?.packages.orEmpty() + environment.packageName)
        }
        sequenceStore.save(updated, recordingProtection)
        ViewModelMain.recordedPointCount.value = updated.size
    }

    override fun dispatchClickPointsEvent() {
        val snapshot = getRecordedClickPoints()
        sequenceScope.launch {
            if (!executeClickSequence(snapshot)) Logger.w(TAG, "Click sequence did not complete")
        }
    }

    suspend fun executeClickSequence(
        points: List<ClickCounterPoint>,
        canContinue: () -> Boolean = { true }
    ): Boolean =
        withContext(Dispatchers.Main.immediate) {
            if (!sequenceMutex.tryLock()) return@withContext false
            try {
                ViewModelMain.isShowFloatWindow.value = false
                ViewModelMain.isShowCustomFloatWindow.value = false
                delay(50)
                ClickSequenceExecutor.execute(
                    points,
                    now = { SystemClock.elapsedRealtime() },
                    wait = { delay(it) },
                    click = { point ->
                        if (!canContinue() || accessibilityCoreService !== this@AccessibilityCoreService) return@execute false
                        withTimeoutOrNull(2_000L) {
                            suspendCancellableCoroutine { continuation ->
                                val path = Path().apply { moveTo(point.x, point.y) }
                                val gesture = GestureDescription.Builder().addStroke(
                                    GestureDescription.StrokeDescription(path, 0, 20)
                                ).build()
                                val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
                                    override fun onCompleted(gestureDescription: GestureDescription) {
                                        if (continuation.isActive) continuation.resume(true)
                                    }

                                    override fun onCancelled(gestureDescription: GestureDescription) {
                                        if (continuation.isActive) continuation.resume(false)
                                    }
                                }, null)
                                if (!accepted && continuation.isActive) continuation.resume(false)
                            }
                        } ?: false
                    }
                )
            } finally {
                sequenceMutex.unlock()
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
        sequenceScope.cancel()
        floatRootView?.let { FloatWindowUtils.removeWindow(this, it) }
        floatCustomView?.let { FloatWindowUtils.removeWindow(this, it) }
        executionControlView?.let { FloatWindowUtils.removeWindow(this, it) }
        executionControlView = null
        floatRootView = null
        floatCustomView = null
        super.onDestroy()
        accessibilityCoreService = null
        Logger.e(TAG, "onDestroy: ")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Logger.e(TAG, "onStartCommand: ")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onUnbind(intent: Intent): Boolean {
        accessibilityCoreService = null
        sequenceScope.coroutineContext.cancelChildren()
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
        if (protectedRecording) config.flags = config.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS

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
        const val CONFIGURE_CLICKS = "configure_clicks"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        var accessibilityCoreService: AccessibilityCoreService? = null
        var onPointLongClickListener: OnPointLongClickListener? = null
        val isStart: Boolean
            get() = accessibilityCoreService != null
    }
}
