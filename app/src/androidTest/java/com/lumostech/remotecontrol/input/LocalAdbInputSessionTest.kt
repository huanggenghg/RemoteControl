package com.lumostech.remotecontrol.input

import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumostech.communication.ScreenGeometry
import com.lumostech.remotecontrol.activity.MainActivity
import com.lumostech.remotecontrol.protocol.RemoteCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LocalAdbInputSessionTest {
    private data class Touch(val action: Int, val x: Int, val y: Int)

    @Test fun pairsAndInjectsTapAndSwipeOnThisDevice() {
        // Supply the live pairing dialog details and the separate wireless debugging connection port.
        val arguments = InstrumentationRegistry.getArguments()
        val pairPort = arguments.getString("adbPairPort")?.toIntOrNull()
        val pairCode = arguments.getString("adbPairCode")
        val connectPort = arguments.getString("adbConnectPort")?.toIntOrNull()
        assumeTrue("Enable wireless debugging and provide adbPairPort, adbPairCode and adbConnectPort",
            LocalAdbInputSession.supported && pairPort != null && pairCode != null && connectPort != null)

        runBlocking {
            assertTrue(LocalAdbInputSession.pair(checkNotNull(pairPort), checkNotNull(pairCode)))
            val touches = LinkedBlockingQueue<Touch>()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var screen: ScreenGeometry
                scenario.onActivity { activity ->
                    val bounds = activity.windowManager.maximumWindowMetrics.bounds
                    screen = ScreenGeometry(bounds.width(), bounds.height(), 0)
                    activity.setContentView(View(activity).apply {
                        setOnTouchListener { _, event ->
                            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP) {
                                touches.add(Touch(event.actionMasked, event.rawX.toInt(), event.rawY.toInt()))
                            }
                            true
                        }
                    })
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                val session = LocalAdbInputSession()
                val ready = CompletableDeferred<Unit>()
                val worker = launch { session.run(checkNotNull(connectPort)) { ready.complete(Unit) } }
                try {
                    withTimeout(12_000L) { ready.await() }
                    val centerX = screen.width / 2
                    val centerY = screen.height / 2
                    val tap = RemoteCommand("click", .5f, .5f)
                    assertTrue(session.submit(tap, screen) { true })
                    assertEquals(Touch(MotionEvent.ACTION_DOWN, centerX, centerY), awaitTouch(touches))
                    assertEquals(Touch(MotionEvent.ACTION_UP, centerX, centerY), awaitTouch(touches))

                    val swipe = RemoteCommand("swipe", .5f, .75f, duration = 220L, endX = .5f, endY = .25f)
                    assertTrue(session.submit(swipe, screen) { true })
                    assertEquals(Touch(MotionEvent.ACTION_DOWN, centerX, (screen.height * .75f).toInt()), awaitTouch(touches))
                    assertEquals(Touch(MotionEvent.ACTION_UP, centerX, (screen.height * .25f).toInt()), awaitTouch(touches))

                    assertTrue(session.submit(tap, screen) { false })
                    assertNull(withContext(Dispatchers.IO) { touches.poll(600L, TimeUnit.MILLISECONDS) })
                } finally {
                    worker.cancelAndJoin()
                }
            }
        }
    }

    private suspend fun awaitTouch(touches: LinkedBlockingQueue<Touch>): Touch? =
        withContext(Dispatchers.IO) { touches.poll(5L, TimeUnit.SECONDS) }
}
