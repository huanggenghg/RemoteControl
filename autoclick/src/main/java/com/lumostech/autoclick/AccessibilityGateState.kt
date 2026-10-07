package com.lumostech.autoclick

internal enum class AccessibilityGatePhase { CHECKING, ENABLE, CONNECTING, RECONNECT, READY }

/** A single disconnected episode keeps its monotonic deadline across UI refreshes. */
internal class AccessibilityGateState(private val maxWaitMs: Long = ClickServiceConnection.MAX_WAIT_MS) {
    var phase = AccessibilityGatePhase.CHECKING
        private set
    private var startedAt: Long? = null
    private var visitingSettings = false

    fun settingsOpened() {
        visitingSettings = true
        resetForSettings()
    }

    fun settingsReturned() {
        if (visitingSettings) {
            visitingSettings = false
            resetForSettings()
        }
    }

    fun resetForSettings() {
        startedAt = null
        phase = AccessibilityGatePhase.CHECKING
    }

    fun update(readiness: ClickServiceReadiness, elapsed: Long): AccessibilityGatePhase {
        if (visitingSettings) return phase
        phase = when (readiness) {
            ClickServiceReadiness.DISABLED -> {
                startedAt = null
                AccessibilityGatePhase.ENABLE
            }
            ClickServiceReadiness.CONNECTED -> {
                startedAt = null
                AccessibilityGatePhase.READY
            }
            ClickServiceReadiness.CONNECTING -> {
                val start = startedAt ?: elapsed.also { startedAt = it }
                if (elapsed - start >= maxWaitMs) AccessibilityGatePhase.RECONNECT
                else AccessibilityGatePhase.CONNECTING
            }
        }
        return phase
    }
}
