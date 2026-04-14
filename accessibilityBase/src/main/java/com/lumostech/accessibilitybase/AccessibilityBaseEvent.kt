package com.lumostech.accessibilitybase

import android.view.View

interface AccessibilityBaseEvent {
    fun dispatchGestureClick(x: Float, y: Float)
    fun dispatchScrollUp(distance: Float, duration: Long)
    fun dispatchScrollDown(distance: Float, duration: Long)
    fun dispatchScrollLeft(distance: Float, duration: Long)
    fun dispatchScrollRight(distance: Float, duration: Long)
    fun dispatchSoftInput(inputText: String)
    fun dispatchBack()
    fun dispatchHome()
    fun dispatchRecents()
    fun dispatchGestureClick(x: Float, y: Float, onComplete: () -> Unit)
    fun setFloatCustomView(floatCustomView: View)
    fun dispatchClickPointsEvent()
}