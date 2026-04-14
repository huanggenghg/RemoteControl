package com.lumostech.remotecontrol.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumostech.remotecontrol.utils.AppCoroutineExceptionHandler
import com.lumostech.remotecontrol.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ZegoTokenViewModel : ViewModel() {

    companion object {
        private const val TAG = "ZegoTokenViewModel"
    }

    private val _zegoTokenState = MutableStateFlow<ZegoToken?>(null)
    val zegoTokenState: StateFlow<ZegoToken?> = _zegoTokenState.asStateFlow()

    fun getZegoToken(
        userId: String,
        loginRoomId: String
    ) {
        Logger.d(TAG, "获取 ZegoToken - userId: $userId, roomId: $loginRoomId")
        
        viewModelScope.launch(AppCoroutineExceptionHandler) {
            try {
                NetUtils.getZegoToken(userId, loginRoomId)
                    .collect { result ->
                        _zegoTokenState.value = result
                        Logger.d(TAG, "Token 状态更新: ${result?.token ?: "null"}")
                    }
            } catch (e: Exception) {
                Logger.e(TAG, "获取 ZegoToken 失败", e)
                _zegoTokenState.value = null
            }
        }
    }
}