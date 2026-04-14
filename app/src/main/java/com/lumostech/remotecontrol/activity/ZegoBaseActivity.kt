package com.lumostech.remotecontrol.activity

import com.lumostech.remotecontrol.utils.Logger

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.lumostech.accessibilitycore.AccessibilityActivity
import com.lumostech.accessibilitycore.ClickPoint
import com.lumostech.remotecontrol.SoftInputUtils
import com.lumostech.remotecontrol.api.ZegoTokenViewModel
import im.zego.zegoexpress.ZegoExpressEngine
import im.zego.zegoexpress.callback.IZegoEventHandler
import im.zego.zegoexpress.constants.ZegoOrientationMode
import im.zego.zegoexpress.constants.ZegoPlayerState
import im.zego.zegoexpress.constants.ZegoPublisherState
import im.zego.zegoexpress.constants.ZegoRoomStateChangedReason
import im.zego.zegoexpress.constants.ZegoScenario
import im.zego.zegoexpress.constants.ZegoStreamQualityLevel
import im.zego.zegoexpress.constants.ZegoUpdateType
import im.zego.zegoexpress.constants.ZegoVideoConfigPreset
import im.zego.zegoexpress.entity.ZegoEngineProfile
import im.zego.zegoexpress.entity.ZegoRoomConfig
import im.zego.zegoexpress.entity.ZegoStream
import im.zego.zegoexpress.entity.ZegoUser
import im.zego.zegoexpress.entity.ZegoVideoConfig
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

abstract class ZegoBaseActivity : AccessibilityActivity() {
    protected var loginUserId: String? = null
    protected val engine: ZegoExpressEngine by lazy {
        // 创建引擎，通用场景接入，并注册 self �?eventHandler 回调
        // 不需要注册回调的话，eventHandler 参数可以�?null，后续可调用 "setEventHandler:" 方法设置回调
        val profile = ZegoEngineProfile()
        profile.appID = 678281271L
        profile.scenario = ZegoScenario.HIGH_QUALITY_VIDEO_CALL // 通用场景接入
        profile.application = application
        ZegoExpressEngine.createEngine(profile, null).apply {
            videoConfig = ZegoVideoConfig(ZegoVideoConfigPreset.PRESET_1080P)
            setAppOrientationMode(ZegoOrientationMode.FIXED_RESOLUTION_RATIO)
        }
    }
    private val viewModel by lazy { ViewModelProvider(this)[ZegoTokenViewModel::class.java] }
    protected var isToLoginRoom: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.logoutRoom()
        ZegoExpressEngine.destroyEngine {}
    }

    protected open fun onRoomStreamUpdate(zegoStream: ZegoStream?, playStreamId: String?) {
        Logger.d(TAG, "onRoomStreamUpdate: ${zegoStream?.extraInfo}")
    }

    protected abstract fun onLoginRoomSuccess()

    protected open fun onPlayerPlaying() {
        Logger.d(TAG, "onPlayerPlaying")
    }

    protected open fun onRoomUserUpdate(userId: String, updateType: ZegoUpdateType) {
        Logger.d(TAG, "onRoomUserUpdate: userId=$userId updateType=$updateType")
    }

    protected fun setEventHandler() {
        engine.setEventHandler(null)
        engine.setEventHandler(object : IZegoEventHandler() {
            // 房间内其他用户推�?停止推流时，我们会在这里收到相应用户的音视频流增减的通知
            override fun onRoomStreamUpdate(
                roomID: String,
                updateType: ZegoUpdateType,
                streamList: ArrayList<ZegoStream>,
                extendedData: JSONObject
            ) {
                super.onRoomStreamUpdate(roomID, updateType, streamList, extendedData)
                //�?updateType �?ZegoUpdateType.ADD 时，代表有音视频流新增，此时我们可以调用 startPlayingStream 接口拉取播放该音视频�?
                if (updateType == ZegoUpdateType.ADD) {
                    // 开始拉流，设置远端拉流渲染视图，视图模式采�?SDK 默认的模式，等比缩放填充整个 View
                    val stream = streamList[0]
                    val playStreamID = stream.streamID
                    this@ZegoBaseActivity.onRoomStreamUpdate(stream, playStreamID)
                }
            }

            //同一房间内的其他用户进出房间时，您可通过此回调收到通知。回调中的参�?ZegoUpdateType �?ZegoUpdateType.ADD 时，表示有用户进入了房间；ZegoUpdateType �?ZegoUpdateType.DELETE 时，表示有用户退出了房间�?
            // 只有在登录房�?loginRoom 时传的配�?ZegoRoomConfig 中的 isUserStatusNotify 参数�?true 时，用户才能收到房间内其他用户的回调�?
            // 房间人数大于 500 人的情况�?onRoomUserUpdate 回调不保证有效。若业务场景存在房间人数大于 500 的情况，请联�?ZEGO 技术支持�?
            override fun onRoomUserUpdate(
                roomID: String,
                updateType: ZegoUpdateType,
                userList: ArrayList<ZegoUser>
            ) {
                super.onRoomUserUpdate(roomID, updateType, userList)
                // 您可以在回调中根据用户的进出/退出情况，处理对应的业务逻辑
                if (updateType == ZegoUpdateType.ADD) {
                    for (user in userList) {
                        onRoomUserUpdate(user.userID, updateType)
                    }
                } else if (updateType == ZegoUpdateType.DELETE) {
                    for (user in userList) {
                        onRoomUserUpdate(user.userID, updateType)
                    }
                }
            }

            // 房间连接状态改�?
            override fun onRoomStateChanged(
                roomID: String,
                reason: ZegoRoomStateChangedReason,
                i: Int,
                jsonObject: JSONObject
            ) {
                super.onRoomStateChanged(roomID, reason, i, jsonObject)
                Logger.i(TAG, "onRoomStateChanged: roomID=$roomID reason=$reason errorCode=$i jsonObject=$jsonObject")
                if (reason == ZegoRoomStateChangedReason.LOGINING) {
                    // 正在登录房间。当调用 [loginRoom] 登录房间�?[switchRoom] 切换到目标房间时，进入该状态，表示正在请求连接服务器。通常通过该状态进行应用界面的展示�?
                } else if (reason == ZegoRoomStateChangedReason.LOGINED) {
                    //登录房间成功。当登录房间或切换房间成功后，进入该状态，表示登录房间已经成功，用户可以正常收到房间内的其他用户和所有流信息增删的回调通知�?
                    //只有当房间状态是登录成功或重连成功时，推流（startPublishingStream）、拉流（startPlayingStream）才能正常收发音视频
                } else if (reason == ZegoRoomStateChangedReason.LOGIN_FAILED) {
                    //登录房间失败。当登录房间或切换房间失败后，进入该状态，表示登录房间或切换房间已经失败，例如 AppID �?Token 不正确等�?
                } else if (reason == ZegoRoomStateChangedReason.RECONNECTING) {
                    //房间连接临时中断。如果因为网络质量不佳产生的中断，SDK 会进行内部重试�?
                } else if (reason == ZegoRoomStateChangedReason.RECONNECTED) {
                    //房间重新连接成功。如果因为网络质量不佳产生的中断，SDK 会进行内部重试，重连成功后进入该状态�?
                } else if (reason == ZegoRoomStateChangedReason.RECONNECT_FAILED) {
                    //房间重新连接失败。如果因为网络质量不佳产生的中断，SDK 会进行内部重试，重连失败后进入该状态�?
                } else if (reason == ZegoRoomStateChangedReason.KICK_OUT) {
                    //被服务器踢出房间。例如有相同用户名在其他地方登录房间导致本端被踢出房间，会进入该状态�?
                } else if (reason == ZegoRoomStateChangedReason.LOGOUT) {
                    //登出房间成功。没有登录房间前默认为该状态，当调�?[logoutRoom] 登出房间成功�?[switchRoom] 内部登出当前房间成功后，进入该状态�?
                } else if (reason == ZegoRoomStateChangedReason.LOGOUT_FAILED) {
                    //登出房间失败。当调用 [logoutRoom] 登出房间失败�?[switchRoom] 内部登出当前房间失败后，进入该状态�?
                }
            }

            //用户推送音视频流的状态通知
            //用户推送音视频流的状态发生变更时，会收到该回调。如果网络中断导致推流异常，SDK 在重试推流的同时也会通知状态变化�?
            override fun onPublisherStateUpdate(
                streamID: String,
                state: ZegoPublisherState,
                errorCode: Int,
                extendedData: JSONObject
            ) {
                super.onPublisherStateUpdate(streamID, state, errorCode, extendedData)
                if (errorCode != 0) {
                    //推流状态出�?
                }
                if (state == ZegoPublisherState.PUBLISHING) {
                    //正在推流�?
                } else if (state == ZegoPublisherState.NO_PUBLISH) {
                    //未推�?
                } else if (state == ZegoPublisherState.PUBLISH_REQUESTING) {
                    //正在请求推流�?
                }
            }

            //用户拉取音视频流的状态通知
            //用户拉取音视频流的状态发生变更时，会收到该回调。如果网络中断导致拉流异常，SDK 会自动进行重试�?
            override fun onPlayerStateUpdate(
                streamID: String,
                state: ZegoPlayerState,
                errorCode: Int,
                extendedData: JSONObject
            ) {
                super.onPlayerStateUpdate(streamID, state, errorCode, extendedData)
                Logger.i(TAG, "onPlayerStateUpdate: $streamID $state $errorCode $extendedData")
                if (errorCode != 0) {
                    //拉流状态出�?
                }
                if (state == ZegoPlayerState.PLAYING) {
                    //正在拉流�?
                    onPlayerPlaying()
                } else if (state == ZegoPlayerState.NO_PLAY) {
                    //未拉�?
                } else if (state == ZegoPlayerState.PLAY_REQUESTING) {
                    //正在请求拉流�?
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
                    //("我的上行网络质量�?%lu", (unsigned long)upstreamQuality);
                    //("我的下行网络质量�?%lu", (unsigned long)downstreamQuality);
                } else {
                    //代表房间内其他用户的网络质量
                    //("用户 %s 的上行网络质量是 %lu", userID, (unsigned long)upstreamQuality);
                    //("用户 %s 的下行网络质量是 %lu", userID, (unsigned long)downstreamQuality);
                }

                /*
                ZegoStreamQualityLevel.EXCELLENT, 网络质量极好
                ZegoStreamQualityLevel.GOOD, 网络质量�?
                ZegoStreamQualityLevel.MEDIUM, 网络质量正常
                ZegoStreamQualityLevel.BAD, 网络质量�?
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
                Logger.d(
                    TAG,
                    """onIMRecvCustomCommand: roomID = $roomID ZegoUser = ${fromUser.userID} 
command = $command"""
                )
                try {
                    val jsonObject = JSONObject(command)
                    when (jsonObject.getString("action")) {
                        "scrollUp" -> {
                            val distance = jsonObject.optDouble("distance", 200.0).toFloat()
                            val duration = jsonObject.optLong("duration", 300L)
                            performScrollUp(distance, duration)
                        }

                        "scrollDown" -> {
                            val distance = jsonObject.optDouble("distance", 200.0).toFloat()
                            val duration = jsonObject.optLong("duration", 300L)
                            performScrollDown(distance, duration)
                        }

                        "scrollLeft" -> {
                            val distance = jsonObject.optDouble("distance", 200.0).toFloat()
                            val duration = jsonObject.optLong("duration", 300L)
                            performScrollLeft(distance, duration)
                        }

                        "scrollRight" -> {
                            val distance = jsonObject.optDouble("distance", 200.0).toFloat()
                            val duration = jsonObject.optLong("duration", 300L)
                            performScrollRight(distance, duration)
                        }

                        "softInput" -> {
                            val x = jsonObject.getString("inputText")
                            performSoftInput(x)
                        }

                        "back" -> {
                            performBack()
                        }

                        "home" -> {
                            performHome()
                        }

                        "recents" -> {
                            performRecents()
                        }

                        "onRemoteControlLoginRoomSuccess" -> {
                            val remoteWindowWidth = jsonObject.getInt("windowWidth")
                            val remoteWindowHeight = jsonObject.getInt("windowHeight")
                            SoftInputUtils.targetDim.width = remoteWindowWidth
                            SoftInputUtils.targetDim.height = remoteWindowHeight
                        }

                        else -> {
                            val x = jsonObject.getString("x").toFloat()
                            val y = jsonObject.getString("y").toFloat()
                            val clickOnTarget = ClickPoint(x, y)
                            val sourceDimens = SoftInputUtils.ScreenDimensions(
                                window.decorView.width,
                                window.decorView.height
                            )
                            val sourceClickPoint = SoftInputUtils.mapCoordinatesFromTargetToSource(
                                clickOnTarget,
                                sourceDimens
                            )
                            sourceClickPoint?.let {
                                performClick(it.x, it.y)
                            }
                        }
                    }
                } catch (e: JSONException) {
                    throw RuntimeException(e)
                }
            }

            override fun onRoomTokenWillExpire(roomID: String?, remainTimeInSecond: Int) {
                super.onRoomTokenWillExpire(roomID, remainTimeInSecond)
                Logger.i(TAG, "onRoomTokenWillExpire: roomID=$roomID")
                notNull(loginUserId, roomID) {
                    isToLoginRoom = false // 设置获取 token 后的更新动作
                    viewModel.getZegoToken(loginUserId!!, roomID!!)
                }
            }
        })
    }

    //登录房间
    protected fun loginRoom(userId: String?, roomId: String?) {
        notNull(userId, roomId) {
            // 先设�?token 监听回调
            collectToken(userId, roomId)
            // 触发获取 zego token
            viewModel.getZegoToken(userId!!, roomId!!)
        }
    }

    private fun collectToken(userId: String?, roomId: String?) {
        // 监听 zogo token 获取状�?
        lifecycleScope.launch {
            viewModel.zegoTokenState.collect { zegoToken ->
                zegoToken?.data?.let {
                    Logger.i(TAG, "collectToken:zegoToken.data=$it isToLoginRoom=$isToLoginRoom")
                    if (isToLoginRoom) {
                        // token 获取成功触发登录
                        Logger.i(TAG, "loginRoom:zegoToken.data=$it")
                        execLoginRoom(userId, roomId, it)
                    } else {
                        // token 获取成功更新 token
                        engine.renewToken(roomId, it)
                    }
                } ?: let {
                    Logger.e(TAG, "loginRoom:zegoToken.data null!")
                }
            }
        }
    }

    private fun execLoginRoom(userId: String?, roomId: String?, token: String) {
        // ZegoUser 的构造方�?public ZegoUser(String userID) 会将 “userName�?设为与传的参�?“userID�?一样。“userID�?�?“userName�?不能�?“null�?否则会导致登录房间失败�?
        val user = ZegoUser(userId)

        val roomConfig = ZegoRoomConfig()
        //如果您使�?appsign 的方式鉴权，token 参数不需填写；如果需要使用更加安全的 鉴权方式�?token 鉴权，请参考[如何�?AppSign 鉴权升级�?Token 鉴权](https://doc-zh.zego.im/faq/token_upgrade?product=ExpressVideo&platform=all)
        roomConfig.token = token
        // 只有传入 “isUserStatusNotify�?参数取值为 “true�?�?ZegoRoomConfig，才能收�?onRoomUserUpdate 回调�?
        roomConfig.isUserStatusNotify = true
        Logger.i(TAG, "execLoginRoom: thread:${Thread.currentThread()}")
        // 登录房间
        engine.loginRoom(
            roomId, user, roomConfig
        ) { error: Int, extendedData: JSONObject? ->
            // 登录房间结果，如果仅关注登录结果，关注此回调即可
            Logger.d(TAG, "loginRoom: roomId=$roomId user=${user.userID} error=$error")
            if (error == 0) {
                // 登录成功
                onLoginRoomSuccess()
            } else {
                // 登录失败，请参�?errorCode 说明 https://doc-zh.zego.im/article/4378
                Toast.makeText(
                    this,
                    "登录失败，请参�?errorCode 说明 https://doc-zh.zego.im/article/4378",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        Logger.i(TAG, "execLoginRoom done: thread:${Thread.currentThread()}")
    }

    //请求摄像头、录音权�?
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

    private inline fun <R> notNull(vararg args: Any?, block: () -> R) =
        when {
            args.filterNotNull().size == args.size -> block()
            else -> null.also {
                Logger.w(TAG, "notNull check fail!")
            }
        }

    companion object {
        private const val TAG = "BaseActivity"
    }
}

