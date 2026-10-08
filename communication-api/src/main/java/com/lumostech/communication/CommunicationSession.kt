package com.lumostech.communication

import android.media.projection.MediaProjection
import android.view.ViewGroup

data class SessionCredentials(val appId: String, val roomId: String, val userId: String, val token: String) {
    override fun toString() = "SessionCredentials(appId=$appId, roomId=$roomId, userId=$userId, token=<redacted>)"
}
enum class SessionRole { HOST, CONTROLLER }
enum class ConnectionState { IDLE, CONNECTING, CONNECTED, RECONNECTING, FAILED, CLOSED }
data class ScreenGeometry(val width: Int, val height: Int, val rotation: Int)
data class Peer(val id: String)
data class CommunicationError(val operation: String, val code: String, val recoverable: Boolean)

interface CommunicationSession {
    val media: MediaTransport
    val control: ControlTransport
    suspend fun join(credentials: SessionCredentials, role: SessionRole): Result<Unit>
    suspend fun renewCredentials(credentials: SessionCredentials): Result<Unit>
    suspend fun close()
}
interface MediaTransport {
    suspend fun startSharing(projection: MediaProjection, geometry: ScreenGeometry): Result<Unit>
    suspend fun stopSharing()
    fun bindRemote(peer: Peer, container: ViewGroup)
    fun unbindRemote()
}
interface ControlTransport {
    val maxFrameBytes: Int
    suspend fun send(peer: Peer, frame: ByteArray): Result<Unit>
}
interface CommunicationListener {
    fun onConnectionState(state: ConnectionState)
    fun onPeerJoined(peer: Peer)
    fun onPeerLeft(peer: Peer)
    fun onRemoteVideoAvailable(peer: Peer)
    fun onRemoteFirstFrame(peer: Peer)
    fun onMessage(peer: Peer, frame: ByteArray)
    fun onCredentialsExpiring()
    fun onError(error: CommunicationError)
}
