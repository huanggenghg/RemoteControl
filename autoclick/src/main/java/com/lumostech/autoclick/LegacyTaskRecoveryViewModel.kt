package com.lumostech.autoclick

import android.app.Application
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumostech.accessibilitycore.AccessibilityCoreService
import com.lumostech.accessibilitycore.ViewModelMain
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Owns a recovery operation across Activity recreation; no Activity, view or binding is retained. */
class LegacyTaskRecoveryViewModel internal constructor(application: Application,
    private val taskStore: ClickTaskStore, private val recoveryStore: LegacyTaskRecoveryStore,
    private val controller: ClickTaskController) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, ClickTaskStore(application),
        LegacyTaskRecoveryStore(application), ClickTaskController(application))

    var session by mutableStateOf<LegacyTaskRecoverySession?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var transitioning by mutableStateOf(true)
        private set
    var storageUnconfirmed by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var resultMessage by mutableStateOf<String?>(null)
        private set
    val busy: Boolean get() = saving || transitioning || storageUnconfirmed
    private val mutex = Mutex()

    init {
        viewModelScope.launch {
            try {
                (getApplication<Application>() as AutoclickApp).awaitStartup()
                mutex.withLock {
                    val saved = withContext(Dispatchers.IO) { recoveryStore.load() }
                    if (saved != null) {
                        session = saved
                        if (withContext(Dispatchers.IO) { taskStore.wasRecovered(saved.recoveryId, saved.replacementTaskId) }) complete()
                        else {
                            val paused = saved.copy(phase = LegacyRecoveryPhase.PAUSED)
                            check(withContext(Dispatchers.IO) { recoveryStore.save(paused) }) { "恢复记录未保存，请重试" }
                            session = paused
                            validateSource(withContext(Dispatchers.IO) { taskStore.load() }, paused)
                        }
                    }
                }
            } catch (failure: Exception) {
                storageUnconfirmed = failure is RecoveryStorageException
                error = failure.message ?: "保存状态未确认"
            }
            finally { transitioning = false }
        }
    }

    fun clearResult() { resultMessage = null }

    fun retryConfirmation() {
        if (!storageUnconfirmed || saving || transitioning) return
        transitioning = true
        viewModelScope.launch {
            try {
                mutex.withLock {
                    val saved = withContext(Dispatchers.IO) { taskStore.load(); recoveryStore.load() }
                    session = saved
                    if (saved != null) {
                        if (withContext(Dispatchers.IO) { taskStore.wasRecovered(saved.recoveryId, saved.replacementTaskId) }) complete()
                        else pausePersisted(saved)
                    }
                    storageUnconfirmed = false
                    error = null
                }
            } catch (failure: Exception) { error = failure.message ?: "保存状态未确认" }
            finally { transitioning = false }
        }
    }

    fun begin(source: ClickTask) = transition {
        requireReady()
        val current = withContext(Dispatchers.IO) { taskStore.load() }
        check(current?.id == source.id && current.scheduleId == source.scheduleId && current.protection == null && !current.enabled) {
            "原任务已变化，请重新进入恢复"
        }
        val fresh = LegacyTaskRecoverySession(UUID.randomUUID().toString(), source.id, source.scheduleId,
            UUID.randomUUID().toString(), source.hour, source.minute, source.days.toSet(), LegacyRecoveryPhase.RECORDING)
        check(withContext(Dispatchers.IO) { recoveryStore.save(fresh) }) { "恢复尚未开始，原任务和录制已保留" }
        session = fresh
        try {
            validateSource(withContext(Dispatchers.IO) { taskStore.load() }, fresh)
            val service = requireReady()
            check(service.beginRecordingSession(fresh.recoveryId)) { "新录制未保存，原任务和录制已保留" }
            ViewModelMain.isShowCustomFloatWindow.value = false
            ViewModelMain.isShowFloatWindow.value = true
        } catch (failure: Exception) { pausePersisted(fresh); throw failure }
    }

    fun resume(source: ClickTask) = transition {
        val current = checkNotNull(session) { "没有可继续的恢复记录" }
        validateSource(source, current)
        validateSource(withContext(Dispatchers.IO) { taskStore.load() }, current)
        val service = requireReady()
        check(service.getRecordedSnapshot().sessionId == current.recoveryId) { "录制草稿已变化，请选择重新开始" }
        val resumed = current.copy(phase = LegacyRecoveryPhase.RECORDING)
        check(withContext(Dispatchers.IO) { recoveryStore.save(resumed) }) { "恢复记录未保存，请重试" }
        session = resumed
        service.enableProtectedRecording()
        ViewModelMain.isShowFloatWindow.value = true
    }

    fun pause() = transition {
        session?.let { pausePersisted(it) }
        ViewModelMain.isShowCustomFloatWindow.value = false
        ViewModelMain.isShowFloatWindow.value = false
    }

    fun updateTime(hour: Int, minute: Int, days: Set<Int>) {
        val original = session ?: return
        val selected = days.toSet()
        if (busy || !original.copy(hour = hour, minute = minute, days = selected).isValid()) return
        viewModelScope.launch {
            mutex.withLock {
                val latest = session?.takeIf { it.recoveryId == original.recoveryId } ?: return@withLock
                if (saving) return@withLock
                val updated = latest.copy(hour = hour, minute = minute, days = selected)
                if (withContext(Dispatchers.IO) { recoveryStore.save(updated) }) session = updated
                else error = "时间草稿未保存，请重试"
            }
        }
    }

    fun save(candidate: ClickTask, recordedSessionId: String?) {
        val original = session ?: return
        if (busy) return
        val snapshot = candidate.copy(days = candidate.days.toSet(), points = candidate.points.map { it.copy() },
            protection = candidate.protection?.let { it.copy(packages = it.packages.toList()) })
        val savingSession = original.copy(hour = snapshot.hour, minute = snapshot.minute, days = snapshot.days,
            phase = LegacyRecoveryPhase.SAVING)
        if (!savingSession.isValid()) { error = "请输入有效的时间和执行星期"; return }
        saving = true
        error = null
        ViewModelMain.isShowFloatWindow.value = false
        viewModelScope.launch {
            mutex.withLock {
                try {
                    check(withContext(Dispatchers.IO) { recoveryStore.save(savingSession) }) { "恢复记录未保存，原任务已保留" }
                    session = savingSession
                    controller.saveRecoveredRecording(savingSession, snapshot, recordedSessionId)
                    complete()
                } catch (failure: Exception) {
                    withContext(NonCancellable) {
                        val committed = runCatching { withContext(Dispatchers.IO) {
                            taskStore.wasRecovered(savingSession.recoveryId, savingSession.replacementTaskId)
                        } }.getOrDefault(false)
                        if (committed) complete()
                        else {
                            runCatching { pausePersisted(savingSession) }
                            error = failure.message ?: "新任务保存未完成，原任务已保留"
                        }
                    }
                    if (failure is CancellationException) throw failure
                } finally { saving = false }
            }
        }
    }

    fun onTaskChanged(task: ClickTask?) {
        val original = session ?: return
        if (busy) return
        if (task?.id == original.sourceTaskId && task.scheduleId == original.sourceScheduleId && task.protection == null) return
        if (original.phase == LegacyRecoveryPhase.PAUSED && error != null) return
        transition {
            if (withContext(Dispatchers.IO) { taskStore.wasRecovered(original.recoveryId, original.replacementTaskId) }) complete()
            else {
                pausePersisted(original)
                ViewModelMain.isShowCustomFloatWindow.value = false
                ViewModelMain.isShowFloatWindow.value = false
                error = "原任务已变化，恢复已暂停，录制草稿已保留"
            }
        }
    }

    private fun transition(action: suspend () -> Unit) {
        if (busy) return
        transitioning = true
        error = null
        viewModelScope.launch {
            try { mutex.withLock { action() } }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = failure.message ?: "恢复未完成，请重试"
            } finally { transitioning = false }
        }
    }
    private fun requireReady(): AccessibilityCoreService {
        val app = getApplication<Application>()
        check(ClickServiceConnection.readiness(app) == ClickServiceReadiness.CONNECTED) { "无障碍服务未连接，请恢复后再操作" }
        check(Settings.canDrawOverlays(app)) { "请先开启悬浮窗权限，再返回继续恢复" }
        check(!ClickExecutionSession.state.value.active) { "任务正在准备或执行，请结束后再恢复" }
        return checkNotNull(AccessibilityCoreService.accessibilityCoreService)
    }
    private fun validateSource(task: ClickTask?, current: LegacyTaskRecoverySession) {
        check(task?.id == current.sourceTaskId && task.scheduleId == current.sourceScheduleId &&
            task.protection == null && !task.enabled) { "原任务已变化，请重新进入恢复；录制草稿已保留" }
    }
    private suspend fun pausePersisted(current: LegacyTaskRecoverySession) {
        val paused = current.copy(phase = LegacyRecoveryPhase.PAUSED)
        check(withContext(Dispatchers.IO) { recoveryStore.save(paused) }) { "恢复状态未保存，录制草稿已保留" }
        session = paused
    }
    private suspend fun complete() {
        // Once the task commit is confirmed, failure to remove a stale session cannot undo it.
        val enabled = withContext(Dispatchers.IO) {
            val current = runCatching { taskStore.load()?.enabled }.getOrNull()
            runCatching { recoveryStore.clear() }
            current
        }
        session = null
        error = null
        ViewModelMain.isShowCustomFloatWindow.value = false
        ViewModelMain.isShowFloatWindow.value = false
        resultMessage = when (enabled) {
            true -> "任务已恢复，当前任务已启用"
            false -> "任务已恢复并保持停用，请先试运行，再手动启用"
            null -> "任务已恢复，请返回查看当前状态"
        }
    }
}
