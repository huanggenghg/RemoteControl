package com.lumostech.remotecontrol.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;

public class DialogHelper {
    public static Dialog showMessagePositiveDialog(Activity activity,String title,String msg) {
       return new AlertDialog.Builder(activity)
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
