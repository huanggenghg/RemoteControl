package com.lumostech.remotecontrol.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.Group
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.lumostech.remotecontrol.R
import com.lumostech.communication.ConnectionState
import com.lumostech.communication.ScreenGeometry
import com.lumostech.communication.SessionRole
import com.lumostech.remotecontrol.input.LocalAdbInputSession
import com.lumostech.remotecontrol.protocol.RemoteCommand
import com.lumostech.remotecontrol.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Random


class MainActivity : MediaProjectionActivity(), View.OnClickListener {
    private var fabProjection: AppCompatButton? = null
    private var fabAssist: AppCompatButton? = null
    private var tvCode: TextView? = null
    private var terminal: AppCompatButton? = null
    private var groupMain: Group? = null
    private var groupProjecting: Group? = null
    private var groupCodeInput: Group? = null
    private var toolbar: Toolbar? = null
    private var goAssist: AppCompatButton? = null
    private var tvCodeInput: EditText? = null
    private var tvCodeProjecting: TextView? = null
    private var tvWaiting: TextView? = null
    private var adbInput: LocalAdbInputSession? = null
    private var adbJob: Job? = null
    private var adbSetupDialog: AlertDialog? = null

    @SuppressLint("CutPasteId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        initCode()
        if (savedInstanceState?.getBoolean(SHOW_ADB_SETUP) == true) showAdbSetup(rememberedAdbPort())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(SHOW_ADB_SETUP, adbSetupDialog?.isShowing == true)
        super.onSaveInstanceState(outState)
    }

    override fun onCommunicationState(state: ConnectionState, ready: Boolean, geometry: ScreenGeometry?) {
        tvWaiting?.text = when {
            state == ConnectionState.FAILED -> "连接失败，请返回重试"
            state == ConnectionState.RECONNECTING -> "网络中断，正在重新连接"
            ready -> getString(R.string.remote_controlling)
            else -> getString(R.string.remote_control_waiting)
        }
        if (state == ConnectionState.FAILED) {
            closeAdbInput()
            releaseProjection()
        }
    }

    override fun onProjectionReady() { switchStatus(Status.ASSIST) }
    override fun onProjectionStopped() {
        switchStatus(Status.MAIN)
    }

    override fun dispatchHostCommand(
        command: RemoteCommand,
        geometry: ScreenGeometry,
        canExecute: () -> Boolean
    ): Boolean =
        if (LocalAdbInputSession.supported && command.action != "softInput") {
            adbInput?.submit(command, geometry, canExecute) == true
        } else {
            super.dispatchHostCommand(command, geometry, canExecute)
        }

    private fun initViews() {
        fabProjection = findViewById(R.id.projection)
        fabAssist = findViewById(R.id.assist)
        tvCode = findViewById(R.id.tv_code)
        tvCodeProjecting = findViewById(R.id.tv_code_projecting)
        terminal = findViewById(R.id.terminal)
        toolbar = findViewById(R.id.toolbar)
        goAssist = findViewById(R.id.go_assist)
        tvCodeInput = findViewById(R.id.tv_code_input)
        tvWaiting = findViewById(R.id.text_waiting)

        groupMain = findViewById(R.id.group_main)
        groupProjecting = findViewById(R.id.group_projecting)
        groupCodeInput = findViewById(R.id.group_code_input)

        fabProjection?.setOnClickListener(this)
        fabAssist?.setOnClickListener(this)
        terminal?.setOnClickListener(this)
        goAssist?.setOnClickListener(this)
        switchStatus(Status.MAIN)
        tvCodeInput?.addTextChangedListener { text ->
            goAssist?.isEnabled = text?.length == 6
        }
    }

    private fun initCode() {
        val random = Random()
        val sb = StringBuilder()
        for (i in 0..<CODE_LENGTH) {
            sb.append(random.nextInt(10))
        }
        tvCode!!.text = sb.toString()
    }

    @SuppressLint("NonConstantResourceId")
    override fun onClick(v: View) {
        if (v.id == R.id.projection) {
            if (!checkCode()) return
            if (LocalAdbInputSession.supported) {
                if (adbJob?.isActive == true || adbSetupDialog?.isShowing == true) return
                val port = rememberedAdbPort()
                if (port == null) showAdbSetup(null) else startAdbInput(port)
            } else {
                requestMediaProjection()
                showAccessibilityDialog()
            }
        } else if (v.id == R.id.assist) {
            switchStatus(Status.INPUT)
        } else if (v.id == R.id.terminal) {
            switchStatus(Status.MAIN)
        } else if (v.id == R.id.go_assist) {
            assist()
        }
    }

    private fun rememberedAdbPort(): Int? =
        getPreferences(MODE_PRIVATE).getInt(ADB_PORT, 0).takeIf { it in 1..65535 }

    private fun showAdbSetup(port: Int?, connectionFailed: Boolean = false) {
        adbSetupDialog = showAdbSetup(port, connectionFailed, ::startAdbInput)
    }

    private fun startAdbInput(port: Int) {
        val input = LocalAdbInputSession()
        adbInput = input
        fabProjection?.isEnabled = false
        fabAssist?.isEnabled = false
        adbJob = lifecycleScope.launch {
            var connected = false
            try {
                input.run(port) {
                    connected = true
                    getPreferences(MODE_PRIVATE).edit().putInt(ADB_PORT, port).apply()
                    requestMediaProjection()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Logger.w("LocalAdb", "Wireless debugging session failed", failure)
                if (adbInput !== input) return@launch
                if (connected) {
                    switchStatus(Status.MAIN)
                    Toast.makeText(this@MainActivity, R.string.adb_session_failed, Toast.LENGTH_LONG).show()
                } else {
                    showAdbSetup(port, connectionFailed = true)
                }
            } finally {
                if (adbInput === input) closeAdbInput()
            }
        }
    }

    private fun closeAdbInput() {
        adbInput = null
        adbJob?.cancel()
        adbJob = null
        fabProjection?.isEnabled = true
        fabAssist?.isEnabled = true
    }

    // 显示返回键
    private fun showBackButton() {
        toolbar?.setNavigationIcon(R.drawable.icon_back)
        toolbar!!.setNavigationOnClickListener { v: View? ->
            switchStatus(Status.MAIN)
        }
    }

    // 隐藏返回键
    private fun hideBackButton() {
        toolbar?.setNavigationIcon(null)
        toolbar?.setNavigationOnClickListener(null)
    }

    private fun switchStatus(status: Status) {
        when (status) {
            Status.MAIN -> {
                groupMain?.visibility = View.VISIBLE
                groupProjecting?.visibility = View.GONE
                groupCodeInput?.visibility = View.GONE
                hideBackButton()
                pauseCast()
            }

            Status.ASSIST -> {
                groupMain?.visibility = View.GONE
                groupProjecting?.visibility = View.VISIBLE
                tvCodeProjecting?.text = projectingCode((tvCode?.text ?: "") as String)
                groupCodeInput?.visibility = View.GONE
                showBackButton()
                startCast()
            }

            Status.INPUT -> {
                groupMain?.visibility = View.GONE
                groupProjecting?.visibility = View.GONE
                groupCodeInput?.visibility = View.VISIBLE
                showBackButton()
            }
        }
    }

    private fun projectingCode(text: String): String {
        val mid = text.length / 2
        val firstHalf = text.substring(0, mid)
        val secondHalf = text.substring(mid)
        return "$firstHalf $secondHalf"
    }

    private fun startCast() {
        if (!checkCode()) {
            return
        }

        val granted = projection ?: return
        val geometry = captureGeometry ?: return
        startSession(tvCode!!.text.toString(), SessionRole.HOST, granted, geometry, usesAdbGestures = adbInput != null)
    }

    private fun checkCode(): Boolean {
        val code = tvCode!!.text.toString()
        if (TextUtils.isEmpty(code) || code.length != CODE_LENGTH) {
            Toast.makeText(this, "协助码是六位数字，请重新输入", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun pauseCast() {
        stopSession()
        closeAdbInput()
        releaseProjection()
    }

    override fun onDestroy() {
        adbSetupDialog?.dismiss()
        closeAdbInput()
        super.onDestroy()
    }

    private fun assist() {
        val code = tvCodeInput!!.text.toString()
        if (!code.matches(Regex("[0-9]{6}"))) {
            Toast.makeText(this, "协助码必须是六位数字", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(
            this@MainActivity,
            RemoteControlActivity::class.java
        )
        intent.putExtra(RemoteControlActivity.EXTRA_CODE, code)
        startActivity(intent)
    }

    companion object {
        private const val CODE_LENGTH = 6
        private const val ADB_PORT = "local_adb_connect_port"
        private const val SHOW_ADB_SETUP = "show_local_adb_setup"
    }

    enum class Status {
        MAIN, ASSIST, INPUT
    }
}
