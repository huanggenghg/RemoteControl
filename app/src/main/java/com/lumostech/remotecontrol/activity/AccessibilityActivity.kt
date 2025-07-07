package com.lumostech.remotecontrol.activity

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.lumostech.remotecontrol.MyService
import com.lumostech.remotecontrol.PowerKeyObserver
import com.lumostech.remotecontrol.PowerKeyObserver.OnPowerKeyListener
import com.lumostech.remotecontrol.R
import com.lumostech.remotecontrol.SmallWindowView
import com.lumostech.remotecontrol.bean.Bean
import com.lumostech.remotecontrol.dialog.DialogHelper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

open class AccessibilityActivity : AppCompatActivity() {
    private val OVERLAY_PERMISSION_REQ_CODE = 2
    private var btn_layoutParams: WindowManager.LayoutParams? = null
    private var mLayoutParams: WindowManager.LayoutParams? = null
    private val myHandler = AccessibilityHandler(this)
    var singleThreadExecutor: ExecutorService? = null
    @JvmField
    var windowView: SmallWindowView? = null
    private var wm: WindowManager? = null
    private var powerKeyObserver: PowerKeyObserver? = null //检测电源键是否被按下
    var dialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initSmallViewLayout()
        this.singleThreadExecutor = Executors.newSingleThreadExecutor()

        powerKeyObserver = PowerKeyObserver(this)
        powerKeyObserver!!.startListen() //开始注册广播
        powerKeyObserver!!.setHomeKeyListener(object : OnPowerKeyListener {
            override fun onPowerKeyPressed() {
                myHandler.sendEmptyMessage(
                    2
                )
            }
        })
    }

    protected fun showAccessibilityDialog() {
        if (!MyService.isStart) {
            dialog = DialogHelper.showMessagePositiveDialog(
                this,
                "辅助功能",
                "使用连点器需要开启(无障碍)辅助功能，是否现在去开启？"
            )
        }
    }

    protected fun performClick(x: Float, y: Float) {
        windowView!!.postDelayed({
            windowView!!.setwmParamsFlags(24)
            singleThreadExecutor!!.execute {
                val message = Message()
                message.obj = Bean(x, y)
                message.what = 1
                myHandler.sendMessage(message)
                //                        for (int t = 0; t < 100; t++) {
                //                            try {
                //                                Thread.sleep(10);
                //                            } catch (InterruptedException e2) {
                //                                e2.printStackTrace();
                //                            }
                //                        }
                //                        AccessibilityActivity.this.myHandler.sendEmptyMessageDelayed(0, 200);
            }
        }, 0)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    fun setMouseClick(x: Float, y: Float) {
        val myService = MyService.myService
        myService?.dispatchGestureClick(x, y)
    }

    @SuppressLint("WrongConstant")
    fun initSmallViewLayout() {
        this.windowView = LayoutInflater.from(this)
            .inflate(R.layout.window_a, null as ViewGroup?) as SmallWindowView
        this.wm = application.getSystemService("window") as WindowManager
        this.mLayoutParams = WindowManager.LayoutParams(-2, -2, 2003, 8, -3)
        val layoutParams = WindowManager.LayoutParams(-2, -2, 2003, 8, -3)
        this.btn_layoutParams = layoutParams
        layoutParams.gravity = 49
        mLayoutParams!!.gravity = 0
        windowView!!.wm = this.wm
        windowView!!.setWmParams(this.mLayoutParams)
    }

    fun alertWindow() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (Build.VERSION.SDK_INT >= 26) {
                mLayoutParams!!.type = 2038
                btn_layoutParams!!.type = 2038
            }
            requestDrawOverLays()
        } else if (Build.VERSION.SDK_INT >= 21) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf("android.permission.SYSTEM_ALERT_WINDOW"),
                1
            )
        }
    }

    fun showWindow() {
        if (this.wm != null && windowView!!.windowId == null) {
            wm!!.addView(this.windowView, this.mLayoutParams)
        }
    }

    @SuppressLint("WrongConstant")
    @RequiresApi(api = Build.VERSION_CODES.M)
    fun requestDrawOverLays() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "can not DrawOverlays", 0).show()
            startActivityForResult(
                Intent(
                    "android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse(
                        "package:$packageName"
                    )
                ), this.OVERLAY_PERMISSION_REQ_CODE
            )
            return
        }
        showWindow()
    }

    /* access modifiers changed from: protected */
    @SuppressLint("WrongConstant")
    @RequiresApi(api = Build.VERSION_CODES.M)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != this.OVERLAY_PERMISSION_REQ_CODE) {
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "设置权限拒绝", 0).show()
        } else {
            Toast.makeText(this, "设置权限成功", 0).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (MyService.isStart) {
            if (dialog != null && dialog!!.isShowing) {
                dialog!!.dismiss()
            }
            alertWindow()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        powerKeyObserver!!.stopListen()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}