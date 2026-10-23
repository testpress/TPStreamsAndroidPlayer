package com.tpstreams.player

import android.os.SystemClock
import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Verifies the intentional safety behavior for hardware-secure Widevine:
 * L1 DRM must not be attempted through Flutter's TextureView surface.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerViewTextureDrmGuardInstrumentedTest {

    companion object {
    }

    @Test
    fun l1DrmOnTextureView_isStoppedInsteadOfRenderingBlackVideo() {
        val orgId = PlayerViewTestSupport.instrumentationArgumentOrDefault("orgId", PlayerViewTestSupport.DEFAULT_ORG_ID)
        val assetId = PlayerViewTestSupport.instrumentationArgumentOrDefault("drmAssetId", PlayerViewTestSupport.DEFAULT_DRM_ASSET_ID)
        val accessToken = PlayerViewTestSupport.instrumentationArgumentOrDefault("drmAccessToken", PlayerViewTestSupport.DEFAULT_ACCESS_TOKEN)

        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            var player: TPStreamsPlayer? = null
            val tracksChanged = CountDownLatch(1)
            val isPlaying = AtomicBoolean(true)
            val playbackState = java.util.concurrent.atomic.AtomicInteger(Player.STATE_IDLE)
            var nativeLevel: String? = null

            try {
                scenario.onActivity { activity ->
                    TPStreamsSDK.init(orgId, TPStreamsSDK.Provider.TPStreams, allowFallbackToL3 = true)
                    val root = FrameLayout(activity)
                    val playerView = TPStreamsPlayerView(
                        activity,
                        TPStreamsPlayerView.attributeSetForTextureView(activity)
                    )
                    activity.setContentView(root)
                    root.addView(playerView)

                    val createdPlayer = TPStreamsPlayer.create(
                        context = activity,
                        assetId = assetId,
                        accessToken = accessToken,
                        shouldAutoPlay = true
                    )
                    player = createdPlayer
                    nativeLevel = createdPlayer.getNativeWidevineLevel()
                    createdPlayer.addListener(object : Player.Listener {
                        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                            tracksChanged.countDown()
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            playbackState.set(state)
                        }
                    })
                    playerView.player = createdPlayer
                    createdPlayer.play()
                }

                assumeTrue("This guard test requires a native L1 device; detected=$nativeLevel", nativeLevel == "L1")
                assertTrue("DRM tracks must be reported before guard evaluation", tracksChanged.await(30, TimeUnit.SECONDS))
                assertTrue(
                    "The configured asset must be recognized as DRM before testing the TextureView guard",
                    scenarioOnActivity(scenario) { player!!.isDrmContent }
                )
                val deadline = SystemClock.elapsedRealtime() + 5_000L
                while (SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity {
                        isPlaying.set(player!!.isPlaying)
                    }
                    if (!isPlaying.get()) break
                    SystemClock.sleep(100L)
                }
                assertFalse("L1 DRM TextureView playback must not remain active", isPlaying.get())
                assertEquals(
                    "L1 DRM TextureView guard must stop the player in STATE_IDLE",
                    Player.STATE_IDLE,
                    playbackState.get()
                )
            } finally {
                scenario.onActivity { player?.release() }
            }
        }
    }

    private fun <T> scenarioOnActivity(
        scenario: ActivityScenario<FullscreenTestActivity>,
        block: FullscreenTestActivity.() -> T,
    ): T {
        var result: T? = null
        scenario.onActivity { result = block(it) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
