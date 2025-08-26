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
import im.zego.zegoexpress.ZegoExpressEngine
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


    @SuppressLint("CutPasteId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        initCode()
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
            if (mMediaProjection == null) {
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

        val code = tvCode!!.text.toString()

        // 创建Express SDK 实例
        createEngine()
        // 监听常用事件
        setEventHandler()
        // 登录房间
        loginRoom("user2", code)
        // 开始预览及推流
        startPublish()
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
        mEngine?.let {
            it.logoutRoom()
            ZegoExpressEngine.destroyEngine {}
        }
    }

    private fun assist() {
        if (!checkCode()) {
            return
        }

        val code = tvCode!!.text.toString()
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
