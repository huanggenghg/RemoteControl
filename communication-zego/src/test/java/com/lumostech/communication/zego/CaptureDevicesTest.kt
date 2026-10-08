package com.lumostech.communication.zego

import org.junit.Assert.*
import org.junit.Test

class CaptureDevicesTest {
    @Test fun joiningReleasesMicrophoneHardware() {
        val hardware = Hardware()
        hardware.devices.initialize()
        assertFalse(hardware.microphoneOccupied)
        assertFalse(hardware.videoEnabled)
    }

    @Test fun customSourceEnablesEncoderWithoutOpeningPhysicalCamera() {
        val hardware = Hardware()
        hardware.devices.initialize()
        hardware.devices.start()
        assertTrue(hardware.videoEnabled)
        assertTrue(hardware.customSource)
        assertFalse(hardware.physicalCameraOpened)
    }

    @Test fun stoppingDisablesEncoderBeforeRestoringDefaultCaptureSource() {
        val hardware = Hardware()
        hardware.devices.initialize()
        hardware.devices.start()
        hardware.devices.stop()
        assertFalse(hardware.videoEnabled)
        assertFalse(hardware.customSource)
        assertFalse(hardware.physicalCameraOpened)
    }

    private class Hardware {
        var videoEnabled = true
        var customSource = false
        var microphoneOccupied = true
        var physicalCameraOpened = false
        val devices = CaptureDevices(
            enableVideo = { enabled ->
                videoEnabled = enabled
                detectPhysicalCamera()
            },
            enableAudioCapture = { enabled -> microphoneOccupied = enabled },
            enableCustomCapture = { enabled ->
                customSource = enabled
                detectPhysicalCamera()
            }
        )
        private fun detectPhysicalCamera() {
            if (videoEnabled && !customSource) physicalCameraOpened = true
        }
    }
}
