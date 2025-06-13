package com.tpstreams.player

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class FullscreenMode(private val view: TPStreamsPlayerView) {
    private var isFullscreen = false
    private var originalParent: ViewGroup? = null
    private var originalLayoutParams: ViewGroup.LayoutParams? = null
    private var backCallback: OnBackPressedCallback? = null

    fun enterFullscreen() {
        val activity = view.getActivity() as? ComponentActivity ?: return
        if (isFullscreen) return
    
        view.lifecycleManager?.preservePlaybackStateAcrossTransition {
            moveToDecorView(activity)
            switchToLandscape(activity)
            hideSystemUI(activity)
            updateFullscreenState()
            registerBackPressHandler(activity)
        }
    }
    
    private fun moveToDecorView(activity: ComponentActivity) {
        val decorView = activity.window.decorView as ViewGroup
    
        originalParent = view.parent as? ViewGroup
        originalLayoutParams = view.layoutParams
    
        originalParent?.removeView(view)
        view.setBackgroundColor(Color.BLACK)
    
        decorView.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }
    
    private fun switchToLandscape(activity: ComponentActivity) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
    
    private fun updateFullscreenState() {
        isFullscreen = true
        view.setFullscreenButtonState(true)
    }
    
    private fun registerBackPressHandler(activity: ComponentActivity) {
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFullscreen) {
                    exitFullscreen()
                } else {
                    isEnabled = false
                    activity.onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        activity.onBackPressedDispatcher.addCallback(activity, backCallback!!)
    }

    fun exitFullscreen() {
        val activity = view.getActivity() as? ComponentActivity ?: return
        if (!isFullscreen) return

        view.lifecycleManager?.preservePlaybackStateAcrossTransition {
            restoreOriginalView(activity)
            switchToPortrait(activity)
            showSystemUI(activity)
            clearBackPressHandler()
            updateFullscreenState(exiting = true)
        }
    }

    private fun restoreOriginalView(activity: ComponentActivity) {
        val decorView = activity.window.decorView as ViewGroup
        decorView.removeView(view)
        originalParent?.addView(view, originalLayoutParams)
    }

    private fun switchToPortrait(activity: ComponentActivity) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    private fun clearBackPressHandler() {
        backCallback?.remove()
        backCallback = null
    }

    private fun updateFullscreenState(exiting: Boolean) {
        isFullscreen = !exiting
        view.setFullscreenButtonState(!exiting)
    }
    
    private fun hideSystemUI(activity: ComponentActivity) {
        activity.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN
    }

    private fun showSystemUI(activity: ComponentActivity) {
        activity.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    fun isInFullscreenMode(): Boolean = isFullscreen
} 