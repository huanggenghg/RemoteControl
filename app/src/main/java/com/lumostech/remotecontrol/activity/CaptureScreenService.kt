package com.lumostech.remotecontrol.activity

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.lumostech.communication.ScreenGeometry
import com.lumostech.remotecontrol.R

class CaptureScreenService : Service() {
    private var projection: MediaProjection? = null
    private var geometry: ScreenGeometry? = null
    private var ready: ((MediaProjection, ScreenGeometry) -> Unit)? = null
    private var stopped: (() -> Unit)? = null
    private var closing = false
    private val binder = LocalBinder()
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { endCapture() }
        override fun onCapturedContentResize(width: Int, height: Int) {
            val current = geometry ?: return
            if (width != current.width || height != current.height) endCapture()
        }
    }

    inner class LocalBinder : Binder() {
        fun listen(onReady: (MediaProjection, ScreenGeometry) -> Unit, onStop: () -> Unit) {
            if (closing) { onStop(); return }
            ready = onReady
            stopped = onStop
            val value = projection
            val dimensions = geometry
            if (value != null && dimensions != null) onReady(value, dimensions)
        }
        fun detach() { ready = null; stopped = null }
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "远程屏幕共享", NotificationManager.IMPORTANCE_LOW))
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.icon_app).setContentTitle("正在共享屏幕")
            .setContentText("返回远程控制应用可停止共享").setOngoing(true).setContentIntent(pending).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (projection != null || closing) return START_NOT_STICKY
        val result = intent?.getIntExtra(RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(RESULT_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") intent?.getParcelableExtra<Intent>(RESULT_DATA)
        if (result != Activity.RESULT_OK || data == null) { endCapture(); return START_NOT_STICKY }
        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            val dimensions = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.let {
                ScreenGeometry(it.width(), it.height(), wm.defaultDisplay.rotation)
            } else android.util.DisplayMetrics().let {
                wm.defaultDisplay.getRealMetrics(it)
                ScreenGeometry(it.widthPixels, it.heightPixels, wm.defaultDisplay.rotation)
            }
            geometry = dimensions
            projection = requireNotNull(manager.getMediaProjection(result, data)).also {
                it.registerCallback(callback, Handler(Looper.getMainLooper()))
            }
            ready?.invoke(projection!!, dimensions)
        } catch (_: Exception) { endCapture() }
        return START_NOT_STICKY
    }

    private fun endCapture() {
        if (closing) return
        closing = true
        val value = projection
        projection = null
        geometry = null
        value?.unregisterCallback(callback)
        value?.stop()
        stopped?.invoke()
        binder.detach()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent): IBinder = binder
    override fun onDestroy() { endCapture(); super.onDestroy() }

    companion object {
        const val RESULT_CODE = "capture_result_code"
        const val RESULT_DATA = "capture_result_data"
        private const val CHANNEL = "remote_screen_capture"
    }
}
