package com.tpstreams.player

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpstreams.player.constants.PlaybackError
import com.tpstreams.player.download.DownloadController
import com.tpstreams.player.util.CodecManager
import com.tpstreams.player.util.WidevineDrmSessionManagerProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Connected instrumented test specifically targeting the HARDWARE DECODER & RESOURCE LEAK
 * caused by ExoPlayer release timeouts (Sentry issue ANDROID-PLAYER-SDK-2-38GDMQ1BS).
 *
 * When player.release() times out:
 * 1. ExoPlayer's internal playback thread is stuck/aborted during teardown.
 * 2. AnalyticsListener.onVideoDecoderReleased is NEVER triggered.
 * 3. CodecManager.getActiveDecoderCount() remains > 0 (hardware decoder is leaked in the OS).
 * 4. Successive playbacks accumulate leaked decoder instances until the device runs out of
 *    hardware decoders (causing MediaCodec NO_MEMORY or DECODER_INIT_FAILED crashes).
 *
 * Run on a connected device:
 * ```
 * ./gradlew :tpstreams-android-player:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.tpstreams.player.PlayerReleaseTimeoutInstrumentedTest
 * ```
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlayerReleaseTimeoutInstrumentedTest {

    companion object {
        private const val TAG = "PlayerReleaseLeakTest"
        private const val DEFAULT_ORG_ID = "9q94nm"
        private const val DEFAULT_ASSET_ID = "7xbZeQzR36h" // DRM sample video
        private const val DEFAULT_ACCESS_TOKEN = "3d9838f3-db51-4fc3-8472-075ab5e40b64"
    }

    private fun createPlayerWithReleaseTimeout(
        context: Context,
        assetId: String,
        accessToken: String,
        releaseTimeoutMs: Long
    ): Pair<TPStreamsPlayer, ExoPlayer> {
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = DefaultTrackSelector.Parameters.Builder()
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .build()
        }

        val renderersFactory = DefaultRenderersFactory(context.applicationContext)
            .setEnableDecoderFallback(true)

        DownloadController.initialize(context)

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(DownloadController.downloadCache)
            .setUpstreamDataSourceFactory(DownloadController.httpDataSourceFactory)
            .setCacheWriteDataSinkFactory(null)

        val drmSessionManagerProvider = WidevineDrmSessionManagerProvider(
            DownloadController.httpDataSourceFactory,
        )

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(cacheDataSourceFactory)
            .setDrmSessionManagerProvider(drmSessionManagerProvider)

        val exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setReleaseTimeoutMs(releaseTimeoutMs)
            .build()

        val player = TPStreamsPlayer(
            context = context,
            exoPlayer = exoPlayer,
            trackSelector = trackSelector,
            assetId = assetId,
            accessToken = accessToken,
            shouldAutoPlay = true
        )

        return player to exoPlayer
    }

    /**
     * Test 1: Verifies the HARDWARE DECODER LEAK when release times out.
     *
     * When release times out:
     * - onVideoDecoderReleased NEVER fires
     * - CodecManager.getActiveDecoderCount() fails to return to 0
     * - The hardware decoder instance is leaked in the Android mediaserver
     */
    @Test
    fun testReleaseTimeout_causesHardwareDecoderLeak() {
        val args = InstrumentationRegistry.getArguments()
        val orgId = args.getString("orgId")?.takeIf { it.isNotBlank() } ?: DEFAULT_ORG_ID
        val assetId = args.getString("assetId")?.takeIf { it.isNotBlank() } ?: DEFAULT_ASSET_ID
        val accessToken = args.getString("accessToken")?.takeIf { it.isNotBlank() } ?: DEFAULT_ACCESS_TOKEN

        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            var player: TPStreamsPlayer? = null
            var exoPlayer: ExoPlayer? = null

            val firstFrameLatch = CountDownLatch(1)
            val decoderReleasedLatch = CountDownLatch(1)
            val timeoutErrorReceived = AtomicBoolean(false)
            val initialActiveDecoders = CodecManager.getActiveDecoderCount()

            scenario.onActivity { activity ->
                TPStreamsSDK.init(orgId, TPStreamsSDK.Provider.TPStreams, allowFallbackToL3 = true)

                val rootLayout = FrameLayout(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                activity.setContentView(rootLayout)

                val pv = TPStreamsPlayerView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                rootLayout.addView(pv)

                // 1ms timeout simulates the exact condition where the hardware decoder doesn't finish in time
                val (p, exo) = createPlayerWithReleaseTimeout(
                    context = activity,
                    assetId = assetId,
                    accessToken = accessToken,
                    releaseTimeoutMs = 1L
                )
                player = p
                exoPlayer = exo

                p.listener = object : TPStreamsPlayer.Listener {
                    override fun onAccessTokenExpired(videoId: String, callback: (String) -> Unit) {}

                    override fun onError(error: PlaybackError, message: String) {
                        Log.e(TAG, "onError received: error=$error, msg=$message")
                        if (error == PlaybackError.UNSPECIFIED || message.contains("timeout", ignoreCase = true)) {
                            timeoutErrorReceived.set(true)
                        }
                    }
                }

                exo.addAnalyticsListener(object : AnalyticsListener {
                    override fun onRenderedFirstFrame(
                        eventTime: AnalyticsListener.EventTime,
                        output: Any,
                        renderTimeMs: Long
                    ) {
                        Log.d(TAG, "First frame rendered! Active decoders: ${CodecManager.getActiveDecoderCount()}")
                        firstFrameLatch.countDown()
                    }

                    override fun onVideoDecoderReleased(
                        eventTime: AnalyticsListener.EventTime,
                        decoderName: String
                    ) {
                        Log.d(TAG, "Video decoder cleanly released: $decoderName")
                        decoderReleasedLatch.countDown()
                    }

                    override fun onPlayerError(
                        eventTime: AnalyticsListener.EventTime,
                        error: PlaybackException
                    ) {
                        Log.e(TAG, "ExoPlayer onPlayerError: ${error.errorCodeName} - ${error.message}")
                    }
                })

                pv.player = p
                p.play()
            }

            // Wait for video playback to start and active hardware decoding to begin
            val playbackStarted = firstFrameLatch.await(10, TimeUnit.SECONDS)
            assertTrue("Playback should start before testing release", playbackStarted)

            val decodersDuringPlayback = CodecManager.getActiveDecoderCount()
            Log.i(TAG, "Decoders active during playback: $decodersDuringPlayback")
            assertTrue("Hardware decoder must be active during playback", decodersDuringPlayback > initialActiveDecoders)

            // Now trigger release on the main thread
            scenario.onActivity {
                Log.d(TAG, "Calling player.release()...")
                player?.release()
            }

            // Wait to see if decoder is ever released
            val decoderReleased = decoderReleasedLatch.await(3, TimeUnit.SECONDS)
            Log.i(TAG, "Decoder released event received: $decoderReleased")

            // Wait briefly (up to 2s) for the background playback thread to finish releasing all decoders (audio & video)
            val deadline = SystemClock.elapsedRealtime() + 2000
            while (SystemClock.elapsedRealtime() < deadline && CodecManager.getActiveDecoderCount() > initialActiveDecoders) {
                Thread.sleep(50)
            }
            val decodersAfterRelease = CodecManager.getActiveDecoderCount()
            Log.i(TAG, "Active decoders remaining after release: $decodersAfterRelease")

            // VERIFICATION:
            // The video decoder must be cleanly released and active decoders return to baseline
            assertTrue(
                "Video decoder must be cleanly released within the timeout window",
                decoderReleased
            )
            assertEquals(
                "Active decoders must return to baseline after release",
                initialActiveDecoders,
                decodersAfterRelease
            )
        }
    }

    /**
     * Test 2: Successive Playbacks Decoder Accumulation Test.
     *
     * Validates whether successive playbacks cause leaked decoder instances to pile up
     * in the application process when release fails.
     */
    @Test
    fun testSuccessivePlaybacks_decoderAccumulationOnTimeout() {
        val args = InstrumentationRegistry.getArguments()
        val orgId = args.getString("orgId")?.takeIf { it.isNotBlank() } ?: DEFAULT_ORG_ID
        val assetId = args.getString("assetId")?.takeIf { it.isNotBlank() } ?: DEFAULT_ASSET_ID
        val accessToken = args.getString("accessToken")?.takeIf { it.isNotBlank() } ?: DEFAULT_ACCESS_TOKEN

        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            val initialDecoders = CodecManager.getActiveDecoderCount()
            Log.i(TAG, "Starting baseline decoder count: $initialDecoders")

            val decoderCountsAfterEachRelease = mutableListOf<Int>()

            for (cycle in 1..2) {
                val firstFrameLatch = CountDownLatch(1)
                var player: TPStreamsPlayer? = null

                scenario.onActivity { activity ->
                    TPStreamsSDK.init(orgId, TPStreamsSDK.Provider.TPStreams, allowFallbackToL3 = true)

                    val pv = TPStreamsPlayerView(activity)
                    activity.setContentView(pv)

                    val (p, exo) = createPlayerWithReleaseTimeout(
                        context = activity,
                        assetId = assetId,
                        accessToken = accessToken,
                        releaseTimeoutMs = 1L
                    )
                    player = p

                    exo.addAnalyticsListener(object : AnalyticsListener {
                        override fun onRenderedFirstFrame(
                            eventTime: AnalyticsListener.EventTime,
                            output: Any,
                            renderTimeMs: Long
                        ) {
                            firstFrameLatch.countDown()
                        }
                    })

                    pv.player = p
                    p.play()
                }

                assertTrue("Playback $cycle should start", firstFrameLatch.await(10, TimeUnit.SECONDS))

                scenario.onActivity {
                    player?.release()
                }

                // Wait briefly (up to 1s) for the background playback thread to finish releasing native resources
                val deadline = SystemClock.elapsedRealtime() + 1000
                while (SystemClock.elapsedRealtime() < deadline && CodecManager.getActiveDecoderCount() > initialDecoders) {
                    Thread.sleep(50)
                }
                val currentDecoders = CodecManager.getActiveDecoderCount()
                decoderCountsAfterEachRelease.add(currentDecoders)
                Log.w(TAG, "Cycle $cycle finished: active decoders remaining = $currentDecoders")
            }

            Log.i(TAG, "Decoder counts across cycles: $decoderCountsAfterEachRelease")

            // Asserts that decoders do NOT accumulate and return cleanly to baseline
            assertEquals(
                "Decoders must return to baseline and not accumulate (found: $decoderCountsAfterEachRelease)",
                initialDecoders,
                decoderCountsAfterEachRelease.last()
            )
        }
    }
}
