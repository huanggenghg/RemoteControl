package com.lumostech.autoclick

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lumostech.accessibilitycore.AccessibilityActivity
import com.lumostech.accessibilitycore.AccessibilityCoreService
import com.lumostech.accessibilitycore.Utils
import com.lumostech.accessibilitycore.ViewModelMain


class MainActivity : AccessibilityActivity(), AccessibilityCoreService.OnPointLongClickListener {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FilledButtonExample {
                showAccessibilityDialog()
                Utils.checkSuspendedWindowPermission(this) {
                    ViewModelMain.isShowFloatWindow.postValue(true)
                }
            }
        }

        // 设置悬浮按钮长按逻辑
        AccessibilityCoreService.onPointLongClickListener = this
    }

    override fun onDestroy() {
        AccessibilityCoreService.onPointLongClickListener = null
        super.onDestroy()
    }

    override fun onPointLongClick() {
        if (AccessibilityCoreService.isStart) {
            val confirmView = LayoutInflater.from(this).inflate(R.layout.layout_confirm, null)
            AccessibilityCoreService.accessibilityCoreService?.setFloatCustomView(confirmView)
            ViewModelMain.isShowCustomFloatWindow.postValue(true)
        }
    }
}

@Composable
fun FilledButtonExample(onClick: () -> Unit) {
    Button(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center),
        onClick = { onClick() }) {
        Text("开启悬浮窗")
    }
}