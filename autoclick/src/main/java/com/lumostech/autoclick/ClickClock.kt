package com.lumostech.autoclick

internal interface ClickClock {
    fun wallMillis(): Long
    fun elapsedMillis(): Long
}

internal object SystemClickClock : ClickClock {
    override fun wallMillis(): Long = System.currentTimeMillis()
    override fun elapsedMillis(): Long = android.os.SystemClock.elapsedRealtime()
}
