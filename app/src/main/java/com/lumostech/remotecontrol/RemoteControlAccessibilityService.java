package com.lumostech.remotecontrol;

import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import cn.coderpig.cp_fast_accessibility.FastAccessibilityService;

public class RemoteControlAccessibilityService extends FastAccessibilityService {

    private static final String TAG = "RemoteControlAccessibilityService";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.d(TAG, "onAccessibilityEvent: " + event.toString());
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public boolean getEnableListenApp() {
        return true;
    }
}
