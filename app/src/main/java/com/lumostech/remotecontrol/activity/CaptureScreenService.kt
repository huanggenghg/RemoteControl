package com.lumostech.remotecontrol.activity

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lumostech.remotecontrol.R
import java.util.Objects

class CaptureScreenService : Service() {
    override fun onCreate() {
        super.onCreate()
        startNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            //在这里获取MediaProjection
            val resultCode = intent.getIntExtra("code", 1)
            val resultData = intent.getParcelableExtra<Intent>("data")
            MediaProjectionActivity.mMediaProjectionManager = getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager
            resultData?.let {
                MediaProjectionActivity.mMediaProjection =
                    MediaProjectionActivity.mMediaProjectionManager
                        ?.getMediaProjection(resultCode, it)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun startNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //Call Start foreground with notification
            val notificationIntent = Intent(
                this,
                CaptureScreenService::class.java
            )
            val pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_MUTABLE)
            val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.icon_app))
                .setSmallIcon(R.mipmap.icon_app)
                .setContentTitle("Starting Service")
                .setContentText("Starting monitoring service")
                .setContentIntent(pendingIntent)
            val notification = notificationBuilder.build()
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = NOTIFICATION_CHANNEL_DESC
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            startForeground(
                1,
                notification
            ) //必须使用此方法显示通知，不能使用notificationManager.notify，否则还是会报上面的错误
        }
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "1234"
        private const val NOTIFICATION_CHANNEL_DESC = "desc"
        private val NOTIFICATION_CHANNEL_NAME: CharSequence = "name"
    }
}