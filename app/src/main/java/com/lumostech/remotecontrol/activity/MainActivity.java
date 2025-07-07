package com.lumostech.remotecontrol.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.lumostech.remotecontrol.R;

import java.util.Random;

import im.zego.zegoexpress.ZegoExpressEngine;

public class MainActivity extends MediaProjectionActivity implements View.OnClickListener {

    private static final String FAB_PROJECTION_TAG_CAST = "cast";
    private static final String FAB_PROJECTION_TAG_CAST_PAUSE = "cast_pause";
    private static final int CODE_LENGTH = 6;
    private FloatingActionButton fabProjection;
    private FloatingActionButton fabAssist;
    private TextView tvCode;

    @SuppressLint("CutPasteId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        initCode();
    }

    private void initViews() {
        fabProjection = findViewById(R.id.fab_projection);
        fabAssist = findViewById(R.id.fab_assist);
        tvCode = findViewById(R.id.tv_code);
        fabProjection.setOnClickListener(this);
        fabAssist.setOnClickListener(this);
    }

    private void initCode() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        tvCode.setText(sb.toString());
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.fab_projection) {
            if (!checkCode()) {
                return;
            }

            if (mMediaProjection == null) {
                requestMediaProjection();
                showAccessibilityDialog();
                return;
            }
            showAccessibilityDialog();// 再次检查，因为可能被关闭了服务，故需要再次检查

            switchProjection();
        } else if (v.getId() == R.id.fab_assist) {
            assist();
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
        if (!checkCode()) {
            return;
        }

        String code = tvCode.getText().toString();

        // 创建Express SDK 实例
        createEngine();
        // 监听常用事件
        setEventHandler();
        // 登录房间
        loginRoom("user2", code);
        // 开始预览及推流
        startPublish();
    }

    private boolean checkCode() {
        String code = tvCode.getText().toString();
        if (TextUtils.isEmpty(code) || code.length() != CODE_LENGTH) {
            Toast.makeText(this, "协助码是六位数字，请重新输入", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
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
        if (!checkCode()) {
            return;
        }

        String code = tvCode.getText().toString();
        Intent intent = new Intent(MainActivity.this, RemoteControlActivity.class);
        intent.putExtra(RemoteControlActivity.EXTRA_CODE, code);
        startActivity(intent);
    }
}
