package com.lumostech.communication.zego

/** Invalidates delayed native callbacks before resources are released or reused. */
internal class Generation {
    @Volatile private var epoch = 0L
    @Volatile private var active = false
    @Synchronized fun begin(): Long { epoch += 1; active = true; return epoch }
    @Synchronized fun invalidate() { active = false; epoch += 1 }
    fun current(): Long = epoch
    fun isCurrent(value: Long): Boolean = active && value == epoch
}
