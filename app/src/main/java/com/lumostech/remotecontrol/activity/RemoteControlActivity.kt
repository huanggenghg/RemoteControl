package com.lumostech.remotecontrol.activity

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import com.lumostech.remotecontrol.R
import im.zego.zegoexpress.entity.ZegoCanvas
import im.zego.zegoexpress.entity.ZegoStream
import org.json.JSONException
import org.json.JSONObject


class RemoteControlActivity : ZegoBaseActivity() {
    private var mRoomId: String? = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_control)
        createEngine()
        setEventHandler()
        mRoomId = intent.getStringExtra(EXTRA_CODE)
        loginRoom("user3", mRoomId)
        val zegoCanvas = ZegoCanvas(findViewById(R.id.remoteUserView))
        mEngine?.startPlayingStream("stream2", zegoCanvas)
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

        val jsonObject = JSONObject()
        try {
            jsonObject.put("action", event.action)
            jsonObject.put("x", event.x.toString())
            jsonObject.put("y", event.y.toString())
            jsonObject.put("rawX", event.rawX.toDouble())
            jsonObject.put("rawY", event.rawY.toDouble())

            Log.d("TAG", "onTouchEvent: $jsonObject")
            mEngine?.sendCustomCommand(
                mRoomId, jsonObject.toString(), null
            ) { errorCode: Int ->
                Log.d(
                    "TAG",
                    "sendCustomCommand: error = $errorCode"
                )
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return super.onTouchEvent(event)
    }

    companion object {
        const val EXTRA_CODE: String = "code"
    }
}