package com.lumostech.autoclick

import android.app.UiAutomation
import android.content.Context
import android.content.SharedPreferences
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import com.lumostech.accessibilitycore.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RecordingSessionIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val raw = context.getSharedPreferences("recording-session-fixture", Context.MODE_PRIVATE)
    private var fail = false
    private var attempts = 0
    private var scenario: ActivityScenario<MainActivity>? = null
    private val failing = object : SharedPreferences by raw {
        override fun edit(): SharedPreferences.Editor {
            val editor = raw.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putString(key: String?, value: String?) = apply { editor.putString(key, value) }
                override fun remove(key: String?) = apply { editor.remove(key) }
                override fun commit(): Boolean { attempts++; val committed = editor.commit(); return !fail && committed }
            }
        }
    }
    private lateinit var automation: UiAutomation
    private var originalServices = "null"
    private var originalEnabled = "0"
    private lateinit var service: AccessibilityCoreService
    private lateinit var originalStore: ClickSequenceStore
    private val field = AccessibilityCoreService::class.java.getDeclaredField("sequenceStore").apply { isAccessible = true }
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
        .bufferedReader().use { it.readText().trim() }
    @Before fun prepare() {
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        originalServices = shell("settings --user 0 get secure enabled_accessibility_services")
        originalEnabled = shell("settings --user 0 get secure accessibility_enabled")
        val component = "${context.packageName}/${AccessibilityCoreService::class.java.name}"
        val services = originalServices.split(':').filter { it != "null" && it.isNotBlank() }
        shell("settings --user 0 put secure enabled_accessibility_services ${(services + component).distinct().joinToString(":")}")
        shell("settings --user 0 put secure accessibility_enabled 1")
        val deadline = System.currentTimeMillis() + 10_000
        while (AccessibilityCoreService.accessibilityCoreService == null && System.currentTimeMillis() < deadline) Thread.sleep(50)
        service = checkNotNull(AccessibilityCoreService.accessibilityCoreService)
        originalStore = field.get(service) as ClickSequenceStore
        raw.edit().clear().commit()
        field.set(service, ClickSequenceStore(failing))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val ready = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < ready) {
            var current: ClickEnvironment? = null
            instrumentation.runOnMainSync { service.enableProtectedRecording(); current = service.currentClickEnvironment() }
            if (current?.packageName == context.packageName) return
            Thread.sleep(50)
        }
        fail("Target window did not become readable")
    }
    @After fun cleanup() {
        if (::service.isInitialized) field.set(service, originalStore)
        scenario?.close()
        raw.edit().clear().commit()
        if (originalServices == "null") shell("settings --user 0 delete secure enabled_accessibility_services")
        else shell("settings --user 0 put secure enabled_accessibility_services $originalServices")
        shell("settings --user 0 put secure accessibility_enabled ${if (originalEnabled == "1") "1" else "0"}")
    }
    @Test fun startFailureDoesNotPublishSession() {
        val before = service.getRecordedSnapshot()
        fail = true
        instrumentation.runOnMainSync { assertFalse(service.beginRecordingSession("new-session")) }
        assertEquals(before, service.getRecordedSnapshot())
    }
    @Test fun failedPointWriteRestoresSnapshot() {
        instrumentation.runOnMainSync {
            assertTrue(service.beginRecordingSession("point-session"))
            val before = service.getRecordedSnapshot()
            fail = true
            val beforeAttempts = attempts
            service.recordClick(100f, 200f)
            assertTrue("Point write was actually attempted", attempts > beforeAttempts)
            assertEquals(before, service.getRecordedSnapshot())
        }
        assertEquals("point-session", ClickSequenceStore(raw).loadSnapshot().sessionId)
        assertTrue(ClickSequenceStore(raw).loadSnapshot().points.isEmpty())
    }
    @Test fun reconnectLoadsSameSession() {
        val snapshot = RecordedClickSnapshot("session", listOf(ClickCounterPoint(100f, 200f, 0)),
            ClickRecordingProtection(1080, 1920, 0, listOf("target")))
        assertTrue(ClickSequenceStore(raw).saveSession(snapshot))
        assertEquals(snapshot, ClickSequenceStore(raw).loadSnapshot())
    }
    @Test fun ordinaryRecordingClearsSessionMarker() {
        assertTrue(ClickSequenceStore(raw).saveSession(RecordedClickSnapshot("session", emptyList(), null)))
        ClickSequenceStore(raw).save(listOf(ClickCounterPoint(20f, 30f, 0)))
        assertNull(ClickSequenceStore(raw).loadSnapshot().sessionId)
    }
}
