package com.lumostech.remotecontrol.activity;


import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;

import com.lumostech.remotecontrol.R;

import org.json.JSONException;
import org.json.JSONObject;

import im.zego.zegoexpress.entity.ZegoCanvas;
import im.zego.zegoexpress.entity.ZegoStream;

public class RemoteControlActivity extends ZegoBaseActivity {

    public static final String EXTRA_CODE = "code";
    private String mRoomId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_control);
        createEngine();
        setEventHandler();
        mRoomId = getIntent().getStringExtra(EXTRA_CODE);
        loginRoom("user3", mRoomId);
        ZegoCanvas zegoCanvas = new ZegoCanvas(findViewById(R.id.remoteUserView));
        mEngine.startPlayingStream("stream2", zegoCanvas);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mEngine.logoutRoom();
    }

    @Override
    protected void onRoomStreamUpdate(ZegoStream zegoStream, String playStreamId) {
        Log.d("REMOTE", "onRoomStreamUpdate: playStreamId = " + playStreamId);

    }

    @Override
    protected void onLoginRoomSuccess() {
        Log.d("REMOTE", "onLoginRoomSuccess");
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (TextUtils.isEmpty(mRoomId)) {
            Log.w("TAG", "onTouchEvent: mRoomId is empty!");
            return super.onTouchEvent(event);
        }

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("action", event.getAction());
            jsonObject.put("x", String.valueOf(event.getX()));
            jsonObject.put("y", String.valueOf(event.getY()));
            jsonObject.put("rawX", event.getRawX());
            jsonObject.put("rawY", event.getRawY());

            Log.d("TAG", "onTouchEvent: " + jsonObject);
            mEngine.sendCustomCommand(mRoomId, jsonObject.toString(), null, errorCode -> {
                Log.d("TAG", "sendCustomCommand: error = " + errorCode);
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return super.onTouchEvent(event);
    }
}