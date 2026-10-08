package com.lumostech.communication.agora

import android.content.Context
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.ViewGroup
import com.lumostech.communication.*
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.ScreenCaptureParameters
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Numeric UID credentials are passed unchanged to Agora, including unsigned 32-bit UIDs. */
class AgoraCommunicationSession(context: Context, private val listener: CommunicationListener) :
    CommunicationSession, MediaTransport, ControlTransport {
    private val application = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val operations = Mutex()
    private val generation = Generation()
    private var engine: RtcEngine? = null
    private var credentials: SessionCredentials? = null
    private var role: SessionRole? = null
    private var connected = false
    private var pendingJoin: CompletableDeferred<Result<Unit>>? = null
    private var streamId: Int? = null
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var remotePeer: Peer? = null
    private var remoteContainer: ViewGroup? = null
    private var remoteView: SurfaceView? = null
    override val media: MediaTransport get() = this
    override val control: ControlTransport get() = this
    override val maxFrameBytes = 900

    override suspend fun join(credentials: SessionCredentials, role: SessionRole): Result<Unit> {
        val uid = runCatching { AgoraIdentity.uid(credentials.userId) }.getOrNull()
        if (uid == null || !credentials.appId.matches(Regex("[0-9a-fA-F]{32}")) ||
            credentials.roomId.isBlank() || credentials.token.isBlank()) return failure("join", "invalid_credentials")
        var joinEpoch = 0L
        val waiter = operations.withLock {
            if (engine != null) return failure("join", "already_joined")
            ownership.withLock {
                if (owner != null) return failure("join", "engine_in_use")
                owner = this
            }
            val epoch = generation.begin()
            joinEpoch = epoch
            val pending = CompletableDeferred<Result<Unit>>()
            pendingJoin = pending
            this.credentials = credentials
            this.role = role
            try {
                withContext(Dispatchers.Main.immediate) {
                    val config = RtcEngineConfig().apply {
                        mContext = application
                        mAppId = credentials.appId
                        mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                        mEventHandler = events(epoch)
                    }
                    engine = RtcEngine.create(config)
                    checked("enableVideo", engine!!.enableVideo())
                    checked("enableLocalVideo", engine!!.enableLocalVideo(false))
                    checked("enableLocalAudio", engine!!.enableLocalAudio(false))
                    listener.onConnectionState(ConnectionState.CONNECTING)
                    checked("join", engine!!.joinChannel(credentials.token, credentials.roomId, uid, options(false)))
                }
            } catch (error: Exception) {
                pending.complete(failure("join", (error as? SdkFailure)?.code ?: "engine_initialization"))
            }
            pending
        }
        val result = try { withTimeoutOrNull(30_000) { waiter.await() } ?: failure("join", "timeout") }
        catch (cancelled: CancellationException) {
            withContext(NonCancellable) { closeIfCurrent(joinEpoch) }
            throw cancelled
        }
        if (result.isFailure) closeIfCurrent(joinEpoch)
        return result
    }

    override suspend fun renewCredentials(credentials: SessionCredentials): Result<Unit> = operations.withLock {
        val previous = this.credentials
        if (previous == null || engine == null || previous.appId != credentials.appId ||
            previous.roomId != credentials.roomId || previous.userId != credentials.userId || credentials.token.isBlank()) {
            return failure("renewCredentials", "identity_changed_or_closed")
        }
        val code = withContext(Dispatchers.Main.immediate) { engine!!.renewToken(credentials.token) }
        if (code != 0) return failure("renewCredentials", code.toString())
        this.credentials = credentials
        Result.success(Unit)
    }

    override suspend fun close() = withContext(NonCancellable) {
        operations.withLock { closeInternal() }
    }

    private suspend fun closeIfCurrent(epoch: Long) = withContext(NonCancellable) {
        operations.withLock { if (generation.isCurrent(epoch)) closeInternal() }
    }

    private suspend fun closeInternal() {
        generation.invalidate()
        connected = false
        pendingJoin?.complete(Result.failure(IllegalStateException("join: closed")))
        pendingJoin = null
        withContext(Dispatchers.Main.immediate) {
            stopSharingInternal()
            unbindRemoteInternal()
            engine?.let { report("leave", it.leaveChannel()) }
        }
        ownership.withLock {
            if (owner === this@AgoraCommunicationSession) {
                // The synchronous destructor must never execute on an SDK callback thread.
                if (engine != null) withContext(Dispatchers.Default) { RtcEngine.destroy() }
                owner = null
            }
        }
        engine = null
        credentials = null
        role = null
        streamId = null
        withContext(Dispatchers.Main.immediate) { listener.onConnectionState(ConnectionState.CLOSED) }
    }

    override suspend fun startSharing(projection: MediaProjection, geometry: ScreenGeometry): Result<Unit> = operations.withLock {
        if (!connected || role != SessionRole.HOST) return failure("startSharing", "host_not_connected")
        if (geometry.width < 2 || geometry.height < 2) return failure("startSharing", "invalid_geometry")
        if (this.projection != null) return failure("startSharing", "already_sharing")
        withContext(Dispatchers.Main.immediate) {
            val current = engine ?: return@withContext failure("startSharing", "closed")
            val epoch = generation.current()
            val callback = object : MediaProjection.Callback() {
                override fun onStop() = dispatch(epoch) {
                    if (this@AgoraCommunicationSession.projection === projection) {
                        stopSharingInternal()
                        failure("capture", "projection_stopped")
                    }
                }
            }
            this@AgoraCommunicationSession.projection = projection
            projectionCallback = callback
            try {
                projection.registerCallback(callback, Handler(Looper.getMainLooper()))
                checked("setExternalMediaProjection", current.setExternalMediaProjection(projection))
                checked("startScreenCapture", current.startScreenCapture(ScreenCaptureParameters().apply {
                    captureVideo = true
                    captureAudio = false
                    videoCaptureParameters.width = geometry.width
                    videoCaptureParameters.height = geometry.height
                    videoCaptureParameters.framerate = 15
                }))
                checked("publishScreen", current.updateChannelMediaOptions(options(true)))
                Result.success(Unit)
            } catch (error: Exception) {
                stopSharingInternal()
                failure("startSharing", (error as? SdkFailure)?.code ?: "capture_initialization")
            }
        }
    }
    override suspend fun stopSharing() = operations.withLock {
        withContext(Dispatchers.Main.immediate) { stopSharingInternal() }
    }
    private fun stopSharingInternal() {
        if (projection == null) return
        engine?.let {
            report("unpublishScreen", it.updateChannelMediaOptions(options(false)))
            report("stopScreenCapture", it.stopScreenCapture())
            report("clearExternalMediaProjection", it.setExternalMediaProjection(null))
        }
        projectionCallback?.let { projection?.unregisterCallback(it) }
        projectionCallback = null
        projection = null
        // Agora owns its VirtualDisplay and may stop the supplied projection when capture stops.
    }
    override fun bindRemote(peer: Peer, container: ViewGroup) {
        val uid = runCatching { AgoraIdentity.uid(peer.id) }.getOrNull()
        if (uid == null) { failure("bindRemote", "invalid_peer"); return }
        val epoch = generation.current()
        scope.launch {
            if (!generation.isCurrent(epoch)) return@launch
            unbindRemoteInternal()
            val current = engine ?: return@launch
            remotePeer = peer
            remoteContainer = container
            remoteView = SurfaceView(container.context).also {
                container.addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                report("bindRemote", current.setupRemoteVideo(VideoCanvas(it, VideoCanvas.RENDER_MODE_FIT, uid)))
            }
        }
    }
    override fun unbindRemote() { scope.launch { unbindRemoteInternal() } }
    private fun unbindRemoteInternal() {
        remotePeer?.let { peer ->
            engine?.let { report("unbindRemote", it.setupRemoteVideo(VideoCanvas(null, VideoCanvas.RENDER_MODE_FIT, AgoraIdentity.uid(peer.id)))) }
        }
        remoteView?.let { remoteContainer?.removeView(it) }
        remoteView = null
        remoteContainer = null
        remotePeer = null
    }

    override suspend fun send(peer: Peer, frame: ByteArray): Result<Unit> = operations.withLock {
        if (frame.isEmpty() || frame.size > maxFrameBytes || runCatching { AgoraIdentity.uid(peer.id) }.isFailure) return failure("send", "invalid_frame_or_peer")
        val stream = streamId
        if (!connected || engine == null || stream == null) return failure("send", "not_connected")
        // RTC streams broadcast to the room; the shared protocol carries and checks the recipient.
        val code = withContext(Dispatchers.Main.immediate) { engine!!.sendStreamMessage(stream, frame) }
        if (code == 0) Result.success(Unit) else failure("send", code.toString())
    }

    private fun options(screen: Boolean) = ChannelMediaOptions().apply {
        channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
        publishCameraTrack = false
        publishMicrophoneTrack = false
        publishScreenCaptureVideo = screen
        publishScreenCaptureAudio = false
        autoSubscribeAudio = false
        autoSubscribeVideo = true
        enableAudioRecordingOrPlayout = false
    }
    private fun events(epoch: Long) = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) = dispatch(epoch) {
            if (AgoraIdentity.peerId(uid) != credentials?.userId) {
                pendingJoin?.complete(failure("join", "uid_mismatch"))
                return@dispatch
            }
            val stream = engine?.createDataStream(true, true) ?: -1
            if (stream < 0) {
                pendingJoin?.complete(failure("createDataStream", stream.toString()))
                return@dispatch
            }
            streamId = stream
            connected = true
            listener.onConnectionState(ConnectionState.CONNECTED)
            pendingJoin?.complete(Result.success(Unit))
        }
        override fun onConnectionStateChanged(state: Int, reason: Int) = dispatch(epoch) {
            val mapped = when (state) {
                Constants.CONNECTION_STATE_CONNECTING -> ConnectionState.CONNECTING
                Constants.CONNECTION_STATE_CONNECTED -> ConnectionState.CONNECTED
                Constants.CONNECTION_STATE_RECONNECTING -> ConnectionState.RECONNECTING
                Constants.CONNECTION_STATE_FAILED -> ConnectionState.FAILED
                Constants.CONNECTION_STATE_DISCONNECTED -> ConnectionState.CLOSED
                else -> return@dispatch
            }
            connected = mapped == ConnectionState.CONNECTED && streamId != null
            listener.onConnectionState(mapped)
            if (mapped == ConnectionState.FAILED) pendingJoin?.complete(failure("connection", reason.toString()))
        }
        override fun onUserJoined(uid: Int, elapsed: Int) = dispatch(epoch) { listener.onPeerJoined(Peer(AgoraIdentity.peerId(uid))) }
        override fun onUserOffline(uid: Int, reason: Int) = dispatch(epoch) { listener.onPeerLeft(Peer(AgoraIdentity.peerId(uid))) }
        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) = dispatch(epoch) {
            if (state == Constants.REMOTE_VIDEO_STATE_STARTING || state == Constants.REMOTE_VIDEO_STATE_DECODING) {
                listener.onRemoteVideoAvailable(Peer(AgoraIdentity.peerId(uid)))
            }
        }
        override fun onFirstRemoteVideoFrame(uid: Int, width: Int, height: Int, elapsed: Int) = dispatch(epoch) {
            listener.onRemoteFirstFrame(Peer(AgoraIdentity.peerId(uid)))
        }
        override fun onStreamMessage(uid: Int, streamId: Int, data: ByteArray) {
            val copy = data.copyOf()
            dispatch(epoch) { listener.onMessage(Peer(AgoraIdentity.peerId(uid)), copy) }
        }
        override fun onStreamMessageError(uid: Int, streamId: Int, error: Int, missed: Int, cached: Int) = dispatch(epoch) {
            failure("receiveMessage", error.toString())
        }
        override fun onTokenPrivilegeWillExpire(token: String) = dispatch(epoch) { listener.onCredentialsExpiring() }
        override fun onRequestToken() = dispatch(epoch) { listener.onCredentialsExpiring() }
        override fun onError(err: Int) = dispatch(epoch) { failure("sdk", err.toString()) }
        override fun onLocalVideoStateChanged(source: Constants.VideoSourceType, state: Int, error: Int) = dispatch(epoch) {
            if (source == Constants.VideoSourceType.VIDEO_SOURCE_SCREEN_PRIMARY && state == Constants.LOCAL_VIDEO_STREAM_STATE_FAILED) {
                stopSharingInternal()
                failure("capture", error.toString())
            }
        }
    }
    private fun dispatch(epoch: Long, action: () -> Unit) { scope.launch(Dispatchers.Main) { if (generation.isCurrent(epoch)) action() } }
    private fun checked(operation: String, code: Int) { if (code != 0) throw SdkFailure(operation, code.toString()) }
    private fun report(operation: String, code: Int) { if (code != 0) failure(operation, code.toString()) }
    private fun failure(operation: String, code: String): Result<Unit> {
        val epoch = generation.current()
        scope.launch(Dispatchers.Main) {
            if (generation.current() == epoch) listener.onError(CommunicationError(operation, code, code == "timeout"))
        }
        return Result.failure(IllegalStateException("$operation: $code"))
    }
    private class SdkFailure(operation: String, val code: String) : IllegalStateException("$operation: $code")
    private companion object {
        val ownership = Mutex()
        var owner: AgoraCommunicationSession? = null
    }
}
