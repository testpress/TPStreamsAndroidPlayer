package com.tpstreams.player

import android.view.TextureView
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerViewSurfaceInstrumentedTest {

    @Test
    fun defaultView_usesProtectedSurfaceForL1Drm() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(
                    "Default PlayerView must use SurfaceView for protected L1 DRM playback",
                    TPStreamsPlayerView(activity).videoSurfaceView is TextureView
                )
            }
        }
    }

    @Test
    fun flutterTextureAttributes_selectTextureViewForHybridComposition() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val attributes = TPStreamsPlayerView.attributeSetForTextureView(activity)
                assertNotNull("TextureView AttributeSet must be created for Flutter composition", attributes)

                val playerView = TPStreamsPlayerView(activity, attributes)
                assertTrue("Flutter composition PlayerView must use TextureView", playerView.videoSurfaceView is TextureView)
            }
        }
    }
}

