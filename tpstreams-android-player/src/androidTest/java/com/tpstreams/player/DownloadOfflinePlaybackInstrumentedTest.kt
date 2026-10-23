package com.tpstreams.player

import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpstreams.player.download.DownloadClient
import com.tpstreams.player.download.DownloadController
import com.tpstreams.player.download.DownloadItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@UnstableApi
@RunWith(AndroidJUnit4::class)
class DownloadOfflinePlaybackInstrumentedTest {

    companion object {
    }

    @Test
    fun completedDownload_isPlayableThroughThePlayerView() {
        val orgId = PlayerViewTestSupport.instrumentationArgumentOrDefault("orgId", PlayerViewTestSupport.DEFAULT_ORG_ID)
        val assetId = PlayerViewTestSupport.instrumentationArgumentOrDefault("downloadAssetId", PlayerViewTestSupport.DEFAULT_NON_DRM_ASSET_ID)
        val accessToken = PlayerViewTestSupport.instrumentationArgumentOrDefault("downloadAccessToken", PlayerViewTestSupport.DEFAULT_ACCESS_TOKEN)
        val resolution = PlayerViewTestSupport.instrumentationArgumentOrDefault("downloadResolution", PlayerViewTestSupport.DEFAULT_DOWNLOAD_RESOLUTION)

        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            var client: DownloadClient? = null
            var player: TPStreamsPlayer? = null
            val completed = CountDownLatch(1)
            val firstFrame = CountDownLatch(1)
            val failure = AtomicReference<Throwable>()
            val playbackState = AtomicReference(Player.STATE_IDLE)
            val deletedCount = java.util.concurrent.atomic.AtomicInteger(0)
            var initialCleanupExpected = false
            var initialCleanupBaseline = 0
            var cleanupBaseline = 0

            val listener = object : DownloadClient.Listener {
                override fun onDownloadsChanged() = Unit
                override fun onDownloadStateChanged(downloadItem: DownloadItem, error: Exception?) {
                    error?.let(failure::set)
                }
                override fun onDownloadCompleted(downloadItem: DownloadItem) {
                    if (downloadItem.assetId == assetId) completed.countDown()
                }
                override fun onDownloadFailed(downloadItem: DownloadItem, error: Exception) {
                    if (downloadItem.assetId == assetId) {
                        failure.set(error)
                        completed.countDown()
                    }
                }
                override fun onDownloadDeleted(deletedAssetId: String) {
                    if (deletedAssetId == assetId) deletedCount.incrementAndGet()
                }
            }

            try {
                scenario.onActivity { activity ->
                    TPStreamsSDK.init(orgId, TPStreamsSDK.Provider.TPStreams, allowFallbackToL3 = true)
                    client = DownloadClient.getInstance(activity)
                    client!!.addListener(listener)
                    initialCleanupExpected = client!!.getDownload(assetId) != null
                    initialCleanupBaseline = deletedCount.get()
                    if (initialCleanupExpected) client!!.removeDownload(assetId)
                }
                if (initialCleanupExpected) {
                    assertTrue("A previous download must emit deleted before offline setup", waitUntil(20_000) {
                        deletedCount.get() > initialCleanupBaseline
                    })
                }
                scenario.onActivity { activity ->
                    client!!.startDownload(activity, assetId, accessToken, resolution)
                }

                assertTrue("Download must complete within the device test timeout", completed.await(180, TimeUnit.SECONDS))
                assertNull("Download emitted an error before completion: ${failure.get()}", failure.get())
                assertEquals(
                    "Download must be in STATE_COMPLETED before offline playback starts",
                    androidx.media3.exoplayer.offline.Download.STATE_COMPLETED,
                    client!!.getDownload(assetId)?.state
                )

                val completedDownload = client!!.getDownload(assetId)
                assertTrue("Completed download metadata must be available", completedDownload != null)
                assertTrue(
                    "Completed download must have cached media bytes before offline playback",
                    DownloadController.downloadCache.getCachedSpans(completedDownload!!.request.uri.toString()).isNotEmpty()
                )

                scenario.onActivity { activity ->
                    val root = FrameLayout(activity)
                    val playerView = TPStreamsPlayerView(activity)
                    activity.setContentView(root)
                    root.addView(playerView)

                    val offlinePlayer = TPStreamsPlayer.create(
                        context = activity,
                        assetId = assetId,
                        accessToken = accessToken,
                        shouldAutoPlay = true
                    )
                    player = offlinePlayer
                    offlinePlayer.addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() {
                            firstFrame.countDown()
                        }

                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            failure.set(error)
                            firstFrame.countDown()
                        }
                    })
                    playerView.player = offlinePlayer
                }

                assertTrue("Completed offline media must render a frame", firstFrame.await(30, TimeUnit.SECONDS))
                assertNull("Offline playback emitted an ExoPlayer error: ${failure.get()}", failure.get())
                scenario.onActivity {
                    playbackState.set(player!!.playbackState)
                    assertEquals(
                        "Offline player should use the downloaded media item",
                        assetId,
                        player!!.currentMediaItem?.mediaId
                    )
                }
                assertTrue(
                    "Offline player must reach an active playback state",
                    playbackState.get() != Player.STATE_IDLE
                )
            } finally {
                scenario.onActivity { player?.release() }
                scenario.onActivity {
                    cleanupBaseline = deletedCount.get()
                    client?.removeDownload(assetId)
                }
                assertTrue("Offline download cleanup did not emit deletion before test exit", waitUntil(20_000) {
                    deletedCount.get() > cleanupBaseline
                })
                scenario.onActivity { client?.removeListener(listener) }
            }
        }
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }
}
