package com.lumostech.remotecontrol.activity

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageButton
import androidx.constraintlayout.widget.Group
import com.lumostech.remotecontrol.ImmersiveFullscreenUtil
import com.lumostech.remotecontrol.R
import im.zego.zegoexpress.constants.ZegoOrientationMode
import im.zego.zegoexpress.constants.ZegoViewMode
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
        setContentView(R.layout.activity_remote_control2)
        ImmersiveFullscreenUtil.enableTrueFullscreen(this)
        initViews()
        createEngine()
        mEngine?.setAppOrientationMode(ZegoOrientationMode.ADAPTION)
        setEventHandler()
        mRoomId = intent.getStringExtra(EXTRA_CODE)
        Log.d("REMOTE", "onCreate:loginRoom")
        loginRoom("user3", mRoomId)
        Log.d("REMOTE", "onCreate:ZegoCanvas")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 某些 ROM/场景下切回前台会丢失，需要重新应用
        if (hasFocus) ImmersiveFullscreenUtil.enableTrueFullscreen(this)
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
        Log.d("REMOTE", "onRoomStreamUpdate: ${zegoStream?.extraInfo}")
        Log.d("REMOTE", "onRoomStreamUpdate: playStreamId = $playStreamId")
        val canvasView: View = findViewById(R.id.remoteUserView)
        val lp = canvasView.layoutParams
        lp.width = window.decorView.width
        val windowData = zegoStream?.extraInfo?.split(",")
        if (windowData?.isEmpty() == false) {
            lp.height =
                (lp.width * (windowData[1].toFloat() / windowData[0].toFloat())).toInt() + getStatusBarHeightPx(
                    this
                )
        }
        Log.d("REMOTE", "onRoomStreamUpdate: lp = ${lp.width},${lp.height}")
        canvasView.layoutParams = lp
        val zegoCanvas = ZegoCanvas(canvasView)
        zegoCanvas.viewMode = ZegoViewMode.SCALE_TO_FILL
        mEngine?.startPlayingStream("stream2", zegoCanvas)
    }

    private fun getStatusBarHeightPx(activity: Activity): Int {
        // 优先用 WindowInsets（忽略可见性，沉浸式也能拿到真实高度）
        val insets = activity.window?.decorView?.rootWindowInsets
        if (insets != null) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
            } else {
                // R 以下没有 Type.statusBars 常量的便捷获取，使用已存在的 top inset
                insets.systemWindowInsetTop
            }
        }
        // 资源兜底（大多数 ROM 提供）
        val res = activity.resources
        val resId = res.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) res.getDimensionPixelSize(resId) else 0
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