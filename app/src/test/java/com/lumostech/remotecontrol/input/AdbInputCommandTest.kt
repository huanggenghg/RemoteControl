package com.lumostech.remotecontrol.input

import com.lumostech.communication.ScreenGeometry
import com.lumostech.remotecontrol.protocol.RemoteCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdbInputCommandTest {
    private val screen = ScreenGeometry(1080, 1920, 0)

    @Test fun tapAndSwipeMapToTheHostScreenIncludingItsEdges() {
        assertEquals("input tap 1079 1919", AdbInputCommand.from(RemoteCommand("click", 1f, 1f), screen)?.shell)
        val swipe = RemoteCommand("swipe", .25f, .75f, duration = 190L, endX = .75f, endY = .25f)
        assertEquals("input swipe 270 1440 810 480 190", AdbInputCommand.from(swipe, screen)?.shell)
        val landscape = ScreenGeometry(1920, 1080, 1)
        assertEquals("input swipe 480 810 1440 270 190", AdbInputCommand.from(swipe, landscape)?.shell)
    }

    @Test fun directionButtonsKeepTheirDistanceAndDurationWithinTheDisplay() {
        assertEquals("input swipe 540 960 540 760 300", AdbInputCommand.from(RemoteCommand("scrollUp"), screen)?.shell)
        assertEquals("input swipe 540 960 540 1919 300",
            AdbInputCommand.from(RemoteCommand("scrollDown", distance = 10000f), screen)?.shell)
        assertEquals("input swipe 540 960 340 960 120",
            AdbInputCommand.from(RemoteCommand("scrollLeft", duration = 120L), screen)?.shell)
        assertEquals("input swipe 540 960 740 960 300", AdbInputCommand.from(RemoteCommand("scrollRight"), screen)?.shell)
    }

    @Test fun navigationUsesFixedKeysAndRemoteTextCannotBecomeShellCode() {
        assertEquals("input keyevent KEYCODE_BACK", AdbInputCommand.from(RemoteCommand("back"), screen)?.shell)
        assertEquals("input keyevent KEYCODE_HOME", AdbInputCommand.from(RemoteCommand("home"), screen)?.shell)
        assertEquals("input keyevent KEYCODE_APP_SWITCH", AdbInputCommand.from(RemoteCommand("recents"), screen)?.shell)
        assertNull(AdbInputCommand.from(RemoteCommand("softInput", inputText = "你好; id"), screen))
        assertNull(AdbInputCommand.from(RemoteCommand("input tap 0 0"), screen))
    }
}
