package com.lumostech.autoclick

import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.lumostech.accessibilitycore.AccessibilityCoreService
import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickEnvironment
import com.lumostech.accessibilitycore.ClickRecordingProtection
import com.lumostech.accessibilitycore.ViewModelMain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import java.util.Calendar

internal class ExactTimingFixture {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val store = ClickTaskStore(context)
    private lateinit var automation: UiAutomation
    private var services = "null"
    private var enabled = "0"
    fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    fun setup(clearTask: Boolean = true) {
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        services = shell("settings --user 0 get secure enabled_accessibility_services")
        enabled = shell("settings --user 0 get secure accessibility_enabled")
        val own = "${context.packageName}/${AccessibilityCoreService::class.java.name}"
        val all = (services.split(':').filter { it != "null" && it.isNotBlank() } + own).distinct().joinToString(":")
        shell("settings --user 0 put secure enabled_accessibility_services $all")
        shell("settings --user 0 put secure accessibility_enabled 1")
        shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
        waitFor("service connected", 10000) { AccessibilityCoreService.accessibilityCoreService != null }
        if (clearTask) runBlocking { ClickTaskController(context).delete() }
        ClickExecutionSession.allowScheduled()
    }
    fun cleanup() {
        runBlocking { ClickTaskController(context).delete() }
        instrumentation.runOnMainSync {
            ViewModelMain.isShowFloatWindow.value = false; ViewModelMain.isShowCustomFloatWindow.value = false
        }
        shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
        if (services == "null" || services.isBlank()) shell("settings --user 0 delete secure enabled_accessibility_services")
        else shell("settings --user 0 put secure enabled_accessibility_services $services")
        shell("settings --user 0 put secure accessibility_enabled ${if (enabled == "1") "1" else "0"}")
    }
    fun environment(): ClickEnvironment {
        var environment: ClickEnvironment? = null
        instrumentation.runOnMainSync { AccessibilityCoreService.accessibilityCoreService!!.enableProtectedRecording() }
        waitFor("target foreground") {
            instrumentation.runOnMainSync { environment = AccessibilityCoreService.accessibilityCoreService!!.currentClickEnvironment() }
            environment?.packageName == context.packageName
        }
        return environment!!
    }
    fun task(id: String, points: List<ClickCounterPoint>): ClickTask {
        val env = environment(); val now = Calendar.getInstance()
        return ClickTask(id, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), (1..7).toSet(), points,
            protection = ClickRecordingProtection(env.width, env.height, env.rotation, List(points.size) { env.packageName }))
    }
    fun due(task: ClickTask): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, task.hour); set(Calendar.MINUTE, task.minute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    fun clock(due: Long, offset: Long = 100): ClickClock {
        val start = SystemClock.elapsedRealtime()
        return object : ClickClock {
            override fun wallMillis() = due + offset + SystemClock.elapsedRealtime() - start
            override fun elapsedMillis() = SystemClock.elapsedRealtime()
        }
    }
    fun seed(task: ClickTask, due: Long): ClickAlarmOccurrence {
        assertTrue(store.save(task))
        val event = ClickAlarmOccurrence(task.id, task.scheduleId, due)
        assertTrue(store.saveAlarmState(task.id, ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMED, next = event)))
        return event
    }
    fun waitFor(message: String, timeout: Long = 10000, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + timeout
        while (!condition() && SystemClock.elapsedRealtime() < until) Thread.sleep(50)
        assertTrue(message, condition())
    }
}
