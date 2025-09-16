package com.lumostech.remotecontrol

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator


object AnimUtils {
    fun showHorView(
        context: Context,
        isShow: Boolean,
        animationEndFunc: () -> Unit,
        vararg views: View
    ) {
        showView(
            context,
            isShow = isShow,
            isHor = true,
            animationEndFunc = animationEndFunc,
            views = views
        )
    }

    fun showVerView(
        context: Context,
        isShow: Boolean,
        animationEndFunc: () -> Unit,
        vararg views: View
    ) {
        showView(
            context,
            isShow = isShow,
            isHor = false,
            animationEndFunc = animationEndFunc,
            views = views
        )
    }

    fun showView(
        context: Context,
        isShow: Boolean,
        isHor: Boolean,
        animationEndFunc: () -> Unit,
        vararg views: View
    ) {
        if (views.isEmpty()) {
            return
        }

        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            76f,
            context.resources.displayMetrics
        )
        val translation = if (isShow) 0 - px else 0F
        for (view in views) {
            if (isHor) {
                buildAnim(view, animationEndFunc).translationX(translation).start()
            } else {
                buildAnim(view, animationEndFunc).translationY(translation).start()
            }
        }
    }

    private fun buildAnim(view: View, animationEndFunc: () -> Unit) =
        view.animate().setDuration(500).setInterpolator(
            DecelerateInterpolator()
        ).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                animationEndFunc.invoke()
            }
        })
}