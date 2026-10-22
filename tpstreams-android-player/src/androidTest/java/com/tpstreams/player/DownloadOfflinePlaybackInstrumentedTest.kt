package com.tpstreams.player

import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpstreams.player.download.DownloadClient
import com.tpstreams.player.download.DownloadItem
import org.junit.Assert.assertEquals
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
        private const val DEFAULT_ORG_ID = "9q94nm"
        private const val DEFAULT_ASSET_ID = "4Zs4MNd5Ksj"
        private const val DEFAULT_ACCESS_TOKEN = "3d9838f3-db51-4fc3-8472-075ab5e40b64"
        private const val DEFAULT_RESOLUTION = "360p"
    }

    @Test
    fun completedDownload_isPlayableThroughThePlayerView() {
        val args = InstrumentationRegistry.getArguments()
        val orgId = args.getString("orgId")?.takeIf { it.isNotBlank() } ?: DEFAULT_ORG_ID
        val assetId = args.getString("downloadAssetId")?.takeIf { it.isNotBlank() } ?: DEFAULT_ASSET_ID
        val accessToken = args.getString("downloadAccessToken")?.takeIf { it.isNotBlank() } ?: DEFAULT_ACCESS_TOKEN
        val resolution = args.getString("downloadResolution")?.takeIf { it.isNotBlank() } ?: DEFAULT_RESOLUTION

        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            var client: DownloadClient? = null
            var player: TPStreamsPlayer? = null
            val completed = CountDownLatch(1)
            val firstFrame = CountDownLatch(1)
            val failure = AtomicReference<Throwable>()
            val playbackState = AtomicReference(Player.STATE_IDLE)

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
                override fun onDownloadDeleted(assetId: String) = Unit
            }

            try {
                scenario.onActivity { activity ->
                    TPStreamsSDK.init(orgId, TPStreamsSDK.Provider.TPStreams, allowFallbackToL3 = true)
                    client = DownloadClient.getInstance(activity)
                    client!!.removeDownload(assetId)
                    client!!.addListener(listener)
                    client!!.startDownload(activity, assetId, accessToken, resolution)
                }

                assertTrue("Download must complete within the device test timeout", completed.await(180, TimeUnit.SECONDS))
                assertEquals(null, failure.get())
                assertEquals(
                    androidx.media3.exoplayer.offline.Download.STATE_COMPLETED,
                    client!!.getDownload(assetId)?.state
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
                assertEquals(null, failure.get())
                scenario.onActivity {
                    playbackState.set(player!!.playbackState)
                }
                assertTrue(
                    "Offline player must reach an active playback state",
                    playbackState.get() != Player.STATE_IDLE
                )
            } finally {
                scenario.onActivity { player?.release() }
                scenario.onActivity {
                    client?.removeListener(listener)
                    client?.removeDownload(assetId)
                }
            }
        }
    }
}
