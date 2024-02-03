package com.lumostech.remotecontrol.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.lumostech.remotecontrol.R;

import im.zego.zegoexpress.ZegoExpressEngine;

public class MainActivity extends MediaProjectionActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 开始通话按钮
        findViewById(R.id.startButton).setOnClickListener(new View.OnClickListener() {
            // 点击开始通话
            @Override
            public void onClick(View view) {
                // 创建Express SDK 实例
                createEngine();
                // 监听常用事件
                setEventHandler();
                // 登录房间
                loginRoom("user2", "room1");
                // 开始预览及推流
                startPublish();
            }
        });

        // 停止通话按钮
        findViewById(R.id.stopButton).setOnClickListener(new View.OnClickListener() {
            // 点击停止通话
            @Override
            public void onClick(View view) {
                if(mEngine != null) {
                    mEngine.logoutRoom();
                    ZegoExpressEngine.destroyEngine(() -> {
                        //销毁成功
                    });
                }
            }
        });

        // 停止通话按钮
        findViewById(R.id.helpButton).setOnClickListener(new View.OnClickListener() {
            // 点击停止通话
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, RemoteControlActivity.class);
                startActivity(intent);

            }
        });

        findViewById(R.id.testButton).setOnClickListener(new View.OnClickListener() {
            // 点击停止通话
            @Override
            public void onClick(View view) {
            }
        });

        findViewById(R.id.testButton2).setOnClickListener(new View.OnClickListener() {
            // 点击停止通话
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, "click me!!!", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
