package com.lumostech.remotecontrol.activity

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.media.projection.MediaProjection
import android.view.ViewGroup
import android.widget.Toast
import com.lumostech.accessibilitycore.AccessibilityActivity
import com.lumostech.accessibilitycore.AccessibilityCoreService
import com.lumostech.communication.*
import com.lumostech.remotecontrol.communication.*
import com.lumostech.remotecontrol.input.ScreenSwipe
import com.lumostech.remotecontrol.protocol.RemoteCommand
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class CommunicationActivity : AccessibilityActivity() {
    private var startJob: Job? = null
    private var hostGeometry: ScreenGeometry? = null
    private var coordinator: SessionCoordinator? = null
    protected var controlReady = false
        private set
    protected var remoteGeometry: ScreenGeometry? = null
        private set
    protected val remoteUsesAdbGestures: Boolean get() = coordinator?.remoteUsesAdbGestures == true
    private var renderContainer: ViewGroup? = null

    protected fun bindRemote(container: ViewGroup) {
        renderContainer = container
        coordinator?.bindRemote(container)
    }

    protected fun startSession(room: String, role: SessionRole, projection: MediaProjection? = null,
                               geometry: ScreenGeometry? = null, usesAdbGestures: Boolean = false) {
        startJob?.cancel()
        val old = coordinator
        hostGeometry = geometry
        startJob = RemoteSessions.scope.launch {
            if (old != null) RemoteSessions.release(old)
            val next = SessionCoordinator(RemoteSessions.scope,
                { listener -> CommunicationFactory.create(applicationContext, listener) },
                CommunicationFactory.credentials(), ::dispatchCommand,
                status = { state, ready, remote ->
                    if (!isDestroyed) {
                        controlReady = ready
                        remoteGeometry = remote
                        onCommunicationState(state, ready, remote)
                    }
                }, error = { message -> if (!isDestroyed) Toast.makeText(this@CommunicationActivity, message, Toast.LENGTH_LONG).show() })
            coordinator = next
            renderContainer?.let(next::bindRemote)
            RemoteSessions.activate(next)
            next.start(room, role, projection, geometry, usesAdbGestures)
        }
    }

    protected open fun onCommunicationState(state: ConnectionState, ready: Boolean, geometry: ScreenGeometry?) = Unit

    protected fun stopSession() {
        startJob?.cancel()
        startJob = null
        hostGeometry = null
        controlReady = false
        remoteGeometry = null
        val old = coordinator
        coordinator = null
        if (old != null) RemoteSessions.scope.launch { RemoteSessions.release(old) }
    }

    protected fun sendCommand(command: RemoteCommand): Boolean =
        controlReady && coordinator?.send(command) == true

    private fun dispatchCommand(command: RemoteCommand, canExecute: () -> Boolean): Boolean {
        val geometry = hostGeometry ?: return false
        return canExecute() && dispatchHostCommand(command, geometry, canExecute)
    }

    protected open fun dispatchHostCommand(
        command: RemoteCommand,
        geometry: ScreenGeometry,
        canExecute: () -> Boolean
    ): Boolean {
        if (!AccessibilityCoreService.isStart) return false
        when (command.action) {
            "click" -> {
                performClick((command.x * geometry.width).coerceAtMost((geometry.width - 1).toFloat()),
                    (command.y * geometry.height).coerceAtMost((geometry.height - 1).toFloat()))
            }
            "swipe" -> {
                val swipe = ScreenSwipe.from(command, geometry) ?: return false
                val path = Path().apply {
                    moveTo(swipe.startX.toFloat(), swipe.startY.toFloat())
                    lineTo(swipe.endX.toFloat(), swipe.endY.toFloat())
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, swipe.durationMs))
                    .build()
                return AccessibilityCoreService.accessibilityCoreService?.dispatchGesture(gesture, null, null) == true
            }
            "scrollUp" -> performScrollUp(command.distance, command.duration)
            "scrollDown" -> performScrollDown(command.distance, command.duration)
            "scrollLeft" -> performScrollLeft(command.distance, command.duration)
            "scrollRight" -> performScrollRight(command.distance, command.duration)
            "softInput" -> performSoftInput(command.inputText)
            "back" -> performBack()
            "home" -> performHome()
            "recents" -> performRecents()
            else -> return false
        }
        return true
    }

    override fun onDestroy() { stopSession(); super.onDestroy() }
}
