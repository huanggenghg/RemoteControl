package com.lumostech.remotecontrol.activity;

import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;

import com.lumostech.remotecontrol.bean.Bean;

import java.lang.ref.WeakReference;

public class AccessibilityHandler extends Handler {

    private final WeakReference<AccessibilityActivity> accessibilityActivityWf;

    public AccessibilityHandler(AccessibilityActivity accessibilityActivity) {
        this.accessibilityActivityWf = new WeakReference<>(accessibilityActivity);
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        super.handleMessage(msg);
        if (accessibilityActivityWf.get() == null) {
            return;
        }
        int i = msg.what;
        if (i == 0) {
            accessibilityActivityWf.get().windowView.setwmParamsFlags(8);
        } else if (i == 1) {
            Bean bean = (Bean) msg.obj;
            accessibilityActivityWf.get().setMouseClick(bean.getX(), bean.getY());
        } else if (i == 2) {
            accessibilityActivityWf.get().windowView.setwmParamsFlags(8);
        }
    }
}
