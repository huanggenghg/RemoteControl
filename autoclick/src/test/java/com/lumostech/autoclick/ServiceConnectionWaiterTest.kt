package com.lumostech.autoclick

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ServiceConnectionWaiterTest {
    @Test fun connectedServiceReturnsImmediately() = runBlocking {
        assertEquals("connected", ServiceConnectionWaiter.await(100, 2, { true }) { "connected" })
    }

    @Test fun waitsForLateConnection() = runBlocking {
        var service: String? = null
        launch { delay(15); service = "connected" }
        assertEquals("connected", ServiceConnectionWaiter.await(200, 2, { true }) { service })
    }

    @Test fun missingServiceTimesOut() = runBlocking {
        assertNull(ServiceConnectionWaiter.await<String>(30, 2, { true }) { null })
    }

    @Test fun disabledOrChangedTaskAbortsBeforeProbing() = runBlocking {
        var probes = 0
        assertNull(ServiceConnectionWaiter.await(100, 2, { false }) { probes++; "connected" })
        assertEquals(0, probes)
    }

    @Test fun stoppingDuringWaitPreventsLateConnection() = runBlocking {
        var allowed = true
        launch { delay(15); allowed = false }
        var probes = 0
        assertNull(ServiceConnectionWaiter.await<String>(200, 2, { allowed }) { probes++; null })
        val previousProbes = probes
        delay(10)
        assertEquals(previousProbes, probes)
    }

    @Test fun callerCancellationIsNotConvertedToTimeout() = runBlocking {
        val result = async { ServiceConnectionWaiter.await<String>(1_000, 2, { true }) { null } }
        yield()
        result.cancel()
        try { result.await(); fail("Cancellation must propagate") } catch (_: CancellationException) { }
    }
}
