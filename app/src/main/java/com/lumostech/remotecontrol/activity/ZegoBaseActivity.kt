package com.lumostech.remotecontrol.activity

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import im.zego.zegoexpress.ZegoExpressEngine
import im.zego.zegoexpress.callback.IZegoEventHandler
import im.zego.zegoexpress.constants.ZegoPlayerState
import im.zego.zegoexpress.constants.ZegoPublisherState
import im.zego.zegoexpress.constants.ZegoRoomStateChangedReason
import im.zego.zegoexpress.constants.ZegoScenario
import im.zego.zegoexpress.constants.ZegoStreamQualityLevel
import im.zego.zegoexpress.constants.ZegoUpdateType
import im.zego.zegoexpress.entity.ZegoEngineProfile
import im.zego.zegoexpress.entity.ZegoRoomConfig
import im.zego.zegoexpress.entity.ZegoStream
import im.zego.zegoexpress.entity.ZegoUser
import org.json.JSONException
import org.json.JSONObject

abstract class ZegoBaseActivity : AccessibilityActivity() {
    protected var mEngine: ZegoExpressEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermission()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        hideSystemUI()
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

    protected abstract fun onRoomStreamUpdate(zegoStream: ZegoStream?, playStreamId: String?)

    protected abstract fun onLoginRoomSuccess()

    // 创建 ZegoExpress 实例，监听常用事件
    protected open fun createEngine() {
        // 创建引擎，通用场景接入，并注册 self 为 eventHandler 回调
        // 不需要注册回调的话，eventHandler 参数可以传 null，后续可调用 "setEventHandler:" 方法设置回调
        val profile = ZegoEngineProfile()
        // TODO: 2023/7/15 密钥安全 gradle + xml + string + so + ...
        profile.appID = 678281271L // 请通过官网注册获取，格式为：1234567890L
        profile.appSign =
            "a356faea31ad94234a560f61c7e4628659d27041846d7b22b239886af3e7e6a4" //请通过官网注册获取，格式为："0123456789012345678901234567890123456789012345678901234567890123"（共64个字符）
        profile.scenario = ZegoScenario.STANDARD_VIDEO_CALL // 通用场景接入
        profile.application = application
        mEngine = ZegoExpressEngine.createEngine(profile, null)
    }

    protected fun setEventHandler() {
        mEngine!!.setEventHandler(object : IZegoEventHandler() {
            // 房间内其他用户推流/停止推流时，我们会在这里收到相应用户的音视频流增减的通知
            override fun onRoomStreamUpdate(
                roomID: String,
                updateType: ZegoUpdateType,
                streamList: ArrayList<ZegoStream>,
                extendedData: JSONObject
            ) {
                super.onRoomStreamUpdate(roomID, updateType, streamList, extendedData)
                //当 updateType 为 ZegoUpdateType.ADD 时，代表有音视频流新增，此时我们可以调用 startPlayingStream 接口拉取播放该音视频流
                if (updateType == ZegoUpdateType.ADD) {
                    // 开始拉流，设置远端拉流渲染视图，视图模式采用 SDK 默认的模式，等比缩放填充整个 View
                    val stream = streamList[0]
                    val playStreamID = stream.streamID
                    this@ZegoBaseActivity.onRoomStreamUpdate(stream, playStreamID)
                }
            }

            //同一房间内的其他用户进出房间时，您可通过此回调收到通知。回调中的参数 ZegoUpdateType 为 ZegoUpdateType.ADD 时，表示有用户进入了房间；ZegoUpdateType 为 ZegoUpdateType.DELETE 时，表示有用户退出了房间。
            // 只有在登录房间 loginRoom 时传的配置 ZegoRoomConfig 中的 isUserStatusNotify 参数为 true 时，用户才能收到房间内其他用户的回调。
            // 房间人数大于 500 人的情况下 onRoomUserUpdate 回调不保证有效。若业务场景存在房间人数大于 500 的情况，请联系 ZEGO 技术支持。
            override fun onRoomUserUpdate(
                roomID: String,
                updateType: ZegoUpdateType,
                userList: ArrayList<ZegoUser>
            ) {
                super.onRoomUserUpdate(roomID, updateType, userList)
                // 您可以在回调中根据用户的进出/退出情况，处理对应的业务逻辑
                if (updateType == ZegoUpdateType.ADD) {
                    for (user in userList) {
                        val text = user.userID + "进入了房间"
                        Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
                    }
                } else if (updateType == ZegoUpdateType.DELETE) {
                    for (user in userList) {
                        val text = user.userID + "退出了房间"
                        Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
                    }
                }
            }

            // 房间连接状态改变
            override fun onRoomStateChanged(
                roomID: String,
                reason: ZegoRoomStateChangedReason,
                i: Int,
                jsonObject: JSONObject
            ) {
                super.onRoomStateChanged(roomID, reason, i, jsonObject)
                if (reason == ZegoRoomStateChangedReason.LOGINING) {
                    // 正在登录房间。当调用 [loginRoom] 登录房间或 [switchRoom] 切换到目标房间时，进入该状态，表示正在请求连接服务器。通常通过该状态进行应用界面的展示。
                } else if (reason == ZegoRoomStateChangedReason.LOGINED) {
                    //登录房间成功。当登录房间或切换房间成功后，进入该状态，表示登录房间已经成功，用户可以正常收到房间内的其他用户和所有流信息增删的回调通知。
                    //只有当房间状态是登录成功或重连成功时，推流（startPublishingStream）、拉流（startPlayingStream）才能正常收发音视频
                } else if (reason == ZegoRoomStateChangedReason.LOGIN_FAILED) {
                    //登录房间失败。当登录房间或切换房间失败后，进入该状态，表示登录房间或切换房间已经失败，例如 AppID 或 Token 不正确等。
                } else if (reason == ZegoRoomStateChangedReason.RECONNECTING) {
                    //房间连接临时中断。如果因为网络质量不佳产生的中断，SDK 会进行内部重试。
                } else if (reason == ZegoRoomStateChangedReason.RECONNECTED) {
                    //房间重新连接成功。如果因为网络质量不佳产生的中断，SDK 会进行内部重试，重连成功后进入该状态。
                } else if (reason == ZegoRoomStateChangedReason.RECONNECT_FAILED) {
                    //房间重新连接失败。如果因为网络质量不佳产生的中断，SDK 会进行内部重试，重连失败后进入该状态。
                } else if (reason == ZegoRoomStateChangedReason.KICK_OUT) {
                    //被服务器踢出房间。例如有相同用户名在其他地方登录房间导致本端被踢出房间，会进入该状态。
                } else if (reason == ZegoRoomStateChangedReason.LOGOUT) {
                    //登出房间成功。没有登录房间前默认为该状态，当调用 [logoutRoom] 登出房间成功或 [switchRoom] 内部登出当前房间成功后，进入该状态。
                } else if (reason == ZegoRoomStateChangedReason.LOGOUT_FAILED) {
                    //登出房间失败。当调用 [logoutRoom] 登出房间失败或 [switchRoom] 内部登出当前房间失败后，进入该状态。
                }
            }

            //用户推送音视频流的状态通知
            //用户推送音视频流的状态发生变更时，会收到该回调。如果网络中断导致推流异常，SDK 在重试推流的同时也会通知状态变化。
            override fun onPublisherStateUpdate(
                streamID: String,
                state: ZegoPublisherState,
                errorCode: Int,
                extendedData: JSONObject
            ) {
                super.onPublisherStateUpdate(streamID, state, errorCode, extendedData)
                if (errorCode != 0) {
                    //推流状态出错
                }
                if (state == ZegoPublisherState.PUBLISHING) {
                    //正在推流中
                } else if (state == ZegoPublisherState.NO_PUBLISH) {
                    //未推流
                } else if (state == ZegoPublisherState.PUBLISH_REQUESTING) {
                    //正在请求推流中
                }
            }

            //用户拉取音视频流的状态通知
            //用户拉取音视频流的状态发生变更时，会收到该回调。如果网络中断导致拉流异常，SDK 会自动进行重试。
            override fun onPlayerStateUpdate(
                streamID: String,
                state: ZegoPlayerState,
                errorCode: Int,
                extendedData: JSONObject
            ) {
                super.onPlayerStateUpdate(streamID, state, errorCode, extendedData)
                if (errorCode != 0) {
                    //拉流状态出错
                }
                if (state == ZegoPlayerState.PLAYING) {
                    //正在拉流中
                } else if (state == ZegoPlayerState.NO_PLAY) {
                    //未拉流
                } else if (state == ZegoPlayerState.PLAY_REQUESTING) {
                    //正在请求拉流中
                }
            }

            override fun onNetworkQuality(
                userID: String,
                zegoStreamQualityLevel: ZegoStreamQualityLevel,
                zegoStreamQualityLevel1: ZegoStreamQualityLevel
            ) {
                super.onNetworkQuality(userID, zegoStreamQualityLevel, zegoStreamQualityLevel1)
                if (userID == null) {
                    // 代表本地用户（我）的网络质量
                    //("我的上行网络质量是 %lu", (unsigned long)upstreamQuality);
                    //("我的下行网络质量是 %lu", (unsigned long)downstreamQuality);
                } else {
                    //代表房间内其他用户的网络质量
                    //("用户 %s 的上行网络质量是 %lu", userID, (unsigned long)upstreamQuality);
                    //("用户 %s 的下行网络质量是 %lu", userID, (unsigned long)downstreamQuality);
                }

                /*
                ZegoStreamQualityLevel.EXCELLENT, 网络质量极好
                ZegoStreamQualityLevel.GOOD, 网络质量好
                ZegoStreamQualityLevel.MEDIUM, 网络质量正常
                ZegoStreamQualityLevel.BAD, 网络质量差
                ZegoStreamQualityLevel.DIE, 网络异常
                ZegoStreamQualityLevel.UNKNOWN, 网络质量未知
                */
            }

            override fun onIMRecvCustomCommand(
                roomID: String,
                fromUser: ZegoUser,
                command: String
            ) {
                super.onIMRecvCustomCommand(roomID, fromUser, command)
                Log.d(
                    TAG,
                    """onIMRecvCustomCommand: roomID = $roomID ZegoUser = ${fromUser.userID} 
command = $command"""
                )
                try {
                    val jsonObject = JSONObject(command)
                    val x = jsonObject.getString("x").toFloat()
                    val y = jsonObject.getString("y").toFloat()
                    performClick(x, y)
                } catch (e: JSONException) {
                    throw RuntimeException(e)
                }
            }
        })
    }

    //登录房间
    protected fun loginRoom(userId: String?, roomId: String?) {
        // ZegoUser 的构造方法 public ZegoUser(String userID) 会将 “userName” 设为与传的参数 “userID” 一样。“userID” 与 “userName” 不能为 “null” 否则会导致登录房间失败。
        val user = ZegoUser(userId)

        val roomConfig = ZegoRoomConfig()
        //如果您使用 appsign 的方式鉴权，token 参数不需填写；如果需要使用更加安全的 鉴权方式： token 鉴权，请参考[如何从 AppSign 鉴权升级为 Token 鉴权](https://doc-zh.zego.im/faq/token_upgrade?product=ExpressVideo&platform=all)
        //roomConfig.token = ;
        // 只有传入 “isUserStatusNotify” 参数取值为 “true” 的 ZegoRoomConfig，才能收到 onRoomUserUpdate 回调。
        roomConfig.isUserStatusNotify = true

        // 登录房间
        mEngine!!.loginRoom(
            roomId, user, roomConfig
        ) { error: Int, extendedData: JSONObject? ->
            // 登录房间结果，如果仅关注登录结果，关注此回调即可
            if (error == 0) {
                // 登录成功
                onLoginRoomSuccess()
            } else {
                // 登录失败，请参考 errorCode 说明 https://doc-zh.zego.im/article/4378
                Toast.makeText(
                    this,
                    "登录失败，请参考 errorCode 说明 https://doc-zh.zego.im/article/4378",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    //请求摄像头、录音权限
    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun requestPermission() {
        val permissionNeeded = arrayOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO"
        )
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                "android.permission.CAMERA"
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                "android.permission.RECORD_AUDIO"
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(permissionNeeded, 101)
        }
    }

    companion object {
        private const val TAG = "BaseActivity"
    }
}
