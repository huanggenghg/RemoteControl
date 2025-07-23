package com.lumostech.accessibilitycore

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
        execInputText(inputText)
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