package com.lumostech.autoclick

import android.content.Context
import android.content.SharedPreferences
import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.lumostech.accessibilitycore.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Host runner kills and reopens the real app only after instrumentation has finished. */
class LegacyRecoveryProcessTest {
    @Test fun seedInterruptedRecovery() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("legacyRecoveryPhase")
        assumeTrue(phase in setOf("draft", "before", "after"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }
        val component = "${context.packageName}/${AccessibilityCoreService::class.java.name}"
        val existing = shell("settings --user 0 get secure enabled_accessibility_services")
        val services = (existing.split(':').filter { it != "null" && it.isNotBlank() } + component).distinct().joinToString(":")
        shell("settings --user 0 put secure enabled_accessibility_services $services")
        shell("settings --user 0 put secure accessibility_enabled 1")
        val deadline = System.currentTimeMillis() + 10_000
        while (AccessibilityCoreService.accessibilityCoreService == null && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertNotNull(AccessibilityCoreService.accessibilityCoreService)
        (context.applicationContext as AutoclickApp).awaitStartup()
        val store = ClickTaskStore(context)
        store.clear(); LegacyTaskRecoveryStore(context).clear()
        val source = ClickTask("legacy-process-source", 9, 30, setOf(2, 4),
            listOf(ClickCounterPoint(100f, 200f, 0)), enabled = false)
        assertTrue(store.save(source, alarmState = ClickAlarmState(source.id, source.scheduleId, ClickAlarmStatus.NEEDS_ENABLE)))
        val session = LegacyTaskRecoverySession("legacy-process-recovery", source.id, source.scheduleId,
            "legacy-process-replacement", 10, 15, setOf(3, 5),
            if (phase == "draft") LegacyRecoveryPhase.PAUSED else LegacyRecoveryPhase.SAVING)
        assertTrue(LegacyTaskRecoveryStore(context).save(session))
        val draft = RecordedClickSnapshot(session.recoveryId, listOf(ClickCounterPoint(300f, 400f, 0)),
            ClickRecordingProtection(1080, 1920, 0, listOf(context.packageName)))
        assertTrue(ClickSequenceStore(context).saveSession(draft))
        val file = File(context.filesDir, "legacy-task-recovery-commit.json")
        if (phase == "before") {
            // An interrupted unconfirmed write must not replace the source on startup.
            File(file.path + ".new").writeText("interrupted unconfirmed bytes")
        }
        if (phase == "after") {
            val raw = context.getSharedPreferences("autoclick_task", Context.MODE_PRIVATE)
            val failingMirror = object : SharedPreferences by raw {
                override fun edit(): SharedPreferences.Editor {
                    val editor = raw.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun clear() = apply { editor.clear() }
                        override fun putString(key: String?, value: String?) = apply { editor.putString(key, value) }
                        override fun putLong(key: String?, value: Long) = apply { editor.putLong(key, value) }
                        override fun putBoolean(key: String?, value: Boolean) = apply { editor.putBoolean(key, value) }
                        override fun remove(key: String?) = apply { editor.remove(key) }
                        override fun commit() = false
                    }
                }
            }
            val protected = RecoverableTaskPreferences(failingMirror, AtomicRecoveryJournalStorage(file))
            val candidate = ClickTask(session.replacementTaskId, session.hour, session.minute, session.days,
                draft.points, enabled = false, protection = draft.protection)
            assertEquals(LegacyTaskRecoveryResult.SAVED_DISABLED,
                ClickTaskController(ClickTaskStore(protected), object : ClickAlarmPlatform {
                    override fun canSchedule() = true
                    override fun schedule(occurrence: ClickAlarmOccurrence) = error("Recovery must not schedule")
                    override fun cancel(occurrence: ClickAlarmOccurrence) {}
                }, {}).saveRecoveredRecording(session, candidate, draft.sessionId))
            assertEquals(source.id, org.json.JSONObject(raw.getString("task", null)!!).getString("id"))
            assertEquals(candidate, store.load())
            assertTrue(file.isFile)
        }
    }
}
