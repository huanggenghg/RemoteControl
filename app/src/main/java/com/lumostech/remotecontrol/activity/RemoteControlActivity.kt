package com.lumostech.remotecontrol.activity

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.constraintlayout.widget.Group
import com.lumostech.communication.ConnectionState
import com.lumostech.communication.ScreenGeometry
import com.lumostech.communication.SessionRole
import com.lumostech.remotecontrol.AnimUtils
import com.lumostech.remotecontrol.ImmersiveFullscreenUtil
import com.lumostech.remotecontrol.R
import com.lumostech.remotecontrol.protocol.RemoteCommand
import com.lumostech.remotecontrol.protocol.ScreenCoordinateMapper
import kotlin.math.abs


class RemoteControlActivity : CommunicationActivity(), View.OnClickListener {
    private var mRoomId: String? = ""
    private var groupMonitor: Group? = null
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

    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }
    private var touchStart: Pair<Float, Float>? = null
    private var touchDragged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_control)
        ImmersiveFullscreenUtil.enableTrueFullscreen(this)
        initViews()
        val container = findViewById<ViewGroup>(R.id.remoteUserView)
        bindRemote(container)
        container.setOnTouchListener(::handleRemoteTouch)
        mRoomId = intent.getStringExtra(EXTRA_CODE)
        if (mRoomId?.matches(Regex("[0-9]{6}")) == true) startSession(mRoomId!!, SessionRole.CONTROLLER)
        else finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 某些 ROM/场景下切回前台会丢失，需要重新应�?
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

    override fun onCommunicationState(state: ConnectionState, ready: Boolean, geometry: ScreenGeometry?) {
        if (!ready) touchStart = null
        groupMonitor?.visibility = if (ready) View.GONE else View.VISIBLE
        findViewById<android.widget.TextView>(R.id.tv_monitor).text = when (state) {
            ConnectionState.FAILED -> "连接失败，请返回后重试"
            ConnectionState.RECONNECTING -> "网络中断，正在重新连接"
            else -> "正在等待远程画面与控制授权"
        }
    }

    private fun handleRemoteTouch(view: View, event: MotionEvent): Boolean {
        val geometry = remoteGeometry
        if (!controlReady || geometry == null) {
            touchStart = null
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStart = event.x to event.y
                touchDragged = false
            }
            MotionEvent.ACTION_MOVE -> {
                val start = touchStart ?: return true
                touchDragged = touchDragged || movedPastSlop(start, event)
            }
            MotionEvent.ACTION_UP -> {
                val start = touchStart ?: return true
                touchStart = null
                val isSwipe = movedPastSlop(start, event)
                if (touchDragged && !isSwipe) return true
                val (startX, startY) = ScreenCoordinateMapper.map(start.first, start.second,
                    geometry.width, geometry.height, view.width, view.height) ?: return true
                val (endX, endY) = ScreenCoordinateMapper.map(event.x, event.y,
                    geometry.width, geometry.height, view.width, view.height, clampToContent = isSwipe) ?: return true
                if (isSwipe) {
                    val duration = (event.eventTime - event.downTime).coerceIn(1L, RemoteCommand.MAX_SWIPE_DURATION_MS)
                    sendCommand(RemoteCommand("swipe", startX / geometry.width, startY / geometry.height,
                        duration = duration, endX = endX / geometry.width, endY = endY / geometry.height))
                } else {
                    sendCommand(RemoteCommand("click", endX / geometry.width, endY / geometry.height))
                    view.performClick()
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> touchStart = null
        }
        return true
    }

    private fun movedPastSlop(start: Pair<Float, Float>, event: MotionEvent): Boolean =
        abs(event.x - start.first) > touchSlop || abs(event.y - start.second) > touchSlop

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.scrollUp -> {
                sendCommand(RemoteCommand("scrollUp"))
            }

            R.id.scrollDown -> {
                sendCommand(RemoteCommand("scrollDown"))
            }

            R.id.scrollLeft -> {
                sendCommand(RemoteCommand("scrollLeft"))
            }

            R.id.scrollRight -> {
                sendCommand(RemoteCommand("scrollRight"))
            }

            R.id.exit -> {
                finish()
            }

            R.id.back -> {
                sendCommand(RemoteCommand("back"))
            }

            R.id.home -> {
                sendCommand(RemoteCommand("home"))
            }

            R.id.recents -> {
                sendCommand(RemoteCommand("recents"))
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

    companion object {
        const val EXTRA_CODE: String = "code"
    }
}
