package com.lumostech.accessibilitycore

import android.content.Context
import android.content.SharedPreferences

data class RecordedClickSnapshot(val sessionId: String?, val points: List<ClickCounterPoint>,
                                 val protection: ClickRecordingProtection?)

class ClickSequenceStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("click_recording", Context.MODE_PRIVATE))

    fun loadSnapshot(): RecordedClickSnapshot = synchronized(lock) {
        RecordedClickSnapshot(preferences.getString("session_id", null), load().map { it.copy() },
            loadProtection()?.let { it.copy(packages = it.packages.toList()) })
    }
    fun saveSession(snapshot: RecordedClickSnapshot): Boolean = synchronized(lock) {
        require(!snapshot.sessionId.isNullOrBlank())
        require(snapshot.points.isEmpty() || ClickSequenceCodec.isValid(snapshot.points))
        require(snapshot.protection == null || snapshot.protection.isValid(snapshot.points))
        val before = loadSnapshot()
        if (sessionEditor(snapshot).commit()) return true
        // SharedPreferences may have changed memory before reporting a disk failure.
        sessionEditor(before).commit()
        false
    }
    private fun sessionEditor(snapshot: RecordedClickSnapshot): SharedPreferences.Editor = preferences.edit().apply {
        putString("points", ClickSequenceCodec.encode(snapshot.points))
        putString("session_id", snapshot.sessionId)
        if (snapshot.protection == null) remove("protection") else putString("protection", snapshot.protection.encode())
    }

    fun load(): List<ClickCounterPoint> =
        ClickSequenceCodec.decode(preferences.getString("points", "") ?: "")

    fun loadProtection(): ClickRecordingProtection? =
        ClickRecordingProtection.decode(preferences.getString("protection", "") ?: "")
            ?.takeIf { it.isValid(load()) }

    fun save(points: List<ClickCounterPoint>, protection: ClickRecordingProtection? = null) {
        require(protection == null || protection.isValid(points))
        val editor = preferences.edit().putString("points", ClickSequenceCodec.encode(points)).remove("session_id")
        if (protection == null) editor.remove("protection") else editor.putString("protection", protection.encode())
        editor.apply()
    }
    companion object { private val lock = Any() }
}
