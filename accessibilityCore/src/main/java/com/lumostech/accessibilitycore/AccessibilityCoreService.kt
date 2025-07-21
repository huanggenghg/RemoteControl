package com.lumostech.accessibilitycore

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.MutableLiveData
import com.lumostech.accessibilitybase.AccessibilityBaseEvent


class AccessibilityCoreService : AccessibilityService(), AccessibilityBaseEvent {
    var pkgNameMutableLiveData: MutableLiveData<String> = MutableLiveData()

    init {
        Log.e(TAG, "MyService: ")
    }

    override fun dispatchGestureClick(x: Float, y: Float) {
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
            null,
            null
        )
    }

    override fun dispatchScrollUp() {
        dispatchScroll(true)
    }

    override fun dispatchScrollDown() {
        dispatchScroll(false)
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
    }

    override fun onRebind(intent: Intent) {
        super.onRebind(intent)
        Log.e(TAG, "onRebind: ")
    }


    override fun onDestroy() {
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
        return super.onUnbind(intent)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
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
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected: ")
        val config = AccessibilityServiceInfo()
        config.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        config.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

        config.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

        serviceInfo = config
        accessibilityCoreService = this
    }

    companion object {
        const val TAG: String = "MyService"
        var accessibilityCoreService: AccessibilityCoreService? = null

        val isStart: Boolean
            get() = accessibilityCoreService != null
    }
}