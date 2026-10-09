package com.lumostech.autoclick

import android.content.SharedPreferences
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModelProvider
import com.lumostech.autoclick.ui.AccessibilityGateDialog
import com.lumostech.autoclick.ui.ExactTimingPermissionDialog
import com.lumostech.accessibilitybase.utils.Logger
import com.lumostech.accessibilitycore.*
import com.lumostech.autoclick.ui.EditScheduleDialog
import com.lumostech.autoclick.ui.AutoclickScreen
import com.lumostech.autoclick.ui.theme.AutoclickTheme
import com.lumostech.autoclick.databinding.LayoutConfirmBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

class MainActivity : AccessibilityActivity(), AccessibilityCoreService.OnPointLongClickListener {
    private lateinit var store: ClickTaskStore
    private lateinit var controller: ClickTaskController
    private var task by mutableStateOf<ClickTask?>(null)
    private var outcome by mutableStateOf("")
    private var presentation by mutableStateOf(ClickTaskPresentation("正在读取任务状态"))
    private var foreground by mutableStateOf(false)
    private var pointCount by mutableIntStateOf(0)
    private var busy by mutableStateOf(false)
    private var timingPrompt by mutableStateOf(false)
    private var serviceReadiness by mutableStateOf(ClickServiceReadiness.DISABLED)
    private lateinit var editViewModel: EditScheduleViewModel
    private lateinit var gateViewModel: AccessibilityGateViewModel
    private var gatePhase by mutableStateOf(AccessibilityGatePhase.CHECKING)
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshTask() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.isEnabled = BuildConfig.DEBUG
        Logger.tagPrefix = "Autoclick"
        store = ClickTaskStore(this)
        controller = ClickTaskController(this)
        gateViewModel = ViewModelProvider(this)[AccessibilityGateViewModel::class.java]
        editViewModel = ViewModelProvider(this)[EditScheduleViewModel::class.java]
        store.observe(preferenceListener)
        refreshTask()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    try {
                        ClickTaskStatusObserver(this@MainActivity).observe { current, _ ->
                            presentation = current
                            refreshTask()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.e("MainActivity", "Cannot observe scheduled task", e)
                        presentation = ClickTaskPresentation("暂无法确认下一次计划")
                    }
                }
                while (true) {
                    refreshGate()
                    if (gatePhase == AccessibilityGatePhase.READY) handleConfigureIntent()
                    delay(500)
                }
            }
        }
        ViewModelMain.recordedPointCount.observe(this) { pointCount = it }
        AccessibilityCoreService.onPointLongClickListener = this
        setContent {
            AutoclickTheme {
                val runState by ClickExecutionSession.state.collectAsState()
                var trialCandidate by remember { mutableStateOf<ClickTask?>(null) }
                LaunchedEffect(gatePhase) { if (gatePhase != AccessibilityGatePhase.READY) trialCandidate = null }
                LaunchedEffect(gatePhase, task?.id, presentation.editingBlocked, editViewModel.saving) {
                    val candidate = editViewModel.candidate
                    if (candidate != null && !editViewModel.saving &&
                        (gatePhase != AccessibilityGatePhase.READY || candidate.id != task?.id || presentation.editingBlocked)) {
                        editViewModel.dismiss("任务状态已变化，请重新打开时间设置")
                    }
                }
                LaunchedEffect(editViewModel.resultMessage) {
                    editViewModel.resultMessage?.let {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                        editViewModel.clearResult()
                        refreshTask()
                    }
                }
                if (gatePhase != AccessibilityGatePhase.READY) {
                    if (foreground) {
                        AccessibilityGateDialog(gatePhase, ::openAccessibilitySettings, ::finish)
                    }
                    return@AutoclickTheme
                }
                AutoclickScreen(
                    pointCount = pointCount,
                    task = task,
                    outcome = outcome,
                    presentation = presentation,
                    busy = busy || editViewModel.saving,
                    runState = runState,
                    serviceReadiness = serviceReadiness,
                    onAccessibilitySettings = ::openAccessibilitySettings,
                    onRecord = ::showRecording,
                    onToggleTask = { if (requireReady()) task?.let { mutateTask {
                        val enabling = !presentation.scheduledEnabled
                        controller.setEnabled(enabling)
                        if (enabling && !AndroidClickAlarmPlatform(this@MainActivity).canSchedule()) requestTimingPermission()
                    } } },
                    onTimingSettings = ::requestTimingPermission,
                    onDeleteTask = { if (requireReady()) mutateTask { controller.delete() } },
                    onTrial = { if (requireReady()) trialCandidate = task },
                    onEmergencyStop = { ClickExecutionSession.emergencyStop(this@MainActivity) },
                    onEditSchedule = {
                        if (requireReady() && !busy && !editViewModel.saving && !presentation.editingBlocked)
                            task?.let { if (it.protection != null) editViewModel.open(it) }
                    }
                )
                editViewModel.candidate?.let { candidate ->
                    EditScheduleDialog(candidate, editViewModel.saving, editViewModel.error,
                        onDismiss = { editViewModel.dismiss() },
                        onSave = { hour, minute, days ->
                            if (requireReady()) editViewModel.save(controller, hour, minute, days)
                        })
                }
                if (timingPrompt && foreground) ExactTimingPermissionDialog(
                    onSettings = { timingPrompt = false; openTimingSettings() }, onDismiss = { timingPrompt = false })
                trialCandidate?.let { candidate ->
                    AlertDialog(
                        onDismissRequest = { trialCandidate = null },
                        title = { Text("试运行保存的点击") },
                        text = { Text("会真实点击 ${candidate.points.size} 个位置。确认后有 5 秒切回目标页面，可随时用悬浮按钮停止并停用任务。\n\n试运行不消耗当天的定时次数。") },
                        confirmButton = {
                            TextButton(onClick = {
                                trialCandidate = null
                                if (!requireReady()) return@TextButton
                                if (!ClickExecutionSession.startTrial(this@MainActivity, candidate)) {
                                    refreshGate()
                                    if (gatePhase == AccessibilityGatePhase.READY)
                                        Toast.makeText(this@MainActivity, "无法开始试运行，请查看执行状态", Toast.LENGTH_LONG).show()
                                }
                            }) { Text("开始 5 秒倒计时") }
                        },
                        dismissButton = { TextButton(onClick = { trialCandidate = null }) { Text("取消") } }
                    )
                }
            }
        }
        mutateTask { (application as AutoclickApp).awaitStartup() }
        handleConfigureIntent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleConfigureIntent()
    }

    private fun handleConfigureIntent() {
        if (!requireReady()) return
        if (intent.getBooleanExtra(AccessibilityCoreService.CONFIGURE_CLICKS, false)) {
            intent.removeExtra(AccessibilityCoreService.CONFIGURE_CLICKS)
            onPointLongClick()
        }
    }

    private fun refreshTask() {
        if (!::store.isInitialized) return
        val current = store.load()
        if (current?.id != task?.id) presentation = ClickTaskPresentation("正在读取任务状态")
        task = current
        val record = store.lastExecutionResult()
        val timestamp = record?.recordedAt ?: 0L
        val message = record?.message ?: "尚无执行结果"
        outcome = message + if (timestamp > 0) "\n${DateFormat.getDateTimeInstance().format(Date(timestamp))}" else ""
    }

    private fun showRecording(reset: Boolean) {
        if (!requireReady()) return
        val service = AccessibilityCoreService.accessibilityCoreService
        if (service == null) {
            refreshGate()
            return
        }
        FloatWindowUtils.checkSuspendedWindowPermission(this) {
            service.enableProtectedRecording()
            if (reset) service.startRecording()
            ViewModelMain.isShowFloatWindow.value = true
        }
    }

    private fun mutateTask(action: suspend () -> Unit) {
        busy = true
        lifecycleScope.launch {
            try {
                action()
                refreshTask()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "操作失败：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    override fun onPointLongClick() {
        if (!requireReady()) return
        val service = AccessibilityCoreService.accessibilityCoreService ?: return
        if (service.getRecordedClickPoints().isEmpty()) {
            Toast.makeText(this, "请先轻点悬浮按钮，录制至少一个点击", Toast.LENGTH_SHORT).show()
            return
        }
        val binding = DataBindingUtil.inflate<LayoutConfirmBinding>(LayoutInflater.from(this), R.layout.layout_confirm, null, false)
        binding.timePicker.setIs24HourView(true)
        task?.let { saved ->
            binding.timePicker.hour = saved.hour
            binding.timePicker.minute = saved.minute
            binding.weekdaysPicker.setSelectedDays(saved.days.sorted())
        }
        binding.confirmEventHandler = ConfirmEventHandler(binding, lifecycleScope, controller, ::requestTimingPermission)
        val density = resources.displayMetrics.density
        val panelWidth = minOf(resources.displayMetrics.widthPixels - (32 * density).toInt(), (560 * density).toInt())
        service.setFloatCustomView(binding.root, panelWidth)
        ViewModelMain.isShowCustomFloatWindow.value = true
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        gateViewModel.gate.settingsReturned()
        refreshTask()
        refreshGate()
        pointCount = AccessibilityCoreService.accessibilityCoreService?.getRecordedClickPoints()?.size ?: ClickSequenceStore(this).load().size
    }

    override fun onPause() {
        foreground = false
        super.onPause()
    }

    private fun refreshGate() {
        serviceReadiness = ClickServiceConnection.readiness(this)
        gatePhase = gateViewModel.gate.update(serviceReadiness, SystemClock.elapsedRealtime())
    }

    private fun requireReady(): Boolean {
        refreshGate()
        return gatePhase == AccessibilityGatePhase.READY
    }

    private fun requestTimingPermission() {
        if (foreground && gatePhase == AccessibilityGatePhase.READY) timingPrompt = true
    }

    private fun openTimingSettings() {
        if (Build.VERSION.SDK_INT < 31) return
        try { startActivity(exactTimingSettingsIntent(this)) }
        catch (error: Exception) {
            Logger.e("MainActivity", "Cannot open exact timing settings", error)
            Toast.makeText(this, "请在系统设置中开启闹钟和提醒权限", Toast.LENGTH_LONG).show()
        }
    }

    private fun openAccessibilitySettings() {
        gateViewModel.gate.settingsOpened()
        gatePhase = AccessibilityGatePhase.CHECKING
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Logger.e("MainActivity", "Cannot open accessibility settings", e)
            gateViewModel.gate.settingsReturned()
            refreshGate()
            Toast.makeText(this, "无法打开系统设置，请手动进入无障碍设置", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        store.stopObserving(preferenceListener)
        if (AccessibilityCoreService.onPointLongClickListener === this) {
            if (ViewModelMain.isShowCustomFloatWindow.value == true) {
                ViewModelMain.isShowCustomFloatWindow.value = false
                ViewModelMain.isShowFloatWindow.value = true
            }
            AccessibilityCoreService.onPointLongClickListener = null
        }
        super.onDestroy()
    }
}
