package com.lumostech.remotecontrol.communication

import android.media.projection.MediaProjection
import android.view.ViewGroup
import com.lumostech.communication.*
import com.lumostech.remotecontrol.protocol.RemoteProtocol
import android.widget.FrameLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCoordinatorTest {
    private class FakeSession : CommunicationSession, MediaTransport, ControlTransport {
        var closes = 0
        val bindings = mutableListOf<Peer>()
        var sent: (Peer, ByteArray) -> Unit = { _, _ -> }
        val joined = CompletableDeferred<Result<Unit>>()
        override val media: MediaTransport get() = this
        override val control: ControlTransport get() = this
        override val maxFrameBytes = 900
        override suspend fun join(credentials: SessionCredentials, role: SessionRole) = joined.await()
        override suspend fun renewCredentials(credentials: SessionCredentials) = Result.success(Unit)
        override suspend fun close() { closes++ }
        override suspend fun startSharing(projection: MediaProjection, geometry: ScreenGeometry) = Result.success(Unit)
        override suspend fun stopSharing() = Unit
        override fun bindRemote(peer: Peer, container: ViewGroup) { bindings += peer }
        override fun unbindRemote() = Unit
        override suspend fun send(peer: Peer, frame: ByteArray): Result<Unit> {
            sent(peer, frame)
            return Result.success(Unit)
        }
    }

    @Test fun delayedCredentialCannotCreateSdkAfterClose() = runTest {
        val deferred = CompletableDeferred<SessionCredentials>()
        var created = 0
        val coordinator = SessionCoordinator(this, { created++; FakeSession() },
            CredentialProvider { _, _, _ -> deferred.await() }, { true }, { _, _, _ -> }, {}, { 0L })
        val starting = launch { coordinator.start("123456", SessionRole.CONTROLLER) }
        runCurrent()
        coordinator.close()
        deferred.complete(SessionCredentials("app", "123456", "user", "token"))
        starting.join()
        assertEquals(0, created)
    }

    @Test fun cancellingPendingJoinClosesSdk() = runTest {
        val sdk = FakeSession()
        val coordinator = SessionCoordinator(this, { sdk },
            CredentialProvider { room, _, _ -> SessionCredentials("app", room, "user", "token") },
            { true }, { _, _, _ -> }, {}, { 0L })
        val starting = launch { coordinator.start("123456", SessionRole.CONTROLLER) }
        runCurrent()
        starting.cancelAndJoin()
        assertEquals(1, sdk.closes)
    }

    @Test fun oldCallbacksCannotChangeClosedSessionState() = runTest {
        lateinit var listener: CommunicationListener
        val states = mutableListOf<ConnectionState>()
        val sdk = FakeSession()
        val coordinator = SessionCoordinator(this, { listener = it; sdk },
            CredentialProvider { room, _, _ -> SessionCredentials("app", room, "user", "token") },
            { true }, { state, _, _ -> states += state }, {}, { 0L })
        val starting = launch { coordinator.start("123456", SessionRole.CONTROLLER) }
        runCurrent()
        coordinator.close()
        sdk.joined.complete(Result.success(Unit))
        starting.join()
        listener.onConnectionState(ConnectionState.CONNECTED)
        runCurrent()
        assertEquals(ConnectionState.CLOSED, states.last())
    }

    @Test fun initialCredentialTimeoutReportsFailureWithoutCreatingSdk() = runTest {
        var created = 0
        val states = mutableListOf<ConnectionState>()
        val errors = mutableListOf<String>()
        val coordinator = SessionCoordinator(this, { created++; FakeSession() },
            CredentialProvider { _, _, _ -> awaitCancellation() }, { true },
            { state, _, _ -> states += state }, errors::add, { testScheduler.currentTime })
        val starting = launch { coordinator.start("123456", SessionRole.CONTROLLER) }
        advanceTimeBy(15001)
        runCurrent()
        starting.join()
        assertEquals(0, created)
        assertEquals(ConnectionState.FAILED, states.last())
        assertEquals(1, errors.size)
    }

    @Test fun renewalTimeoutClosesSdkAndReportsFailure() = runTest {
        val sdk = FakeSession().apply { joined.complete(Result.success(Unit)) }
        lateinit var listener: CommunicationListener
        var fetched = 0
        val states = mutableListOf<ConnectionState>()
        val errors = mutableListOf<String>()
        val coordinator = SessionCoordinator(this, { listener = it; sdk },
            CredentialProvider { room, _, _ ->
                if (++fetched == 1) SessionCredentials("app", room, "user", "token")
                else awaitCancellation()
            }, { true }, { state, _, _ -> states += state }, errors::add,
            { testScheduler.currentTime })
        try {
            coordinator.start("123456", SessionRole.CONTROLLER)
            listener.onConnectionState(ConnectionState.CONNECTED)
            runCurrent()
            listener.onCredentialsExpiring()
            listener.onCredentialsExpiring()
            runCurrent()
            assertEquals(2, fetched)
            advanceTimeBy(15001)
            runCurrent()
            assertEquals(1, sdk.closes)
            assertEquals(ConnectionState.FAILED, states.last())
            assertEquals(1, errors.size)
        } finally { coordinator.close() }
    }

    @Test fun additionalPublisherCannotReplaceSelectedPeersVideo() = runTest {
        val sdk = FakeSession().apply { joined.complete(Result.success(Unit)) }
        lateinit var listener: CommunicationListener
        var ready = false
        val host = RemoteProtocol(SessionRole.HOST, "host-a", { testScheduler.currentTime },
            { _, frame, _ -> listener.onMessage(Peer("host-a"), frame) }, { true }, {}, { _, _ -> })
        sdk.sent = { peer, frame -> if (peer.id == "host-a") host.receive(Peer("controller"), frame) }
        val coordinator = SessionCoordinator(this, { listener = it; sdk },
            CredentialProvider { room, _, _ -> SessionCredentials("app", room, "controller", "token") },
            { true }, { _, canControl, _ -> ready = canControl }, {}, { testScheduler.currentTime })
        // The fake renderer only records this opaque container; no Android method is invoked.
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val container = unsafeClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafeField.get(null), FrameLayout::class.java) as ViewGroup
        try {
            coordinator.bindRemote(container)
            coordinator.start("123456", SessionRole.CONTROLLER)
            host.connection(true)
            host.sharing(ScreenGeometry(1080, 1920, 0))
            host.peerJoined(Peer("controller"))
            listener.onConnectionState(ConnectionState.CONNECTED)
            listener.onPeerJoined(Peer("host-a"))
            listener.onRemoteVideoAvailable(Peer("host-a"))
            advanceTimeBy(500)
            runCurrent()
            listener.onRemoteFirstFrame(Peer("host-a"))
            runCurrent()
            assertTrue(ready)
            assertEquals(Peer("host-a"), sdk.bindings.last())
            listener.onRemoteVideoAvailable(Peer("host-b"))
            runCurrent()
            assertTrue(ready)
            assertEquals(Peer("host-a"), sdk.bindings.last())
            assertFalse(sdk.bindings.contains(Peer("host-b")))
        } finally { coordinator.close() }
    }
}
