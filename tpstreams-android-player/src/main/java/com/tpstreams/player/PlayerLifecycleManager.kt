package com.tpstreams.player

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.Player

/**
 * Manages player lifecycle events to ensure proper playback state during
 * app backgrounding, orientation changes, and other lifecycle transitions.
 */
open class PlayerLifecycleManager(
    private val player: Player?,
    private val lifecycleOwner: LifecycleOwner? = null
) : DefaultLifecycleObserver {
    
    private var userPausedPlayback = true  // Default to paused to prevent auto-play
    private var wasPlayingBeforePause = false
    private var isAppInForeground = true
    private var isInTransition = false
    private var pauseHandledInOnPause = false
    
    private val owner: LifecycleOwner
        get() = lifecycleOwner ?: ProcessLifecycleOwner.get()

    fun startObserving() {
        owner.lifecycle.addObserver(this)
    }

    fun stopObserving() {
        owner.lifecycle.removeObserver(this)
    }
    
    /**
     * Set whether the player is currently in a transition (fullscreen, etc.)
     * to prevent unwanted pause/play during UI changes.
     */
    fun setInTransition(inTransition: Boolean) {
        this.isInTransition = inTransition
    }
    
    /**
     * Call when user manually plays/pauses to track user intent.
     * Must be called with [playWhenReady] (not isPlaying) — isPlaying is false during
     * buffering and suppression, which would incorrectly mark the state as user-paused.
     */
    fun onPlaybackStateChanged(playWhenReady: Boolean) {
        // Only track user intent if not during transition and app is in foreground
        if (isAppInForeground && !isInTransition) {
            userPausedPlayback = !playWhenReady
        }
    }
    
    /**
     * Save current playback state and restore it after a transition
     */
    open fun preservePlaybackStateAcrossTransition(action: () -> Unit) {
        val shouldPlay = player?.playWhenReady ?: false
        setInTransition(true)
        
        action()
        
        // Restore playback state after transition
        if (shouldPlay && player?.playWhenReady == false) {
            player?.play()
        } else if (!shouldPlay && player?.playWhenReady == true) {
            player?.pause()
        }
        setInTransition(false)
    }
    
    // Lifecycle observer methods
    override fun onStart(owner: LifecycleOwner) {
        isAppInForeground = true
    }
    
    override fun onStop(owner: LifecycleOwner) {
        if (isInTransition) return
        isAppInForeground = false
        // ProcessLifecycleOwner normally calls onPause first. Keep this fallback for
        // lifecycle owners that deliver onStop without onPause, but never overwrite the
        // intent already captured by onPause.
        if (!pauseHandledInOnPause) {
            wasPlayingBeforePause = player?.playWhenReady ?: false
            player?.pause()
        }
    }
    
    override fun onPause(owner: LifecycleOwner) {
        if (isInTransition || pauseHandledInOnPause) return

        // Pause immediately when the app leaves the foreground. Capture playWhenReady before
        // pausing because it represents user intent even when the player is buffering.
        isAppInForeground = false
        wasPlayingBeforePause = player?.playWhenReady ?: false
        player?.pause()
        pauseHandledInOnPause = true
    }

    override fun onResume(owner: LifecycleOwner) {
        isAppInForeground = true
        pauseHandledInOnPause = false
        if (isInTransition) return
        // Only resume if it was playing before AND the user didn't manually pause
        if (wasPlayingBeforePause && !userPausedPlayback) {
            player?.play()
        }
    }
}
