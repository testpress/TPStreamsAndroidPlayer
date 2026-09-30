package com.tpstreams.player

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi

@UnstableApi
class FullscreenMode(private val view: TPStreamsPlayerView) {
    private var isFullscreen = false
    private var isTransitioning = false
    private var originalParent: ViewGroup? = null
    private var originalLayoutParams: ViewGroup.LayoutParams? = null
    private var originalViewIndex: Int = -1
    private var originalBackground: Drawable? = null
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var originalSystemBarsBehavior: Int = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    private var originalSystemBarsVisible: Boolean = true
    private var backCallback: OnBackPressedCallback? = null

    fun enterFullscreen() {
        val activity = view.getActivity() as? ComponentActivity ?: return
        if (isFullscreen || isTransitioning) return

        val player = view.getPlayer()
        val lifecycleManager = (player as? TPStreamsPlayer)?.lifecycleManager ?: view.lifecycleManager
        val transitionAction = {
            // Release the codec's surface binding before detaching the player.
            // Prevents MediaTek secure decoder NO_MEMORY crash on rapid surface cycling.
            (player as? TPStreamsPlayer)?.releaseVideoSurface()
            view.setPlayer(null)
            moveToDecorView(activity)
            if (player != null) {
                view.setPlayer(player)
            }
            switchToLandscape(activity)
            hideSystemUI(activity)
            isFullscreen = true
            view.setFullscreenButtonState(true)
            registerBackPressHandler(activity)
        }

        runTransition {
            if (lifecycleManager != null) {
                lifecycleManager.preservePlaybackStateAcrossTransition(transitionAction)
            } else {
                transitionAction()
            }
        }
    }

    private fun moveToDecorView(activity: ComponentActivity) {
        val decorView = activity.window.decorView as ViewGroup

        val parent = view.parent as? ViewGroup
        originalParent = parent
        originalLayoutParams = view.layoutParams
        originalViewIndex = parent?.indexOfChild(view) ?: -1
        originalBackground = view.background

        parent?.removeView(view)
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
        // Save the host's original orientation policy before overriding it so we can
        // restore it exactly on exit — not assume it was always SENSOR_PORTRAIT.
        originalOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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

        val player = view.getPlayer()
        val lifecycleManager = (player as? TPStreamsPlayer)?.lifecycleManager ?: view.lifecycleManager
        val transitionAction = {
            // Release the codec's surface binding before detaching the player.
            // Prevents MediaTek secure decoder NO_MEMORY crash on rapid surface cycling.
            (player as? TPStreamsPlayer)?.releaseVideoSurface()
            view.setPlayer(null)
            restoreUI(activity)
            if (player != null) {
                view.setPlayer(player)
            }
        }

        runTransition {
            if (lifecycleManager != null) {
                lifecycleManager.preservePlaybackStateAcrossTransition(transitionAction)
            } else {
                transitionAction()
            }
        }
    }

    /**
     * Restores the view hierarchy, original orientation, system UI, and back callback
     * without touching the player. Called from [exitFullscreen] and when the player is
     * detached while in fullscreen (via [TPStreamsPlayerView.setPlayer] with null), so
     * the view is never left stranded in the DecorView after the player is released.
     */
    internal fun restoreUI(activity: ComponentActivity) {
        restoreOriginalView(activity)
        val targetOrientation = if (
            originalOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED &&
            originalOrientation != ActivityInfo.SCREEN_ORIENTATION_USER &&
            originalOrientation != ActivityInfo.SCREEN_ORIENTATION_SENSOR &&
            originalOrientation != ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR &&
            originalOrientation != ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        ) {
            originalOrientation
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        activity.requestedOrientation = targetOrientation
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).also {
            if (originalSystemBarsVisible) {
                it.show(WindowInsetsCompat.Type.systemBars())
            } else {
                it.hide(WindowInsetsCompat.Type.systemBars())
            }
            it.systemBarsBehavior = originalSystemBarsBehavior
        }
        clearBackPressHandler()
        isFullscreen = false
        view.setFullscreenButtonState(false)
    }

    private fun runTransition(action: () -> Unit) {
        // Reattaching the player can synchronously request fullscreen again via
        // TPStreamsPlayerView.setPlayer(). Block re-entry until either transition ends.
        isTransitioning = true
        try {
            action()
        } finally {
            isTransitioning = false
        }
    }

    private fun restoreOriginalView(activity: ComponentActivity) {
        val decorView = activity.window.decorView as ViewGroup
        decorView.removeView(view)
        // Restore original background (clears the black set during enterFullscreen)
        view.background = originalBackground
        // Re-insert at the original child index to preserve sibling ordering
        if (originalViewIndex >= 0) {
            val safeIndex = originalViewIndex.coerceAtMost(originalParent?.childCount ?: 0)
            originalParent?.addView(view, safeIndex, originalLayoutParams)
        } else {
            originalParent?.addView(view, originalLayoutParams)
        }
    }

    private fun clearBackPressHandler() {
        backCallback?.remove()
        backCallback = null
    }

    private fun hideSystemUI(activity: ComponentActivity) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).also {
            originalSystemBarsBehavior = it.systemBarsBehavior
            originalSystemBarsVisible = ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.systemBars()) ?: true
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    fun isInFullscreenMode(): Boolean = isFullscreen
    fun isInTransition(): Boolean = isTransitioning
}
