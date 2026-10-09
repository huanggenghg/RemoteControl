package com.lumostech.autoclick

import android.app.Application
import com.lumostech.accessibilitybase.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred

class AutoclickApp : Application() {
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var startup: Deferred<Unit>
    internal suspend fun awaitStartup() { startup.await() }

    override fun onCreate() {
        super.onCreate()
        Logger.isEnabled = BuildConfig.DEBUG
        Logger.tagPrefix = "Autoclick"
        startup = startupScope.async {
            try {
                ClickTaskController(this@AutoclickApp).reconcile()
            } catch (e: Exception) {
                Logger.e("AutoclickApp", "Schedule recovery failed", e)
            }
        }
    }
}
