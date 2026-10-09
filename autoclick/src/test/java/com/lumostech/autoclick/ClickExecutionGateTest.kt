package com.lumostech.autoclick

import org.junit.Assert.*
import org.junit.Test

class ClickExecutionGateTest {
    @Test
    fun trialAndScheduleCannotRunAtTheSameTime() {
        val gate = ClickExecutionGate()
        val lease = gate.tryStart(manual = true)!!
        assertNull(gate.tryStart(manual = false))
        assertNull(gate.tryStart(manual = true))
        gate.finish(lease)
        assertNotNull(gate.tryStart(manual = false))
    }

    @Test
    fun stopInvalidatesTheLeaseAndKeepsAutomaticStartsHalted() {
        val gate = ClickExecutionGate()
        val lease = gate.tryStart(manual = false)!!
        gate.stop()
        assertFalse(gate.canContinue(lease))
        gate.finish(lease)
        assertNull(gate.tryStart(manual = false))
        val manual = gate.tryStart(manual = true)!!
        gate.finish(manual)
        assertNull(gate.tryStart(manual = false))
        gate.allowScheduled()
        assertNotNull(gate.tryStart(manual = false))
    }

    @Test
    fun staleCompletionCannotReleaseAReplacementRun() {
        val gate = ClickExecutionGate()
        val old = gate.tryStart(manual = true)!!
        gate.finish(old)
        val current = gate.tryStart(manual = true)!!
        gate.finish(old)
        assertTrue(gate.canContinue(current))
        assertFalse(gate.canContinue(old))
        assertNull(gate.tryStart(manual = false))
    }

    @Test fun editAndExecutionCannotAcquireEachOthersReservation() {
        val gate = ClickExecutionGate()
        val run = gate.tryStart(manual = true)!!
        assertNull(gate.tryBeginEdit())
        gate.finish(run)
        val edit = gate.tryBeginEdit()!!
        assertTrue(gate.canEdit(edit))
        assertNull(gate.tryStart(manual = false))
        assertNull(gate.tryStart(manual = true))
        assertNull(gate.tryBeginEdit())
        gate.finishEdit(edit)
        assertNotNull(gate.tryStart(manual = false))
    }

    @Test fun emergencyStopRevokesEditAndCannotBeUndoneByItsCompletion() {
        val gate = ClickExecutionGate()
        val edit = gate.tryBeginEdit()!!
        gate.stop()
        assertFalse(gate.canEdit(edit))
        assertFalse(gate.allowScheduledAfterEdit(edit))
        gate.finishEdit(edit)
        assertNull(gate.tryStart(manual = false))
    }

    @Test fun staleEditReleaseCannotUnlockAnotherEdit() {
        val gate = ClickExecutionGate()
        val old = gate.tryBeginEdit()!!
        gate.finishEdit(old)
        val current = gate.tryBeginEdit()!!
        gate.finishEdit(old)
        assertTrue(gate.canEdit(current))
        assertNull(gate.tryStart(manual = false))
        assertTrue(gate.allowScheduledAfterEdit(current))
        gate.finishEdit(current)
        assertNotNull(gate.tryStart(manual = false))
    }

    @Test fun stopBeforeEditReservationCannotBeUndoneByThatEdit() {
        val gate = ClickExecutionGate()
        gate.stop()
        val edit = gate.tryBeginEdit()!!
        assertTrue(gate.canEdit(edit))
        assertFalse(gate.allowScheduledAfterEdit(edit))
        gate.finishEdit(edit)
        assertNull(gate.tryStart(manual = false))
    }
}
