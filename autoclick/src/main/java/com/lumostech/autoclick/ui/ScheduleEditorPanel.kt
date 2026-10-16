package com.lumostech.autoclick.ui

import android.content.res.Resources
import androidx.core.view.isVisible
import com.lumostech.autoclick.R
import com.lumostech.autoclick.databinding.LayoutConfirmBinding
import java.util.TimeZone

/** Shared by recording overlays and the saved-task dialog, including their sizing and validation. */
internal fun scheduleEditorWidthPixels(resources: Resources): Int {
    val density = resources.displayMetrics.density
    return minOf(resources.displayMetrics.widthPixels - (32 * density).toInt(), (560 * density).toInt())
        .coerceAtLeast(1)
}

internal data class ScheduleSelection(val hour: Int, val minute: Int, val days: Set<Int>) {
    val validationError: String? get() = when {
        hour !in 0..23 || minute !in 0..59 -> "请输入有效的时间（00:00–23:59）"
        days.isEmpty() -> "请至少选择一个执行星期"
        else -> null
    }
}

internal fun LayoutConfirmBinding.configureScheduleEditor(hour: Int, minute: Int, days: Set<Int>,
    onChanged: (ScheduleSelection) -> Unit = {}) {
    timePicker.setIs24HourView(true)
    timePicker.hour = hour
    timePicker.minute = minute
    weekdaysPicker.setSelectedDays(days.sorted())
    setScheduleTimeZone(TimeZone.getDefault().id)
    timePicker.setOnTimeChangedListener { _, h, m ->
        showScheduleError(null)
        onChanged(ScheduleSelection(h, m, weekdaysPicker.selectedDays.toSet()))
    }
    weekdaysPicker.setOnWeekdaysChangeListener { _, _, selected ->
        showScheduleError(null)
        onChanged(ScheduleSelection(timePicker.hour, timePicker.minute, selected.toSet()))
    }
}

internal fun LayoutConfirmBinding.readScheduleSelection(): ScheduleSelection {
    // Commit a number typed directly into a spinner before reading its value.
    timePicker.clearFocus()
    return ScheduleSelection(timePicker.hour, timePicker.minute, weekdaysPicker.selectedDays.toSet())
}

internal fun LayoutConfirmBinding.setScheduleTimeZone(timeZoneId: String, warnIfChanged: Boolean = false) {
    scheduleTimeZone.text = root.context.getString(R.string.schedule_saved_time_zone, timeZoneId)
    scheduleZoneWarning.isVisible = warnIfChanged && timeZoneId != TimeZone.getDefault().id
}

internal fun LayoutConfirmBinding.showScheduleError(message: String?) {
    scheduleError.text = message
    scheduleError.isVisible = message != null
}

internal fun LayoutConfirmBinding.setScheduleBusy(busy: Boolean, saveAllowed: Boolean = true) {
    confirm.isEnabled = !busy && saveAllowed
    cancel.isEnabled = !busy
    timePicker.isEnabled = !busy
    weekdaysPicker.setEditable(!busy)
}
