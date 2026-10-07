package com.lumostech.autoclick

import org.junit.Assert.assertEquals
import org.junit.Test

class ClickServiceReadinessTest {
    @Test fun disabledPermissionTakesPrecedenceOverStaleReference() {
        assertEquals(ClickServiceReadiness.DISABLED, ClickServiceReadiness.from(false, true))
    }
    @Test fun enabledPermissionDoesNotMeanConnected() {
        assertEquals(ClickServiceReadiness.CONNECTING, ClickServiceReadiness.from(true, false))
    }
    @Test fun enabledAndConnectedServiceIsReady() {
        assertEquals(ClickServiceReadiness.CONNECTED, ClickServiceReadiness.from(true, true))
    }
}
