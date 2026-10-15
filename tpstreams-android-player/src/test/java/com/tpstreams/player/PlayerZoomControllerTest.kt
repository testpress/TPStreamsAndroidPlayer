package com.tpstreams.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@UnstableApi
class PlayerZoomControllerTest {

    private val playerView = mock(TPStreamsPlayerView::class.java)
    private val context = mock(Context::class.java)
    private val contentFrame = mock(AspectRatioFrameLayout::class.java)
    private lateinit var zoomController: PlayerZoomController

    @Before
    fun setUp() {
        `when`(playerView.context).thenReturn(context)
        `when`(playerView.width).thenReturn(1080)
        `when`(playerView.height).thenReturn(2400)
        `when`(contentFrame.width).thenReturn(1080)
        `when`(contentFrame.height).thenReturn(608)
        `when`(contentFrame.left).thenReturn(0)
        `when`(contentFrame.top).thenReturn(896)

        zoomController = PlayerZoomController(playerView)
        zoomController.contentFrame = contentFrame
    }

    @Test
    fun `initial state is enabled, 1x scale, not zoomed, and max scale 8x`() {
        assertTrue(zoomController.isEnabled)
        assertEquals(1.0f, zoomController.currentScale, 0.001f)
        assertEquals(0.0f, zoomController.currentTranslationX, 0.001f)
        assertEquals(0.0f, zoomController.currentTranslationY, 0.001f)
        assertEquals(8.0f, zoomController.maxScale, 0.001f)
        assertFalse(zoomController.isZoomed)
        assertTrue(zoomController.showZoomIndicator)
        assertFalse(zoomController.zoomOnlyInFullscreen)
        assertTrue(zoomController.isGestureAllowed)
    }

    @Test
    fun `calculateBounds returns zero bounds at 1x when content fits in parent`() {
        val bounds = zoomController.calculateBounds(1.0f)
        assertEquals(0.0f, bounds.minX, 0.001f)
        assertEquals(0.0f, bounds.maxX, 0.001f)
        assertEquals(0.0f, bounds.minY, 0.001f)
        assertEquals(0.0f, bounds.maxY, 0.001f)
    }

    @Test
    fun `calculateBounds clamps horizontally and vertically when scaled beyond parent`() {
        // At 4x: scaledW = 1080 * 4 = 4320 (> 1080). maxDiffX = (4320 - 1080) / 2 = 1620
        // scaledH = 608 * 4 = 2432 (> 2400). maxDiffY = (2432 - 2400) / 2 = 16
        val bounds = zoomController.calculateBounds(4.0f)
        assertEquals(-1620.0f, bounds.minX, 0.001f)
        assertEquals(1620.0f, bounds.maxX, 0.001f)
        assertEquals(-16.0f, bounds.minY, 0.001f)
        assertEquals(16.0f, bounds.maxY, 0.001f)
    }

    @Test
    fun `calculateFillScale calculates correct ratio to eliminate letterbox`() {
        // parent: 1080x2400, content: 1080x608.
        // scaleX = 1080 / 1080 = 1.0, scaleY = 2400 / 608 = 3.947
        // fillScale = max(1.0, 3.947) = 3.947
        val fillScale = zoomController.calculateFillScale()
        assertEquals(2400f / 608f, fillScale, 0.01f)
    }

    @Test
    fun `zoomTo updates scale, translation, isZoomed, and notifies listener`() {
        var notifiedScale = 0f
        var notifiedTx = 0f
        var notifiedTy = 0f
        zoomController.onZoomChangeListener = object : PlayerZoomController.OnZoomChangeListener {
            override fun onZoomChanged(scale: Float, translationX: Float, translationY: Float) {
                notifiedScale = scale
                notifiedTx = translationX
                notifiedTy = translationY
            }

            override fun onZoomReset() {}
        }

        zoomController.zoomTo(targetScale = 2.0f, focusX = 540f, focusY = 1200f, animated = false)

        assertEquals(2.0f, zoomController.currentScale, 0.001f)
        assertTrue(zoomController.isZoomed)
        assertEquals(2.0f, notifiedScale, 0.001f)
    }

    @Test
    fun `resetZoom restores scale to 1x and notifies listener`() {
        zoomController.zoomTo(targetScale = 3.0f, focusX = 540f, focusY = 1200f, animated = false)
        assertTrue(zoomController.isZoomed)

        var resetNotified = false
        zoomController.onZoomChangeListener = object : PlayerZoomController.OnZoomChangeListener {
            override fun onZoomChanged(scale: Float, translationX: Float, translationY: Float) {}
            override fun onZoomReset() {
                resetNotified = true
            }
        }

        zoomController.resetZoom(animated = false)

        assertEquals(1.0f, zoomController.currentScale, 0.001f)
        assertEquals(0.0f, zoomController.currentTranslationX, 0.001f)
        assertEquals(0.0f, zoomController.currentTranslationY, 0.001f)
        assertFalse(zoomController.isZoomed)
        assertTrue(resetNotified)
    }

    @Test
    fun `setting isEnabled to false resets active zoom`() {
        zoomController.zoomTo(targetScale = 2.5f, focusX = 540f, focusY = 1200f, animated = false)
        assertTrue(zoomController.isZoomed)

        zoomController.isEnabled = false

        assertFalse(zoomController.isEnabled)
        assertEquals(1.0f, zoomController.currentScale, 0.001f)
        assertFalse(zoomController.isZoomed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setting maxScale below 1 throws IllegalArgumentException`() {
        zoomController.maxScale = 0.5f
    }

    @Test
    fun `zoomOnlyInFullscreen controls gesture permission based on player fullscreen state`() {
        `when`(playerView.isInFullscreenMode()).thenReturn(false)
        zoomController.zoomOnlyInFullscreen = false
        assertTrue(zoomController.isGestureAllowed)

        zoomController.zoomOnlyInFullscreen = true
        assertFalse(zoomController.isGestureAllowed)

        `when`(playerView.isInFullscreenMode()).thenReturn(true)
        assertTrue(zoomController.isGestureAllowed)
    }

    @Test
    fun `enabling zoomOnlyInFullscreen while zoomed in outside of fullscreen resets zoom`() {
        `when`(playerView.isInFullscreenMode()).thenReturn(false)
        zoomController.zoomTo(targetScale = 2.0f, focusX = 540f, focusY = 1200f, animated = false)
        assertTrue(zoomController.isZoomed)

        zoomController.zoomOnlyInFullscreen = true

        assertFalse(zoomController.isZoomed)
        assertEquals(1.0f, zoomController.currentScale, 0.001f)
    }

    @Test
    fun `getZoomBadgeText shows Original at 1x, Zoomed to fill at fill scale, and multiplier otherwise`() {
        // at or near 1.0x shows Original
        assertEquals("Original", zoomController.getZoomBadgeText(1.0f))
        assertEquals("Original", zoomController.getZoomBadgeText(1.02f))
        assertEquals("Original", zoomController.getZoomBadgeText(1.05f))

        // near fillScale (3.947x) shows Zoomed to fill
        val fillScale = zoomController.calculateFillScale()
        assertEquals("Zoomed to fill", zoomController.getZoomBadgeText(fillScale))

        // other scales show multiplier formatted
        assertEquals("2.0x", zoomController.getZoomBadgeText(2.0f))
        assertEquals("5.5x", zoomController.getZoomBadgeText(5.5f))
        assertEquals("8.0x", zoomController.getZoomBadgeText(8.0f))
    }
}

