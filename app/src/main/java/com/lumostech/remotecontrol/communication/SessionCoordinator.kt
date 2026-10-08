package com.lumostech.remotecontrol.communication

import android.media.projection.MediaProjection
import android.os.SystemClock
import android.view.ViewGroup
import com.lumostech.communication.*
import com.lumostech.remotecontrol.protocol.*
import com.lumostech.remotecontrol.utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.UUID

class SessionCoordinator(
    private val scope: CoroutineScope,
    private val factory: (CommunicationListener) -> CommunicationSession,
    private val credentials: CredentialProvider,
    private val dispatch: (RemoteCommand) -> Boolean,
    private val status: (ConnectionState, Boolean, ScreenGeometry?) -> Unit,
    private val error: (String) -> Unit,
    private val clock: () -> Long = SystemClock::elapsedRealtime
) {
    private data class Outgoing(val epoch: Long, val peer: Peer, val bytes: ByteArray, val deadline: Long?)
    private var generation = 0L
    private var epoch = 0L
    private var session: CommunicationSession? = null
    private var protocol: RemoteProtocol? = null
    private var work: Job? = null
    private var refresh: Job? = null
    private var queue = Channel<Outgoing>(32)
    private var currentState = ConnectionState.IDLE
    private var firstFrames = mutableSetOf<String>()
    private var container: ViewGroup? = null
    private var boundPeer: Peer? = null
    private var request: Triple<String, String, SessionRole>? = null
    private var joinedCredentials: SessionCredentials? = null
    private var host = false

    suspend fun start(roomId: String, role: SessionRole, projection: MediaProjection? = null,
                      geometry: ScreenGeometry? = null) {
        close()
        val generationAtStart = ++generation
        require(roomId.matches(Regex("[0-9]{6}"))) { "协助码必须是六位数字" }
        val requestedUser = UUID.randomUUID().toString()
        request = Triple(roomId, requestedUser, role)
        host = role == SessionRole.HOST
        currentState = ConnectionState.CONNECTING
        notifyStatus()
        try {
            val credential = withTimeoutOrNull(15000) { credentials.fetch(roomId, requestedUser, role) }
                ?: throw IllegalStateException("Credential request timed out")
            if (generation != generationAtStart) return
            require(credential.roomId == roomId && credential.appId.isNotBlank() &&
                credential.userId.isNotBlank() && credential.token.isNotBlank())
            joinedCredentials = credential
            protocol = RemoteProtocol(role, credential.userId, clock,
                send = { peer, frame, deadline ->
                    if (queue.trySend(Outgoing(epoch, peer, frame, deadline)).isFailure) error("操作发送繁忙，请稍后重试")
                }, execute = dispatch, changed = ::notifyStatus,
                result = { id, result -> Logger.d("Control", "message=$id status=$result") })
            val adapter = factory(listener(generationAtStart))
            session = adapter
            adapter.join(credential, role).getOrThrow()
            if (generation != generationAtStart) return
            if (role == SessionRole.HOST) {
                require(projection != null && geometry != null)
                adapter.media.startSharing(projection, geometry).getOrThrow()
                protocol?.sharing(geometry)
            }
            work = scope.launch {
                launch {
                    for (item in queue) {
                        if (item.epoch != epoch || currentState != ConnectionState.CONNECTED ||
                            (item.deadline != null && clock() >= item.deadline)) continue
                        if (item.bytes.size > adapter.control.maxFrameBytes) { error("控制消息超过传输上限"); continue }
                        val sent = withTimeoutOrNull(2000) { adapter.control.send(item.peer, item.bytes) }
                        if (sent == null || sent.isFailure) error("控制消息发送失败，未自动重试")
                        delay(50)
                    }
                }
                launch { while (isActive) { protocol?.tick(); delay(1000) } }
            }
            notifyStatus()
        } catch (cancelled: CancellationException) {
            if (generation == generationAtStart) close()
            throw cancelled
        } catch (failure: Exception) {
            if (generation != generationAtStart) return
            close()
            currentState = ConnectionState.FAILED
            notifyStatus()
            error(if (failure is MissingCredentialsException) failure.message!! else "连接失败，请检查凭证、配置和网络")
        }
    }

    fun bindRemote(view: ViewGroup) { container = view; notifyStatus() }
    fun send(command: RemoteCommand): Boolean = protocol?.peer?.id in firstFrames && protocol?.command(command) == true

    suspend fun close() {
        generation++
        epoch++
        protocol?.connection(false)
        protocol = null
        work?.cancelAndJoin()
        work = null
        refresh?.cancel()
        refresh = null
        queue.close()
        queue = Channel(32)
        firstFrames.clear()
        boundPeer = null
        joinedCredentials = null
        request = null
        val adapter = session
        session = null
        withContext(NonCancellable) { adapter?.close() }
        currentState = ConnectionState.CLOSED
        notifyStatus()
    }

    private fun notifyStatus() {
        val p = protocol
        val target = p?.peer
        if (target != null && container != null && target != boundPeer) {
            session?.media?.bindRemote(target, container!!)
            boundPeer = target
        }
        val ready = if (host) p?.connected == true && target != null
            else p?.canControl == true && target?.id in firstFrames
        status(currentState, ready, p?.geometry)
    }

    private fun listener(expected: Long) = object : CommunicationListener {
        private fun event(block: () -> Unit) { scope.launch { if (generation == expected) block() } }
        override fun onConnectionState(state: ConnectionState) = event {
            val changed = currentState != state
            currentState = state
            if (changed) {
                epoch++
                while (queue.tryReceive().isSuccess) { /* discard operations from the previous connection */ }
                firstFrames.clear()
                session?.media?.unbindRemote()
                boundPeer = null
                protocol?.connection(state == ConnectionState.CONNECTED)
            }
            notifyStatus()
        }
        override fun onPeerJoined(peer: Peer) = event { protocol?.peerJoined(peer) }
        override fun onPeerLeft(peer: Peer) = event {
            firstFrames.remove(peer.id)
            if (boundPeer == peer) { session?.media?.unbindRemote(); boundPeer = null }
            protocol?.peerLeft(peer)
        }
        override fun onRemoteVideoAvailable(peer: Peer) = event {
            protocol?.peerJoined(peer)
            // The protocol-selected peer owns both the displayed video and all commands.
            notifyStatus()
        }
        override fun onRemoteFirstFrame(peer: Peer) = event { firstFrames += peer.id; notifyStatus() }
        override fun onMessage(peer: Peer, frame: ByteArray) = event { protocol?.receive(peer, frame) }
        override fun onCredentialsExpiring() = event {
            if (refresh?.isActive == true) return@event
            val args = request ?: return@event
            val previous = joinedCredentials ?: return@event
            refresh = scope.launch {
                try {
                    val updated = withTimeoutOrNull(15000) { credentials.fetch(args.first, args.second, args.third) }
                        ?: throw IllegalStateException("Credential renewal timed out")
                    if (expected != generation) return@launch
                    require(updated.roomId == previous.roomId && updated.userId == previous.userId &&
                        updated.appId == previous.appId && updated.token.isNotBlank())
                    session?.renewCredentials(updated)?.getOrThrow()
                    joinedCredentials = updated
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    if (generation == expected) {
                        refresh = null
                        terminalFailure("凭证更新失败，请重新连接")
                    }
                }
            }
        }
        override fun onError(error: CommunicationError) = event {
            Logger.w("Communication", "operation=${error.operation} code=${error.code}")
            if (!error.recoverable) scope.launch {
                if (generation == expected) terminalFailure("通信失败：${error.operation} (${error.code})")
            } else this@SessionCoordinator.error("通信失败：${error.operation} (${error.code})")
        }
    }

    private suspend fun terminalFailure(message: String) {
        close()
        currentState = ConnectionState.FAILED
        notifyStatus()
        error(message)
    }
}
