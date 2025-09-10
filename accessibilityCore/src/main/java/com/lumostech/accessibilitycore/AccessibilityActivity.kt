package com.lumostech.accessibilitycore

import android.app.Dialog
import android.os.Message
import androidx.activity.ComponentActivity

open class AccessibilityActivity : ComponentActivity() {
    private val myHandler = AccessibilityHandler(this)
    private var dialog: Dialog? = null

    protected fun showAccessibilityDialog() {
        if (!AccessibilityCoreService.isStart) {
            if (dialog == null) {
                dialog = FullScreenWidthDialog(this)
            }
            dialog?.show()
        }
    }

    protected fun performClick(x: Float, y: Float) {
        val message = Message()
        message.obj = ClickPoint(x, y)
        message.what = 1
        myHandler.sendMessage(message)
    }

    protected fun performScrollUp() {
        val message = Message()
        message.what = 2
        myHandler.sendMessage(message)
    }

    protected fun performScrollDown() {
        val message = Message()
        message.what = 3
        myHandler.sendMessage(message)
    }

    protected fun performScrollLeft() {
        val message = Message()
        message.what = 4
        myHandler.sendMessage(message)
    }

    protected fun performScrollRight() {
        val message = Message()
        message.what = 5
        myHandler.sendMessage(message)
    }

    protected fun performSoftInput(inputText: String) {
        val message = Message()
        message.what = 6
        message.obj = inputText
        myHandler.sendMessage(message)
    }

    protected fun performBack() {
        val message = Message()
        message.what = 7
        myHandler.sendMessage(message)
    }

    protected fun performHome() {
        val message = Message()
        message.what = 8
        myHandler.sendMessage(message)
    }

    protected fun performRecents() {
        val message = Message()
        message.what = 9
        myHandler.sendMessage(message)
    }

    fun setMouseClick(x: Float, y: Float) {
        AccessibilityCoreService.accessibilityCoreService?.dispatchGestureClick(x, y)
    }

    fun scrollUp() {
        AccessibilityCoreService.accessibilityCoreService?.dispatchScrollUp()
    }

    fun scrollDown() {
        AccessibilityCoreService.accessibilityCoreService?.dispatchScrollDown()
    }

    fun scrollLeft() {
        AccessibilityCoreService.accessibilityCoreService?.dispatchScrollLeft()
    }

    fun scrollRight() {
        AccessibilityCoreService.accessibilityCoreService?.dispatchScrollRight()
    }

    fun softInput(inputText: String) {
        AccessibilityCoreService.accessibilityCoreService?.dispatchSoftInput(inputText)
    }

    fun back() {
        AccessibilityCoreService.accessibilityCoreService?.dispatchBack()
    }

    fun home() {
        AccessibilityCoreService.accessibilityCoreService?.dispatchHome()
    }

    fun recents() {
        AccessibilityCoreService.accessibilityCoreService?.dispatchRecents()
    }


    override fun onResume() {
        super.onResume()
        if (AccessibilityCoreService.isStart) {
            if (dialog != null && dialog!!.isShowing) {
                dialog!!.dismiss()
            }
        }
    }
}