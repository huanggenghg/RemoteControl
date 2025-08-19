package com.lumostech.accessibilitycore

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager


class FullScreenWidthDialog(
    context: Context,
    private val confirmClickListener: View.OnClickListener? = null,
    private val cancelClickListener: View.OnClickListener? = null
) : Dialog(context, R.style.FullScreenWidthDialog) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_alert)
        window?.apply {
            decorView.background = null
            attributes.gravity = Gravity.CENTER
            attributes.width = WindowManager.LayoutParams.MATCH_PARENT
            attributes.height = WindowManager.LayoutParams.WRAP_CONTENT
            decorView.setPadding(0, 0, 0, 0)
        }
        setCanceledOnTouchOutside(true)
        findViewById<View>(R.id.confirm).setOnClickListener(confirmClickListener ?: View.OnClickListener {
            try {
                context.startActivity(Intent("android.settings.ACCESSIBILITY_SETTINGS"))
            } catch (e: Exception) {
                context.startActivity(Intent("android.settings.SETTINGS"))
                e.printStackTrace()
            }
            dismiss()
        })
        findViewById<View>(R.id.cancel).setOnClickListener(cancelClickListener ?: View.OnClickListener {
            dismiss()
        })
    }
}