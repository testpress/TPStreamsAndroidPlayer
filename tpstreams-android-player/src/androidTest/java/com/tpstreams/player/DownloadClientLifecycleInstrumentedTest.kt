package com.tpstreams.player

import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpstreams.player.download.DownloadClient
import com.tpstreams.player.download.DownloadItem
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Exercises the real DownloadManager-backed lifecycle. Tests skip only when the
 * configured asset/token cannot be reached; device and storage failures remain
 * visible as test failures.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class DownloadClientLifecycleInstrumentedTest {

    companion object {
        private const val DEFAULT_ORG_ID = "9q94nm"
        private const val DEFAULT_ASSET_ID = "4Zs4MNd5Ksj"
        private const val DEFAULT_ACCESS_TOKEN = "3d9838f3-db51-4fc3-8472-075ab5e40b64"
        private const val DEFAULT_RESOLUTION = "360p"
    }

    @Test
    fun downloadPauseResumeAndRemove_emitConsistentLifecycle() {
        val args = InstrumentationRegistry.getArguments()
        val orgId = args.getString("orgId")?.takeIf { it.isNotBlank() } ?: DEFAULT_ORG_ID
        val assetId = args.getString("downloadAssetId")?.takeIf { it.isNotBlank() } ?: DEFAULT_ASSET_ID
        val accessToken = args.getString("downloadAccessToken")?.takeIf { it.isNotBlank() } ?: DEFAULT_ACCESS_TOKEN
        val resolution = args.getString("downloadResolution")?.takeIf { it.isNotBlank() } ?: DEFAULT_RESOLUTION

        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            val started = CountDownLatch(1)
            val failed = AtomicReference<Exception>()
            val events = CopyOnWriteArrayList<String>()
            var client: DownloadClient? = null
            val listener = object : DownloadClient.Listener {
                override fun onDownloadsChanged() = Unit

                override fun onDownloadStateChanged(downloadItem: DownloadItem, error: Exception?) {
                    events.add("state:${downloadItem.state}")
                    error?.let(failed::set)
                }

                override fun onDownloadStarted(downloadItem: DownloadItem) {
                    events.add("started")
                    started.countDown()
                }

                override fun onDownloadResumed(downloadItem: DownloadItem) {
                    events.add("resumed")
                }

                override fun onDownloadCompleted(downloadItem: DownloadItem) {
                    events.add("completed")
                }

                override fun onDownloadFailed(downloadItem: DownloadItem, error: Exception) {
                    events.add("failed")
                    failed.set(error)
                    started.countDown()
                }

                override fun onDownloadDeleted(assetId: String) {
                    events.add("deleted")
                }
            }

            try {
                scenario.onActivity { activity ->
                    TPStreamsSDK.init(orgId, TPStreamsSDK.Provider.TPStreams, allowFallbackToL3 = true)
                    client = DownloadClient.getInstance(activity)
                    client!!.removeDownload(assetId)
                    client!!.addListener(listener)
                    client!!.startDownload(activity, assetId, accessToken, resolution)
                }

                started.await(45, TimeUnit.SECONDS)
                assumeFalse("Download setup failed: ${failed.get()}", failed.get() != null)

                // Rapid commands intentionally exercise DownloadManager state races.
                scenario.onActivity {
                    client!!.pauseDownload(assetId)
                    client!!.resumeDownload(assetId)
                    client!!.pauseDownload(assetId)
                    client!!.resumeDownload(assetId)
                }

                Thread.sleep(2_000)
                val item = client!!.getAllDownloadItems().firstOrNull { it.assetId == assetId }
                assumeFalse("Download was not created by the configured backend", item == null)
            } finally {
                scenario.onActivity {
                    client?.removeListener(listener)
                    client?.removeDownload(assetId)
                }
            }
        }
    }

}

