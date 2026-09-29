package com.tpstreams.player

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.media3.common.util.UnstableApi

@UnstableApi
class FullscreenMode(private val view: TPStreamsPlayerView) {
    private var isFullscreen = false
    private var isTransitioning = false
    private var originalParent: ViewGroup? = null
    private var originalLayoutParams: ViewGroup.LayoutParams? = null
    private var backCallback: OnBackPressedCallback? = null

    fun enterFullscreen() {
        val activity = view.getActivity() as? ComponentActivity ?: return
        if (isFullscreen || isTransitioning) return
        isTransitioning = true

        try {
            view.lifecycleManager?.preservePlaybackStateAcrossTransition {
                // Reparent directly to decorView without tearing down the player binding.
                // The previous setPlayer(null)/setPlayer(player) teardown cycle was introduced in PR #111
                // to work around MediaTek secure decoder dual-allocation crashes, but it introduced
                // black screen flicker and triggered Qualcomm OMX synchronous detach timeouts.
                // Direct view reparenting keeps the PlayerView bound to ExoPlayer so Media3 manages
                // any surface re-attachment naturally through its lifecycle callbacks.
                moveToDecorView(activity)
                switchToLandscape(activity)
                hideSystemUI(activity)
                updateFullscreenState()
                registerBackPressHandler(activity)
            }
        } finally {
            isTransitioning = false
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
        if (!isFullscreen || isTransitioning) return
        isTransitioning = true

        try {
            view.lifecycleManager?.preservePlaybackStateAcrossTransition {
                // Restore directly to original parent without tearing down the player binding.
                // Avoids setPlayer(null)/setPlayer(player) teardown cycles, allowing Media3
                // to handle surface lifecycle callbacks directly.
                restoreOriginalView(activity)
                switchToPortrait(activity)
                showSystemUI(activity)
                clearBackPressHandler()
                updateFullscreenState(exiting = true)
            }
        } finally {
            isTransitioning = false
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