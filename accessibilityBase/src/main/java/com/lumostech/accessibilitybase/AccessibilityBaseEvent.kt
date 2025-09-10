package com.lumostech.accessibilitybase

interface AccessibilityBaseEvent {
    fun dispatchGestureClick(x: Float, y: Float)
    fun dispatchScrollUp()
    fun dispatchScrollDown()
    fun dispatchScrollLeft()
    fun dispatchScrollRight()
    fun dispatchSoftInput(inputText: String)
    fun dispatchBack()
    fun dispatchHome()
    fun dispatchRecents()
}