package com.lumostech.autoclick

import android.content.Context
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class MemoryRecoveryJournal : RecoveryJournalStorage {
    var value: TaskPreferenceSnapshot? = null
    var failWrite = false
    var failClear = false
    override fun read() = value
    override fun write(snapshot: TaskPreferenceSnapshot): Boolean {
        if (failWrite) return false
        value = snapshot
        return true
    }
    override fun clear(): Boolean { if (failClear) return false; value = null; return true }
}

class RecoverableTaskPreferencesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val raw = context.getSharedPreferences("legacy-prefs-fixture", Context.MODE_PRIVATE)
    private val journal = MemoryRecoveryJournal()
    private val old = "{\"id\":\"old\"}"
    private val new = "{\"id\":\"new\"}"
    private fun replacement() = mapOf<String, Any>("task" to new, "enabled" to false, "number" to 125L)
    @Before fun prepare() { raw.edit().clear().putString("task", old).putLong("outcome_time", 123).commit() }
    @After fun cleanup() { raw.edit().clear().commit() }

    @Test fun failedJournalWriteKeepsOriginalAndHistory() {
        journal.failWrite = true
        val prefs = RecoverableTaskPreferences(raw, journal)
        assertFalse(prefs.commitRecovery("old", replacement(), "session"))
        assertEquals(old, prefs.getString("task", null))
        assertEquals(123L, prefs.getLong("outcome_time", 0))
        assertEquals(old, raw.getString("task", null))
    }
    @Test fun completedWriteSurvivesMirrorFailureAndNewInstance() {
        val failing = failingCommits(raw)
        val prefs = RecoverableTaskPreferences(failing, journal)
        assertTrue(prefs.commitRecovery("old", replacement(), "session"))
        assertNotNull(journal.value)
        assertEquals(new, prefs.getString("task", null))
        assertFalse(prefs.getBoolean("enabled", true))
        assertEquals(new, RecoverableTaskPreferences(failing, journal).getString("task", null))
    }
    @Test fun mirrorFailureDoesNotLoseLaterChangesOrResurrectDeletedTask() {
        val prefs = RecoverableTaskPreferences(failingCommits(raw), journal)
        assertTrue(prefs.commitRecovery("old", replacement(), "session"))
        assertTrue(prefs.edit().putLong("number", 456).commit())
        assertEquals(456L, RecoverableTaskPreferences(raw, journal).getLong("number", 0))
        assertTrue(prefs.edit().clear().commit())
        assertFalse(RecoverableTaskPreferences(raw, journal).contains("task"))
    }
    @Test fun cleanupFailureKeepsCommittedSource() {
        journal.failClear = true
        val prefs = RecoverableTaskPreferences(raw, journal)
        assertTrue(prefs.commitRecovery("old", replacement(), "session"))
        assertNotNull(journal.value)
        assertEquals(new, RecoverableTaskPreferences(raw, journal).getString("task", null))
    }
    @Test fun staleSourceCannotOverwriteTask() {
        assertFalse(RecoverableTaskPreferences(raw, journal).commitRecovery("changed", replacement(), "session"))
        assertEquals(old, raw.getString("task", null))
        assertNull(journal.value)
    }
    @Test fun journalFailureDuringLaterEditKeepsLastConfirmedValues() {
        val prefs = RecoverableTaskPreferences(failingCommits(raw), journal)
        assertTrue(prefs.commitRecovery("old", replacement(), "session"))
        journal.failWrite = true
        assertFalse(prefs.edit().putLong("number", 456).commit())
        assertEquals(125L, prefs.getLong("number", 0))
    }
    @Test fun snapshotRoundTripPreservesTypesAndOldHistory() {
        val values = mapOf<String, Any>("string" to "中文", "int" to 2, "long" to Long.MAX_VALUE,
            "float" to 1.25f, "boolean" to false, "set" to setOf("a", "b"))
        val snapshot = TaskPreferenceSnapshot("session", mapOf("history" to old), values)
        assertEquals(snapshot, TaskPreferenceSnapshot.decode(snapshot.encode()))
    }
    @Test fun atomicRecordSurvivesNewReaderAndCanBeCleared() {
        val file = File(context.filesDir, "legacy-atomic-fixture.json")
        val backend = AtomicRecoveryJournalStorage(file)
        try {
            val snapshot = TaskPreferenceSnapshot("session", mapOf("task" to old), replacement())
            assertTrue(backend.write(snapshot))
            assertEquals(snapshot, AtomicRecoveryJournalStorage(file).read())
            assertTrue(backend.clear())
            assertNull(backend.read())
        } finally { backend.clear() }
    }
    @Test fun mirrorFailureNotifiesOnlyConfirmedReplacement() {
        val prefs = RecoverableTaskPreferences(failingCommits(raw), journal)
        val notified = CountDownLatch(1)
        val observed = mutableListOf<String?>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { source, key ->
            if (key == "task") { synchronized(observed) { observed.add(source.getString("task", null)) }; notified.countDown() }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        try {
            assertTrue(prefs.commitRecovery("old", replacement(), "session"))
            assertTrue(notified.await(5, TimeUnit.SECONDS))
            synchronized(observed) { assertTrue(observed.isNotEmpty()); assertTrue(observed.all { it == new }) }
        } finally { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    @Test fun corruptedRecordIsNotSilentlyReadAsAnEmptyTask() {
        val file = File(context.filesDir, "legacy-corrupt-fixture.json")
        val backend = AtomicRecoveryJournalStorage(file)
        file.writeText("incomplete")
        try {
            val store = ClickTaskStore(RecoverableTaskPreferences(raw, backend))
            try { store.load(); fail("Unconfirmed storage must block reading") } catch (expected: RecoveryStorageException) {
                assertEquals(old, raw.getString("task", null))
                assertTrue(file.exists())
            }
        } finally { backend.clear() }
    }

    @Test fun sessionFileRoundTripRejectsDamagedIdentity() {
        val file = File(context.filesDir, "legacy-session-fixture.json")
        val store = LegacyTaskRecoveryStore(file)
        try {
            val session = LegacyTaskRecoverySession("recovery", "old", "plan", "new", 10, 15, setOf(3, 5), LegacyRecoveryPhase.SAVING)
            assertTrue(store.save(session)); assertEquals(session, LegacyTaskRecoveryStore(file).load())
            file.writeText(session.encode().replace("\"old\"", "\"\""))
            try { store.load(); fail("Damaged identity must be preserved and reported") } catch (expected: RecoveryStorageException) { assertTrue(file.exists()) }
        } finally { store.clear() }
    }
    @Test fun orderedApplyCannotRestoreValuesAfterConfirmedDeletion() {
        val prefs = RecoverableTaskPreferences(failingCommits(raw), journal)
        assertTrue(prefs.commitRecovery("old", replacement(), "session"))
        prefs.edit().putLong("number", 456).apply()
        prefs.edit().putString("task", "{\"id\":\"later\"}").apply()
        assertTrue(prefs.edit().clear().commit())
        assertTrue(prefs.all.isEmpty())
        assertTrue(RecoverableTaskPreferences(raw, journal).all.isEmpty())
    }
    private fun failingCommits(delegate: SharedPreferences): SharedPreferences = object : SharedPreferences by delegate {
        override fun edit(): SharedPreferences.Editor {
            val editor = delegate.edit()
            return object : SharedPreferences.Editor by editor {
                override fun clear() = apply { editor.clear() }
                override fun putString(key: String?, value: String?) = apply { editor.putString(key, value) }
                override fun putInt(key: String?, value: Int) = apply { editor.putInt(key, value) }
                override fun putLong(key: String?, value: Long) = apply { editor.putLong(key, value) }
                override fun putFloat(key: String?, value: Float) = apply { editor.putFloat(key, value) }
                override fun putBoolean(key: String?, value: Boolean) = apply { editor.putBoolean(key, value) }
                override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { editor.putStringSet(key, values) }
                override fun remove(key: String?) = apply { editor.remove(key) }
                override fun commit(): Boolean { editor.commit(); return false }
            }
        }
    }
}
