package com.lumostech.accessibilitycore

import org.junit.Assert.*
import org.junit.Test

class ClickRecordingProtectionTest {
    private val points = listOf(ClickCounterPoint(10f, 20f, 0), ClickCounterPoint(30f, 40f, 100))
    private val protection = ClickRecordingProtection(1080, 2400, 0, listOf("com.example.first", "com.example.second"))
    private val environment = ClickEnvironment(1080, 2400, 0, "com.example.first")

    @Test
    fun protectionSurvivesSerializationWithOnePackagePerPoint() {
        assertEquals(protection, ClickRecordingProtection.decode(protection.encode()))
        assertTrue(protection.isValid(points))
        assertNull(ClickRecordingProtection.decode("corrupt"))
        assertNull(ClickRecordingProtection.decode("v1\n0,2400,0\ncom.example.first"))
    }

    @Test
    fun rejectsIncompleteProfilesAndCoordinatesOutsideDisplay() {
        assertFalse(protection.copy(packages = listOf("com.example.first")).isValid(points))
        assertFalse(protection.isValid(points.map { it.copy(x = 1080f) }))
        assertFalse(protection.isValid(points.map { it.copy(y = 2400f) }))
        assertFalse(protection.copy(rotation = 4).isValid(points))
    }

    @Test
    fun eachPointMustMatchItsOwnApplication() {
        assertNull(protection.failureAt(0, environment, true, false))
        assertEquals(ClickProtectionFailure.APP_CHANGED, protection.failureAt(1, environment, true, false))
        assertNull(protection.failureAt(1, environment.copy(packageName = "com.example.second"), true, false))
    }

    @Test
    fun geometryRotationAndUnknownForegroundStopPlayback() {
        assertEquals(ClickProtectionFailure.DISPLAY_CHANGED, protection.failureAt(0, environment.copy(width = 1000), true, false))
        assertEquals(ClickProtectionFailure.DISPLAY_CHANGED, protection.failureAt(0, environment.copy(rotation = 2), true, false))
        assertEquals(ClickProtectionFailure.ENVIRONMENT_UNAVAILABLE, protection.failureAt(0, null, true, false))
    }

    @Test
    fun lockingBetweenPointsStopsRemainingGestures() {
        assertNull(protection.failureAt(0, environment, true, false))
        assertEquals(ClickProtectionFailure.SCREEN_LOCKED, protection.failureAt(1, environment, true, true))
        assertEquals(ClickProtectionFailure.SCREEN_LOCKED, protection.failureAt(0, environment, false, false))
    }

    @Test
    fun anotherDisplayAndSplitWindowCannotReceiveDefaultDisplayClicks() {
        assertEquals(ClickProtectionFailure.UNSUPPORTED_DISPLAY,
            protection.failureAt(0, environment.copy(displayId = 1), true, false, points[0]))
        assertEquals(ClickProtectionFailure.WINDOW_MISMATCH,
            protection.failureAt(0, environment.copy(left = 500), true, false, points[0]))
    }
}
