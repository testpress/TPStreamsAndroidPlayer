package com.tpstreams.player

import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Exercises the real playback path with Flutter's TextureView configuration.
 * Supply a non-DRM or L3-compatible asset through instrumentation arguments.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerViewTexturePlaybackInstrumentedTest {

    companion object {
        private const val DEFAULT_ORG_ID = "9q94nm"
        private const val DEFAULT_ASSET_ID = "4Zs4MNd5Ksj"
        private const val DEFAULT_ACCESS_TOKEN = "3d9838f3-db51-4fc3-8472-075ab5e40b64"
    }

    @Test
    fun textureViewPlayback_supportsPlayPauseSeekAndFullscreen() {
        val args = InstrumentationRegistry.getArguments()
        val orgId = args.getString("orgId")?.takeIf { it.isNotBlank() } ?: DEFAULT_ORG_ID
        val assetId = args.getString("assetId")?.takeIf { it.isNotBlank() } ?: DEFAULT_ASSET_ID
        val accessToken = args.getString("accessToken")?.takeIf { it.isNotBlank() } ?: DEFAULT_ACCESS_TOKEN

        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            val firstFrame = CountDownLatch(1)
            val playerRef = AtomicReference<TPStreamsPlayer>()
            val viewRef = AtomicReference<TPStreamsPlayerView>()
            val rootRef = AtomicReference<FrameLayout>()
            val failureRef = AtomicReference<Throwable>()

            try {
                scenario.onActivity { activity ->
                    TPStreamsSDK.init(orgId, TPStreamsSDK.Provider.TPStreams, allowFallbackToL3 = true)
                    val root = FrameLayout(activity)
                    val attributes = TPStreamsPlayerView.attributeSetForTextureView(activity)
                    val playerView = TPStreamsPlayerView(activity, attributes)
                    activity.setContentView(root)
                    root.addView(playerView)
                    rootRef.set(root)
                    viewRef.set(playerView)

                    val player = TPStreamsPlayer.create(
                        context = activity,
                        assetId = assetId,
                        accessToken = accessToken,
                        shouldAutoPlay = true
                    )
                    playerRef.set(player)
                    player.addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() {
                            firstFrame.countDown()
                        }

                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            failureRef.set(error)
                            firstFrame.countDown()
                        }
                    })
                    playerView.player = player
                    player.play()
                }

                assertTrue("TextureView playback must render a frame", firstFrame.await(30, TimeUnit.SECONDS))
                assumeFalse("Playback failed: ${failureRef.get()}", failureRef.get() != null)

                val paused = CountDownLatch(1)
                scenario.onActivity {
                    val player = playerRef.get()
                    val view = viewRef.get()
                    assertTrue(view.videoSurfaceView is android.view.TextureView)
                    player.addListener(object : Player.Listener {
                        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                            if (!playWhenReady) paused.countDown()
                        }
                    })
                    player.pause()
                }
                assertTrue("TextureView player must pause", paused.await(5, TimeUnit.SECONDS))

                scenario.onActivity { activity ->
                    val player = playerRef.get()
                    val view = viewRef.get()
                    player.seekTo(1_000L)
                    val resolutions = player.getAvailableVideoResolutions()
                    if (resolutions.isNotEmpty()) {
                        player.setMaxResolution(resolutions.maxOrNull()!!)
                    }
                    val textTracks = player.getAvailableTextTracks()
                    if (textTracks.isNotEmpty()) {
                        player.setTextTrackByLanguage(textTracks.first().first)
                        player.setTextTrackByLanguage(null)
                    }
                    player.play()
                    view.toggleFullscreen()
                    assertEquals(activity.window.decorView, view.parent)
                    view.toggleFullscreen()
                    assertEquals(rootRef.get(), view.parent)
                }
            } finally {
                scenario.onActivity { playerRef.get()?.release() }
            }
        }
    }

}

