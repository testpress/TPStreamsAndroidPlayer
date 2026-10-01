package com.tpstreams.player

import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.Window
import androidx.activity.OnBackPressedDispatcher
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class FullscreenModeTest {
    private val view = mock(TPStreamsPlayerView::class.java)
    private val player = mock(TPStreamsPlayer::class.java)
    private val activity = mock(FragmentActivity::class.java)
    private val window = mock(Window::class.java)
    private val originalParent = mock(ViewGroup::class.java)
    private val decor = mock(ViewGroup::class.java)
    private val layoutParams = ViewGroup.LayoutParams(320, 180)
    private lateinit var fullscreen: FullscreenMode

    @Before
    fun setUp() {
        `when`(view.getActivity()).thenReturn(activity)
        `when`(activity.window).thenReturn(window)
        `when`(window.decorView).thenReturn(decor)
        `when`(activity.requestedOrientation).thenReturn(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        `when`(activity.onBackPressedDispatcher)
            .thenReturn(mock(OnBackPressedDispatcher::class.java))
        `when`(player.lifecycleManager).thenReturn(PlayerLifecycleManager(null))
        `when`(view.getPlayer()).thenReturn(player)
        `when`(view.parent).thenReturn(originalParent)
        `when`(view.layoutParams).thenReturn(layoutParams)
        `when`(originalParent.indexOfChild(view)).thenReturn(0)
        fullscreen = FullscreenMode(view)
    }

    @Test
    fun `enterFullscreen and exitFullscreen move view without detaching or reattaching player`() {
        fullscreen.enterFullscreen()
        assertTrue(fullscreen.isInFullscreenMode())
        verify(view, never()).setPlayer(any())
        verify(originalParent, times(1)).removeView(view)

        fullscreen.exitFullscreen()
        assertFalse(fullscreen.isInFullscreenMode())
        verify(view, never()).setPlayer(any())
        verify(originalParent).addView(view, 0, layoutParams)
        verify(activity).requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    @Test
    fun `re-entrant enter or exit during transition is blocked`() {
        var callCount = 0
        `when`(view.parent).thenAnswer {
            callCount++
            if (callCount == 1) {
                fullscreen.enterFullscreen()
            }
            originalParent
        }

        fullscreen.enterFullscreen()
        assertTrue(fullscreen.isInFullscreenMode())
        verify(originalParent, times(1)).removeView(view)

        fullscreen.exitFullscreen()
        assertFalse(fullscreen.isInFullscreenMode())
        verify(originalParent, times(1)).addView(view, 0, layoutParams)
        verify(view, never()).setPlayer(any())
    }

    @Test
    fun `repeated requests after completion remain harmless`() {
        fullscreen.enterFullscreen()
        fullscreen.enterFullscreen()
        fullscreen.exitFullscreen()
        fullscreen.exitFullscreen()

        assertFalse(fullscreen.isInFullscreenMode())
        verify(originalParent, times(1)).removeView(view)
        verify(originalParent, times(1)).addView(view, 0, layoutParams)
    }

    @Test
    fun `enterFullscreen and exitFullscreen complete successfully when player has no lifecycle manager`() {
        `when`(player.lifecycleManager).thenReturn(null)

        fullscreen.enterFullscreen()
        assertTrue(fullscreen.isInFullscreenMode())

        fullscreen.exitFullscreen()
        assertFalse(fullscreen.isInFullscreenMode())
    }

    @Test
    fun `enterFullscreen preserves playback state using lifecycleManager`() {
        var actionExecuted = false
        val customManager = object : PlayerLifecycleManager(player) {
            override fun preservePlaybackStateAcrossTransition(action: () -> Unit) {
                actionExecuted = true
                action()
            }
        }
        `when`(player.lifecycleManager).thenReturn(customManager)

        fullscreen.enterFullscreen()

        assertTrue(actionExecuted)
        assertTrue(fullscreen.isInFullscreenMode())
    }

    @Test
    fun `exitFullscreen sets SENSOR_PORTRAIT when original orientation was UNSPECIFIED`() {
        `when`(activity.requestedOrientation).thenReturn(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)

        fullscreen.enterFullscreen()
        verify(activity).requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        fullscreen.exitFullscreen()
        verify(activity).requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    @Test
    fun `exitFullscreen restores explicit orientation when original was set`() {
        `when`(activity.requestedOrientation).thenReturn(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        fullscreen.enterFullscreen()
        verify(activity).requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        fullscreen.exitFullscreen()
        verify(activity, atLeastOnce()).requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    @Test
    fun `exitFullscreen sets SENSOR_PORTRAIT when original orientation was landscape`() {
        `when`(activity.requestedOrientation).thenReturn(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)

        fullscreen.enterFullscreen()
        verify(activity).requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        fullscreen.exitFullscreen()
        verify(activity).requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    @Test
    fun `exitFullscreen clamps re-insert index when parent loses children while in fullscreen`() {
        // View was at sibling index 2 when fullscreen was entered (e.g. header, ad banner, player).
        // At entry time the parent has 3 children, so index 2 is valid.
        `when`(originalParent.indexOfChild(view)).thenReturn(2)
        `when`(originalParent.childCount).thenReturn(3)
        fullscreen.enterFullscreen()

        // While in fullscreen the host screen removes siblings (e.g. the ad banner is dismissed),
        // so the parent now only has 1 child left. Re-inserting at index 2 would throw
        // IndexOutOfBoundsException in a real ViewGroup.
        `when`(originalParent.childCount).thenReturn(1)

        fullscreen.exitFullscreen()

        // The safe clamped index must be used: min(2, 1) = 1
        verify(originalParent).addView(view, 1, layoutParams)
    }

    @Test
    fun `enterFullscreen updates player isFullscreenRequested to true`() {
        fullscreen.enterFullscreen()
        verify(player).isFullscreenRequested = true
    }

    @Test
    fun `exitFullscreen updates player isFullscreenRequested to false`() {
        fullscreen.enterFullscreen()
        fullscreen.exitFullscreen()
        verify(player).isFullscreenRequested = false
    }
}
