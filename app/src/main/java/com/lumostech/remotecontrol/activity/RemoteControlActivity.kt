package com.lumostech.remotecontrol.activity

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.constraintlayout.widget.Group
import com.lumostech.remotecontrol.R
import im.zego.zegoexpress.entity.ZegoCanvas
import im.zego.zegoexpress.entity.ZegoStream
import org.json.JSONObject


class RemoteControlActivity : ZegoBaseActivity(), View.OnClickListener {
    private var mRoomId: String? = ""
    private var scrollUpButton: ImageButton? = null
    private var scrollDownButton: ImageButton? = null
    private var softInputButton: ImageButton? = null
    private var softInputButtonOff: ImageButton? = null
//    private var editText: EditText? = null
    private var groupMonitor: Group? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFullScreenWithNotch()
        setContentView(R.layout.activity_remote_control2)
        initViews()
        createEngine()
        setEventHandler()
        mRoomId = intent.getStringExtra(EXTRA_CODE)
        loginRoom("user3", mRoomId)
        val zegoCanvas = ZegoCanvas(findViewById(R.id.remoteUserView))
        mEngine?.startPlayingStream("stream2", zegoCanvas, )
    }

    private fun setupFullScreenWithNotch() {
        val window = window

        // 隐藏状态栏和导航栏
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        // Android 9.0+ 刘海屏适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        // 状态栏和导航栏透明
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

    private fun hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val decorView = window.decorView
        decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE // Set the content to appear under the system bars so that the
                    // content doesn't resize when the system bars hide and show.
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN // Hide the nav bar and status bar
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    private fun initViews() {
        scrollUpButton = findViewById(R.id.scrollUp)
        scrollDownButton = findViewById(R.id.scrollDown)
        softInputButton = findViewById(R.id.softInput)
//        softInputButtonOff = findViewById(R.id.softInputOff)
//        editText = findViewById(R.id.editText)
        groupMonitor = findViewById(R.id.group_monitor)
        scrollUpButton?.setOnClickListener(this)
        scrollDownButton?.setOnClickListener(this)
        softInputButton?.setOnClickListener(this)
//        softInputButtonOff?.setOnClickListener(this)
//        editText?.addTextChangedListener(object : TextWatcher {
//            override fun afterTextChanged(s: Editable?) {
//                Log.d("REMOTE", "afterTextChanged:")
//            }
//
//            override fun beforeTextChanged(
//                s: CharSequence?,
//                start: Int,
//                count: Int,
//                after: Int
//            ) {
//                Log.d("REMOTE", "beforeTextChanged:")
//            }
//
//            override fun onTextChanged(
//                s: CharSequence?,
//                start: Int,
//                before: Int,
//                count: Int
//            ) {
//                Log.d("REMOTE", "onTextChanged:$s")
//                sendCustomCommand(JSONObject().apply {
//                    put("action", "softInput")
//                    put("inputText", "$s")
//                }.toString())
//            }
//        })
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
        sendCustomCommand(JSONObject().apply {
            put("action", "onRemoteControlLoginRoomSuccess")
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            put("windowWidth", window.decorView.width)
            put("windowHeight", window.decorView.height)
        }.toString())
    }

    override fun onPlayerPlaying() {
        super.onPlayerPlaying()
        groupMonitor?.visibility = View.GONE
    }

    private var isClicked = false;

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (TextUtils.isEmpty(mRoomId)) {
            Log.w("TAG", "onTouchEvent: mRoomId is empty!")
            return super.onTouchEvent(event)
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isClicked = true
            }

            MotionEvent.ACTION_UP -> {
                if (isClicked) {
                    sendCustomCommand(JSONObject().apply {
                        put("action", event.action)
                        put("x", event.x.toString())
                        put("y", event.y.toString())
                        put("rawX", event.rawX.toDouble())
                        put("rawY", event.rawY.toDouble())
                    }.toString())
                }
                isClicked = false
            }
        }
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
//                editText?.let {
//                    it.visibility = View.VISIBLE
//                    it.requestFocus()
//                    if (it.requestFocus()) {
//                        SoftInputUtils.showSoftInput(it)
//                    }
//                }
            }

            R.id.softInputOff -> {
//                editText?.visibility = View.GONE
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