package com.lumostech.remotecontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

open class PowerKeyObserver(private val mContext: Context) {
    private var mIntentFilter: IntentFilter? = null
    private var mOnPowerKeyListener: OnPowerKeyListener? = null
    private var mPowerKeyBroadcastReceiver: PowerKeyBroadcastReceiver? = null

    //注册广播接收者
    fun startListen() {
        mIntentFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        mPowerKeyBroadcastReceiver = PowerKeyBroadcastReceiver()
        mContext.registerReceiver(mPowerKeyBroadcastReceiver, mIntentFilter)
    }

    //取消广播接收者
    fun stopListen() {
        if (mPowerKeyBroadcastReceiver != null) {
            mContext.unregisterReceiver(mPowerKeyBroadcastReceiver)
        }
    }

    // 对外暴露接口
    fun setHomeKeyListener(powerKeyListener: OnPowerKeyListener?) {
        mOnPowerKeyListener = powerKeyListener
    }

    // 回调接口
    interface OnPowerKeyListener {
        fun onPowerKeyPressed()
    }

    //广播接收者
    internal inner class PowerKeyBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == Intent.ACTION_SCREEN_OFF) {
                mOnPowerKeyListener!!.onPowerKeyPressed()
            }
        }
    }
}