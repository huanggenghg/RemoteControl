package com.lumostech.autoclick

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lumostech.accessibilitycore.AccessibilityActivity
import com.lumostech.autoclick.ui.widget.WeekdaysPicker
import java.util.Calendar

class MainActivity : AccessibilityActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
//            FilledButtonExample {
////                showAccessibilityDialog()
////                Utils.checkSuspendedWindowPermission(this) {
////                    ViewModelMain.isShowWindow.postValue(true)
////                }
//
//            }
//            DialWithDialogExample({}, {})
            CustomTimeInputLayout()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialWithDialogExample(
    onConfirm: (TimePickerState) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentTime = Calendar.getInstance()

    val timePickerState = rememberTimePickerState(
        initialHour = currentTime.get(Calendar.HOUR_OF_DAY),
        initialMinute = currentTime.get(Calendar.MINUTE),
        is24Hour = true,
    )

    TimePickerDialog(
        onDismiss = { onDismiss() },
        onConfirm = { onConfirm(timePickerState) }
    ) {
        TimeInput(
            state = timePickerState,
        )
    }
}

@Composable
fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("Dismiss")
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm() }) {
                Text("OK")
            }
        },
        text = { content() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTimeInputLayout() {
    val timePickerState = rememberTimePickerState(is24Hour = true)

    var selectedDaysState by remember { mutableStateOf<List<Int>>(emptyList()) }

    // 使用 Column 作为布局容器
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 在 TimeInput 上方添加自定义布局（如 Text）
        Text(
            text = "设置一个提醒时间",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 放置 TimeInput 组件
        TimeInput(state = timePickerState)

        Spacer(modifier = Modifier.height(32.dp))

        AndroidView(factory = { context ->
            WeekdaysPicker(context).apply {
                setEditable(true)
                setOnWeekdaysChangeListener { view, clickedDayOfWeek, selectedDays ->
                    selectedDaysState = selectedDays
                }
            }
        }, modifier = Modifier.fillMaxWidth(), update = { view ->
            // View's been inflated or state read in this block has been updated
            // Add logic here if necessary

            // As selectedItem is read here, AndroidView will recompose
            // whenever the state changes
            // Example of Compose -> View communication
            view.selectedDays = selectedDaysState
        })

        Spacer(modifier = Modifier.height(32.dp))

        // 在 TimeInput 下方添加自定义布局（如 Button）
        Button(onClick = {
            // 处理确认逻辑
            println("已确认时间: ${timePickerState.hour}:${timePickerState.minute}")
        }) {
            Text("确认")
        }
    }
}