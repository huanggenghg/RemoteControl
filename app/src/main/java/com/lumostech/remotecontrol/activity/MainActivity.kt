package com.lumostech.remotecontrol.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.Group
import androidx.core.widget.addTextChangedListener
import com.lumostech.remotecontrol.R
import com.lumostech.communication.ConnectionState
import com.lumostech.communication.ScreenGeometry
import com.lumostech.communication.SessionRole
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

    @SuppressLint("CutPasteId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        initCode()
    }

    override fun onCommunicationState(state: ConnectionState, ready: Boolean, geometry: ScreenGeometry?) {
        tvWaiting?.text = when {
            state == ConnectionState.FAILED -> "连接失败，请返回重试"
            state == ConnectionState.RECONNECTING -> "网络中断，正在重新连接"
            ready -> getString(R.string.remote_controlling)
            else -> getString(R.string.remote_control_waiting)
        }
        if (state == ConnectionState.FAILED) releaseProjection()
    }

    override fun onProjectionReady() { switchStatus(Status.ASSIST) }
    override fun onProjectionStopped() {
        stopSession()
        releaseProjection()
        switchStatus(Status.MAIN)
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
            if (!checkCode()) {
                return
            }
            if (projection == null) {
                requestMediaProjection()
                showAccessibilityDialog()
                return
            }
            showAccessibilityDialog() // 再次检查，因为可能被关闭了服务，故需要再次检查
            switchStatus(Status.ASSIST)
        } else if (v.id == R.id.assist) {
            switchStatus(Status.INPUT)
        } else if (v.id == R.id.terminal) {
            switchStatus(Status.MAIN)
        } else if (v.id == R.id.go_assist) {
            assist()
        }
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
        startSession(tvCode!!.text.toString(), SessionRole.HOST, granted, geometry)
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
        releaseProjection()
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
    }

    enum class Status {
        MAIN, ASSIST, INPUT
    }
}
