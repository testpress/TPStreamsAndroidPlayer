package com.tpstreams.player

import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    }

    @Test
    fun textureViewPlayback_supportsPlayPauseSeekAndFullscreen() {
        val orgId = PlayerViewTestSupport.instrumentationArgumentOrDefault("orgId", PlayerViewTestSupport.DEFAULT_ORG_ID)
        val assetId = PlayerViewTestSupport.instrumentationArgumentOrDefault("assetId", PlayerViewTestSupport.DEFAULT_NON_DRM_ASSET_ID)
        val accessToken = PlayerViewTestSupport.instrumentationArgumentOrDefault("accessToken", PlayerViewTestSupport.DEFAULT_ACCESS_TOKEN)

        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            val firstFrame = CountDownLatch(1)
            val seeked = CountDownLatch(1)
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

                        override fun onPositionDiscontinuity(
                            oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo,
                            reason: Int,
                        ) {
                            if (reason == Player.DISCONTINUITY_REASON_SEEK) seeked.countDown()
                        }
                    })
                    playerView.player = player
                    player.play()
                }

                assertTrue("TextureView playback must render a frame", firstFrame.await(30, TimeUnit.SECONDS))
                assertNull("TextureView playback reported an error: ${failureRef.get()}", failureRef.get())

                val paused = CountDownLatch(1)
                scenario.onActivity {
                    val player = playerRef.get()
                    val view = viewRef.get()
                    assertTrue("Texture playback test must be using TextureView", view.videoSurfaceView is android.view.TextureView)
                    player.addListener(object : Player.Listener {
                        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                            if (!playWhenReady) paused.countDown()
                        }
                    })
                    player.pause()
                }
                assertTrue("TextureView player did not report pause within 5 seconds", paused.await(5, TimeUnit.SECONDS))

                scenario.onActivity { activity ->
                    val player = playerRef.get()
                    val view = viewRef.get()
                    player.seekTo(1_000L)
                    val resolutions = player.getAvailableVideoResolutions()
                    if (resolutions.isNotEmpty()) {
                        player.setMaxResolution(resolutions.maxOrNull()!!)
                        assertTrue("Player should expose at least one available video resolution", player.getAvailableVideoResolutions().isNotEmpty())
                    }
                    val textTracks = player.getAvailableTextTracks()
                    if (textTracks.isNotEmpty()) {
                        player.setTextTrackByLanguage(textTracks.first().first)
                        assertEquals("Selected text track should become active", textTracks.first().first, player.getActiveTextTrack()?.first)
                        player.setTextTrackByLanguage(null)
                        assertEquals("Text track should be disabled after clearing the selection", null, player.getActiveTextTrack())
                    }
                    player.play()
                    view.toggleFullscreen()
                    assertEquals("TextureView should move to the decor during fullscreen", activity.window.decorView, view.parent)
                    view.toggleFullscreen()
                    assertEquals("TextureView should return to its original parent after fullscreen", rootRef.get(), view.parent)
                }
                assertTrue("Seek request must produce a seek discontinuity within 5 seconds", seeked.await(5, TimeUnit.SECONDS))
            } finally {
                scenario.onActivity { playerRef.get()?.release() }
            }
        }
    }

}

