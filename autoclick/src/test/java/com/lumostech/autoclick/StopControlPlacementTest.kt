package com.lumostech.autoclick

import com.lumostech.accessibilitycore.ClickCounterPoint
import org.junit.Assert.*
import org.junit.Test

class StopControlPlacementTest {
    @Test
    fun stopControlAvoidsRecordedClickPositions() {
        val points = listOf(ClickCounterPoint(900f, 100f, 0), ClickCounterPoint(100f, 100f, 100))
        val position = StopControlPlacement.find(1080, 2400, 300, 180, points)!!
        assertTrue(position.x >= 0 && position.x + 300 <= 1080)
        assertTrue(position.y >= 0 && position.y + 180 <= 2400)
        assertTrue(points.none { position.covers(it, 300, 180) })
    }

    @Test
    fun tooSmallOrFullyCoveredDisplayCannotOfferASafeControl() {
        assertNull(StopControlPlacement.find(100, 100, 200, 50, emptyList()))
        assertNull(StopControlPlacement.find(100, 100, 100, 100, listOf(ClickCounterPoint(50f, 50f, 0))))
    }
}
