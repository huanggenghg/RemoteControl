package com.lumostech.remotecontrol.activity;

import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import im.zego.zegoexpress.constants.ZegoPublishChannel;
import im.zego.zegoexpress.constants.ZegoVideoBufferType;
import im.zego.zegoexpress.entity.ZegoCustomVideoCaptureConfig;
import im.zego.zegoexpress.entity.ZegoStream;

public class MediaProjectionActivity extends ZegoBaseActivity {

    public static MediaProjectionManager mMediaProjectionManager;
    public static MediaProjection mMediaProjection;
    private static final int REQUEST_CODE = 111;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestMediaProjection();
    }

    @Override
    protected void createEngine() {
        super.createEngine();
        //VideoCaptureScreen继承IZegoCustomVideoCaptureHandler，用于监听自定义采集onStart和onStop回调
        int width = getWindow().getDecorView().getWidth();
        int height = getWindow().getDecorView().getHeight();
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
                Intent service = new Intent(MediaProjectionActivity.this, CaptureScreenService.class);
                service.putExtra("code", resultCode);
                service.putExtra("data", data);
                startForegroundService(service);
            } else {
                //Target版本低于10.0直接获取MediaProjection
                mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
            }
        }
    }

    protected void startPublish() {
        // 开始推流
        // 用户调用 loginRoom 之后再调用此接口进行推流
        // 在同一个 AppID 下，todo 开发者需要保证“streamID” 全局唯一，如果不同用户各推了一条 “streamID” 相同的流，后推流的用户会推流失败。
        mEngine.startPublishingStream("stream2");
    }

    private void requestMediaProjection() {
        // 5.0及以上版本
        // 请求录屏权限，等待用户授权
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
        Log.d("TAG", "init: mMediaProjectionManager = " + mMediaProjectionManager);
    }
}
