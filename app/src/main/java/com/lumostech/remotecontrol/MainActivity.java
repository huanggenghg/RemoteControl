package com.lumostech.remotecontrol;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.lumostech.remotecontrol.fw_permission.FloatWinPermissionCompat;

import cn.coderpig.cp_fast_accessibility.FastAccessibilityService;
import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.constants.ZegoPublishChannel;
import im.zego.zegoexpress.constants.ZegoVideoBufferType;
import im.zego.zegoexpress.entity.ZegoCustomVideoCaptureConfig;
import im.zego.zegoexpress.entity.ZegoStream;

public class MainActivity extends BaseActivity {

    public static MediaProjectionManager mMediaProjectionManager;
    public static MediaProjection mMediaProjection;
    private static final int REQUEST_CODE = 111;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestMediaProjection();

        // 开始通话按钮
        findViewById(R.id.startButton).setOnClickListener(new View.OnClickListener() {
            // 点击开始通话
            @Override
            public void onClick(View view) {
                // 创建Express SDK 实例
                createEngine();
                // 监听常用事件
                setEventHandler();
                // 登录房间
                loginRoom("user2", "room1");
                // 开始预览及推流
                startPublish();
            }
        });

        // 停止通话按钮
        findViewById(R.id.stopButton).setOnClickListener(new View.OnClickListener() {
            // 点击停止通话
            @Override
            public void onClick(View view) {
                if(mEngine != null) {
                    mEngine.logoutRoom();
                    ZegoExpressEngine.destroyEngine(() -> {
                        //销毁成功
                    });
                }
            }
        });

        // 停止通话按钮
        findViewById(R.id.helpButton).setOnClickListener(new View.OnClickListener() {
            // 点击停止通话
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, RemoteControlActivity.class);
                startActivity(intent);

            }
        });

        findViewById(R.id.testButton).setOnClickListener(new View.OnClickListener() {
            // 点击停止通话
            @Override
            public void onClick(View view) {
                if (!FastAccessibilityService.Companion.isServiceEnable()) {
                    FastAccessibilityService.Companion.requireAccessibility();
                }
                boolean hasWinPermission = FloatWinPermissionCompat.getInstance().check(MainActivity.this);
                if (!hasWinPermission) {
                    requestPermissionAndShow();
                }
//                moveTaskToBack(true);
            }
        });

        findViewById(R.id.testButton2).setOnClickListener(new View.OnClickListener() {
            // 点击停止通话
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, "click me!!!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        Toast.makeText(MainActivity.this, "dispatchTouchEvent!!!", Toast.LENGTH_SHORT).show();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void createEngine() {
        super.createEngine();
        //VideoCaptureScreen继承IZegoCustomVideoCaptureHandler，用于监听自定义采集onStart和onStop回调
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        int width = wm.getDefaultDisplay().getWidth();
        int height = wm.getDefaultDisplay().getHeight();
        VideoCaptureScreen videoCapture = new VideoCaptureScreen(mMediaProjection, width, height, mEngine);
        //监听自定义采集开始停止回调
        mEngine.setCustomVideoCaptureHandler(videoCapture);
        ZegoCustomVideoCaptureConfig videoCaptureConfig = new ZegoCustomVideoCaptureConfig();
        //使用SurfaceTexture类型进行自定义采集
        videoCaptureConfig.bufferType = ZegoVideoBufferType.SURFACE_TEXTURE;
        //开始自定义采集
        mEngine.enableCustomVideoCapture(true, videoCaptureConfig, ZegoPublishChannel.MAIN);
    }

    @Override
    protected void onRoomStreamUpdate(ZegoStream zegoStream, String playStreamId) {
        // 通知推流已成功
        Log.d("MAIN", "onRoomStreamUpdate: playStreamId = " + playStreamId);
    }

    @Override
    protected void onLoginRoomSuccess() {
        // 通知已登录房间
        Log.d("MAIN", "onLoginRoomSuccess");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                //Target版本高于等于10.0需要使用前台服务，并在前台服务的onStartCommand方法中创建MediaProjection
                Intent service = new Intent(MainActivity.this, CaptureScreenService.class);
                service.putExtra("code", resultCode);
                service.putExtra("data", data);
                startForegroundService(service);
            } else {
                //Target版本低于10.0直接获取MediaProjection
                mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
            }
        }
    }

    private void requestMediaProjection() {
        // 5.0及以上版本
        // 请求录屏权限，等待用户授权
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
        Log.d("TAG", "init: mMediaProjectionManager = " + mMediaProjectionManager);
    }

    private void startPublish() {
        // 开始推流
        // 用户调用 loginRoom 之后再调用此接口进行推流
        // 在同一个 AppID 下，todo 开发者需要保证“streamID” 全局唯一，如果不同用户各推了一条 “streamID” 相同的流，后推流的用户会推流失败。
        mEngine.startPublishingStream("stream2");
    }

    private void requestPermissionAndShow() {
        new AlertDialog.Builder(this).setTitle("悬浮窗权限未开启")
                .setMessage(getString(R.string.app_name) + "获得悬浮窗权限，才能正常使用应用")
                .setPositiveButton("去开启", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 显示授权界面
                        try {
                            FloatWinPermissionCompat.getInstance().apply(MainActivity.this);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                })
                .setNegativeButton("取消", null).show();
    }
}
