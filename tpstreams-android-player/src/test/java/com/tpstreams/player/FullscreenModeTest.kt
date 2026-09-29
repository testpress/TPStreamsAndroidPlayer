package com.tpstreams.player

import android.view.ViewGroup
import android.view.Window
import androidx.activity.OnBackPressedDispatcher
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(UnstableApi::class)
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
        `when`(activity.onBackPressedDispatcher)
            .thenReturn(mock(OnBackPressedDispatcher::class.java))
        `when`(view.getPlayer()).thenReturn(player)
        `when`(view.parent).thenReturn(originalParent)
        `when`(view.layoutParams).thenReturn(layoutParams)
        `when`(view.lifecycleManager).thenReturn(PlayerLifecycleManager(null))
        fullscreen = FullscreenMode(view)
    }

    @Test
    fun `reattaching player requesting fullscreen does not reenter or lose original parent`() {
        // Simulate setPlayer's startInFullscreen callback at the reattachment boundary.
        // Bound the callback so a regression fails assertions instead of overflowing the stack.
        var attachments = 0
        doAnswer {
            attachments++
            if (attachments == 1) {
                `when`(view.parent).thenReturn(decor)
                fullscreen.enterFullscreen()
            }
            null
        }.`when`(view).setPlayer(player)

        fullscreen.enterFullscreen()
        assertTrue(fullscreen.isInFullscreenMode())
        verify(view, times(1)).setPlayer(player)
        verify(originalParent, times(1)).removeView(view)

        fullscreen.exitFullscreen()
        assertFalse(fullscreen.isInFullscreenMode())
        verify(originalParent).addView(view, layoutParams)
    }

    @Test
    fun `reattaching player during exit cannot recursively exit`() {
        fullscreen.enterFullscreen()
        var attachments = 0
        doAnswer {
            attachments++
            if (attachments == 1) fullscreen.exitFullscreen()
            null
        }.`when`(view).setPlayer(player)

        fullscreen.exitFullscreen()

        assertFalse(fullscreen.isInFullscreenMode())
        verify(originalParent, times(1)).addView(view, layoutParams)
        verify(view, times(2)).setPlayer(player)
    }

    @Test
    fun `repeated requests after completion remain harmless`() {
        fullscreen.enterFullscreen()
        fullscreen.enterFullscreen()
        fullscreen.exitFullscreen()
        fullscreen.exitFullscreen()

        assertFalse(fullscreen.isInFullscreenMode())
        verify(originalParent, times(1)).removeView(view)
        verify(originalParent, times(1)).addView(view, layoutParams)
    }

    @Test
    fun `entry can be retried when lifecycle manager was initially absent`() {
        `when`(view.lifecycleManager).thenReturn(null)
        fullscreen.enterFullscreen()
        assertFalse(fullscreen.isInFullscreenMode())

        `when`(view.lifecycleManager).thenReturn(PlayerLifecycleManager(null))
        fullscreen.enterFullscreen()
        assertTrue(fullscreen.isInFullscreenMode())
    }
}
