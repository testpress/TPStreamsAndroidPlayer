package com.tpstreams.player

import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpstreams.player.constants.PlaybackError
import com.tpstreams.player.data.network.api.BaseApiService
import com.tpstreams.player.data.network.model.AssetInfo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Connected-device test for online DRM token recovery.
 *
 * The API service decorator corrupts only the first DRM licence token. The host callback
 * then supplies the valid token and the test verifies that playback recovers.
 *
 * Run with:
 * ```
 * ./gradlew :tpstreams-android-player:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.tpstreams.player.PlayerDrmTokenRefreshInstrumentedTest \
 *   -Pandroid.testInstrumentationRunnerArguments.orgId=<org-id> \
 *   -Pandroid.testInstrumentationRunnerArguments.assetId=<drm-asset-id> \
 *   -Pandroid.testInstrumentationRunnerArguments.accessToken=<valid-access-token>
 * ```
 */
@RunWith(AndroidJUnit4::class)
class PlayerDrmTokenRefreshInstrumentedTest {

    @Test
    fun unauthorizedDrmLicense_requestsFreshTokenAndContinuesPlayback() {
        val orgId = PlayerViewTestSupport.instrumentationArgumentOrDefault(
            "orgId",
            PlayerViewTestSupport.DEFAULT_ORG_ID,
        )
        val assetId = PlayerViewTestSupport.instrumentationArgumentOrDefault(
            "assetId",
            PlayerViewTestSupport.DEFAULT_DRM_ASSET_ID,
        )
        val validAccessToken = PlayerViewTestSupport.instrumentationArgumentOrDefault(
            "accessToken",
            PlayerViewTestSupport.DEFAULT_ACCESS_TOKEN,
        )

        val tokenExpiredCallback = CountDownLatch(1)
        val retriedLicenseRequest = CountDownLatch(1)
        val playerReady = CountDownLatch(1)
        val playerPlaying = CountDownLatch(1)
        val callbackCount = AtomicInteger(0)
        val playbackErrors = CopyOnWriteArrayList<PlaybackError>()
        val callbackAssetIdMatches = AtomicBoolean(false)
        var player: TPStreamsPlayer? = null
        var originalApiService: BaseApiService? = null

        try {
            ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    TPStreamsSDK.init(orgId, allowFallbackToL3 = false)
                    originalApiService = TPStreamsSDK.apiService

                    val apiService = OneShotInvalidDrmLicenseApiService(
                        delegate = originalApiService!!,
                        targetAssetId = assetId,
                        validAccessToken = validAccessToken,
                        retriedLicenseRequest = retriedLicenseRequest,
                    )
                    TPStreamsSDK.apiService = apiService

                    val playerView = TPStreamsPlayerView(activity)
                    activity.setContentView(playerView)

                    player = TPStreamsPlayer.create(
                        context = activity,
                        assetId = assetId,
                        accessToken = validAccessToken,
                        shouldAutoPlay = true,
                    ).also { createdPlayer ->
                        createdPlayer.listener = object : TPStreamsPlayer.Listener {
                            override fun onAccessTokenExpired(videoId: String, callback: (String) -> Unit) {
                                callbackAssetIdMatches.set(videoId == assetId)
                                callbackCount.incrementAndGet()
                                tokenExpiredCallback.countDown()
                                callback(validAccessToken)
                            }

                            override fun onError(error: PlaybackError, message: String) {
                                playbackErrors.add(error)
                            }
                        }
                        createdPlayer.addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_READY) {
                                    playerReady.countDown()
                                }
                            }

                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                if (isPlaying) {
                                    playerPlaying.countDown()
                                }
                            }
                        })
                        playerView.player = createdPlayer
                    }

                    // Player creation starts loading immediately. Releasing here guarantees that
                    // the application listener is registered before the first licence failure.
                    apiService.releaseAssetRequest()
                }

                assertTrue(
                    "onAccessTokenExpired was not emitted after the DRM licence returned 401",
                    tokenExpiredCallback.await(TOKEN_CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                )
                assertTrue("Token callback used an unexpected asset ID", callbackAssetIdMatches.get())
                assertEquals("Token refresh callback should be emitted once", 1, callbackCount.get())
                assertTrue(
                    "DRM licence was not retried with the refreshed token",
                    retriedLicenseRequest.await(PLAYBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                )
                assertTrue(
                    "Player did not become ready after the DRM token refresh",
                    playerReady.await(PLAYBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                )
                assertTrue(
                    "Playback did not continue after the DRM token refresh",
                    playerPlaying.await(PLAYBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                )
                assertFalse("A terminal playback error was shown: $playbackErrors", playbackErrors.isNotEmpty())

                scenario.onActivity {
                    player?.release()
                    player = null
                }
            }
        } finally {
            player?.release()
            originalApiService?.let { TPStreamsSDK.apiService = it }
        }
    }

    private class OneShotInvalidDrmLicenseApiService(
        private val delegate: BaseApiService,
        private val targetAssetId: String,
        private val validAccessToken: String,
        private val retriedLicenseRequest: CountDownLatch,
    ) : BaseApiService() {
        private val assetRequestStarted = CountDownLatch(1)
        private val allowAssetRequest = CountDownLatch(1)
        private val licenseRequestCount = AtomicInteger(0)

        override fun assetInfoUrl(orgId: String, assetId: String, accessToken: String): String {
            if (assetId == targetAssetId && assetRequestStarted.count > 0) {
                assetRequestStarted.countDown()
                check(allowAssetRequest.await(ASSET_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    "Timed out waiting for the player listener to be registered"
                }
            }
            return delegate.assetInfoUrl(orgId, assetId, accessToken)
        }

        override fun drmLicenseUrl(
            orgId: String,
            assetId: String,
            accessToken: String,
            download: Boolean,
            licenseDurationSeconds: Long?,
        ): String {
            val requestNumber = licenseRequestCount.incrementAndGet()
            val effectiveToken = if (requestNumber == 1) "$accessToken$INVALID_TOKEN_SUFFIX" else accessToken

            if (requestNumber > 1 && effectiveToken == validAccessToken) {
                retriedLicenseRequest.countDown()
            }

            return delegate.drmLicenseUrl(
                orgId = orgId,
                assetId = assetId,
                accessToken = effectiveToken,
                download = download,
                licenseDurationSeconds = licenseDurationSeconds,
            )
        }

        override fun tokenValidationUrl(orgId: String, assetId: String, accessToken: String): String =
            delegate.tokenValidationUrl(orgId, assetId, accessToken)

        override fun parseAsset(json: JSONObject): AssetInfo = delegate.parseAsset(json)

        fun releaseAssetRequest() {
            check(assetRequestStarted.await(ASSET_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "Player did not start the asset request"
            }
            allowAssetRequest.countDown()
        }
    }

    private companion object {
        const val INVALID_TOKEN_SUFFIX = "1"
        const val ASSET_REQUEST_TIMEOUT_SECONDS = 10L
        const val TOKEN_CALLBACK_TIMEOUT_SECONDS = 30L
        const val PLAYBACK_TIMEOUT_SECONDS = 45L
    }
}
