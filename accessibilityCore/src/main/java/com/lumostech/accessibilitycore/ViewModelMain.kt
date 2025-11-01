package com.lumostech.accessibilitycore

import androidx.lifecycle.MutableLiveData

object ViewModelMain {
    //悬浮窗口创建 移除  基于无障碍服务
    var isShowFloatWindow = MutableLiveData<Boolean>()
    //悬浮窗口创建 移除
    var isShowCustomFloatWindow = MutableLiveData<Boolean>()
}