package com.lumostech.autoclick

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.lumostech.accessibilitycore.AccessibilityActivity
import com.lumostech.accessibilitycore.Utils
import com.lumostech.accessibilitycore.ViewModelMain

class MainActivity : AccessibilityActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FilledButtonExample {
                showAccessibilityDialog()
                Utils.checkSuspendedWindowPermission(this) {
                    ViewModelMain.isShowWindow.postValue(true)
                }
            }
        }
    }
}

@Composable
fun FilledButtonExample(onClick: () -> Unit) {
    Button(onClick = { onClick() }) {
        Text("开启悬浮窗")
    }
}