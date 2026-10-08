package com.lumostech.remotecontrol.communication

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-wide SDK ownership prevents competing activity engines. */
object RemoteSessions {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var active: SessionCoordinator? = null

    suspend fun activate(coordinator: SessionCoordinator) = mutex.withLock {
        if (active !== coordinator) { active?.close(); active = coordinator }
    }

    suspend fun release(coordinator: SessionCoordinator) = mutex.withLock {
        coordinator.close()
        if (active === coordinator) active = null
    }
}
