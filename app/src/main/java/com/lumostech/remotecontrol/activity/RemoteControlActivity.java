package com.lumostech.remotecontrol.activity;


import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;

import com.lumostech.remotecontrol.R;

import org.json.JSONException;
import org.json.JSONObject;

import im.zego.zegoexpress.entity.ZegoCanvas;
import im.zego.zegoexpress.entity.ZegoStream;

public class RemoteControlActivity extends ZegoBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_control);
        createEngine();
        setEventHandler();
        loginRoom("user3", "room1");
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
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("action", event.getAction());
            jsonObject.put("x", String.valueOf(event.getX()));
            jsonObject.put("y", String.valueOf(event.getY()));
            jsonObject.put("rawX", event.getRawX());
            jsonObject.put("rawY", event.getRawY());

            Log.d("TAG", "onTouchEvent: " + jsonObject);
            mEngine.sendCustomCommand("room1", jsonObject.toString(), null, errorCode -> {
                Log.d("TAG", "sendCustomCommand: error = " + errorCode);
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return super.onTouchEvent(event);
    }
}