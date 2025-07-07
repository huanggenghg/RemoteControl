package com.lumostech.remotecontrol

import android.app.Application
import android.content.Context
import android.provider.Settings

class MyApp : Application() {
    private var context: Context? = null

    override fun onCreate() {
        super.onCreate()
        context = this
        try {
            setMyServiceEnable()
        } catch (ignored: Exception) {
        }
    }

    /**
     * 需要授予权限 android.permission.WRITE_SECURE_SETTINGS
     * adb shell pm grant 包名 android.permission.WRITE_SECURE_SETTINGS
     */
    private fun setMyServiceEnable() {
        val name = packageName + "/" + MyService::class.java.name

        val string = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val stringBuffer = StringBuffer(string)
        if (!string.contains(name)) {
            val s = stringBuffer.append(":").append(name).toString()
            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, s
            )
        }
    }

    companion object {
        const val TAG: String = "MyApp"
    }
}
