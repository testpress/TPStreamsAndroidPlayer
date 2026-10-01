package com.tpstreams.player

import android.content.pm.ActivityInfo
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * End-to-end connected instrumented test verifying that fullscreen transitions
 * preserve the Activity instance, Player instance, and hardware decoder without
 * recreation, decoder re-initialization, or surface detachment timeouts.
 *
 * By default, this test runs against the sample Widevine DRM video (assetId: 7xbZeQzR36h).
 * Custom or non-DRM assets can be passed via instrumentation arguments (e.g. non-DRM sample 4Zs4MNd5Ksj):
 * ```
 * ./gradlew :tpstreams-android-player:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.tpstreams.player.PlayerFullscreenLifecycleInstrumentedTest \
 *   -Pandroid.testInstrumentationRunnerArguments.orgId=9q94nm \
 *   -Pandroid.testInstrumentationRunnerArguments.assetId=4Zs4MNd5Ksj \
 *   -Pandroid.testInstrumentationRunnerArguments.accessToken=c4f36a4f-3859-4b24-aca8-189b7e8cfeb0
 * ```
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlayerFullscreenLifecycleInstrumentedTest {

    companion object {
        private const val TAG = "PlayerFullscreenLifecycle"
        private const val DEFAULT_ORG_ID = "9q94nm"
        private const val DEFAULT_ASSET_ID = "7xbZeQzR36h" // DRM sample video
        private const val DEFAULT_ACCESS_TOKEN = "3d9838f3-db51-4fc3-8472-075ab5e40b64"
    }

    @Test
    fun fullscreenAndOrientationCycles_preservesActivityAndDecoderWithoutErrors() {
        val args = InstrumentationRegistry.getArguments()
        val orgId = args.getString("orgId")?.takeIf { it.isNotBlank() } ?: DEFAULT_ORG_ID
        val assetId = args.getString("assetId")?.takeIf { it.isNotBlank() } ?: DEFAULT_ASSET_ID
        val accessToken = args.getString("accessToken")?.takeIf { it.isNotBlank() } ?: DEFAULT_ACCESS_TOKEN

        val instrumentation = InstrumentationRegistry.getInstrumentation()

        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            var playerView: TPStreamsPlayerView? = null
            var player: TPStreamsPlayer? = null
            var originalContainer: ViewGroup? = null
            var fullscreenMode: FullscreenMode? = null
            var initialActivityHash = 0
            var initialPlayerHash = 0

            val decoderInitCount = AtomicInteger(0)
            val decoderReleaseCount = AtomicInteger(0)
            val renderedFirstFrameCount = AtomicInteger(0)
            val errors = CopyOnWriteArrayList<Throwable>()
            val apiErrors = CopyOnWriteArrayList<com.tpstreams.player.constants.PlaybackError>()
            val tokenExpired = java.util.concurrent.atomic.AtomicBoolean(false)

            val firstFrameLatch = CountDownLatch(1)
            val playingLatch = CountDownLatch(1)

            scenario.onActivity { activity ->
                initialActivityHash = System.identityHashCode(activity)
                Log.d(TAG, "Activity launched: hash=$initialActivityHash")

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                    activity.setShowWhenLocked(true)
                    activity.setTurnScreenOn(true)
                }
                activity.window.addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                )

                TPStreamsSDK.init(orgId, TPStreamsSDK.Provider.TPStreams, allowFallbackToL3 = true)

                val rootLayout = FrameLayout(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                activity.setContentView(rootLayout)

                val container = FrameLayout(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                rootLayout.addView(container)
                originalContainer = container

                val pv = TPStreamsPlayerView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                playerView = pv
                container.addView(pv)

                // SurfaceView diagnostic logging only (do not assert exact counts)
                val sv = pv.videoSurfaceView as? SurfaceView
                sv?.holder?.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        Log.d(TAG, "SURFACE_CREATED: surfaceId=${System.identityHashCode(holder.surface)}")
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        Log.d(TAG, "SURFACE_CHANGED: size=${width}x${height}")
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        Log.d(TAG, "SURFACE_DESTROYED: surfaceId=${System.identityHashCode(holder.surface)}")
                    }
                })

                val p = TPStreamsPlayer.create(
                    context = activity,
                    assetId = assetId,
                    accessToken = accessToken,
                    shouldAutoPlay = true
                )
                player = p
                initialPlayerHash = System.identityHashCode(p)

                p.listener = object : TPStreamsPlayer.Listener {
                    override fun onAccessTokenExpired(videoId: String, callback: (String) -> Unit) {
                        tokenExpired.set(true)
                        Log.w(TAG, "ACCESS_TOKEN_EXPIRED: videoId=$videoId")
                    }

                    override fun onError(error: com.tpstreams.player.constants.PlaybackError, message: String) {
                        apiErrors.add(error)
                        Log.e(TAG, "API_ERROR: error=$error, msg=$message")
                    }
                }

                val exoField = TPStreamsPlayer::class.java.getDeclaredField("exoPlayer").apply { isAccessible = true }
                val exo = exoField.get(p) as ExoPlayer

                exo.addAnalyticsListener(object : AnalyticsListener {
                    override fun onVideoDecoderInitialized(
                        eventTime: AnalyticsListener.EventTime,
                        decoderName: String,
                        initializedTimestampMs: Long,
                        initializationDurationMs: Long
                    ) {
                        val count = decoderInitCount.incrementAndGet()
                        Log.d(TAG, "DECODER_INIT: codec=$decoderName, count=$count")
                    }

                    override fun onVideoDecoderReleased(
                        eventTime: AnalyticsListener.EventTime,
                        decoderName: String
                    ) {
                        val count = decoderReleaseCount.incrementAndGet()
                        Log.d(TAG, "DECODER_RELEASED: codec=$decoderName, count=$count")
                    }

                    override fun onRenderedFirstFrame(
                        eventTime: AnalyticsListener.EventTime,
                        output: Any,
                        renderTimeMs: Long
                    ) {
                        val count = renderedFirstFrameCount.incrementAndGet()
                        Log.d(TAG, "RENDERED_FIRST_FRAME (Analytics): count=$count")
                        firstFrameLatch.countDown()
                    }

                    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
                        errors.add(error)
                        Log.e(TAG, "PLAYER_ERROR: code=${error.errorCodeName}, msg=${error.message}")
                    }
                })

                p.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d(TAG, "IS_PLAYING_CHANGED: isPlaying=$isPlaying")
                        if (isPlaying) {
                            playingLatch.countDown()
                        }
                    }

                    override fun onRenderedFirstFrame() {
                        val count = renderedFirstFrameCount.incrementAndGet()
                        Log.d(TAG, "RENDERED_FIRST_FRAME (Player.Listener): count=$count")
                        firstFrameLatch.countDown()
                    }
                })

                fullscreenMode = FullscreenMode(pv)
                pv.player = p
                p.play()
            }

            // 3. Wait for the first frame and isPlaying == true
            Log.d(TAG, "Waiting for initial playback...")
            val played = playingLatch.await(30, TimeUnit.SECONDS) || (player?.isPlaying == true)
            val firstFrame = firstFrameLatch.await(30, TimeUnit.SECONDS) || (renderedFirstFrameCount.get() > 0) || (player != null && player!!.currentPosition > 0)

            // Graceful skip strictly if running in an offline environment or if the remote demo token expired
            val isNetworkOrTokenFailure = tokenExpired.get() ||
                apiErrors.any {
                    it == com.tpstreams.player.constants.PlaybackError.NETWORK_CONNECTION_FAILED ||
                    it == com.tpstreams.player.constants.PlaybackError.NETWORK_CONNECTION_TIMEOUT ||
                    it == com.tpstreams.player.constants.PlaybackError.VIDEO_SERVICE_BLOCKED ||
                    it == com.tpstreams.player.constants.PlaybackError.INVALID_ACCESS_TOKEN_FOR_ASSETS ||
                    it == com.tpstreams.player.constants.PlaybackError.EXPIRED_ACCESS_TOKEN_FOR_ASSETS ||
                    it == com.tpstreams.player.constants.PlaybackError.INVALID_ACCESS_TOKEN_FOR_DRM_LICENSE
                } ||
                errors.any {
                    val pe = it as? PlaybackException
                    pe?.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    pe?.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                }

            if (isNetworkOrTokenFailure) {
                Assume.assumeTrue(
                    "Skipping test: Remote network unreachable or demo token expired in test environment (apiErrors=$apiErrors, errors=${errors.map { (it as? PlaybackException)?.errorCodeName ?: it.message }})",
                    false
                )
            }

            assertTrue("Player should start playing within 30s", played)
            assertTrue("First frame should render or playback advance within 30s", firstFrame)

            // Let playback settle
            Thread.sleep(1500)

            // 4. Capture baseline state
            val baselineInitCount = decoderInitCount.get()
            val baselineReleaseCount = decoderReleaseCount.get()
            assertEquals("Initial decoder initialization count must be 1", 1, baselineInitCount)
            assertEquals("Initial decoder release count must be 0", 0, baselineReleaseCount)

            // 5. Run five fullscreen enter/exit cycles & 6. Trigger orientation changes
            val cycles = 5
            for (i in 1..cycles) {
                Log.d(TAG, "CYCLE $i: Enter Fullscreen")
                scenario.onActivity {
                    fullscreenMode?.enterFullscreen()
                }
                Thread.sleep(400)

                // Trigger explicit orientation switch to landscape
                scenario.onActivity { act ->
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                Thread.sleep(400)

                Log.d(TAG, "CYCLE $i: Exit Fullscreen")
                scenario.onActivity {
                    fullscreenMode?.exitFullscreen()
                }
                Thread.sleep(400)

                // Trigger explicit orientation switch back to portrait
                scenario.onActivity { act ->
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
                Thread.sleep(400)
            }

            // Let UI settle after cycling
            Thread.sleep(1000)

            val activePlayer = player!!

            // 7. Assertions
            scenario.onActivity { currentActivity ->
                // Activity instance is unchanged
                val currentActivityHash = System.identityHashCode(currentActivity)
                assertEquals(
                    "Activity instance must remain unchanged across fullscreen & orientation changes",
                    initialActivityHash,
                    currentActivityHash
                )
                assertTrue("Activity must not be finishing", !currentActivity.isFinishing)
                assertTrue("Activity must not be destroyed", !currentActivity.isDestroyed)

                // Player instance is unchanged
                val currentPlayer = playerView?.player
                assertNotNull("Player must still be attached to PlayerView", currentPlayer)
                assertEquals(
                    "Player instance must remain unchanged",
                    initialPlayerHash,
                    System.identityHashCode(currentPlayer)
                )

                // Playback remains active
                assertTrue("Playback must remain active", activePlayer.isPlaying)

                // Fullscreen view returns to its original parent
                assertEquals(
                    "Fullscreen view must return to its original parent container",
                    originalContainer,
                    playerView?.parent
                )
            }

            // No playback errors occurred
            assertTrue("No playback errors should have occurred during testing", errors.isEmpty())

            // Decoder initialization count remains 1
            assertEquals(
                "Decoder initialization count must remain 1 throughout all cycles",
                1,
                decoderInitCount.get()
            )

            // Decoder release count remains 0
            assertEquals(
                "Decoder release count must remain 0 throughout all cycles",
                0,
                decoderReleaseCount.get()
            )
        }
    }
}
