package com.lumostech.accessibilitycore

import android.os.Handler
import android.os.Message
import java.lang.ref.WeakReference

class AccessibilityHandler(accessibilityActivity: AccessibilityActivity?) : Handler() {
    private val accessibilityActivityWf =
        WeakReference(accessibilityActivity)

    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)
        if (accessibilityActivityWf.get() == null) {
            return
        }
        when (msg.what) {
            1 -> {
                val clickPoint = msg.obj as ClickPoint
                accessibilityActivityWf.get()!!.setMouseClick(clickPoint.x, clickPoint.y)
            }

            2 -> {
                accessibilityActivityWf.get()!!.scrollUp()
            }

            3 -> {
                accessibilityActivityWf.get()!!.scrollDown()
            }

            4 -> {
                val inputText = msg.obj as String
                accessibilityActivityWf.get()!!.softInput(inputText)
            }
        }
    }
}
