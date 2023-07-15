package com.lumostech.remotecontrol;

import android.os.Bundle;

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
    }

    @Override
    protected void onRoomStreamUpdate(ZegoStream zegoStream, String playStreamId) {
        ZegoCanvas zegoCanvas = new ZegoCanvas(findViewById(R.id.remoteUserView));
        mEngine.startPlayingStream(playStreamId, zegoCanvas);
    }

    @Override
    protected void onLoginRoomSuccess() {

    }
}