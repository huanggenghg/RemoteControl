package com.lumostech.accessibilitycore

import org.junit.Assert.*
import org.junit.Test

class ClickRecordingTest {
    @Test
    fun recordsCoordinatesAndOffsetsWithoutDependingOnAView() {
        val recording = ClickRecording()
        recording.record(120f, 240f, 1_000L)
        recording.record(360f, 480f, 2_500L)
        assertEquals(listOf(ClickCounterPoint(120f, 240f, 0L), ClickCounterPoint(360f, 480f, 1_500L)), recording.snapshot())
    }

    @Test
    fun capturedSnapshotDoesNotChangeWhenRecordingContinues() {
        val recording = ClickRecording()
        recording.record(10f, 20f, 100L)
        val saved = recording.snapshot()
        recording.record(30f, 40f, 200L)
        assertEquals(1, saved.size)
        assertEquals(2, recording.snapshot().size)
    }

    @Test
    fun restoresSequenceAndStartsFreshAfterReset() {
        val recording = ClickRecording(listOf(ClickCounterPoint(12f, 34f, 0L)))
        assertEquals(1, recording.snapshot().size)
        recording.clear()
        recording.record(56f, 78f, 9_000L)
        assertEquals(listOf(ClickCounterPoint(56f, 78f, 0L)), recording.snapshot())
    }

    @Test
    fun sequenceSurvivesSerializationForProcessRestart() {
        val points = listOf(ClickCounterPoint(12.5f, 34.5f, 0L), ClickCounterPoint(56f, 78f, 250L))
        assertEquals(points, ClickSequenceCodec.decode(ClickSequenceCodec.encode(points)))
    }

    @Test
    fun rejectsCorruptCoordinatesAndUnorderedOffsets() {
        assertTrue(ClickSequenceCodec.decode("v1\nNaN,1.0,0").isEmpty())
        assertTrue(ClickSequenceCodec.decode("v1\n1.0,2.0,100\n3.0,4.0,50").isEmpty())
        assertTrue(ClickSequenceCodec.decode("not-a-sequence").isEmpty())
        assertFalse(ClickSequenceCodec.isValid(emptyList()))
        assertFalse(ClickSequenceCodec.isValid(listOf(ClickCounterPoint(1f, 2f, 600_000L))))
    }
}
