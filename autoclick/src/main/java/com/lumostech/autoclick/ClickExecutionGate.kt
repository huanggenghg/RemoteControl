package com.lumostech.autoclick

/** One live lease; emergency stop also blocks future automatic starts. */
class ClickExecutionGate {
    private var nextLease = 0L
    private var active: Long? = null
    private var stopped = false
    private var scheduledHalted = false
    private var editing: Long? = null
    private var editRevoked = false

    @Synchronized
    fun tryStart(manual: Boolean): Long? {
        if (active != null || editing != null || (!manual && scheduledHalted)) return null
        stopped = false
        return (++nextLease).also { active = it }
    }

    @Synchronized
    fun canContinue(lease: Long): Boolean = active == lease && !stopped

    @Synchronized
    fun stop() {
        stopped = true
        scheduledHalted = true
        editRevoked = true
    }

    @Synchronized
    fun finish(lease: Long) {
        if (active == lease) active = null
    }

    @Synchronized
    fun allowScheduled() { scheduledHalted = false }

    @Synchronized
    fun tryBeginEdit(): Long? {
        if (active != null || editing != null) return null
        editRevoked = false
        return (++nextLease).also { editing = it }
    }

    @Synchronized
    fun canEdit(lease: Long): Boolean = editing == lease && !editRevoked

    @Synchronized
    fun allowScheduledAfterEdit(lease: Long): Boolean {
        // Editing keeps the existing enabled state; only an explicit enable/save
        // may reopen a gate already halted before this edit acquired its lease.
        return canEdit(lease) && !scheduledHalted
    }

    @Synchronized
    fun finishEdit(lease: Long) {
        if (editing == lease) editing = null
    }
}
