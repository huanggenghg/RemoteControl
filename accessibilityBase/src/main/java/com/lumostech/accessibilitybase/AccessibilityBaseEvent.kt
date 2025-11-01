package com.lumostech.accessibilitybase

import android.view.View

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
    fun dispatchGestureClick(x: Float, y: Float, onComplete: () -> Unit)
    fun setFloatCustomView(floatCustomView: View)
    fun dispatchClickPointsEvent()
}