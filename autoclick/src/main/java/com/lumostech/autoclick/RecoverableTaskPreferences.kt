package com.lumostech.autoclick

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import com.lumostech.accessibilitybase.utils.Logger

/** A confirmed recovery journal remains authoritative until preferences are durably synchronized. */
class RecoverableTaskPreferences(private val raw: SharedPreferences, private val journal: RecoveryJournalStorage) : SharedPreferences {
    private val lock = Any()
    private val listeners = CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var published = copyPreferenceValues(raw.all)
    private val rawListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        synchronized(lock) { runCatching { publish(source()) }.onFailure { notifyListeners(setOf(null)) } }
    }
    init { raw.registerOnSharedPreferenceChangeListener(rawListener) }

    fun ensureReadable() { synchronized(lock) { source() } }
    fun hasRecoverySnapshot(): Boolean = synchronized(lock) { journal.read() != null }
    private fun source(): Map<String, Any> = journal.read()?.current?.let(::copyPreferenceValues) ?: copyPreferenceValues(raw.all)

    fun commitRecovery(expectedTaskId: String, replacement: Map<String, Any>, transactionId: String): Boolean =
        ordered { recover(expectedTaskId, replacement, transactionId) }

    private fun recover(expectedTaskId: String, replacement: Map<String, Any>, transactionId: String): Boolean = synchronized(lock) {
        val old = source()
        val id = runCatching { JSONObject(old["task"] as? String ?: return false).getString("id") }.getOrNull()
        if (id != expectedTaskId || transactionId.isBlank()) return false
        val next = TaskPreferenceSnapshot(transactionId, copyPreferenceValues(old), copyPreferenceValues(replacement))
        if (!confirmWrite(next)) return false
        publish(next.current)
        mirror(next)
        true
    }

    private fun ordered(action: () -> Boolean): Boolean = try {
        executor.submit<Boolean> { action() }.get()
    } catch (failure: ExecutionException) { throw (failure.cause ?: failure) }

    private fun confirmWrite(snapshot: TaskPreferenceSnapshot): Boolean =
        journal.write(snapshot) || journal.read() == snapshot

    private fun mirror(snapshot: TaskPreferenceSnapshot) {
        // A failed commit may already have updated memory. The journal stays authoritative.
        val synced = runCatching {
            val editor = raw.edit().clear()
            snapshot.current.forEach { (key, value) -> putValue(editor, key, value) }
            editor.commit() && copyPreferenceValues(raw.all) == snapshot.current
        }.getOrDefault(false)
        if (synced) runCatching { journal.clear() }
    }

    private fun publish(values: Map<String, Any>) {
        val changed = (published.keys + values.keys).filter { published[it] != values[it] }.toSet()
        published = copyPreferenceValues(values)
        if (changed.isNotEmpty()) notifyListeners(changed)
    }
    private fun notifyListeners(keys: Set<String?>) {
        handler.post { keys.forEach { key -> listeners.forEach { it.onSharedPreferenceChanged(this, key) } } }
    }

    override fun getAll(): MutableMap<String, *> = synchronized(lock) { copyPreferenceValues(source()).toMutableMap() }
    override fun contains(key: String?): Boolean = synchronized(lock) { source().containsKey(key) }
    override fun getString(key: String?, defValue: String?): String? = synchronized(lock) { source()[key]?.let { it as String } ?: defValue }
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = synchronized(lock) {
        @Suppress("UNCHECKED_CAST")
        (source()[key] as? Set<String>)?.toMutableSet() ?: defValues?.toMutableSet()
    }
    override fun getInt(key: String?, defValue: Int): Int = synchronized(lock) { source()[key]?.let { it as Int } ?: defValue }
    override fun getLong(key: String?, defValue: Long): Long = synchronized(lock) { source()[key]?.let { it as Long } ?: defValue }
    override fun getFloat(key: String?, defValue: Float): Float = synchronized(lock) { source()[key]?.let { it as Float } ?: defValue }
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = synchronized(lock) { source()[key]?.let { it as Boolean } ?: defValue }
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) { listener?.let { listeners.add(it) } }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) { listeners.remove(listener) }
    override fun edit(): SharedPreferences.Editor = Editor()

    private fun commitChanges(changes: Map<String, Any?>, clear: Boolean): Boolean = synchronized(lock) {
        val currentJournal = journal.read()
        if (currentJournal == null) {
            val editor = raw.edit()
            if (clear) editor.clear()
            changes.forEach { (key, value) -> if (value == null) editor.remove(key) else putValue(editor, key, value) }
            val committed = editor.commit()
            publish(source())
            return committed
        }
        val nextValues = if (clear) mutableMapOf() else copyPreferenceValues(currentJournal.current).toMutableMap()
        changes.forEach { (key, value) -> if (value == null) nextValues.remove(key) else nextValues[key] = value }
        val next = currentJournal.copy(current = copyPreferenceValues(nextValues))
        if (!confirmWrite(next)) return false
        publish(next.current)
        mirror(next)
        true
    }

    private inner class Editor : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private var clearing = false
        private fun put(key: String?, value: Any?): SharedPreferences.Editor = apply { changes[requireNotNull(key)] = value }
        override fun putString(key: String?, value: String?) = put(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values?.toSet())
        override fun putInt(key: String?, value: Int) = put(key, value)
        override fun putLong(key: String?, value: Long) = put(key, value)
        override fun putFloat(key: String?, value: Float) = put(key, value)
        override fun putBoolean(key: String?, value: Boolean) = put(key, value)
        override fun remove(key: String?) = put(key, null)
        override fun clear(): SharedPreferences.Editor = apply { clearing = true }
        override fun commit(): Boolean {
            val snapshot = changes.toMap()
            val clear = clearing
            return ordered { commitChanges(snapshot, clear) }
        }
        override fun apply() {
            val snapshot = changes.toMap()
            val clear = clearing
            executor.execute {
                try { commitChanges(snapshot, clear) }
                catch (failure: Exception) {
                    Logger.e("RecoveryStorage", "Cannot confirm asynchronous task write", failure)
                    notifyListeners(setOf(null))
                }
            }
        }
    }

    companion object {
        private val instances = mutableMapOf<String, RecoverableTaskPreferences>()
        fun forContext(context: Context): RecoverableTaskPreferences = synchronized(instances) {
            val app = context.applicationContext
            val file = File(app.filesDir, "legacy-task-recovery-commit.json")
            instances.getOrPut(file.absolutePath) {
                RecoverableTaskPreferences(app.getSharedPreferences("autoclick_task", Context.MODE_PRIVATE), AtomicRecoveryJournalStorage(file))
            }
        }
        private fun putValue(editor: SharedPreferences.Editor, key: String, value: Any) {
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> editor.putStringSet(key, value.map { require(it is String); it }.toSet())
                else -> error("Unsupported preference type")
            }
        }
    }
}
