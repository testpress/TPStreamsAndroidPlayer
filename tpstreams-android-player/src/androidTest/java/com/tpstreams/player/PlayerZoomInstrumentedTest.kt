package com.tpstreams.player

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.SubtitleView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerZoomInstrumentedTest {

    @Test
    fun playerView_initializesWithZoomEnabledAndSubtitlesDecoupled() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val playerView = TPStreamsPlayerView(activity)
                activity.setContentView(playerView)

                assertTrue("Pinch to zoom should be enabled by default", playerView.isPinchToZoomEnabled)
                assertEquals("Max zoom scale should default to 8.0f", 8.0f, playerView.maxZoomScale, 0.001f)
                assertEquals("Initial zoom scale should be 1.0f", 1.0f, playerView.currentZoomScale, 0.001f)
                assertFalse("Player should not be zoomed initially", playerView.isZoomed)

                val contentFrame = playerView.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
                val subtitleView = playerView.findViewById<SubtitleView>(androidx.media3.ui.R.id.exo_subtitles)

                assertNotNull("Content frame must exist", contentFrame)
                assertNotNull("SubtitleView must exist", subtitleView)
                assertNotEquals(
                    "SubtitleView must NOT be inside content frame so it does not scale with video",
                    contentFrame,
                    subtitleView.parent
                )
            }
        }
    }

    @Test
    fun zoomTo_scalesContentFrameWhileSubtitlesRemainUnscaled() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            lateinit var playerView: TPStreamsPlayerView
            lateinit var contentFrame: AspectRatioFrameLayout
            lateinit var subtitleView: SubtitleView

            scenario.onActivity { activity ->
                playerView = TPStreamsPlayerView(activity)
                activity.setContentView(
                    playerView,
                    FrameLayout.LayoutParams(1080, 1920)
                )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                contentFrame = playerView.findViewById(androidx.media3.ui.R.id.exo_content_frame)
                subtitleView = playerView.findViewById(androidx.media3.ui.R.id.exo_subtitles)

                val zoomField = TPStreamsPlayerView::class.java.getDeclaredField("zoomController")
                zoomField.isAccessible = true
                val zoomController = zoomField.get(playerView) as PlayerZoomController

                zoomController.zoomTo(targetScale = 3.5f, focusX = 540f, focusY = 960f, animated = false)

                assertEquals(3.5f, playerView.currentZoomScale, 0.001f)
                assertTrue(playerView.isZoomed)
                assertEquals(3.5f, contentFrame.scaleX, 0.001f)
                assertEquals(3.5f, contentFrame.scaleY, 0.001f)

                // Subtitles must remain unscaled at 1.0x
                assertEquals(1.0f, subtitleView.scaleX, 0.001f)
                assertEquals(1.0f, subtitleView.scaleY, 0.001f)
            }
        }
    }

    @Test
    fun resetZoom_restoresContentFrameAndStateToNormal() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            lateinit var playerView: TPStreamsPlayerView
            lateinit var contentFrame: AspectRatioFrameLayout

            scenario.onActivity { activity ->
                playerView = TPStreamsPlayerView(activity)
                activity.setContentView(playerView)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                contentFrame = playerView.findViewById(androidx.media3.ui.R.id.exo_content_frame)

                val zoomField = TPStreamsPlayerView::class.java.getDeclaredField("zoomController")
                zoomField.isAccessible = true
                val zoomController = zoomField.get(playerView) as PlayerZoomController

                zoomController.zoomTo(targetScale = 4.0f, focusX = 300f, focusY = 300f, animated = false)
                assertTrue(playerView.isZoomed)

                playerView.resetZoom(animated = false)

                assertFalse(playerView.isZoomed)
                assertEquals(1.0f, playerView.currentZoomScale, 0.001f)
                assertEquals(1.0f, contentFrame.scaleX, 0.001f)
                assertEquals(1.0f, contentFrame.scaleY, 0.001f)
                assertEquals(0.0f, contentFrame.translationX, 0.001f)
                assertEquals(0.0f, contentFrame.translationY, 0.001f)
            }
        }
    }

    @Test
    fun maxZoomScale_strictlyCappedAt8xAndNeverExceedsMax() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            lateinit var playerView: TPStreamsPlayerView
            lateinit var contentFrame: AspectRatioFrameLayout

            scenario.onActivity { activity ->
                playerView = TPStreamsPlayerView(activity)
                activity.setContentView(playerView)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                contentFrame = playerView.findViewById(androidx.media3.ui.R.id.exo_content_frame)

                val zoomField = TPStreamsPlayerView::class.java.getDeclaredField("zoomController")
                zoomField.isAccessible = true
                val zoomController = zoomField.get(playerView) as PlayerZoomController

                // Attempt to zoom to 12x
                zoomController.zoomTo(targetScale = 12.0f, focusX = 500f, focusY = 500f, animated = false)

                // Must be strictly capped at maxScale (8.0f)
                assertEquals(8.0f, playerView.currentZoomScale, 0.001f)
                assertEquals(8.0f, contentFrame.scaleX, 0.001f)
                assertEquals(8.0f, contentFrame.scaleY, 0.001f)
            }
        }
    }

    @Test
    fun minZoomScale_strictlyDisallowsShrinkingBelowOriginal() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            lateinit var playerView: TPStreamsPlayerView

            scenario.onActivity { activity ->
                playerView = TPStreamsPlayerView(activity)
                activity.setContentView(playerView)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                val zoomField = TPStreamsPlayerView::class.java.getDeclaredField("zoomController")
                zoomField.isAccessible = true
                val zoomController = zoomField.get(playerView) as PlayerZoomController

                // Attempt to scale below 1.0x (e.g. 0.5x)
                zoomController.zoomTo(targetScale = 0.5f, focusX = 500f, focusY = 500f, animated = false)

                // Scale must be clamped to minimum 1.0f
                assertEquals(1.0f, playerView.currentZoomScale, 0.001f)
                assertFalse(playerView.isZoomed)
            }
        }
    }

    @Test
    fun zoomBadgeText_showsOriginalAtRestAndCustomTextAtFill() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val playerView = TPStreamsPlayerView(activity)
                activity.setContentView(playerView)

                val zoomField = TPStreamsPlayerView::class.java.getDeclaredField("zoomController")
                zoomField.isAccessible = true
                val zoomController = zoomField.get(playerView) as PlayerZoomController

                assertEquals("Original", zoomController.getZoomBadgeText(1.0f))
                assertEquals("2.5x", zoomController.getZoomBadgeText(2.5f))
                assertEquals("8.0x", zoomController.getZoomBadgeText(8.0f))
            }
        }
    }

    @Test
    fun fullscreenTransition_automaticallyResetsActiveZoom() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            lateinit var playerView: TPStreamsPlayerView

            scenario.onActivity { activity ->
                val rootLayout = FrameLayout(activity)
                activity.setContentView(rootLayout)

                playerView = TPStreamsPlayerView(activity)
                rootLayout.addView(playerView)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                val zoomField = TPStreamsPlayerView::class.java.getDeclaredField("zoomController")
                zoomField.isAccessible = true
                val zoomController = zoomField.get(playerView) as PlayerZoomController

                zoomController.zoomTo(targetScale = 3.0f, focusX = 400f, focusY = 400f, animated = false)
                assertTrue("Player should be zoomed before fullscreen transition", playerView.isZoomed)

                // Entering or exiting fullscreen triggers setFullscreenButtonState which resets zoom
                playerView.setFullscreenButtonState(true)

                assertFalse("Player zoom must be reset on fullscreen state change", playerView.isZoomed)
                assertEquals(1.0f, playerView.currentZoomScale, 0.001f)
            }
        }
    }

    @Test
    fun doubleTapZoom_animatesToTargetAndResetsOnSecondDoubleTap() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            lateinit var playerView: TPStreamsPlayerView

            scenario.onActivity { activity ->
                playerView = TPStreamsPlayerView(activity)
                activity.setContentView(
                    playerView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                val now = SystemClock.uptimeMillis()
                // Tap 1
                playerView.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 500f, 500f, 0))
                playerView.dispatchTouchEvent(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, 500f, 500f, 0))

                // Tap 2 (double tap)
                playerView.dispatchTouchEvent(MotionEvent.obtain(now, now + 120, MotionEvent.ACTION_DOWN, 500f, 500f, 0))
                playerView.dispatchTouchEvent(MotionEvent.obtain(now, now + 170, MotionEvent.ACTION_UP, 500f, 500f, 0))
            }

            // Wait for 250ms value animation to complete
            SystemClock.sleep(400)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                assertTrue("Player should be zoomed in after double tap", playerView.isZoomed)
                assertTrue("Scale should be greater than 1.0f after double tap", playerView.currentZoomScale > 1.05f)

                // Now double tap again to reset
                val now2 = SystemClock.uptimeMillis()
                playerView.dispatchTouchEvent(MotionEvent.obtain(now2, now2, MotionEvent.ACTION_DOWN, 500f, 500f, 0))
                playerView.dispatchTouchEvent(MotionEvent.obtain(now2, now2 + 50, MotionEvent.ACTION_UP, 500f, 500f, 0))
                playerView.dispatchTouchEvent(MotionEvent.obtain(now2, now2 + 120, MotionEvent.ACTION_DOWN, 500f, 500f, 0))
                playerView.dispatchTouchEvent(MotionEvent.obtain(now2, now2 + 170, MotionEvent.ACTION_UP, 500f, 500f, 0))
            }

            SystemClock.sleep(400)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                assertFalse("Player should not be zoomed after second double tap", playerView.isZoomed)
                assertEquals(1.0f, playerView.currentZoomScale, 0.05f)
            }
        }
    }
}
