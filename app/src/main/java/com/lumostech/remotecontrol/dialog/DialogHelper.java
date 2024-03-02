package com.lumostech.remotecontrol.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DialogHelper {
    public static Dialog showMessagePositiveDialog(Activity activity,String title,String msg) {
        return new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(msg)
               .setCancelable(false)
               .setNegativeButton("取消", (dialog, which) -> dialog.cancel())
               .setPositiveButton("确定", (dialog, which) -> {
                   try {
                       activity.startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS"));
                   } catch (Exception e) {
                       activity.startActivity(new Intent("android.settings.SETTINGS"));
                       e.printStackTrace();
                   }
               }).show();
    }
}
