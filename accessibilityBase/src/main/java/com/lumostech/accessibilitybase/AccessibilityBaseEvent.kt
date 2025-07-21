package com.lumostech.accessibilitybase

interface AccessibilityBaseEvent {
    fun dispatchGestureClick(x: Float, y: Float)
    fun dispatchScrollUp()
    fun dispatchScrollDown()
    fun dispatchSoftInput(inputText: String)
}