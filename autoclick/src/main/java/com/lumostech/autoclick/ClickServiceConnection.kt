package com.lumostech.autoclick

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.lumostech.accessibilitycore.AccessibilityCoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The settings grant and the actual system connection are different states. */
internal object ClickServiceConnection {
    const val MAX_WAIT_MS = 10_000L

    private fun isEnabled(context: Context): Boolean {
        if (Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
        val expected = ComponentName(context, AccessibilityCoreService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabled?.split(':')?.any { ComponentName.unflattenFromString(it) == expected } == true
    }

    fun readiness(context: Context): ClickServiceReadiness = ClickServiceReadiness.from(
        isEnabled(context), AccessibilityCoreService.accessibilityCoreService != null)

    suspend fun await(context: Context, canContinue: () -> Boolean): AccessibilityCoreService? =
        withContext(Dispatchers.Main.immediate) {
            ServiceConnectionWaiter.await(MAX_WAIT_MS, 100,
                { canContinue() && isEnabled(context) },
                { AccessibilityCoreService.accessibilityCoreService })
        }
}
