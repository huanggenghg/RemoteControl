package com.lumostech.accessibilitycore

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object DialogHelper {
    fun showMessagePositiveDialog(activity: Activity, title: String?, msg: String?): Dialog {
        return MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(msg)
            .setCancelable(false)
            .setNegativeButton(
                "取消"
            ) { dialog: DialogInterface, which: Int -> dialog.cancel() }
            .setPositiveButton(
                "确定"
            ) { dialog: DialogInterface?, which: Int ->
                try {
                    activity.startActivity(Intent("android.settings.ACCESSIBILITY_SETTINGS"))
                } catch (e: Exception) {
                    activity.startActivity(Intent("android.settings.SETTINGS"))
                    e.printStackTrace()
                }
            }.show()
    }
}
