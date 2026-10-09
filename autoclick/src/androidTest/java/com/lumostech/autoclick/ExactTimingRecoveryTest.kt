package com.lumostech.autoclick

import androidx.test.platform.app.InstrumentationRegistry
import com.lumostech.accessibilitycore.ClickCounterPoint
import com.lumostech.accessibilitycore.ClickRecordingProtection
import org.junit.Before
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ExactTimingRecoveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ClickTaskStore(context)
    private val now = Calendar.getInstance().apply { set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private val clock = object : ClickClock {
        override fun wallMillis() = now
        override fun elapsedMillis() = 10000L
    }
    private val date = Calendar.getInstance().apply { timeInMillis = now }
    private val task = ClickTask("recovery", date.get(Calendar.HOUR_OF_DAY), date.get(Calendar.MINUTE), (1..7).toSet(),
        listOf(ClickCounterPoint(200f, 200f, 0)), protection = ClickRecordingProtection(1080, 2400, 0, listOf(context.packageName)))
    private class Platform : ClickAlarmPlatform {
        var permission = true
        var failure: Exception? = null
        val registered = mutableListOf<ClickAlarmOccurrence>()
        val cancelled = mutableListOf<ClickAlarmOccurrence>()
        override fun canSchedule() = permission
        override fun schedule(occurrence: ClickAlarmOccurrence) { failure?.let { throw it }; registered += occurrence }
        override fun cancel(occurrence: ClickAlarmOccurrence) { cancelled += occurrence }
    }
    @Before fun awaitStartup() = runBlocking { (context.applicationContext as AutoclickApp).awaitStartup() }
    @After fun cleanup() { store.clear() }
    @Test fun blockedControllerCannotKeepBroadcastPendingOrClaimClick() = runBlocking {
        store.clear()
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val finished = java.util.concurrent.CountDownLatch(1)
        val platform = object : ClickAlarmPlatform {
            override fun canSchedule() = true
            override fun cancel(occurrence: ClickAlarmOccurrence) = Unit
            override fun schedule(occurrence: ClickAlarmOccurrence) {
                entered.countDown()
                check(release.await(3, java.util.concurrent.TimeUnit.SECONDS))
            }
        }
        val mutation = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async {
            ClickTaskController(store, platform, {}, clock).save(task)
        }
        val receiverScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        try {
            assertTrue(entered.await(1, java.util.concurrent.TimeUnit.SECONDS))
            val event = store.alarmState()!!.next!!
            launchBoundedReceiverTask(receiverScope, 100, { finished.countDown() }) {
                ClickAlarmDispatcher.dispatch(context, event, clock)
            }
            assertTrue(finished.await(1, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(1L, release.count)
            assertNull(store.alarmState()!!.active)
            assertNull(store.startTrace())
        } finally { release.countDown(); receiverScope.cancel() }
        mutation.await()
        assertNull(store.alarmState()!!.active)
        assertEquals(ClickAlarmStatus.ARMED, store.alarmState()!!.status)
    }

    @Test fun cancellationWhileAcceptReturnsClosesReservedEventAndKeepsFuture() = runBlocking {
        store.clear(); store.save(task)
        val event = ClickAlarmOccurrence(task.id, task.scheduleId, now)
        store.saveAlarmState(task.id, ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMED, next = event))
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val finished = java.util.concurrent.CountDownLatch(1)
        val p = object : ClickAlarmPlatform {
            override fun canSchedule() = true
            override fun cancel(occurrence: ClickAlarmOccurrence) = Unit
            override fun schedule(occurrence: ClickAlarmOccurrence) {
                entered.countDown(); check(release.await(3, java.util.concurrent.TimeUnit.SECONDS))
            }
        }
        val c = ClickTaskController(store, p, {}, clock)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            launchBoundedReceiverTask(scope, 100, { finished.countDown() }) { c.acceptAlarm(event, now) }
            assertTrue(entered.await(1, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(event, store.alarmState()!!.active)
            assertTrue(finished.await(1, java.util.concurrent.TimeUnit.SECONDS))
        } finally { release.countDown() }
        withTimeout(2000) { while (store.alarmState()!!.active != null) delay(20) }
        assertEquals(ClickOutcomeReason.CANCELLED, store.lastExecutionResult()!!.reason)
        assertEquals(now, store.closedThrough())
        val future = requireNotNull(store.alarmState()!!.next)
        assertFalse(store.wasConsumed(task.id, now))
        assertNotNull(c.acceptAlarm(future, future.scheduledAt))
        assertEquals(future, store.alarmState()!!.active)
        c.finishAlarm(future, ClickOutcomeReason.CANCELLED, "fixture cleanup")
        scope.cancel()
    }

    @Test fun legacyMigrationPreservesHistoryAndRequiresExplicitEnable() = runBlocking {
        store.clear(); store.save(task)
        store.claimExecution(task.id, now - 86400000)
        store.recordOccurrenceOutcome(task.id, now - 86400000, true, "history", ClickOutcomeReason.COMPLETED)
        val p = Platform(); var cancellations = 0
        val c = ClickTaskController(store, p, { cancellations++ }, clock)
        c.reconcile()
        assertFalse(store.load()!!.enabled)
        assertEquals(ClickAlarmStatus.NEEDS_ENABLE, store.alarmState()!!.status)
        assertEquals("history", store.lastExecutionResult()!!.message)
        assertEquals(now - 86400000, store.closedThrough())
        assertTrue(cancellations > 0); assertTrue(p.registered.isEmpty())
        c.reconcile(); assertTrue(p.registered.isEmpty())
        c.setEnabled(true); assertEquals(1, p.registered.size)
        assertTrue(p.registered.single().scheduledAt > now)
        store.clear(); store.save(task)
        val failedPlatform = Platform()
        val failed = ClickTaskController(store, failedPlatform, { throw IllegalStateException("legacy cancellation failed") }, clock)
        assertTrue(runCatching { failed.reconcile() }.isFailure)
        assertNull(store.alarmState())
        assertEquals(task, store.load())
        assertTrue(failedPlatform.registered.isEmpty())
    }
    @Test fun permissionGrantNeverArmsAndStartupNeverInventsMissingAlarm() = runBlocking {
        store.clear(); val p = Platform().apply { permission = false }
        val c = ClickTaskController(store, p, {}, clock)
        assertEquals(ClickTaskSaveResult.SAVED_NEEDS_PERMISSION, c.save(task))
        assertFalse(store.load()!!.enabled); assertTrue(p.registered.isEmpty())
        p.permission = true; c.reconcile(ClickRecoveryEvent.PERMISSION_CHANGED); c.reconcile()
        assertFalse(store.load()!!.enabled); assertTrue(p.registered.isEmpty())
        c.setEnabled(true); val count = p.registered.size
        c.reconcile(); assertEquals(count, p.registered.size)
        c.reconcile(ClickRecoveryEvent.BOOT); assertEquals(count + 1, p.registered.size)
        c.setEnabled(false); c.reconcile(ClickRecoveryEvent.BOOT)
        assertEquals(count + 1, p.registered.size)
    }
    @Test fun concurrentAlarmAcceptanceReservesOnlyOneOwnerAndOneFuture() = runBlocking {
        store.clear(); store.save(task)
        val event = ClickAlarmOccurrence(task.id, task.scheduleId, now)
        store.saveAlarmState(task.id, ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMED, next = event))
        val p = Platform(); val c = ClickTaskController(store, p, {}, clock)
        val results = listOf(async(Dispatchers.Default) { c.acceptAlarm(event, now) },
            async(Dispatchers.Default) { c.acceptAlarm(event, now) }).awaitAll()
        assertEquals(1, results.count { it != null })
        assertEquals(1, p.registered.size)
        assertEquals(event, store.alarmState()!!.active)
        c.finishAlarm(event, ClickOutcomeReason.CANCELLED, "fixture cleanup")
    }

    @Test fun duplicateAndStaleEventsCannotAdvanceOrOverwrite() = runBlocking {
        store.clear(); store.save(task)
        val o = ClickAlarmOccurrence(task.id, task.scheduleId, now)
        store.saveAlarmState(task.id, ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMED, o))
        val p = Platform(); val c = ClickTaskController(store, p, {}, clock)
        assertNotNull(c.acceptAlarm(o, now))
        assertNull(c.acceptAlarm(o, now)); assertEquals(1, p.registered.size)
        assertTrue(store.finishAlarm(o, ClickOutcomeReason.START_EXPIRED, "skip"))
        val next = store.alarmState()!!.next
        c.finishAlarm(o, ClickOutcomeReason.UNKNOWN, "duplicate")
        assertEquals(next, store.alarmState()!!.next)
        assertEquals("skip", store.lastExecutionResult()!!.message)
        c.setEnabled(false); c.setEnabled(true)
        assertNull(c.acceptAlarm(o, now)); assertEquals(now, store.closedThrough())
    }
    @Test fun futureRegistrationFailureKeepsOwnedActiveButNeverAdvertisesArmed() = runBlocking {
        store.clear(); store.save(task)
        val o = ClickAlarmOccurrence(task.id, task.scheduleId, now)
        store.saveAlarmState(task.id, ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMED, o))
        val p = Platform().apply { failure = SecurityException("test registration failure") }
        val c = ClickTaskController(store, p, {}, clock)
        assertNotNull(c.acceptAlarm(o, now))
        assertEquals(o, store.alarmState()!!.active)
        assertEquals(ClickAlarmStatus.SCHEDULE_FAILED, store.alarmState()!!.status)
        assertNull(store.alarmState()!!.next)
        assertTrue(p.cancelled.contains(o.copy(scheduledAt = now + 86400000)))
    }
    @Test fun timezonePausesAndClaimedCrashCannotReplay() = runBlocking {
        store.clear(); store.save(task)
        val o = ClickAlarmOccurrence(task.id, task.scheduleId, now)
        store.saveAlarmState(task.id, ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMED, o))
        store.reserveAlarm(o, now); store.claimAlarm(o)
        val p = Platform(); val c = ClickTaskController(store, p, {}, clock)
        c.reconcile()
        assertNull(store.alarmState()!!.active); assertEquals(now, store.closedThrough())
        assertEquals(ClickOutcomeReason.CANCELLED, store.lastExecutionResult()!!.reason)
        c.setEnabled(true); c.reconcile(ClickRecoveryEvent.ZONE_CHANGED)
        assertFalse(store.load()!!.enabled)
        assertEquals(ClickAlarmStatus.TIME_ZONE_CHANGED, store.alarmState()!!.status)
        val count = p.registered.size; c.reconcile(ClickRecoveryEvent.BOOT)
        assertEquals(count, p.registered.size)
    }
    @Test fun startupBeforeBootOrUpdatePreservesConfirmedEnableIntent() = runBlocking {
        for (event in listOf(ClickRecoveryEvent.BOOT, ClickRecoveryEvent.PACKAGE_REPLACED)) {
            store.clear(); store.save(task)
            val missed = ClickAlarmOccurrence(task.id, task.scheduleId, now - 86400000)
            store.saveAlarmState(task.id, ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMED, missed))
            val p = Platform(); val c = ClickTaskController(store, p, {}, clock)
            c.reconcile(ClickRecoveryEvent.STARTUP)
            assertTrue("Startup must preserve explicit v2 enable intent for system recovery", store.load()!!.enabled)
            assertTrue(p.registered.isEmpty())
            assertEquals(ClickOutcomeReason.START_EXPIRED, store.lastExecutionResult()!!.reason)
            c.reconcile(event)
            assertEquals(1, p.registered.size)
            assertTrue(p.registered.single().scheduledAt > now)
            assertEquals(missed.scheduledAt, store.closedThrough())
        }
    }
    @Test fun cancellationDuringFutureRegistrationClosesOwnedOccurrenceAndPropagates() = runBlocking {
        store.clear(); store.save(task)
        val o = ClickAlarmOccurrence(task.id, task.scheduleId, now)
        store.saveAlarmState(task.id, ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMED, o))
        val p = Platform().apply { failure = kotlinx.coroutines.CancellationException("cancel test") }
        val c = ClickTaskController(store, p, {}, clock)
        try { c.acceptAlarm(o, now); fail("Cancellation must propagate") }
        catch (_: kotlinx.coroutines.CancellationException) { }
        assertNull(store.alarmState()!!.active); assertNull(store.alarmState()!!.next)
        assertEquals(now, store.closedThrough())
        assertEquals(ClickOutcomeReason.CANCELLED, store.lastExecutionResult()!!.reason)
    }
    @Test fun earlyEventKeepsOriginalFutureAndDoesNotCloseOrConsume() = runBlocking {
        store.clear(); store.save(task)
        val o = ClickAlarmOccurrence(task.id, task.scheduleId, now + 86400000)
        store.saveAlarmState(task.id, ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMED, o))
        val p = Platform(); val c = ClickTaskController(store, p, {}, clock)
        assertNull(c.acceptAlarm(o, now)); assertEquals(o, store.alarmState()!!.next)
        assertEquals(0L, store.closedThrough()); assertNull(store.lastExecutionRecord())
        assertEquals(listOf(o), p.registered)
    }

    @Test fun startupThenLateAlarmSkipsButAdvancesFutureWithoutClickClaim() = runBlocking {
        store.clear(); store.save(task)
        val old = ClickAlarmOccurrence(task.id, task.scheduleId, now - 86400000)
        store.saveAlarmState(task.id, ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMED, old))
        val p = Platform(); val c = ClickTaskController(store, p, {}, clock)
        c.reconcile(ClickRecoveryEvent.STARTUP)
        c.acceptAlarm(old, now)
        assertEquals(1, p.registered.size)
        assertTrue(p.registered.single().scheduledAt > now)
        assertEquals(old.scheduledAt, store.closedThrough())
        assertEquals(ClickOutcomeReason.START_EXPIRED, store.lastExecutionResult()!!.reason)
        assertNull(store.alarmState()!!.active)
        assertNull(c.acceptAlarm(old, now)); assertEquals(1, p.registered.size)
    }
    @Test fun recoveryDiscardInsideWindowClosesOccurrenceBeforeClockRollback() = runBlocking {
        for (event in listOf(ClickRecoveryEvent.BOOT, ClickRecoveryEvent.PACKAGE_REPLACED, ClickRecoveryEvent.TIME_CHANGED)) {
            store.clear(); store.save(task)
            val o = ClickAlarmOccurrence(task.id, task.scheduleId, now)
            store.saveAlarmState(task.id, ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMED, o))
            var wall = now + 1000
            val changingClock = object : ClickClock {
                override fun wallMillis() = wall
                override fun elapsedMillis() = 10000L
            }
            val p = Platform(); val c = ClickTaskController(store, p, {}, changingClock)
            c.reconcile(event)
            assertEquals("Discarding an already due occurrence must retain its terminal boundary", now, store.closedThrough())
            wall = now - 1000; c.reconcile(ClickRecoveryEvent.TIME_CHANGED)
            assertTrue(store.alarmState()!!.next!!.scheduledAt > now)
            assertNull(c.acceptAlarm(o, now))
        }
    }

    @Test fun timeChangeClosesActiveWithActualReasonAndRetainsLedger() = runBlocking {
        store.clear(); store.save(task)
        val o = ClickAlarmOccurrence(task.id, task.scheduleId, now)
        store.saveAlarmState(task.id, ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMED, o))
        store.reserveAlarm(o, now); store.claimAlarm(o)
        val p = Platform(); val c = ClickTaskController(store, p, {}, clock)
        c.reconcile(ClickRecoveryEvent.TIME_CHANGED)
        assertEquals(ClickOutcomeReason.TIME_CHANGED, store.lastExecutionResult()!!.reason)
        assertEquals(now, store.closedThrough()); assertNull(store.alarmState()!!.active)
        assertTrue(store.alarmState()!!.next!!.scheduledAt > now)
    }

}
