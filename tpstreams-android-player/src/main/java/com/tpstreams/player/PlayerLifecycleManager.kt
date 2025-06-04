package com.tpstreams.player

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player

/**
 * Manages player lifecycle events to ensure proper playback state during
 * app backgrounding, orientation changes, and other lifecycle transitions.
 */
class PlayerLifecycleManager(private val player: Player?) : DefaultLifecycleObserver {
    
    private var userPausedPlayback = true  // Default to paused to prevent auto-play
    private var wasPlayingBeforePause = false
    private var lastPlaybackState = false
    private var isAppInForeground = true
    private var isInTransition = false
    
    private val tag = "PlayerLifecycleManager"
    
    /**
     * Set whether the player is currently in a transition (fullscreen, etc.)
     * to prevent unwanted pause/play during UI changes
     */
    fun setInTransition(inTransition: Boolean) {
        this.isInTransition = inTransition
        Log.d(tag, "Transition state changed: $inTransition")
    }
    
    /**
     * Call when user manually plays/pauses to track user intent
     */
    fun onPlaybackStateChanged(isPlaying: Boolean) {
        Log.d(tag, "PlaybackStateChanged: $isPlaying, inTransition=$isInTransition")
        // Only track user intent if not during transition and app is in foreground
        if (isAppInForeground && !isInTransition) {
            if (isPlaying) {
                userPausedPlayback = false
            } else if (lastPlaybackState) {
                // Only mark as user-paused if it was previously playing
                // This prevents marking as user-paused during initial loading
                userPausedPlayback = true
            }
            lastPlaybackState = isPlaying
        }
    }
    
    /**
     * Save current playback state and restore it after a transition
     */
    fun preservePlaybackStateAcrossTransition(action: () -> Unit) {
        val currentlyPlaying = player?.isPlaying ?: false
        setInTransition(true)
        
        action()
        
        // Restore playback state after transition
        if (currentlyPlaying && player?.isPlaying == false) {
            player?.play()
        } else if (!currentlyPlaying && player?.isPlaying == true) {
            player?.pause()
        }
        setInTransition(false)
    }
    
    // Lifecycle observer methods
    override fun onStart(owner: LifecycleOwner) {
        isAppInForeground = true
        Log.d(tag, "Lifecycle onStart")
    }
    
    override fun onStop(owner: LifecycleOwner) {
        // Skip handling if we're in a transition
        if (isInTransition) {
            return
        }
        
        isAppInForeground = false
        Log.d(tag, "Lifecycle onStop")
        // Always pause on stop (includes app switching via recents button)
        if (player != null && player.isPlaying) {
            wasPlayingBeforePause = true
            player.pause()
        } else {
            wasPlayingBeforePause = false
        }
    }
    
    override fun onPause(owner: LifecycleOwner) {
        // Skip handling if we're in a transition
        if (isInTransition) {
            return
        }
        
        Log.d(tag, "Lifecycle onPause")
        // Record if the player was playing before pausing
        if (player != null) {
            lastPlaybackState = player.isPlaying
            
            // Only record true user pause if app is in foreground 
            // (otherwise it's system-initiated pause)
            if (isAppInForeground && !player.isPlaying) {
                userPausedPlayback = true
            }
            
            // Always pause on lifecycle pause
            if (player.isPlaying) {
                wasPlayingBeforePause = true
                player.pause()
            } else {
                wasPlayingBeforePause = false
            }
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        // Skip handling if we're in a transition
        if (isInTransition) {
            return
        }
        
        Log.d(tag, "Lifecycle onResume, wasPlaying=$wasPlayingBeforePause, userPaused=$userPausedPlayback")
        // Only resume if it was playing before AND the user didn't manually pause
        if (wasPlayingBeforePause && !userPausedPlayback) {
            player?.play()
        }
    }
} 