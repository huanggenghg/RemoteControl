package com.lumostech.remotecontrol.activity

import android.os.Handler
import android.os.Message
import com.lumostech.remotecontrol.bean.Bean
import java.lang.ref.WeakReference

class AccessibilityHandler(accessibilityActivity: AccessibilityActivity?) : Handler() {
    private val accessibilityActivityWf =
        WeakReference(accessibilityActivity)

    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)
        if (accessibilityActivityWf.get() == null) {
            return
        }
        val i = msg.what
        if (i == 1) {
            val bean = msg.obj as Bean
            accessibilityActivityWf.get()!!.setMouseClick(bean.x, bean.y)
        }
    }
}
