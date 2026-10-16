package com.tpstreams.player

import android.content.Context
import android.view.MotionEvent
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.ui.DoubleTapSeekOverlayView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@UnstableApi
class DoubleTapSeekControllerTest {

    private val playerView = mock(TPStreamsPlayerView::class.java)
    private val player = mock(TPStreamsPlayer::class.java)
    private val context = mock(Context::class.java)
    private val overlayView = mock(DoubleTapSeekOverlayView::class.java)
    private lateinit var controller: DoubleTapSeekController
    private var currentTime = 1000L

    private fun mockMotionEvent(action: Int, x: Float, y: Float): MotionEvent {
        val event = mock(MotionEvent::class.java)
        `when`(event.actionMasked).thenReturn(action)
        `when`(event.x).thenReturn(x)
        `when`(event.y).thenReturn(y)
        return event
    }

    @Before
    fun setUp() {
        currentTime = 1000L
        `when`(playerView.context).thenReturn(context)
        `when`(playerView.width).thenReturn(1000)
        `when`(playerView.height).thenReturn(500)
        `when`(playerView.getPlayer()).thenReturn(player)
        `when`(player.isCurrentMediaItemSeekable).thenReturn(true)
        `when`(player.currentPosition).thenReturn(30_000L)
        `when`(player.duration).thenReturn(120_000L)

        controller = DoubleTapSeekController(playerView)
        controller.timeProvider = { currentTime }
        controller.overlayView = overlayView
    }

    @Test
    fun `initial state defaults to enabled, 10s increment, overlay enabled, not seeking`() {
        assertTrue(controller.isEnabled)
        assertEquals(10_000L, controller.seekIncrementMs)
        assertTrue(controller.showSeekOverlay)
        assertFalse(controller.isSeeking)
        assertEquals(DoubleTapSeekController.Zone.NONE, controller.activeSide)
        assertEquals(0L, controller.accumulatedSeekMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `seekIncrementMs must be positive`() {
        controller.seekIncrementMs = 0L
    }

    @Test
    fun `double-tap on right half updates UI immediately and commits seek on finish`() {
        // Tap 1 on right side (x = 800)
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 800f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 800f, 250f))

        assertFalse("Single tap should not start seeking immediately", controller.isSeeking)

        // Tap 2 on right side within 150ms
        currentTime += 150L
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 800f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 800f, 250f))

        assertTrue("Double tap on right side should start seeking", controller.isSeeking)
        assertEquals(DoubleTapSeekController.Zone.RIGHT, controller.activeSide)
        assertEquals(10_000L, controller.accumulatedSeekMs)
        assertEquals(40_000L, controller.virtualTargetPositionMs)

        // UI updates immediately
        verify(playerView).hideController()
        verify(overlayView).showSeek(true, 10, 800f, 250f)
        // SeekTo is debounced until tapping stops
        verify(player, never()).seekTo(anyLong())

        // Finish tapping (timeout elapses)
        controller.finishSeeking()
        verify(player).seekTo(40_000L)
    }

    @Test
    fun `double-tap on left half updates UI immediately and commits rewind on finish`() {
        // Tap 1 on left side (x = 200)
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 200f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 200f, 250f))

        // Tap 2 on left side
        currentTime += 150L
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 200f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 200f, 250f))

        assertTrue("Double tap on left side should start seeking", controller.isSeeking)
        assertEquals(DoubleTapSeekController.Zone.LEFT, controller.activeSide)
        assertEquals(10_000L, controller.accumulatedSeekMs)
        assertEquals(20_000L, controller.virtualTargetPositionMs)

        verify(overlayView).showSeek(false, 10, 200f, 250f)
        verify(player, never()).seekTo(anyLong())

        controller.finishSeeking()
        verify(player).seekTo(20_000L)
    }

    @Test
    fun `rapid multi-tap debounces seek and commits only once directly to final position`() {
        // Double tap right (+10s)
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 800f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 800f, 250f))

        currentTime += 150L
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 800f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 800f, 250f))

        assertEquals(10_000L, controller.accumulatedSeekMs)

        // Tap 3 within accumulation window (+20s)
        currentTime += 200L
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 800f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 800f, 250f))

        assertEquals(20_000L, controller.accumulatedSeekMs)
        assertEquals(50_000L, controller.virtualTargetPositionMs)

        // Tap 4 within accumulation window (+30s)
        currentTime += 200L
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 800f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 800f, 250f))

        assertEquals(30_000L, controller.accumulatedSeekMs)
        assertEquals(60_000L, controller.virtualTargetPositionMs)

        // No intermediate seekTo calls made while tapping
        verify(player, never()).seekTo(anyLong())
        verify(overlayView).showSeek(true, 30, 800f, 250f)

        // Tapping ceases -> single seekTo call to final 60s
        controller.finishSeeking()
        verify(player).seekTo(60_000L)
    }

    @Test
    fun `rewind clamps to zero position`() {
        `when`(player.currentPosition).thenReturn(5_000L) // only 5s in

        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 200f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 200f, 250f))

        currentTime += 150L
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 200f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 200f, 250f))

        assertEquals(0L, controller.virtualTargetPositionMs)
        controller.finishSeeking()
        verify(player).seekTo(0L)
    }

    @Test
    fun `forward clamps to total duration`() {
        `when`(player.currentPosition).thenReturn(115_000L) // 115s of 120s duration

        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 800f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 800f, 250f))

        currentTime += 150L
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 800f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 800f, 250f))

        assertEquals(120_000L, controller.virtualTargetPositionMs)
        controller.finishSeeking()
        verify(player).seekTo(120_000L)
    }

    @Test
    fun `double tap in center area does not trigger seek`() {
        // Tap 1 at center (x = 500)
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 500f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 500f, 250f))

        currentTime += 150L
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 500f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 500f, 250f))

        assertFalse("Center tap should not trigger seek", controller.isSeeking)
        verify(player, never()).seekTo(anyLong())
    }

    @Test
    fun `non-seekable stream does not trigger seek`() {
        `when`(player.isCurrentMediaItemSeekable).thenReturn(false)

        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 800f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 800f, 250f))

        currentTime += 150L
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 800f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 800f, 250f))

        assertFalse("Non-seekable stream must not trigger seek", controller.isSeeking)
        verify(player, never()).seekTo(anyLong())
    }

    @Test
    fun `disabling controller cancels seeking without committing seek`() {
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 800f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 800f, 250f))

        currentTime += 150L
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 800f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 800f, 250f))

        assertTrue(controller.isSeeking)

        controller.isEnabled = false
        assertFalse(controller.isSeeking)
        assertEquals(0L, controller.accumulatedSeekMs)
        verify(player, never()).seekTo(anyLong())
    }

    @Test
    fun `onDetachedFromWindow resets all seeking state and hides overlay`() {
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 800f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 800f, 250f))

        currentTime += 150L
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 800f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 800f, 250f))

        assertTrue(controller.isSeeking)
        assertEquals(DoubleTapSeekController.Zone.RIGHT, controller.activeSide)
        assertEquals(10_000L, controller.accumulatedSeekMs)

        controller.onDetachedFromWindow()

        assertFalse(controller.isSeeking)
        assertEquals(DoubleTapSeekController.Zone.NONE, controller.activeSide)
        assertEquals(0L, controller.accumulatedSeekMs)
        assertEquals(0L, controller.virtualTargetPositionMs)
        verify(overlayView).hideSeek()
        verify(player, never()).seekTo(anyLong())
    }

    @Test
    fun `second finger down cancels pending single tap and prevents UP from triggering tap`() {
        // First finger down on right side
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 800f, 250f))
        // Second finger down (pinch-to-zoom starts)
        val pointerDownHandled = controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_POINTER_DOWN, 850f, 260f))
        assertFalse("ACTION_POINTER_DOWN should not be consumed so zoom can handle it", pointerDownHandled)

        // Finger lifts (ACTION_UP)
        val upHandled = controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 800f, 250f))
        assertFalse("ACTION_UP after multi-touch should not be consumed as a tap", upHandled)
        assertFalse(controller.isSeeking)
    }

    @Test
    fun `second finger down during active seek finishes seek and dismisses overlay`() {
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 800f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 800f, 250f))

        currentTime += 150L
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_DOWN, 800f, 250f))
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_UP, 800f, 250f))

        assertTrue(controller.isSeeking)

        // Second finger touches screen
        controller.handleTouchEvent(mockMotionEvent(MotionEvent.ACTION_POINTER_DOWN, 850f, 260f))

        assertFalse(controller.isSeeking)
        verify(player).seekTo(40_000L)
        verify(overlayView).hideSeek()
    }
}
