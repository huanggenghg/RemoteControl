package com.lumostech.communication.zego

import android.app.Application
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import com.lumostech.communication.*
import im.zego.zegoexpress.ZegoExpressEngine
import im.zego.zegoexpress.callback.IZegoCustomVideoCaptureHandler
import im.zego.zegoexpress.callback.IZegoEventHandler
import im.zego.zegoexpress.constants.*
import im.zego.zegoexpress.entity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** One session owns the process-wide ZEGO engine until destruction has completed. */
class ZegoCommunicationSession(context: Context, private val listener: CommunicationListener) :
    CommunicationSession, MediaTransport, ControlTransport {
    private val application = context.applicationContext as Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val operations = Mutex()
    private val generation = Generation()
    private var engine: ZegoExpressEngine? = null
    private var captureDevices: CaptureDevices? = null
    private var credentials: SessionCredentials? = null
    private var role: SessionRole? = null
    private var pendingJoin: CompletableDeferred<Result<Unit>>? = null
    private var connected = false
    private val streams = mutableMapOf<String, Peer>()
    private var remotePeer: Peer? = null
    private var remoteContainer: ViewGroup? = null
    private var remoteView: TextureView? = null
    private var playingStream: String? = null
    private var capture: ScreenCapture? = null
    override val media: MediaTransport get() = this
    override val control: ControlTransport get() = this
    override val maxFrameBytes = 900

    override suspend fun join(credentials: SessionCredentials, role: SessionRole): Result<Unit> {
        val appId = credentials.appId.toLongOrNull()
        if (appId == null || appId <= 0 || credentials.roomId.isBlank() ||
            credentials.userId.isBlank() || credentials.token.isBlank()) return failure("join", "invalid_credentials")
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
                    val profile = ZegoEngineProfile().apply {
                        appID = appId
                        scenario = ZegoScenario.HIGH_QUALITY_VIDEO_CALL
                        application = this@ZegoCommunicationSession.application
                    }
                    engine = ZegoExpressEngine.createEngine(profile, events(epoch))
                    val createdEngine = engine!!
                    captureDevices = CaptureDevices(
                        enableVideo = { createdEngine.enableCamera(it) },
                        enableAudioCapture = { createdEngine.enableAudioCaptureDevice(it) },
                        enableCustomCapture = { enabled ->
                            createdEngine.enableCustomVideoCapture(enabled, if (enabled) ZegoCustomVideoCaptureConfig().apply {
                                bufferType = ZegoVideoBufferType.SURFACE_TEXTURE
                            } else null, ZegoPublishChannel.MAIN)
                        }
                    ).also { it.initialize() }
                    engine!!.muteMicrophone(true)
                    engine!!.mutePublishStreamAudio(true)
                    listener.onConnectionState(ConnectionState.CONNECTING)
                    engine!!.loginRoom(credentials.roomId, ZegoUser(credentials.userId), ZegoRoomConfig().apply {
                        token = credentials.token
                        isUserStatusNotify = true
                    }) { code, _ -> dispatch(epoch) {
                        if (code == 0) {
                            connected = true
                            listener.onConnectionState(ConnectionState.CONNECTED)
                            pending.complete(Result.success(Unit))
                        } else {
                            listener.onConnectionState(ConnectionState.FAILED)
                            pending.complete(failure("join", code.toString()))
                        }
                    } }
                }
            } catch (_: Exception) {
                pending.complete(failure("join", "engine_initialization"))
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
        withContext(Dispatchers.Main.immediate) { engine!!.renewToken(credentials.roomId, credentials.token) }
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
            streams.clear()
            engine?.setEventHandler(null)
            credentials?.let { engine?.logoutRoom(it.roomId) }
        }
        ownership.withLock {
            if (owner === this@ZegoCommunicationSession) {
                if (engine != null) withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        ZegoExpressEngine.destroyEngine { continuation.resumeWith(Result.success(Unit)) }
                    }
                }
                owner = null
            }
        }
        engine = null
        captureDevices = null
        credentials = null
        role = null
        withContext(Dispatchers.Main.immediate) { listener.onConnectionState(ConnectionState.CLOSED) }
    }

    override suspend fun startSharing(projection: MediaProjection, geometry: ScreenGeometry): Result<Unit> = operations.withLock {
        if (!connected || role != SessionRole.HOST) return failure("startSharing", "host_not_connected")
        if (geometry.width < 2 || geometry.height < 2) return failure("startSharing", "invalid_geometry")
        if (capture != null) return failure("startSharing", "already_sharing")
        withContext(Dispatchers.Main.immediate) {
            val current = engine ?: return@withContext failure("startSharing", "closed")
            val screen = ScreenCapture(projection, geometry, current, generation.current())
            capture = screen
            try {
                screen.register()
                current.videoConfig = ZegoVideoConfig(ZegoVideoConfigPreset.PRESET_1080P).apply {
                    captureWidth = geometry.width / 2 * 2
                    captureHeight = geometry.height / 2 * 2
                    encodeWidth = captureWidth
                    encodeHeight = captureHeight
                }
                current.setCustomVideoCaptureHandler(screen)
                captureDevices!!.start()
                current.startPublishingStream("screen_${credentials!!.roomId}_${credentials!!.userId}")
                Result.success(Unit)
            } catch (_: Exception) {
                stopSharingInternal()
                failure("startSharing", "capture_initialization")
            }
        }
    }

    override suspend fun stopSharing() = operations.withLock {
        withContext(Dispatchers.Main.immediate) { stopSharingInternal() }
    }
    private fun stopSharingInternal() {
        capture?.release()
        capture = null
        engine?.stopPublishingStream()
        captureDevices?.stop()
        engine?.setCustomVideoCaptureHandler(null)
    }

    override fun bindRemote(peer: Peer, container: ViewGroup) {
        val epoch = generation.current()
        scope.launch {
            if (!generation.isCurrent(epoch)) return@launch
            unbindRemoteInternal()
            remotePeer = peer
            remoteContainer = container
            remoteView = TextureView(container.context).also {
                container.addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            playBoundStream()
        }
    }
    override fun unbindRemote() { scope.launch { unbindRemoteInternal() } }
    private fun unbindRemoteInternal() {
        playingStream?.let { engine?.stopPlayingStream(it) }
        playingStream = null
        remoteView?.let { remoteContainer?.removeView(it) }
        remoteView = null
        remoteContainer = null
        remotePeer = null
    }
    private fun playBoundStream() {
        val stream = streams.entries.firstOrNull { it.value == remotePeer }?.key ?: return
        if (playingStream == stream) return
        playingStream?.let { engine?.stopPlayingStream(it) }
        remoteView?.let { engine?.startPlayingStream(stream, ZegoCanvas(it)) }
        playingStream = stream
    }

    override suspend fun send(peer: Peer, frame: ByteArray): Result<Unit> {
        val command = frame.toString(Charsets.UTF_8)
        if (frame.isEmpty() || frame.size > maxFrameBytes || !command.toByteArray(Charsets.UTF_8).contentEquals(frame)) {
            return failure("send", "invalid_frame")
        }
        val pending = CompletableDeferred<Result<Unit>>()
        operations.withLock {
            if (!connected || engine == null) return failure("send", "not_connected")
            val epoch = generation.current()
            withContext(Dispatchers.Main.immediate) {
                engine!!.sendCustomCommand(credentials!!.roomId, command, arrayListOf(ZegoUser(peer.id))) { code ->
                    if (generation.isCurrent(epoch)) pending.complete(if (code == 0) Result.success(Unit) else failure("send", code.toString()))
                    else pending.complete(Result.failure(IllegalStateException("send: closed")))
                }
            }
        }
        return withTimeoutOrNull(5_000) { pending.await() } ?: failure("send", "timeout")
    }

    private fun events(epoch: Long) = object : IZegoEventHandler() {
        override fun onRoomStateChanged(roomID: String, reason: ZegoRoomStateChangedReason, errorCode: Int, extendedData: JSONObject) = dispatch(epoch) {
            val state = when (reason) {
                ZegoRoomStateChangedReason.LOGINING -> ConnectionState.CONNECTING
                ZegoRoomStateChangedReason.LOGINED, ZegoRoomStateChangedReason.RECONNECTED -> ConnectionState.CONNECTED
                ZegoRoomStateChangedReason.RECONNECTING -> ConnectionState.RECONNECTING
                ZegoRoomStateChangedReason.LOGIN_FAILED, ZegoRoomStateChangedReason.RECONNECT_FAILED,
                ZegoRoomStateChangedReason.KICK_OUT -> ConnectionState.FAILED
                else -> return@dispatch
            }
            connected = state == ConnectionState.CONNECTED
            listener.onConnectionState(state)
            if (state == ConnectionState.FAILED) {
                val result = failure("connection", errorCode.toString())
                pendingJoin?.complete(result)
            }
        }
        override fun onRoomUserUpdate(roomID: String, updateType: ZegoUpdateType, userList: ArrayList<ZegoUser>) = dispatch(epoch) {
            userList.forEach {
                val peer = Peer(it.userID)
                if (updateType == ZegoUpdateType.ADD) listener.onPeerJoined(peer) else listener.onPeerLeft(peer)
            }
        }
        override fun onRoomStreamUpdate(roomID: String, updateType: ZegoUpdateType, streamList: ArrayList<ZegoStream>, extendedData: JSONObject) = dispatch(epoch) {
            streamList.forEach {
                if (updateType == ZegoUpdateType.ADD) {
                    val peer = Peer(it.user.userID)
                    streams[it.streamID] = peer
                    listener.onRemoteVideoAvailable(peer)
                } else {
                    streams.remove(it.streamID)
                    if (playingStream == it.streamID) {
                        engine?.stopPlayingStream(it.streamID)
                        playingStream = null
                    }
                }
            }
            playBoundStream()
        }
        override fun onPlayerRenderVideoFirstFrame(streamID: String) = dispatch(epoch) {
            streams[streamID]?.let { listener.onRemoteFirstFrame(it) }
        }
        override fun onRoomTokenWillExpire(roomID: String, remainTimeInSecond: Int) = dispatch(epoch) { listener.onCredentialsExpiring() }
        override fun onIMRecvCustomCommand(roomID: String, fromUser: ZegoUser, command: String) = dispatch(epoch) {
            listener.onMessage(Peer(fromUser.userID), command.toByteArray(Charsets.UTF_8))
        }
        override fun onPublisherStateUpdate(streamID: String, state: ZegoPublisherState, errorCode: Int, extendedData: JSONObject) = dispatch(epoch) {
            if (errorCode != 0) {
                stopSharingInternal()
                failure("publish", errorCode.toString())
            }
        }
        override fun onPlayerStateUpdate(streamID: String, state: ZegoPlayerState, errorCode: Int, extendedData: JSONObject) = dispatch(epoch) {
            if (errorCode != 0) failure("play", errorCode.toString())
        }
    }
    private fun dispatch(epoch: Long, action: () -> Unit) { scope.launch(Dispatchers.Main) { if (generation.isCurrent(epoch)) action() } }
    private fun failure(operation: String, code: String): Result<Unit> {
        val epoch = generation.current()
        scope.launch(Dispatchers.Main) {
            if (generation.current() == epoch) listener.onError(CommunicationError(operation, code, code == "timeout"))
        }
        return Result.failure(IllegalStateException("$operation: $code"))
    }

    private inner class ScreenCapture(
        private val projection: MediaProjection,
        private val geometry: ScreenGeometry,
        private val captureEngine: ZegoExpressEngine,
        private val epoch: Long
    ) : IZegoCustomVideoCaptureHandler() {
        private var display: VirtualDisplay? = null
        private var surface: Surface? = null
        private val callback = object : MediaProjection.Callback() {
            override fun onStop() = dispatch(epoch) {
                if (capture === this@ScreenCapture) {
                    stopSharingInternal()
                    failure("capture", "projection_stopped")
                }
            }
        }
        fun register() { projection.registerCallback(callback, Handler(Looper.getMainLooper())) }
        override fun onStart(channel: ZegoPublishChannel) = dispatch(epoch) {
            if (capture !== this@ScreenCapture || display != null) return@dispatch
            try {
                val texture = captureEngine.customVideoCaptureSurfaceTexture
                texture.setDefaultBufferSize(geometry.width, geometry.height)
                surface = Surface(texture)
                display = projection.createVirtualDisplay("RemoteControl", geometry.width, geometry.height,
                    application.resources.displayMetrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface, null, Handler(Looper.getMainLooper()))
            } catch (_: Exception) {
                stopSharingInternal()
                failure("capture", "virtual_display")
            }
        }
        override fun onStop(channel: ZegoPublishChannel) = dispatch(epoch) { release() }
        fun release() {
            display?.release()
            display = null
            surface?.release()
            surface = null
            projection.unregisterCallback(callback)
        }
    }
    private companion object {
        val ownership = Mutex()
        var owner: ZegoCommunicationSession? = null
    }
}
