package com.tpstreams.player

import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpstreams.player.download.DownloadClient
import com.tpstreams.player.download.DownloadItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    }

    @Test
    fun downloadPauseResumeAndRemove_emitConsistentLifecycle() {
        val orgId = PlayerViewTestSupport.instrumentationArgumentOrDefault("orgId", PlayerViewTestSupport.DEFAULT_ORG_ID)
        val assetId = PlayerViewTestSupport.instrumentationArgumentOrDefault("downloadAssetId", PlayerViewTestSupport.DEFAULT_NON_DRM_ASSET_ID)
        val accessToken = PlayerViewTestSupport.instrumentationArgumentOrDefault("downloadAccessToken", PlayerViewTestSupport.DEFAULT_ACCESS_TOKEN)
        val resolution = PlayerViewTestSupport.instrumentationArgumentOrDefault("downloadResolution", PlayerViewTestSupport.DEFAULT_DOWNLOAD_RESOLUTION)

        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            val started = CountDownLatch(1)
            val failed = AtomicReference<Exception>()
            val events = CopyOnWriteArrayList<String>()
            val deletedCount = java.util.concurrent.atomic.AtomicInteger(0)
            var client: DownloadClient? = null
            var initialCleanupExpected = false
            var initialCleanupBaseline = 0
            var cleanupExpected = false
            var cleanupBaseline = 0
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
                    deletedCount.incrementAndGet()
                }
            }

            try {
                scenario.onActivity { activity ->
                    TPStreamsSDK.init(orgId, TPStreamsSDK.Provider.TPStreams, allowFallbackToL3 = true)
                    client = DownloadClient.getInstance(activity)
                    client!!.addListener(listener)
                    initialCleanupExpected = client!!.getDownload(assetId) != null
                    initialCleanupBaseline = deletedCount.get()
                    if (initialCleanupExpected) {
                        client!!.removeDownload(assetId)
                    }
                }
                if (initialCleanupExpected) {
                    assertTrue("A previous download must emit deleted before lifecycle setup", waitUntil(20_000) {
                        deletedCount.get() > initialCleanupBaseline
                    })
                }
                scenario.onActivity { activity ->
                    client!!.startDownload(activity, assetId, accessToken, resolution)
                }

                assertTrue(
                    "Download did not emit started within 45 seconds. Events=$events, error=${failed.get()}",
                    started.await(45, TimeUnit.SECONDS)
                )
                assertFalse("Download emitted a failure callback: ${failed.get()}", failed.get() != null)
                assertTrue("Download lifecycle must emit a started event. Events=$events", events.contains("started"))

                scenario.onActivity {
                    client!!.pauseDownload(assetId)
                }
                assertTrue("Download should enter paused state after pauseDownload", waitUntil(10_000) { client!!.isPaused(assetId) })

                scenario.onActivity { client!!.resumeDownload(assetId) }
                assertTrue(
                    "Download should leave paused state after resumeDownload",
                    waitUntil(10_000) { !client!!.isPaused(assetId) }
                )

                // Rapid commands intentionally exercise DownloadManager state races.
                scenario.onActivity {
                    client!!.pauseDownload(assetId)
                    client!!.resumeDownload(assetId)
                    client!!.pauseDownload(assetId)
                    client!!.resumeDownload(assetId)
                }

                val item = client!!.getAllDownloadItems().firstOrNull { it.assetId == assetId }
                assertNotNull("Download item should exist after start/pause/resume operations", item)
                assertFalse("Download lifecycle emitted an error: ${failed.get()}", failed.get() != null)
            } finally {
                scenario.onActivity {
                    cleanupExpected = client?.getDownload(assetId) != null
                    cleanupBaseline = deletedCount.get()
                    if (cleanupExpected) client?.removeDownload(assetId)
                }
                if (cleanupExpected) {
                    assertTrue(
                        "Download cleanup did not emit deleted before test exit. Events=$events",
                        waitUntil(20_000) { deletedCount.get() > cleanupBaseline }
                    )
                }
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

