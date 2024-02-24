package com.lumostech.remotecontrol.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.lumostech.remotecontrol.R;

import im.zego.zegoexpress.ZegoExpressEngine;

public class MainActivity extends MediaProjectionActivity implements View.OnClickListener {

    private static final String FAB_PROJECTION_TAG_CAST = "cast";
    private static final String FAB_PROJECTION_TAG_CAST_PAUSE = "cast_pause";
    private FloatingActionButton fabProjection;
    private FloatingActionButton fabAssist;
    private TextView tvCode;

    @SuppressLint("CutPasteId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
    }

    private void initViews() {
        fabProjection = findViewById(R.id.fab_projection);
        fabAssist = findViewById(R.id.fab_assist);
        tvCode = findViewById(R.id.tv_code);
        fabProjection.setOnClickListener(this);
        fabAssist.setOnClickListener(this);
        tvCode.setOnClickListener(this);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fab_projection:
                switchProjection();
                break;
            case R.id.fab_assist:
                assist();
            case R.id.tv_code:
                tvCode.setInputType(InputType.TYPE_CLASS_NUMBER);
                break;
        }
    }

    private void switchProjection() {
        String switchTag = fabProjection.getTag() != null ? (String) fabProjection.getTag() : "";
        if (TextUtils.isEmpty(switchTag) || switchTag.equals(FAB_PROJECTION_TAG_CAST)) {
            fabProjection.setImageResource(R.drawable.cast_pause);
            fabProjection.setTag(FAB_PROJECTION_TAG_CAST_PAUSE);
            startCast();
        } else {
            fabProjection.setImageResource(R.drawable.cast);
            fabProjection.setTag(FAB_PROJECTION_TAG_CAST);
            pauseCast();
        }
    }

    private void startCast() {
        // 创建Express SDK 实例
        createEngine();
        // 监听常用事件
        setEventHandler();
        // 登录房间
        loginRoom("user2", "room1");
        // 开始预览及推流
        startPublish();
    }

    private void pauseCast() {
        if (mEngine != null) {
            mEngine.logoutRoom();
            ZegoExpressEngine.destroyEngine(() -> {
                // 销毁成功
            });
        }
    }

    private void assist() {
        Intent intent = new Intent(MainActivity.this, RemoteControlActivity.class);
        startActivity(intent);
    }
}
