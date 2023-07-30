package com.lumostech.remotecontrol;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.Arrays;

import cn.coderpig.cp_fast_accessibility.AnalyzeSourceResult;
import cn.coderpig.cp_fast_accessibility.EventWrapper;
import cn.coderpig.cp_fast_accessibility.FastAccessibilityService;
import cn.coderpig.cp_fast_accessibility.NodeWrapper;
import kotlin.Unit;
import kotlin.jvm.functions.Function2;

public class RemoteControlAccessibilityService extends FastAccessibilityService {

    private static final String TAG = "RemoteControlAccessibilityService";
    private View mFloatRootView;
    private WindowManager mWindowManager;

    @Override
    public void onCreate() {
        super.onCreate();
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(outMetrics);
        WindowManager.LayoutParams layoutParam = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParam.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            //刘海屏延伸到刘海里面
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutParam.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
        } else {
            layoutParam.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }
        layoutParam.flags =
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        layoutParam.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParam.height = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParam.format = PixelFormat.TRANSPARENT;
        //设置剧中屏幕显示
//        layoutParam.x = outMetrics.widthPixels / 2;
//        layoutParam.y = outMetrics.heightPixels / 2;
        mFloatRootView = LayoutInflater.from(this).inflate(R.layout.layout_window, null);
        mFloatRootView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                Log.d(TAG, "onTouch: " + motionEvent);
                return false;
            }
        });
//        floatRootView?.setOnTouchListener(ItemViewTouchListener(layoutParam, windowManager))
        mWindowManager.addView(mFloatRootView, layoutParam);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        super.onAccessibilityEvent(event);
        Log.d(TAG, "onAccessibilityEvent: " + event.toString());
    }

    @Override
    public void analyzeCallBack(@Nullable EventWrapper wrapper, @NonNull AnalyzeSourceResult result) {
        for (NodeWrapper nodeWrapper: result.getNodes()) {
            Log.d(TAG, "=============analyzeCallBack: nodeWrapper = " + nodeWrapper);
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "onInterrupt");
    }

    @Override
    public boolean getEnableListenApp() {
        return true;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo serviceInfo = new AccessibilityServiceInfo();
        serviceInfo.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        serviceInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
        serviceInfo.flags = AccessibilityServiceInfo.DEFAULT | AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_ENHANCED_WEB_ACCESSIBILITY
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION;
        serviceInfo.notificationTimeout = 100;
        setServiceInfo(serviceInfo);
        Log.d(TAG, "=======================onServiceConnected: " + getServiceInfo());

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "=======================getRootInActiveWindow = " + getRootInActiveWindow());

                if (getRootInActiveWindow() != null) {
                    getRootInActiveWindow().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            }
        },10000);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void autoTest() {
        Log.d(TAG, "=======================autoTest");
        GestureDescription.Builder builder = new GestureDescription.Builder();
        Path path = new Path();
        path.moveTo(549.4912f, 676.6101f);
        GestureDescription gestureDescription = builder.addStroke(
                        new GestureDescription.StrokeDescription(path, 0, 100))
                .build();
        dispatchGesture(gestureDescription, new AccessibilityService.GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.d("AutoTouchService", "滑动结束" + gestureDescription.getStrokeCount());
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Log.d("AutoTouchService", "滑动取消");
            }
        }, null);
    }
}
