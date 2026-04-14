package com.lumostech.remotecontrol.activity

import com.lumostech.remotecontrol.utils.Logger

import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.view.WindowManager
import im.zego.zegoexpress.constants.ZegoPublishChannel
import im.zego.zegoexpress.constants.ZegoVideoBufferType
import im.zego.zegoexpress.entity.ZegoCustomVideoCaptureConfig
import im.zego.zegoexpress.entity.ZegoStream


open class MediaProjectionActivity : ZegoBaseActivity() {

    protected fun enableCustomVideoCapture() {
        //VideoCaptureScreen继承IZegoCustomVideoCaptureHandler，用于监听自定义采集onStart和onStop回调
        val wm = this.getSystemService(WINDOW_SERVICE) as WindowManager
        val width = wm.defaultDisplay.width
        val height = wm.defaultDisplay.height
        val videoCapture = VideoCaptureScreen(mMediaProjection, width, height, engine)
        //传递投屏的长宽
        engine.setStreamExtraInfo("$width,$height", null)
        //监听自定义采集开始停止回�?
        engine.setCustomVideoCaptureHandler(videoCapture)
        val videoCaptureConfig = ZegoCustomVideoCaptureConfig()
        //使用SurfaceTexture类型进行自定义采�?
        videoCaptureConfig.bufferType = ZegoVideoBufferType.SURFACE_TEXTURE
        //开始自定义采集
        engine.enableCustomVideoCapture(true, videoCaptureConfig, ZegoPublishChannel.MAIN)
    }

    override fun onRoomStreamUpdate(zegoStream: ZegoStream?, playStreamId: String?) {
        // 通知推流已成�?
        Logger.d("MAIN", "onRoomStreamUpdate: playStreamId = $playStreamId")
    }

    override fun onLoginRoomSuccess() {
        // 通知已登录房�?
        Logger.d("MAIN", "onLoginRoomSuccess")
    }

    @Deprecated("later update")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                //Target版本高于等于10.0需要使用前台服务，并在前台服务的onStartCommand方法中创建MediaProjection
                val service = Intent(
                    this@MediaProjectionActivity,
                    CaptureScreenService::class.java
                )
                service.putExtra("code", resultCode)
                service.putExtra("data", data)
                startForegroundService(service)
            } else {
                //Target版本低于10.0直接获取MediaProjection
                mMediaProjection = mMediaProjectionManager!!.getMediaProjection(
                    resultCode,
                    data!!
                )
            }
        }
    }

    protected fun startPublish(streamId: String) {
        // 开始推�?
        // 用户调用 loginRoom 之后再调用此接口进行推流
        // 在同一�?AppID �? 开发者需要保证“streamID�?全局唯一，如果不同用户各推了一�?“streamID�?相同的流，后推流的用户会推流失败�?
        engine.startPublishingStream(streamId)
    }

    protected fun requestMediaProjection() {
        // 5.0及以上版�?
        // 请求录屏权限，等待用户授�?
        mMediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mMediaProjectionManager!!.createScreenCaptureIntent(), REQUEST_CODE)
        Logger.d("TAG", "init: mMediaProjectionManager = $mMediaProjectionManager")
    }

    companion object {
        var mMediaProjectionManager: MediaProjectionManager? = null
        var mMediaProjection: MediaProjection? = null
        private const val REQUEST_CODE = 111
    }
}

