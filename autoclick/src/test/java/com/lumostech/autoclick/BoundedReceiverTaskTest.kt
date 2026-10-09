package com.lumostech.autoclick

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BoundedReceiverTaskTest {
    @Test fun finishesBroadcastWhileBlockingWorkStillHasNotReturned() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val count = AtomicInteger()
        try {
            launchBoundedReceiverTask(scope, 100, { count.incrementAndGet(); finished.countDown() }) {
                withContext(Dispatchers.IO) { entered.countDown(); release.await(3, TimeUnit.SECONDS) }
            }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertTrue("finish must not wait for uninterruptible work", finished.await(1, TimeUnit.SECONDS))
            assertEquals(1L, release.count)
        } finally {
            release.countDown()
            scope.cancel()
        }
        delay(100)
        assertEquals(1, count.get())
    }

    @Test fun normallyCompletedWorkFinishesOnlyOnce() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val count = AtomicInteger()
        val finished = CompletableDeferred<Unit>()
        launchBoundedReceiverTask(scope, 100, { count.incrementAndGet(); finished.complete(Unit) }) { }
        withTimeout(1000) { finished.await() }
        delay(150)
        assertEquals(1, count.get())
    }
}
