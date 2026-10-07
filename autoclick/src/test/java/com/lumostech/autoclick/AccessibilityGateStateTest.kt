package com.lumostech.autoclick

import org.junit.Assert.*
import org.junit.Test

class AccessibilityGateStateTest {
    @Test fun unknownInitialStateBlocksHome() {
        assertEquals(AccessibilityGatePhase.CHECKING, AccessibilityGateState().phase)
    }

    @Test fun disabledServiceRequiresExplicitGrant() {
        assertEquals(AccessibilityGatePhase.ENABLE,
            AccessibilityGateState().update(ClickServiceReadiness.DISABLED, 0))
    }

    @Test fun connectedServiceOpensHome() {
        assertEquals(AccessibilityGatePhase.READY,
            AccessibilityGateState().update(ClickServiceReadiness.CONNECTED, 0))
    }

    @Test fun repeatedRefreshDoesNotExtendConnectionWait() {
        val gate = AccessibilityGateState()
        assertEquals(AccessibilityGatePhase.CONNECTING, gate.update(ClickServiceReadiness.CONNECTING, 1_000))
        assertEquals(AccessibilityGatePhase.CONNECTING, gate.update(ClickServiceReadiness.CONNECTING, 10_999))
        assertEquals(AccessibilityGatePhase.RECONNECT, gate.update(ClickServiceReadiness.CONNECTING, 11_000))
        assertEquals(AccessibilityGatePhase.RECONNECT, gate.update(ClickServiceReadiness.CONNECTING, 12_000))
    }

    @Test fun recoveryClosesReconnectPrompt() {
        val gate = AccessibilityGateState()
        gate.update(ClickServiceReadiness.CONNECTING, 0)
        gate.update(ClickServiceReadiness.CONNECTING, 10_000)
        assertEquals(AccessibilityGatePhase.READY, gate.update(ClickServiceReadiness.CONNECTED, 10_100))
    }

    @Test fun losingConnectionStartsNewWaitAfterSuccessfulConnection() {
        val gate = AccessibilityGateState()
        gate.update(ClickServiceReadiness.CONNECTING, 0)
        gate.update(ClickServiceReadiness.CONNECTED, 9_000)
        assertEquals(AccessibilityGatePhase.CONNECTING, gate.update(ClickServiceReadiness.CONNECTING, 20_000))
        assertEquals(AccessibilityGatePhase.CONNECTING, gate.update(ClickServiceReadiness.CONNECTING, 29_999))
    }

    @Test fun explicitSettingsVisitProvidesNewConnectionWindow() {
        val gate = AccessibilityGateState()
        gate.update(ClickServiceReadiness.CONNECTING, 0)
        gate.update(ClickServiceReadiness.CONNECTING, 10_000)
        gate.resetForSettings()
        assertEquals(AccessibilityGatePhase.CHECKING, gate.phase)
        assertEquals(AccessibilityGatePhase.CONNECTING, gate.update(ClickServiceReadiness.CONNECTING, 20_000))
    }

    @Test fun settingsNavigationCannotStartWaitBeforeReturning() {
        val gate = AccessibilityGateState()
        gate.update(ClickServiceReadiness.CONNECTING, 0)
        gate.settingsOpened()
        assertEquals(AccessibilityGatePhase.CHECKING, gate.update(ClickServiceReadiness.CONNECTING, 500))
        assertEquals(AccessibilityGatePhase.CHECKING, gate.update(ClickServiceReadiness.CONNECTING, 60_000))
        gate.settingsReturned()
        assertEquals(AccessibilityGatePhase.CONNECTING, gate.update(ClickServiceReadiness.CONNECTING, 61_000))
        assertEquals(AccessibilityGatePhase.CONNECTING, gate.update(ClickServiceReadiness.CONNECTING, 70_999))
        assertEquals(AccessibilityGatePhase.RECONNECT, gate.update(ClickServiceReadiness.CONNECTING, 71_000))
    }

    @Test fun disablingGrantClearsPreviousConnectionWait() {
        val gate = AccessibilityGateState()
        gate.update(ClickServiceReadiness.CONNECTING, 0)
        gate.update(ClickServiceReadiness.DISABLED, 10_000)
        assertEquals(AccessibilityGatePhase.CONNECTING, gate.update(ClickServiceReadiness.CONNECTING, 20_000))
    }
}
