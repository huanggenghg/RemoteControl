package com.lumostech.remotecontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import androidx.lifecycle.MutableLiveData

class MyService : AccessibilityService() {
    var pkgNameMutableLiveData: MutableLiveData<String> = MutableLiveData()

    init {
        Log.e(TAG, "MyService: ")
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    fun dispatchGestureClick(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        path.lineTo(x + 1, y + 1)
        dispatchGesture(
            GestureDescription.Builder().addStroke(StrokeDescription(path, 0, 20)).build(),
            null,
            null
        )
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    fun dispatchGestureClick(x: Float, y: Float, duration: Int) {
        val path = Path()
        path.moveTo(x, y)
        path.lineTo(x + 1, y + 1)
        dispatchGesture(
            GestureDescription.Builder().addStroke(StrokeDescription(path, 0, duration.toLong()))
                .build(), null, null
        )
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    fun dispatchGesture(x1: Float, y1: Float, x2: Float, y2: Float, duration: Int) {
        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        dispatchGesture(
            GestureDescription.Builder().addStroke(StrokeDescription(path, 0, duration.toLong()))
                .build(), null, null
        )
    }


    fun dispatchGesture(path: Path, duration: Int) {
        dispatchGesture(
            GestureDescription.Builder().addStroke(StrokeDescription(path, 0, duration.toLong()))
                .build(), null, null
        )
    }

    override fun onRebind(intent: Intent) {
        super.onRebind(intent)
        Log.e(TAG, "onRebind: ")
    }


    override fun onDestroy() {
        super.onDestroy()
        myService = null
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
        myService = this
    }

    companion object {
        const val TAG: String = "MyService"
        var myService: MyService? = null

        val isStart: Boolean
            get() = myService != null
    }
}
