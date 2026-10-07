package com.lumostech.autoclick

import android.app.UiAutomation
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumostech.accessibilitycore.AccessibilityCoreService
import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickEnvironment
import com.lumostech.accessibilitycore.ClickRecordingProtection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/** Seed only; the host verifies real OS kill/rebind without instrumentation running. */
@RunWith(AndroidJUnit4::class)
class ClickRecoveryProcessTest {
    @Test fun seedNormalScheduleForActualProcessRecovery() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("processRecoverySeed") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }
        val component = "${context.packageName}/${AccessibilityCoreService::class.java.name}"
        val old = shell("settings --user 0 get secure enabled_accessibility_services")
        val services = (old.split(':').filter { it != "null" && it.isNotBlank() } + component).distinct().joinToString(":")
        shell("settings --user 0 put secure enabled_accessibility_services $services")
        shell("settings --user 0 put secure accessibility_enabled 1")
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        val end = System.currentTimeMillis() + 10_000
        while (AccessibilityCoreService.accessibilityCoreService == null && System.currentTimeMillis() < end) Thread.sleep(100)
        val service = checkNotNull(AccessibilityCoreService.accessibilityCoreService)
        instrumentation.context.startActivity(Intent(instrumentation.context, RecoveryTargetActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("reset", true))
        var environment: ClickEnvironment? = null
        val targetEnd = System.currentTimeMillis() + 10_000
        while (environment?.packageName != instrumentation.context.packageName && System.currentTimeMillis() < targetEnd) {
            instrumentation.runOnMainSync { service.enableProtectedRecording(); environment = service.currentClickEnvironment() }
            if (environment?.packageName != instrumentation.context.packageName) Thread.sleep(100)
        }
        val env = checkNotNull(environment)
        assertEquals(instrumentation.context.packageName, env.packageName)
        val target = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis - System.currentTimeMillis() < 30_000) add(Calendar.MINUTE, 1)
        }
        val task = ClickTask("actual-process-recovery", target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE),
            (1..7).toSet(), listOf(ClickCounterPoint(300f, 300f, 0)),
            protection = ClickRecordingProtection(env.width, env.height, env.rotation, listOf(env.packageName)))
        runBlocking { ClickTaskController(context).save(task) }
        android.util.Log.i("AutoclickProcessRecovery", "scheduledAt=${target.timeInMillis}, waitMs=${target.timeInMillis - System.currentTimeMillis()}")
        assertEquals(task, ClickTaskStore(context).load())
    }
}
