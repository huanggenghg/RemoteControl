package com.lumostech.remotecontrol;

import android.os.Bundle;
import android.util.Log;

import im.zego.zegoexpress.entity.ZegoCanvas;
import im.zego.zegoexpress.entity.ZegoStream;

public class RemoteControlActivity extends BaseActivity {

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
}