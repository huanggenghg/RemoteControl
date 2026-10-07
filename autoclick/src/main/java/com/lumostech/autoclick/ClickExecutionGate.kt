package com.lumostech.autoclick

/** One live lease; emergency stop also blocks future automatic starts. */
class ClickExecutionGate {
    private var nextLease = 0L
    private var active: Long? = null
    private var stopped = false
    private var scheduledHalted = false

    @Synchronized
    fun tryStart(manual: Boolean): Long? {
        if (active != null || (!manual && scheduledHalted)) return null
        stopped = false
        return (++nextLease).also { active = it }
    }

    @Synchronized
    fun canContinue(lease: Long): Boolean = active == lease && !stopped

    @Synchronized
    fun stop() {
        stopped = true
        scheduledHalted = true
    }

    @Synchronized
    fun finish(lease: Long) {
        if (active == lease) active = null
    }

    @Synchronized
    fun allowScheduled() { scheduledHalted = false }
}
