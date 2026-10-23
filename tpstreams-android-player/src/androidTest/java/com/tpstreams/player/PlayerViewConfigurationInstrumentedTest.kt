package com.tpstreams.player

import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerViewConfigurationInstrumentedTest {

    @Test
    fun zoomAndGestureConfiguration_preservesPublicBehavior() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val playerView = TPStreamsPlayerView(activity)
                activity.setContentView(playerView)

                playerView.isPinchToZoomEnabled = false
                playerView.maxZoomScale = 4.0f
                playerView.showZoomIndicator = false
                playerView.zoomOnlyInFullscreen = true
                playerView.isDoubleTapToSeekEnabled = false
                playerView.doubleTapSeekIncrementMs = 10_000L
                playerView.showDoubleTapSeekOverlay = false
                playerView.resetZoom(animated = false)

                assertFalse("Pinch-to-zoom should be disabled after configuration", playerView.isPinchToZoomEnabled)
                assertEquals("Maximum zoom scale should be 4x", 4.0f, playerView.maxZoomScale, 0.001f)
                assertFalse("Zoom indicator should be hidden after configuration", playerView.showZoomIndicator)
                assertTrue("Zoom should be restricted to fullscreen", playerView.zoomOnlyInFullscreen)
                assertFalse("Double-tap seeking should be disabled after configuration", playerView.isDoubleTapToSeekEnabled)
                assertEquals("Double-tap seek increment should be 10 seconds", 10_000L, playerView.doubleTapSeekIncrementMs)
                assertFalse("Double-tap seek overlay should be hidden after configuration", playerView.showDoubleTapSeekOverlay)
                assertEquals("Reset zoom should restore the 1x scale", 1.0f, playerView.currentZoomScale, 0.001f)
            }
        }
    }

    @Test
    fun playerViewComponents_andResolutionConfiguration_areAvailable() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val playerView = TPStreamsPlayerView(activity)
                activity.setContentView(playerView)

                assertEquals("PlayerView should resolve its hosting Activity", activity, playerView.getActivity())
                assertNotNull("Settings bottom sheet should be available", playerView.settingsBottomSheet)
                assertNotNull("Quality bottom sheet should be available", playerView.qualityOptionsBottomSheet)
                assertNotNull("Advanced resolution bottom sheet should be available", playerView.advancedResolutionBottomSheet)
                assertNotNull("Playback speed bottom sheet should be available", playerView.playbackSpeedBottomSheet)
                assertNotNull("Captions bottom sheet should be available", playerView.captionsBottomSheet)
                assertNotNull("Download options bottom sheet should be available", playerView.downloadOptionsBottomSheet)
                assertNotNull("Download action bottom sheet should be available", playerView.downloadActionBottomSheet)

                playerView.setVideoResolution(720)
                playerView.setAutoFullscreenOnRotateEnabled(false)
            }
        }
    }
}

