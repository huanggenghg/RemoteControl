package com.lumostech.communication.zego
import org.junit.Assert.*
import org.junit.Test
class GenerationTest {
    @Test fun closedGenerationCannotDeliverCallbacksAfterNewJoin() {
        val guard = Generation()
        val old = guard.begin()
        assertTrue(guard.isCurrent(old))
        guard.invalidate()
        assertFalse(guard.isCurrent(old))
        val current = guard.begin()
        assertFalse(guard.isCurrent(old))
        assertTrue(guard.isCurrent(current))
    }
}
