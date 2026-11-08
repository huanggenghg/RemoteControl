package com.lumostech.remotecontrol.activity

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lumostech.remotecontrol.R
import com.lumostech.remotecontrol.input.LocalAdbInputSession
import com.lumostech.remotecontrol.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun ComponentActivity.showAdbSetup(
    port: Int?,
    connectionFailed: Boolean,
    onConnect: (Int) -> Unit
): AlertDialog {
    val builder = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_RemoteControl_AdbSetup)
    val content = LayoutInflater.from(builder.context).inflate(R.layout.dialog_adb_setup, null)
    val connectPort = content.findViewById<EditText>(R.id.adb_connect_port)
    val pairPort = content.findViewById<EditText>(R.id.adb_pair_port)
    val pairCode = content.findViewById<EditText>(R.id.adb_pair_code)
    val pairButton = content.findViewById<Button>(R.id.adb_pair_button)
    val status = content.findViewById<TextView>(R.id.adb_setup_status)
    fun showStatus(message: Int) {
        status.setText(message)
        status.isVisible = true
    }
    if (port != null) connectPort.setText(port.toString())
    if (connectionFailed) showStatus(R.string.adb_connect_failed)

    var pairing: Job? = null
    val dialog = builder
        .setTitle(R.string.adb_setup_title)
        .setView(content)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.adb_connect_and_share, null)
        .create()
    dialog.setOnDismissListener {
        pairing?.cancel()
        pairCode.text.clear()
    }
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    dialog.show()

    val connectButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
    connectButton.setOnClickListener {
        val value = connectPort.readPort() ?: return@setOnClickListener
        dialog.dismiss()
        onConnect(value)
    }
    content.findViewById<Button>(R.id.adb_open_settings_button).setOnClickListener {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            showStatus(R.string.adb_settings_unavailable)
        }
    }
    pairButton.setOnClickListener {
        val value = pairPort.readPort() ?: return@setOnClickListener
        val code = pairCode.text.toString()
        if (code.length != 6 || code.any { it !in '0'..'9' }) {
            pairCode.error = getString(R.string.adb_invalid_code)
            return@setOnClickListener
        }
        pairButton.isEnabled = false
        connectButton.isEnabled = false
        showStatus(R.string.adb_pairing)
        pairing = lifecycleScope.launch {
            try {
                val paired = LocalAdbInputSession.pair(value, code)
                showStatus(if (paired) R.string.adb_pair_success else R.string.adb_pair_failed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Logger.w("LocalAdb", "Wireless debugging pairing failed", failure)
                showStatus(R.string.adb_pair_failed)
            } finally {
                pairCode.text.clear()
                pairButton.isEnabled = true
                connectButton.isEnabled = true
            }
        }
    }
    return dialog
}

private fun EditText.readPort(): Int? {
    val port = text.toString().trim().toIntOrNull()
    if (port != null && port in 1..65535) return port
    error = context.getString(R.string.adb_invalid_port)
    return null
}
