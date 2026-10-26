package com.tpstreams.player.ui.controller

import android.content.Context
import android.content.res.Configuration
import com.tpstreams.player.TPStreamsPlayer
import com.tpstreams.player.ui.host.FullscreenHost

/** Owns automatic fullscreen behavior and playback preservation across rotations. */
internal class AutoFullscreenController(
    private val host: FullscreenHost,
    private val fullscreenMode: FullscreenMode,
    context: Context,
    private val onLiveStreamStateChanged: (Boolean) -> Unit
) {
    private var orientationEventListener: OrientationListener? = null
    private var autoFullscreenOnRotateEnabled = true
    private var autoFullscreenEnabled = false
    private val orientationContext = context

    val isCurrentlyLandscape: Boolean
        get() = orientationEventListener?.isCurrentlyLandscape == true

    fun onAttached() {
        if (autoFullscreenOnRotateEnabled) enable()
        host.hostedView.post { restoreRequestedFullscreenIfNeeded() }
    }

    fun onDetached() = disable()

    fun setEnabled(enabled: Boolean) {
        if (autoFullscreenOnRotateEnabled == enabled) return
        autoFullscreenOnRotateEnabled = enabled
        if (enabled) enable() else disable()
    }

    fun enable() {
        disable()
        orientationEventListener = OrientationListener(orientationContext).apply {
            onPortraitDetected = {
                host.currentPlayer?.suppressAutoFullscreenUntilPortrait = false
            }
            setOnChangeListener { isLandscape ->
                host.hostedView.post {
                    if (isLandscape) {
                        if (
                            !fullscreenMode.isInFullscreenMode() &&
                            host.currentPlayer?.suppressAutoFullscreenUntilPortrait != true
                        ) {
                            fullscreenMode.enterFullscreen()
                        }
                    } else {
                        host.currentPlayer?.suppressAutoFullscreenUntilPortrait = false
                        if (fullscreenMode.isInFullscreenMode()) {
                            fullscreenMode.exitFullscreen(suppressAutoReentry = false)
                        }
                    }
                }
            }
            start()
        }
        autoFullscreenEnabled = true
    }

    fun disable() {
        orientationEventListener?.stop()
        orientationEventListener = null
        autoFullscreenEnabled = false
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        if (!autoFullscreenOnRotateEnabled || autoFullscreenEnabled) return

        // Preserve playWhenReady, including while the player is buffering during reattachment.
        val wasPlayingBefore = host.currentPlayer?.playWhenReady ?: false
        host.lifecycleManager?.setInTransition(true)
        host.hostedView.post {
            when (newConfig.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    if (host.currentPlayer?.suppressAutoFullscreenUntilPortrait != true) {
                        host.setFullscreenButtonState(true)
                        fullscreenMode.enterFullscreen()
                    }
                }
                Configuration.ORIENTATION_PORTRAIT -> {
                    host.currentPlayer?.suppressAutoFullscreenUntilPortrait = false
                    host.setFullscreenButtonState(false)
                    fullscreenMode.exitFullscreen(suppressAutoReentry = false)
                }
            }

            host.hostedView.post {
                val player = host.currentPlayer
                if (wasPlayingBefore && player?.isPlaying == false) {
                    player.play()
                } else if (!wasPlayingBefore && player?.isPlaying == true) {
                    player.pause()
                }
                host.lifecycleManager?.setInTransition(false)
                player?.let { onLiveStreamStateChanged(it.isLiveStream) }
            }
        }
    }

    fun restoreRequestedFullscreenIfNeeded() {
        val player = host.currentPlayer ?: return
        if (
            player.isFullscreenRequested &&
            !fullscreenMode.isInFullscreenMode() &&
            !fullscreenMode.isInTransition() &&
            host.hostedView.isAttachedToWindow
        ) {
            fullscreenMode.enterFullscreen()
        }
    }
}
