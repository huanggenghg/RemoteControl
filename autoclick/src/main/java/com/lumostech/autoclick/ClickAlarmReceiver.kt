package com.lumostech.autoclick

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lumostech.accessibilitybase.utils.Logger
import kotlinx.coroutines.*

class ClickAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val receivedAt = SystemClickClock.wallMillis()
        val receivedElapsed = SystemClickClock.elapsedMillis()
        val occurrence = AndroidClickAlarmPlatform(app).parse(intent) ?: return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        launchBoundedReceiverTask(scope, finish = pending::finish) {
            try {
                ClickAlarmDispatcher.dispatch(app, occurrence, receivedAt = receivedAt, receivedElapsed = receivedElapsed)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Logger.e("ClickAlarmReceiver", "Alarm handoff failed", error)
                withContext(NonCancellable) { withTimeoutOrNull(1_000) {
                    ClickTaskController(app).finishAlarm(occurrence, ClickOutcomeReason.UNKNOWN, "执行检查异常，未执行")
                } }
            }
        }
    }
}
