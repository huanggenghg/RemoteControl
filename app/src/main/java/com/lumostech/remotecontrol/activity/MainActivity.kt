package com.lumostech.remotecontrol.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.lumostech.remotecontrol.R
import im.zego.zegoexpress.ZegoExpressEngine
import java.util.Random

class MainActivity : MediaProjectionActivity(), View.OnClickListener {
    private var fabProjection: FloatingActionButton? = null
    private var fabAssist: FloatingActionButton? = null
    private var tvCode: TextView? = null

    @SuppressLint("CutPasteId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        initCode()
    }

    private fun initViews() {
        fabProjection = findViewById(R.id.fab_projection)
        fabAssist = findViewById(R.id.fab_assist)
        tvCode = findViewById(R.id.tv_code)
        fabProjection?.setOnClickListener(this)
        fabAssist?.setOnClickListener(this)
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
        if (v.id == R.id.fab_projection) {
            if (!checkCode()) {
                return
            }

            if (mMediaProjection == null) {
                requestMediaProjection()
                showAccessibilityDialog()
                return
            }
            showAccessibilityDialog() // 再次检查，因为可能被关闭了服务，故需要再次检查

            switchProjection()
        } else if (v.id == R.id.fab_assist) {
            assist()
        }
    }

    private fun switchProjection() {
        val switchTag = if (fabProjection!!.tag != null) fabProjection!!.tag as String else ""
        if (TextUtils.isEmpty(switchTag) || switchTag == FAB_PROJECTION_TAG_CAST) {
            fabProjection!!.setImageResource(R.drawable.cast_pause)
            fabProjection!!.tag = FAB_PROJECTION_TAG_CAST_PAUSE
            startCast()
        } else {
            fabProjection!!.setImageResource(R.drawable.cast)
            fabProjection!!.tag = FAB_PROJECTION_TAG_CAST
            pauseCast()
        }
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
        private const val FAB_PROJECTION_TAG_CAST = "cast"
        private const val FAB_PROJECTION_TAG_CAST_PAUSE = "cast_pause"
        private const val CODE_LENGTH = 6
    }
}
