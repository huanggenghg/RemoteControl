package com.lumostech.accessibilitycore

import android.content.Context

class ClickSequenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("click_recording", Context.MODE_PRIVATE)

    fun load(): List<ClickCounterPoint> =
        ClickSequenceCodec.decode(preferences.getString("points", "") ?: "")

    fun loadProtection(): ClickRecordingProtection? =
        ClickRecordingProtection.decode(preferences.getString("protection", "") ?: "")
            ?.takeIf { it.isValid(load()) }

    fun save(points: List<ClickCounterPoint>, protection: ClickRecordingProtection? = null) {
        require(protection == null || protection.isValid(points))
        val editor = preferences.edit().putString("points", ClickSequenceCodec.encode(points))
        if (protection == null) editor.remove("protection") else editor.putString("protection", protection.encode())
        editor.apply()
    }
}
