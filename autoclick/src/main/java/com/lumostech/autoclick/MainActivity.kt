package com.lumostech.autoclick

import android.annotation.SuppressLint
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lumostech.accessibilitycore.AccessibilityActivity
import com.lumostech.accessibilitycore.AccessibilityCoreService
import com.lumostech.accessibilitycore.Utils
import com.lumostech.accessibilitycore.ViewModelMain
import com.lumostech.autoclick.ui.widget.WeekdaysPicker
import java.util.Calendar

class MainActivity : AccessibilityActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
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

    private lateinit var showDialog: (Boolean) -> Unit
    private var dialogComposeView: ComposeView? = null


    @OptIn(ExperimentalMaterial3Api::class)
    override fun onResume() {
        super.onResume()
        if (AccessibilityCoreService.isStart) {
            if (dialogComposeView == null) {
                dialogComposeView = ComposeView(this@MainActivity).apply {
                    setContent {
                        var isDialogVisible by remember { mutableStateOf(false) }
                        showDialog = { isDialogVisible = it }
                        if (isDialogVisible) {
                            DialWithDialogExample(
                                { timePickerState, selectedDaysState ->
                                    {
                                        isDialogVisible = false
                                    }
                                },
                                {
                                    isDialogVisible = false
                                })
                        }
                    }
                }
                setContentView(dialogComposeView)
            }

            AccessibilityCoreService.accessibilityCoreService?.setOnPointLongClickListener(object :
                AccessibilityCoreService.OnPointLongClickListener {
                override fun onPointLongClick() {
                    showDialog.invoke(true)
                }
            })
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

@SuppressLint("MutableCollectionMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialWithDialogExample(
    onConfirm: (TimePickerState, List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentTime = Calendar.getInstance()

    val timePickerState = rememberTimePickerState(
        initialHour = currentTime.get(Calendar.HOUR_OF_DAY),
        initialMinute = currentTime.get(Calendar.MINUTE),
        is24Hour = true,
    )
    val selectedDaysState by remember { mutableStateOf<MutableList<Int>>(mutableListOf()) }

    TimePickerDialog(
        onDismiss = { onDismiss() },
        onConfirm = { onConfirm(timePickerState, selectedDaysState) }
    ) {
        CustomTimeInputLayout(timePickerState, selectedDaysState)
    }
}

@Composable
fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            dismissButton = {
                TextButton(onClick = {
                    onDismiss()
                    showDialog = false
                }) {
                    Text("Dismiss")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm()
                    showDialog = false
                }) {
                    Text("OK")
                }
            },
            text = { content() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTimeInputLayout(timePickerState: TimePickerState, selectedDaysState: MutableList<Int>) {
    // 使用 Column 作为布局容器
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 在 TimeInput 上方添加自定义布局（如 Text）
        Text(
            text = "设置一个触发时间和循环周期",
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
                    selectedDaysState.clear()
                    selectedDaysState.addAll(selectedDays)
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
    }
}