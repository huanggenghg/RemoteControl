package com.lumostech.remotecontrol.activity

import android.app.Dialog
import android.os.Bundle
import android.os.Message
import androidx.appcompat.app.AppCompatActivity
import com.lumostech.accessibilitycore.AccessibilityCoreService
import com.lumostech.remotecontrol.R
import com.lumostech.remotecontrol.bean.Bean
import com.lumostech.remotecontrol.dialog.DialogHelper

open class AccessibilityActivity : AppCompatActivity() {
    private val myHandler = AccessibilityHandler(this)
    var dialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    protected fun showAccessibilityDialog() {
        if (!AccessibilityCoreService.isStart) {
            dialog = DialogHelper.showMessagePositiveDialog(
                this,
                "辅助功能",
                "使用连点器需要开启(无障碍)辅助功能，是否现在去开启？"
            )
        }
    }

    protected fun performClick(x: Float, y: Float) {
        val message = Message()
        message.obj = Bean(x, y)
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

    protected fun performSoftInput(inputText: String) {
        val message = Message()
        message.what = 4
        message.obj = inputText
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

    fun softInput(inputText: String) {
        AccessibilityCoreService.accessibilityCoreService?.dispatchSoftInput(inputText)
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