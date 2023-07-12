package com.lumostech.remotecontrol;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Objects;

public class CaptureScreenService extends Service {
    public CaptureScreenService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //在这里获取MediaProjection
        Log.d("TAG", "onStartCommand: mMediaProjectionManager = " + MainActivity.mMediaProjectionManager);
        int resultCode = intent.getIntExtra("code", 1);
        Intent resultData = intent.getParcelableExtra("data");
        MainActivity.mMediaProjection = MainActivity.mMediaProjectionManager
                .getMediaProjection(resultCode, Objects.requireNonNull(resultData));
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}