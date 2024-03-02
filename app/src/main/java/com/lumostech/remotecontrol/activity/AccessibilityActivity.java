package com.lumostech.remotecontrol.activity;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.lumostech.remotecontrol.MyService;
import com.lumostech.remotecontrol.PowerKeyObserver;
import com.lumostech.remotecontrol.R;
import com.lumostech.remotecontrol.SmallWindowView;
import com.lumostech.remotecontrol.bean.Bean;
import com.lumostech.remotecontrol.dialog.DialogHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccessibilityActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private int OVERLAY_PERMISSION_REQ_CODE = 2;
    private WindowManager.LayoutParams btn_layoutParams;
    private WindowManager.LayoutParams mLayoutParams;
    private AccessibilityHandler myHandler = new AccessibilityHandler(this);
    public ExecutorService singleThreadExecutor;
    public SmallWindowView windowView;
    private WindowManager wm;
    private PowerKeyObserver powerKeyObserver;//检测电源键是否被按下
    Dialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initSmallViewLayout();
        this.singleThreadExecutor = Executors.newSingleThreadExecutor();

        powerKeyObserver = new PowerKeyObserver(this);
        powerKeyObserver.startListen();//开始注册广播
        powerKeyObserver.setHomeKeyListener(new PowerKeyObserver.OnPowerKeyListener() {
            @Override
            public void onPowerKeyPressed() {
                myHandler.sendEmptyMessage(2);
            }
        });
    }

    protected void showAccessibilityDialog() {
        if (!MyService.isStart()) {
            dialog = DialogHelper.showMessagePositiveDialog(this, "辅助功能", "使用连点器需要开启(无障碍)辅助功能，是否现在去开启？");
        }
    }

    protected void performClick(float x, float y) {
        windowView.postDelayed(new Runnable() {
            @Override
            public void run() {
                windowView.setwmParamsFlags(24);
                singleThreadExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Message message = new Message();
                        message.obj = new Bean(x, y);
                        message.what = 1;
                        AccessibilityActivity.this.myHandler.sendMessage(message);
                        for (int t = 0; t < 100; t++) {
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException e2) {
                                e2.printStackTrace();
                            }
                        }
                        AccessibilityActivity.this.myHandler.sendEmptyMessageDelayed(0, 200);
                    }
                });
            }
        }, 10000);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void setMouseClick(float x, float y) {
        MyService myService = MyService.myService;
        if (myService != null) {
            myService.dispatchGestureClick(x, y);
        }
    }

    @SuppressLint("WrongConstant")
    public void initSmallViewLayout() {
        this.windowView = (SmallWindowView) LayoutInflater.from(this).inflate(R.layout.window_a, (ViewGroup) null);
        this.wm = (WindowManager) getApplication().getSystemService("window");
        this.mLayoutParams = new WindowManager.LayoutParams(-2, -2, 2003, 8, -3);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(-2, -2, 2003, 8, -3);
        this.btn_layoutParams = layoutParams;
        layoutParams.gravity = 49;
        this.mLayoutParams.gravity = 0;
        this.windowView.setWm(this.wm);
        this.windowView.setWmParams(this.mLayoutParams);
    }

    public void alertWindow() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (Build.VERSION.SDK_INT >= 26) {
                this.mLayoutParams.type = 2038;
                this.btn_layoutParams.type = 2038;
            }
            requestDrawOverLays();
        } else if (Build.VERSION.SDK_INT >= 21) {
            ActivityCompat.requestPermissions(this, new String[]{"android.permission.SYSTEM_ALERT_WINDOW"}, 1);
        }
    }

    public void showWindow() {
        if (this.wm != null && this.windowView.getWindowId() == null) {
            this.wm.addView(this.windowView, this.mLayoutParams);
        }
    }

    @SuppressLint("WrongConstant")
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void requestDrawOverLays() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "can not DrawOverlays", 0).show();
            startActivityForResult(new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + getPackageName())), this.OVERLAY_PERMISSION_REQ_CODE);
            return;
        }
        showWindow();

    }

    /* access modifiers changed from: protected */
    @SuppressLint("WrongConstant")
    @Override
    @RequiresApi(api = Build.VERSION_CODES.M)
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != this.OVERLAY_PERMISSION_REQ_CODE) {
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "设置权限拒绝", 0).show();
        } else {
            Toast.makeText(this, "设置权限成功", 0).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (MyService.isStart()) {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
            alertWindow();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        powerKeyObserver.stopListen();
    }
}