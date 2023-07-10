package com.lumostech.remotecontrol;

import im.zego.zegoexpress.constants.ZegoPublishChannel;

public class VideoCaptureScreen extends ZegoVideoCaptureCallback {
    @Override
    //当收到onStart回调后，就可以通过MediaProjection创建VirtualDisplay，并给ZEGO SDK塞屏幕数据
    public void onStart(ZegoPublishChannel channel) {
//        if (mZegoEngine != null && !mIsCapturing && mMediaProjection != null) {
//            mIsCapturing = true;
//            //通过ZEGO API getCustomVideoCaptureSurfaceTexture获取SurfaceTexture，该接口默认使用主路通道进行推流
//            SurfaceTexture texture = mZegoEngine.getCustomVideoCaptureSurfaceTexture();
//            texture.setDefaultBufferSize(mCaptureWidth, mCaptureHeight);
//            //通过获取的SurfaceTexture创建Surface
//            mSurface = new Surface(texture);
//            //通过mSurface，完成将录屏数据塞给ZEGO SDK
//            mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
//                    mCaptureWidth, mCaptureHeight, 1,
//                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mSurface, null, mHandler);
//        }
    }
}
