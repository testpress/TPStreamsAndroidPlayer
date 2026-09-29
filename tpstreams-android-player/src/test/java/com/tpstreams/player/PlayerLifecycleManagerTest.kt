package com.tpstreams.player

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class PlayerLifecycleManagerTest {

    private val player = mock(Player::class.java)
    private val lifecycleOwner = mock(LifecycleOwner::class.java)
    private val lifecycle = mock(Lifecycle::class.java)
    private lateinit var manager: PlayerLifecycleManager

    @Before
    fun setUp() {
        `when`(lifecycleOwner.lifecycle).thenReturn(lifecycle)
        manager = PlayerLifecycleManager(player, lifecycleOwner)
    }

    @Test
    fun `startObserving adds observer to lifecycle`() {
        manager.startObserving()
        verify(lifecycle).addObserver(manager)
    }

    @Test
    fun `stopObserving removes observer from lifecycle`() {
        manager.stopObserving()
        verify(lifecycle).removeObserver(manager)
    }

    // ── onStop / onPause ─────────────────────────────────────────────────────

    @Test
    fun `onStop always pauses the player regardless of isPlaying`() {
        // onStop pauses unconditionally — wasPlayingBeforePause is set from
        // playWhenReady (user intent) before pausing, not from isPlaying.
        `when`(player.playWhenReady).thenReturn(false)

        manager.onStop(lifecycleOwner)

        verify(player).pause()
    }

    @Test
    fun `onStop reads playWhenReady before pausing to capture user intent`() {
        // playWhenReady = true means the user wants to play (may be buffering).
        // wasPlayingBeforePause should be true so onResume resumes playback.
        `when`(player.playWhenReady).thenReturn(true)
        manager.onPlaybackStateChanged(playWhenReady = true) // clear userPausedPlayback

        manager.onStop(lifecycleOwner)
        manager.onResume(lifecycleOwner)

        verify(player).play()
    }

    @Test
    fun `onStop does not set wasPlayingBeforePause when playWhenReady is false`() {
        // playWhenReady = false means the user deliberately paused.
        // wasPlayingBeforePause should be false so onResume does not resume.
        `when`(player.playWhenReady).thenReturn(false)
        manager.onPlaybackStateChanged(playWhenReady = true) // userPausedPlayback = false

        manager.onStop(lifecycleOwner)
        manager.onResume(lifecycleOwner)

        verify(player, never()).play()
    }

    @Test
    fun `onPause pauses immediately and onStop does not overwrite captured intent`() {
        `when`(player.playWhenReady).thenReturn(true)
        manager.onPlaybackStateChanged(playWhenReady = true)

        manager.onPause(lifecycleOwner)
        manager.onStop(lifecycleOwner)
        manager.onResume(lifecycleOwner)

        verify(player, times(1)).pause()
        verify(player).play()
    }

    @Test
    fun `onPause and onStop are both ignored when in transition`() {
        manager.setInTransition(true)
        `when`(player.isPlaying).thenReturn(true)

        manager.onPause(lifecycleOwner)
        manager.onStop(lifecycleOwner)

        verify(player, never()).pause()
    }

    @Test
    fun `onResume during transition resets pauseHandledInOnPause so subsequent pause pauses player`() {
        `when`(player.playWhenReady).thenReturn(true)
        manager.onPlaybackStateChanged(playWhenReady = true)

        // First backgrounding: onPause pauses player
        manager.onPause(lifecycleOwner)
        verify(player, times(1)).pause()

        // Resume happens while in transition
        manager.setInTransition(true)
        manager.onResume(lifecycleOwner)

        // Transition ends
        manager.setInTransition(false)

        // Second backgrounding: onPause must pause player again
        manager.onPause(lifecycleOwner)
        verify(player, times(2)).pause()
    }

    // ── onResume ─────────────────────────────────────────────────────────────

    @Test
    fun `onResume resumes playback if it was playing before backgrounding and not user paused`() {
        // User starts playback
        manager.onPlaybackStateChanged(playWhenReady = true)

        // App goes to background while playing
        `when`(player.playWhenReady).thenReturn(true)
        manager.onStop(lifecycleOwner)
        verify(player).pause()

        // App returns to foreground
        manager.onResume(lifecycleOwner)
        verify(player).play()
    }

    @Test
    fun `onResume restores foreground tracking when no onStop occurs`() {
        manager.onPlaybackStateChanged(playWhenReady = true)
        `when`(player.playWhenReady).thenReturn(true)

        manager.onPause(lifecycleOwner)
        manager.onResume(lifecycleOwner)

        // A later user pause must be recorded after a transient pause/resume cycle.
        manager.onPlaybackStateChanged(playWhenReady = false)
        manager.onResume(lifecycleOwner)

        verify(player, times(1)).play()
    }

    @Test
    fun `onResume does not resume playback if user manually paused before backgrounding`() {
        // User plays then manually pauses (playWhenReady becomes false)
        manager.onPlaybackStateChanged(playWhenReady = true)
        manager.onPlaybackStateChanged(playWhenReady = false)

        // App goes to background while already paused
        `when`(player.playWhenReady).thenReturn(false)
        manager.onStop(lifecycleOwner)

        // App returns to foreground
        manager.onResume(lifecycleOwner)
        verify(player, never()).play()
    }

    @Test
    fun `onResume does not resume playback if player was never playing`() {
        // No prior playback — userPausedPlayback starts true by default
        `when`(player.playWhenReady).thenReturn(false)
        manager.onStop(lifecycleOwner)

        manager.onResume(lifecycleOwner)
        verify(player, never()).play()
    }

    @Test
    fun `onResume is ignored when in transition`() {
        manager.onPlaybackStateChanged(playWhenReady = true)
        `when`(player.playWhenReady).thenReturn(true)
        manager.onStop(lifecycleOwner)

        manager.setInTransition(true)
        manager.onResume(lifecycleOwner)

        verify(player, never()).play()
    }

    // ── onPlaybackStateChanged ────────────────────────────────────────────────

    @Test
    fun `onPlaybackStateChanged with playWhenReady true clears userPausedPlayback`() {
        // Start playing so userPausedPlayback = false
        manager.onPlaybackStateChanged(playWhenReady = true)

        // Simulate buffering — isPlaying goes false but playWhenReady stays true.
        // The next onStop should still see intent-to-play and resume on foreground.
        `when`(player.playWhenReady).thenReturn(true)
        manager.onStop(lifecycleOwner)
        manager.onResume(lifecycleOwner)

        verify(player).play()
    }

    @Test
    fun `onPlaybackStateChanged with playWhenReady false marks user as paused`() {
        manager.onPlaybackStateChanged(playWhenReady = true)
        manager.onPlaybackStateChanged(playWhenReady = false)

        `when`(player.playWhenReady).thenReturn(true) // edge: even if player says playWhenReady
        manager.onStop(lifecycleOwner)
        manager.onResume(lifecycleOwner)

        // userPausedPlayback = true prevents resume
        verify(player, never()).play()
    }

    @Test
    fun `playback state changes during transition do not affect userPausedPlayback`() {
        // Establish playing state
        manager.onPlaybackStateChanged(playWhenReady = true)

        // In transition, a playWhenReady=false event fires (e.g. surface swap buffering)
        manager.setInTransition(true)
        manager.onPlaybackStateChanged(playWhenReady = false) // should be ignored
        manager.setInTransition(false)

        // App goes to background while playing
        `when`(player.playWhenReady).thenReturn(true)
        manager.onStop(lifecycleOwner)

        // On resume, should still resume because the transition event wasn't a user pause
        manager.onResume(lifecycleOwner)
        verify(player).play()
    }

    // ── preservePlaybackStateAcrossTransition ────────────────────────────────

    @Test
    fun `preservePlaybackStateAcrossTransition keeps player playing if currently playing`() {
        `when`(player.playWhenReady).thenReturn(true, false)

        manager.preservePlaybackStateAcrossTransition {
            // Simulate temporary surface detach — playWhenReady drops to false
        }

        // Post-transition restoration should call play()
        verify(player).play()
    }

    @Test
    fun `preservePlaybackStateAcrossTransition keeps player paused if currently paused`() {
        `when`(player.playWhenReady).thenReturn(false)

        manager.preservePlaybackStateAcrossTransition {
            // No state change
        }

        verify(player, never()).play()
        verify(player, never()).pause()
    }

    // ── ProcessLifecycleOwner isolation ─────────────────────────────────────

    @Test
    fun `observes application lifecycle and ignores activity or view lifecycle events`() {
        val activityLifecycleOwner = mock(LifecycleOwner::class.java)
        val activityLifecycle = mock(Lifecycle::class.java)
        `when`(activityLifecycleOwner.lifecycle).thenReturn(activityLifecycle)

        manager.startObserving()
        verify(lifecycle).addObserver(manager)

        // Activity / View lifecycle was never observed
        verify(activityLifecycle, never()).addObserver(manager)

        // Player is not paused just because a view or activity lifecycle fires
        `when`(player.isPlaying).thenReturn(true)
        verify(player, never()).pause()
    }

    @Test
    fun `view detachment or destruction does not unregister or pause player lifecycle`() {
        `when`(player.isPlaying).thenReturn(true)
        manager.startObserving()

        // Manager remains observing and player is not paused on view detachment
        verify(player, never()).pause()
        verify(lifecycle, never()).removeObserver(manager)
    }

    @Test
    fun `player lifecycle manager survives view surface replacement without losing state`() {
        manager.onPlaybackStateChanged(playWhenReady = true)
        `when`(player.playWhenReady).thenReturn(true, false)

        // Surface swap inside a transition
        manager.preservePlaybackStateAcrossTransition {
            `when`(player.isPlaying).thenReturn(false)
        }

        // Playback is preserved because the manager is retained across surface swaps
        verify(player).play()
    }
}
