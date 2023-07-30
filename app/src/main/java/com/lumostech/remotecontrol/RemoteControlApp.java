package com.lumostech.remotecontrol;

import android.app.Application;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;

import cn.coderpig.cp_fast_accessibility.FastAccessibilityService;

public class RemoteControlApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FastAccessibilityService.Companion.init(this, RemoteControlAccessibilityService.class,
                new ArrayList<Integer>() {{
                    add(AccessibilityEvent.TYPE_VIEW_CLICKED);
                    add(AccessibilityEvent.TYPE_WINDOWS_CHANGED);
                }});
    }
}
