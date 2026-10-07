package com.lumostech.autoclick

import android.app.Activity
import android.os.Bundle
import android.widget.Button

/** A separate test-APK process keeps the target alive when Autoclick is killed. */
class RecoveryTargetActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = getSharedPreferences("recovery_target", MODE_PRIVATE)
        if (intent.getBooleanExtra("reset", false)) check(store.edit().putInt("clicks", 0).commit())
        setContentView(Button(this).apply {
            text = "后台恢复验证：已点击 ${store.getInt("clicks", 0)} 次"
            setOnClickListener {
                val clicks = store.getInt("clicks", 0) + 1
                check(store.edit().putInt("clicks", clicks).commit())
                text = "后台恢复验证：已点击 $clicks 次"
            }
        })
    }
}
