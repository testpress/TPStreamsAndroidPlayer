package com.tpstreams.player

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerCoreControlsInstrumentedTest {

    companion object {
    }

    @Test
    fun corePlayer_controlsTracksAndRelease_areSafeAcrossAsyncBoundaries() {
        val orgId = PlayerViewTestSupport.instrumentationArgumentOrDefault("orgId", PlayerViewTestSupport.DEFAULT_ORG_ID)
        val assetId = PlayerViewTestSupport.instrumentationArgumentOrDefault("assetId", PlayerViewTestSupport.DEFAULT_NON_DRM_ASSET_ID)
        val token = PlayerViewTestSupport.instrumentationArgumentOrDefault("accessToken", PlayerViewTestSupport.DEFAULT_ACCESS_TOKEN)
        val ready = CountDownLatch(1)
        val error = AtomicBoolean(false)
        var player: TPStreamsPlayer? = null

        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                TPStreamsSDK.init(orgId, allowFallbackToL3 = true)
                player = TPStreamsPlayer.create(activity, assetId, token, shouldAutoPlay = false)
                player!!.addListener(object : TPStreamsPlayer.Listener {
                    override fun onError(errorValue: com.tpstreams.player.constants.PlaybackError, message: String) {
                        error.set(true)
                    }
                })
                player!!.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) ready.countDown()
                    }
                })
            }

            assertTrue("asset did not become ready", ready.await(30, TimeUnit.SECONDS))
            assertFalse("unexpected playback error", error.get())

            scenario.onActivity {
                player!!.setPlaybackSpeed(1.5f)
                assertEquals("Player playback speed should change to 1.5x", 1.5f, player!!.getPlaybackSpeed(), 0.001f)
                player!!.setMaxResolution(720)
                player!!.setTextTrackByLanguage(null)
                player!!.play()
            }
            Thread.sleep(800)
            scenario.onActivity {
                assertTrue("Player should be playing after the asynchronous play command", player!!.isPlaying)
                player!!.pause()
            }
            Thread.sleep(250)
            scenario.onActivity {
                assertFalse("Player should be paused after the asynchronous pause command", player!!.isPlaying)
                player!!.release()
                player!!.release()
            }
        }
    }
}
