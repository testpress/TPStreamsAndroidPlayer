package com.tpstreams.player

import android.os.SystemClock
import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
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
        private const val DEFAULT_ORG_ID = "9q94nm"
        private const val DEFAULT_ASSET_ID = "7xbZeQzR36h"
        private const val DEFAULT_ACCESS_TOKEN = "3d9838f3-db51-4fc3-8472-075ab5e40b64"
    }

    @Test
    fun l1DrmOnTextureView_isStoppedInsteadOfRenderingBlackVideo() {
        val args = InstrumentationRegistry.getArguments()
        val orgId = args.getString("orgId")?.takeIf { it.isNotBlank() } ?: DEFAULT_ORG_ID
        val assetId = args.getString("drmAssetId")?.takeIf { it.isNotBlank() } ?: DEFAULT_ASSET_ID
        val accessToken = args.getString("drmAccessToken")?.takeIf { it.isNotBlank() } ?: DEFAULT_ACCESS_TOKEN

        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            var player: TPStreamsPlayer? = null
            val tracksChanged = CountDownLatch(1)
            val isPlaying = AtomicBoolean(true)

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
                    assumeTrue(
                        "This guard test requires a native L1 device",
                        createdPlayer.getNativeWidevineLevel() == "L1"
                    )
                    createdPlayer.addListener(object : Player.Listener {
                        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                            tracksChanged.countDown()
                        }
                    })
                    playerView.player = createdPlayer
                    createdPlayer.play()
                }

                assertTrue("DRM tracks must be reported before guard evaluation", tracksChanged.await(30, TimeUnit.SECONDS))
                val deadline = SystemClock.elapsedRealtime() + 5_000L
                while (SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity {
                        isPlaying.set(player!!.isPlaying)
                    }
                    if (!isPlaying.get()) break
                    SystemClock.sleep(100L)
                }
                assertFalse("L1 DRM TextureView playback must not remain active", isPlaying.get())
            } finally {
                scenario.onActivity { player?.release() }
            }
        }
    }
}
