package com.lumostech.remotecontrol.activity

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.view.Surface
import im.zego.zegoexpress.ZegoExpressEngine
import im.zego.zegoexpress.constants.ZegoPublishChannel

class VideoCaptureScreen(
    private val mMediaProjection: MediaProjection?,
    private val mCaptureWidth: Int,
    private val mCaptureHeight: Int,
    private val mZegoEngine: ZegoExpressEngine?
) :
    ZegoVideoCaptureCallback() {
    private var mIsCapturing = false
    private var mSurface: Surface? = null
    private var mVirtualDisplay: VirtualDisplay? = null

    //当收到onStart回调后，就可以通过MediaProjection创建VirtualDisplay，并给ZEGO SDK塞屏幕数据
    override fun onStart(channel: ZegoPublishChannel) {
        if (mZegoEngine != null && !mIsCapturing && mMediaProjection != null) {
            mIsCapturing = true
            //通过ZEGO API getCustomVideoCaptureSurfaceTexture获取SurfaceTexture，该接口默认使用主路通道进行推流
            val texture = mZegoEngine.customVideoCaptureSurfaceTexture
            texture.setDefaultBufferSize(mCaptureWidth, mCaptureHeight)
            //通过获取的SurfaceTexture创建Surface
            mSurface = Surface(texture)
            //通过mSurface，完成将录屏数据塞给ZEGO SDK
            mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                "ScreenCapture",
                mCaptureWidth, mCaptureHeight, 1,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mSurface, null, Handler()
            )
        }
    }

    companion object {
    }
}
