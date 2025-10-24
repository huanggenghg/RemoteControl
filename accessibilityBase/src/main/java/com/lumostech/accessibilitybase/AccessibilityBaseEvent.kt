package com.lumostech.accessibilitybase

interface AccessibilityBaseEvent {
    fun dispatchGestureClick(x: Float, y: Float)
    fun dispatchLongClick()
    fun dispatchScrollUp()
    fun dispatchScrollDown()
    fun dispatchScrollLeft()
    fun dispatchScrollRight()
    fun dispatchSoftInput(inputText: String)
    fun dispatchBack()
    fun dispatchHome()
    fun dispatchRecents()
    fun dispatchGestureClick(x: Float, y: Float, onComplete: () -> Unit)
}