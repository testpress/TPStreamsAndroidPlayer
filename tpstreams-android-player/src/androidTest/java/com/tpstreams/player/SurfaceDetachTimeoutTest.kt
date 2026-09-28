package com.tpstreams.player

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class SurfaceDetachTimeoutTest {

    @Test
    fun testClearVideoSurface_whenDetachTimeoutExceeded_triggersErrorCodeTimeout() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        var errorReceived: PlaybackException? = null
        val errorLatch = CountDownLatch(1)
        val readyLatch = CountDownLatch(1)

        var player: ExoPlayer? = null
        var surface: Surface? = null
        var surfaceTexture: SurfaceTexture? = null

        instrumentation.runOnMainSync {
            surfaceTexture = SurfaceTexture(0)
            surface = Surface(surfaceTexture)

            // Force a 1ms detach surface timeout so that clearing the surface during playback times out
            player = ExoPlayer.Builder(context)
                .setDetachSurfaceTimeoutMs(1)
                .build()

            player?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        readyLatch.countDown()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    errorReceived = error
                    errorLatch.countDown()
                }
            })

            player?.setVideoSurface(surface)
            // Use Google's standard sample test video to engage video decoding
            val mediaItem = MediaItem.fromUri("https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4")
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.playWhenReady = true
        }

        // Wait until player is ready (video decoding started)
        val isReady = readyLatch.await(15, TimeUnit.SECONDS)
        assertTrue("Player should reach STATE_READY within timeout", isReady)

        instrumentation.runOnMainSync {
            // Clearing the active video surface while decoding with 1ms threshold triggers timeout
            player?.clearVideoSurface()
        }

        // Wait for ERROR_CODE_TIMEOUT error callback
        val timeoutOccurred = errorLatch.await(5, TimeUnit.SECONDS)

        instrumentation.runOnMainSync {
            player?.release()
            surface?.release()
            surfaceTexture?.release()
        }

        assertTrue("Expected player error callback to be triggered", timeoutOccurred)
        assertNotNull("Expected PlaybackException to be captured", errorReceived)
        assertEquals(
            "Expected ERROR_CODE_TIMEOUT (1003)",
            PlaybackException.ERROR_CODE_TIMEOUT,
            errorReceived?.errorCode
        )
        assertTrue(
            "Expected isSurfaceDetachTimeout to detect the surface detach timeout error",
            TPStreamsPlayer.isSurfaceDetachTimeout(errorReceived!!)
        )
    }
}
