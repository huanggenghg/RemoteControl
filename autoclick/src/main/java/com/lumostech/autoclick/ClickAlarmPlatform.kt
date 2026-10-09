package com.lumostech.autoclick

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

internal interface ClickAlarmPlatform {
    fun canSchedule(): Boolean
    fun schedule(occurrence: ClickAlarmOccurrence)
    fun cancel(occurrence: ClickAlarmOccurrence)
}

internal class AndroidClickAlarmPlatform(context: Context) : ClickAlarmPlatform {
    private val app = context.applicationContext
    private val manager = app.getSystemService(AlarmManager::class.java)
    override fun canSchedule(): Boolean = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    fun eventIntent(o: ClickAlarmOccurrence): Intent = Intent(app, ClickAlarmReceiver::class.java).apply {
        action = app.packageName + ".EXACT_CLICK"
        data = Uri.Builder().scheme("autoclick").authority(app.packageName).appendPath(o.version.toString())
            .appendPath(o.scheduleId).appendPath(o.taskId).appendPath(o.scheduledAt.toString()).build()
    }
    override fun schedule(occurrence: ClickAlarmOccurrence) {
        check(canSchedule()) { "定时权限未开启" }
        require(occurrence.isValid())
        val pending = PendingIntent.getBroadcast(app, 0, eventIntent(occurrence),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, occurrence.scheduledAt, pending)
    }
    override fun cancel(occurrence: ClickAlarmOccurrence) {
        val pending = PendingIntent.getBroadcast(app, 0, eventIntent(occurrence),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        manager.cancel(pending)
        pending.cancel()
    }
    fun parse(intent: Intent): ClickAlarmOccurrence? = runCatching {
        if (intent.action != app.packageName + ".EXACT_CLICK") return null
        val uri = intent.data ?: return null
        if (uri.scheme != "autoclick" || uri.authority != app.packageName) return null
        val p = uri.pathSegments
        if (p.size != 4 || p[0] != "2" || p[1].isBlank() || p[2].isBlank()) return null
        ClickAlarmOccurrence(p[2], p[1], p[3].toLong()).takeIf { it.isValid() }
    }.getOrNull()
}

internal fun exactTimingSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
