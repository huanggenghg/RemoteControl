package com.lumostech.remotecontrol.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ZegoTokenViewModel : ViewModel() {

    private val _zegoTokenState = MutableStateFlow<ZegoToken?>(null)
    val zegoTokenState: StateFlow<ZegoToken?> = _zegoTokenState.asStateFlow()

    fun getZegoToken(
        userId: String,
        loginRoomId: String
    ) {
        viewModelScope.launch {
            NetUtils.getZegoToken(userId, loginRoomId).collect { result ->
                _zegoTokenState.value = result
            }
        }
    }
}