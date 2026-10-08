package com.lumostech.remotecontrol.activity

import android.app.Activity
import android.content.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.activity.result.contract.ActivityResultContracts
import com.lumostech.communication.ScreenGeometry

open class MediaProjectionActivity : CommunicationActivity() {
    protected var projection: MediaProjection? = null
        private set
    protected var captureGeometry: ScreenGeometry? = null
        private set
    private var bound = false
    private var capture: CaptureScreenService.LocalBinder? = null
    private var awaitingProjection = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            capture = binder as CaptureScreenService.LocalBinder
            capture?.listen({ value, geometry ->
                if (awaitingProjection && !isDestroyed) {
                    awaitingProjection = false
                    projection = value
                    captureGeometry = geometry
                    onProjectionReady()
                }
            }, {
                projection = null
                captureGeometry = null
                if (!isDestroyed) onProjectionStopped()
            })
        }
        override fun onServiceDisconnected(name: ComponentName) {
            capture = null
            projection = null
            captureGeometry = null
            if (!isDestroyed) onProjectionStopped()
        }
    }

    private val requestCapture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (!awaitingProjection) return@registerForActivityResult
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            awaitingProjection = false
            onProjectionStopped()
            return@registerForActivityResult
        }
        val intent = Intent(this, CaptureScreenService::class.java)
            .putExtra(CaptureScreenService.RESULT_CODE, result.resultCode)
            .putExtra(CaptureScreenService.RESULT_DATA, result.data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        if (!bound) bound = bindService(Intent(this, CaptureScreenService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    protected fun requestMediaProjection() {
        if (awaitingProjection) return
        awaitingProjection = true
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = if (Build.VERSION.SDK_INT >= 34) {
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else manager.createScreenCaptureIntent()
        requestCapture.launch(intent)
    }

    protected open fun onProjectionReady() = Unit
    protected open fun onProjectionStopped() { stopSession() }

    protected fun releaseProjection() {
        awaitingProjection = false
        capture?.detach()
        capture = null
        if (bound) { unbindService(connection); bound = false }
        stopService(Intent(this, CaptureScreenService::class.java))
        projection = null
        captureGeometry = null
    }

    override fun onDestroy() { releaseProjection(); super.onDestroy() }
}
