package com.lumostech.accessibilitycore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ClickSequenceExecutorTest {
    @Test
    fun preservesOffsetsAndWaitsForEachGesture() = runBlocking {
        var now = 0L
        val started = mutableListOf<Long>()
        val result = ClickSequenceExecutor.execute(
            listOf(ClickCounterPoint(1f, 2f, 0L), ClickCounterPoint(3f, 4f, 100L)),
            now = { now }, wait = { now += it },
            click = { started.add(now); now += 20; true }
        )
        assertTrue(result)
        assertEquals(listOf(0L, 100L), started)
        assertEquals(120L, now)
    }

    @Test
    fun stopsAfterRejectedGesture() = runBlocking {
        var calls = 0
        val result = ClickSequenceExecutor.execute(
            listOf(ClickCounterPoint(1f, 2f, 0), ClickCounterPoint(3f, 4f, 10)),
            now = { 0L }, wait = {}, click = { calls++; false }
        )
        assertFalse(result)
        assertEquals(1, calls)
    }

    @Test
    fun emptySequenceNeverReportsSuccess() = runBlocking {
        assertFalse(ClickSequenceExecutor.execute(emptyList(), now = { 0 }, wait = {}, click = { true }))
    }

    @Test(expected = CancellationException::class)
    fun cancellationPropagatesWithoutExecutingRemainingPoints() = runBlocking<Unit> {
        ClickSequenceExecutor.execute(
            listOf(ClickCounterPoint(1f, 2f, 0)),
            now = { 0 }, wait = {}, click = { throw CancellationException("stopped") }
        )
    }
}
