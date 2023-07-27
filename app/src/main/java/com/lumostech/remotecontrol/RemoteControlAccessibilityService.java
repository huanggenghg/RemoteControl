package com.lumostech.remotecontrol;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class RemoteControlAccessibilityService extends AccessibilityService {

    private static final String TAG = "RemoteControlAccessibilityService";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.d(TAG, "onAccessibilityEvent: " + event.toString());
    }

    @Override
    public void onInterrupt() {

    }
}
