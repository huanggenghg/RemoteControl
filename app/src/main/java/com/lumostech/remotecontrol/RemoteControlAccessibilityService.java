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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.lumostech.remotecontrol.utils.Utils;

import java.util.Arrays;
import java.util.concurrent.Executors;

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
        Log.d(TAG, "onCreate");
    }

    private void addFloatView() {
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
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
//                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        layoutParam.width = 100;
        layoutParam.height = 100;
        layoutParam.format = PixelFormat.TRANSPARENT;
        //设置剧中屏幕显示
//        layoutParam.x = outMetrics.widthPixels / 2;
//        layoutParam.y = outMetrics.heightPixels / 2;
        mFloatRootView = LayoutInflater.from(getApplicationContext()).inflate(R.layout.layout_window, null);
        mFloatRootView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                Log.d(TAG, "onTouch: " + motionEvent);
                return false;
            }
        });
        mFloatRootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getApplicationContext(), "click float window!", Toast.LENGTH_SHORT).show();
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
        Log.d(TAG, "onServiceConnected");
//        addFloatView();
        Log.d(TAG, "=======================onServiceConnected: " + getServiceInfo());
        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000);
                    autoTest();
                    super.run();
                } catch (InterruptedException e) {
                    Log.e(TAG, "=======================onServiceConnected: " + e);
                }
            }
        }.start();
    }

    private void autoTest() {
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "=======================autoTest: " + Thread.currentThread().getName());
                boolean res = Utils.INSTANCE.click(RemoteControlAccessibilityService.this, 725f, 838f);
                Log.d(TAG, "=======================autoTest: res = " + res);
            }
        });
    }
}
