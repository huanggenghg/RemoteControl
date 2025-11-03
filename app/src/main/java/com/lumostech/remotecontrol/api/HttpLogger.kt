package com.lumostech.remotecontrol.api

import android.util.Log
import okhttp3.logging.HttpLoggingInterceptor

class HttpLogger : HttpLoggingInterceptor.Logger {
    override fun log(message: String?) {
        Log.i(TAG, message ?: "")
    }

    companion object {
        private const val TAG = "HTTP"
    }
}