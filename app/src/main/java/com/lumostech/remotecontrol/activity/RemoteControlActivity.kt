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
import com.lumostech.remotecontrol.AnimUtils
import com.lumostech.remotecontrol.ImmersiveFullscreenUtil
import com.lumostech.remotecontrol.MyApp
import com.lumostech.remotecontrol.R
import im.zego.zegoexpress.constants.ZegoOrientationMode
import im.zego.zegoexpress.constants.ZegoViewMode
import im.zego.zegoexpress.entity.ZegoCanvas
import im.zego.zegoexpress.entity.ZegoStream
import org.json.JSONObject
import java.util.UUID


class RemoteControlActivity : ZegoBaseActivity(), View.OnClickListener {
    private var mRoomId: String? = ""
    private var groupMonitor: Group? = null
    private var hasStartedPlayingStream = false
    private var scrollUpView: View? = null
    private var scrollDownView: View? = null
    private var scrollLeftView: View? = null
    private var scrollRightView: View? = null
    private var moreHorBtn: ImageButton? = null
    private var exit: View? = null
    private var back: View? = null
    private var home: View? = null
    private var recents: View? = null
    private var moreVerBtn: ImageButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_control)
        ImmersiveFullscreenUtil.enableTrueFullscreen(this)
        initViews()
        createEngine()
        mEngine?.setAppOrientationMode(ZegoOrientationMode.ADAPTION)
        setEventHandler()
        mRoomId = intent.getStringExtra(EXTRA_CODE)
        val uniqueID = UUID.randomUUID().toString()
        loginRoom(uniqueID, mRoomId)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 某些 ROM/场景下切回前台会丢失，需要重新应用
        if (hasFocus) ImmersiveFullscreenUtil.enableTrueFullscreen(this)
    }

    private fun initViews() {
        groupMonitor = findViewById(R.id.group_monitor)
        scrollUpView = findViewById(R.id.scrollUp)
        scrollDownView = findViewById(R.id.scrollDown)
        scrollLeftView = findViewById(R.id.scrollLeft)
        scrollRightView = findViewById(R.id.scrollRight)
        moreHorBtn = findViewById(R.id.more_hor)
        exit = findViewById(R.id.exit)
        back = findViewById(R.id.back)
        home = findViewById(R.id.home)
        recents = findViewById(R.id.recents)
        moreVerBtn = findViewById(R.id.more_ver)
        scrollUpView?.setOnClickListener(this)
        scrollDownView?.setOnClickListener(this)
        scrollLeftView?.setOnClickListener(this)
        scrollRightView?.setOnClickListener(this)
        moreHorBtn?.setOnClickListener(this)
        exit?.setOnClickListener(this)
        back?.setOnClickListener(this)
        home?.setOnClickListener(this)
        recents?.setOnClickListener(this)
        moreVerBtn?.setOnClickListener(this)
    }

    override fun onRoomStreamUpdate(zegoStream: ZegoStream?, playStreamId: String?) { // 应用启动只会调用一次
        Log.d("REMOTE", "onRoomStreamUpdate: ${zegoStream?.extraInfo}")
        Log.d("REMOTE", "onRoomStreamUpdate: playStreamId = $playStreamId")
        MyApp.remoteScreenAdaptedWidth = window.decorView.width
        val windowData = zegoStream?.extraInfo?.split(",")
        if (windowData?.isEmpty() == false) {
            MyApp.remoteScreenAdaptedHeight =
                (MyApp.remoteScreenAdaptedWidth * (windowData[1].toFloat() / windowData[0].toFloat())).toInt() + getStatusBarHeightPx(
                    this
                )
        }
        startPlayingStreamOnAdaptedCanvas()
    }

    private fun startPlayingStreamOnAdaptedCanvas() {
        if (hasStartedPlayingStream) {
            Log.i("REMOTE", "startPlayingStreamOnAdaptedCanvas: hasStartedPlayingStream, return.")
            return
        }
        Log.d(
            "REMOTE",
            "startPlayingStreamOnAdaptedCanvas: MyApp.remoteScreenAdaptedWidth = $MyApp.remoteScreenAdaptedWidth, MyApp.remoteScreenAdaptedHeight = $MyApp.remoteScreenAdaptedHeight"
        )
        val zegoCanvas = getScreenAdaptedCanvas()
        zegoCanvas?.let {
            mEngine?.startPlayingStream("stream2", it)
            hasStartedPlayingStream = true
        } ?: let {
            Log.w("REMOTE", "onRoomStreamUpdate: getScreenAdaptedCanvas is null!")
        }
    }

    private fun getScreenAdaptedCanvas(): ZegoCanvas? {
        if (MyApp.remoteScreenAdaptedWidth == -1 || MyApp.remoteScreenAdaptedHeight == -1) {
            Log.w(
                "REMOTE",
                "adaptScreenParams: remoteScreenWidth or remoteScreenHeight is not valid! check onRoomStreamUpdate."
            )
            return null;
        }

        val canvasView: View = findViewById(R.id.remoteUserView)
        val lp = canvasView.layoutParams
        lp.width = MyApp.remoteScreenAdaptedWidth
        lp.height = MyApp.remoteScreenAdaptedHeight
        canvasView.layoutParams = lp
        val zegoCanvas = ZegoCanvas(canvasView)
        zegoCanvas.viewMode = ZegoViewMode.SCALE_TO_FILL
        return zegoCanvas
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
        startPlayingStreamOnAdaptedCanvas()
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

            R.id.scrollLeft -> {
                sendCustomCommand(JSONObject().apply {
                    put("action", "scrollLeft")
                }.toString())
            }

            R.id.scrollRight -> {
                sendCustomCommand(JSONObject().apply {
                    put("action", "scrollRight")
                }.toString())
            }

            R.id.exit -> {
                finish()
            }

            R.id.back -> {
                sendCustomCommand(JSONObject().apply {
                    put("action", "back")
                }.toString())
            }

            R.id.home -> {
                sendCustomCommand(JSONObject().apply {
                    put("action", "home")
                }.toString())
            }

            R.id.recents -> {
                sendCustomCommand(JSONObject().apply {
                    put("action", "recents")
                }.toString())
            }

            R.id.more_hor -> {
                val isShowHor = "left" == moreHorBtn?.tag
                AnimUtils.showHorView(
                    this,
                    isShowHor,
                    {
                        if (isShowHor) {
                            moreHorBtn?.tag = "right"
                            moreHorBtn?.setImageResource(R.drawable.ic_chevron_right)
                        } else {
                            moreHorBtn?.tag = "left"
                            moreHorBtn?.setImageResource(R.drawable.ic_chevron_left)
                        }
                    },
                    moreHorBtn!!,
                    scrollUpView!!,
                    scrollDownView!!,
                    scrollLeftView!!,
                    scrollRightView!!
                )
            }

            R.id.more_ver -> {
                val isShowVer = "up" == moreVerBtn?.tag
                AnimUtils.showVerView(
                    this,
                    isShowVer,
                    {
                        if (isShowVer) {
                            moreVerBtn?.tag = "down"
                            moreVerBtn?.setImageResource(R.drawable.ic_chevron_down)
                        } else {
                            moreVerBtn?.tag = "up"
                            moreVerBtn?.setImageResource(R.drawable.ic_chevron_up)
                        }
                    },
                    moreVerBtn!!,
                    exit!!,
                    back!!,
                    home!!,
                    recents!!
                )
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