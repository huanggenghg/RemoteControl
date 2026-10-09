package com.lumostech.autoclick

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lumostech.accessibilitybase.utils.Logger
import kotlinx.coroutines.*

class ClickScheduleRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> ClickRecoveryEvent.BOOT
            Intent.ACTION_MY_PACKAGE_REPLACED -> ClickRecoveryEvent.PACKAGE_REPLACED
            Intent.ACTION_TIME_CHANGED -> ClickRecoveryEvent.TIME_CHANGED
            Intent.ACTION_TIMEZONE_CHANGED -> ClickRecoveryEvent.ZONE_CHANGED
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> ClickRecoveryEvent.PERMISSION_CHANGED
            else -> return
        }
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        launchBoundedReceiverTask(scope, finish = pending::finish) {
            try {
                (context.applicationContext as? AutoclickApp)?.awaitStartup()
                ClickTaskController(context.applicationContext).reconcile(event)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Logger.e("ClickScheduleRecoveryReceiver", "Recovery failed", error) }
        }
    }
}
