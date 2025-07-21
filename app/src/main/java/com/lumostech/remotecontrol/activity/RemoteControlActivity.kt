package com.lumostech.remotecontrol.activity

import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import com.lumostech.remotecontrol.R
import com.lumostech.remotecontrol.SoftInputUtils
import im.zego.zegoexpress.entity.ZegoCanvas
import im.zego.zegoexpress.entity.ZegoStream
import org.json.JSONObject


class RemoteControlActivity : ZegoBaseActivity(), View.OnClickListener {
    private var mRoomId: String? = ""
    private var scrollUpButton: Button? = null
    private var scrollDownButton: Button? = null
    private var softInputButton: Button? = null
    private var softInputButtonOff: Button? = null
    private var editText: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_control)
        initViews()
        createEngine()
        setEventHandler()
        mRoomId = intent.getStringExtra(EXTRA_CODE)
        loginRoom("user3", mRoomId)
        val zegoCanvas = ZegoCanvas(findViewById(R.id.remoteUserView))
        mEngine?.startPlayingStream("stream2", zegoCanvas)
    }

    private fun initViews() {
        scrollUpButton = findViewById(R.id.scrollUp)
        scrollDownButton = findViewById(R.id.scrollDown)
        softInputButton = findViewById(R.id.softInput)
        softInputButtonOff = findViewById(R.id.softInputOff)
        editText = findViewById(R.id.editText)
        scrollUpButton?.setOnClickListener(this)
        scrollDownButton?.setOnClickListener(this)
        softInputButton?.setOnClickListener(this)
        softInputButtonOff?.setOnClickListener(this)
        editText?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                Log.d("REMOTE", "afterTextChanged:")
            }

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
                Log.d("REMOTE", "beforeTextChanged:")
            }

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                Log.d("REMOTE", "onTextChanged:$s")
                sendCustomCommand(JSONObject().apply {
                    put("action", "softInput")
                    put("inputText", "$s")
                }.toString())
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        mEngine?.logoutRoom()
    }

    override fun onRoomStreamUpdate(zegoStream: ZegoStream?, playStreamId: String?) {
        Log.d("REMOTE", "onRoomStreamUpdate: playStreamId = $playStreamId")
    }

    override fun onLoginRoomSuccess() {
        Log.d("REMOTE", "onLoginRoomSuccess")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (TextUtils.isEmpty(mRoomId)) {
            Log.w("TAG", "onTouchEvent: mRoomId is empty!")
            return super.onTouchEvent(event)
        }
        sendCustomCommand(JSONObject().apply {
            put("action", event.action)
            put("x", event.x.toString())
            put("y", event.y.toString())
            put("rawX", event.rawX.toDouble())
            put("rawY", event.rawY.toDouble())
        }.toString())
        return super.onTouchEvent(event)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.scrollUp -> {
                sendCustomCommand(JSONObject().apply {
                    put("action", "scrollUp")
                }.toString())
            }

            R.id.scrollDown -> {
                sendCustomCommand(JSONObject().apply {
                    put("action", "scrollDown")
                }.toString())
            }

            R.id.softInput -> {
                editText?.let {
                    it.visibility = View.VISIBLE
                    it.requestFocus()
                    if (it.requestFocus()) {
                        SoftInputUtils.showSoftInput(it)
                    }
                }
            }

            R.id.softInputOff -> {
                editText?.visibility = View.GONE
            }
        }
    }

    private fun sendCustomCommand(command: String) {
        mEngine?.sendCustomCommand(
            mRoomId, command, null
        ) { errorCode: Int ->
            Log.d(
                "TAG",
                "sendCustomCommand: error = $errorCode"
            )
        }
    }

    companion object {
        const val EXTRA_CODE: String = "code"
    }
}