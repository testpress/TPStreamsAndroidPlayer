package com.tpstreams.player.download

import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Regression test for preparing the download manager before foreground-service startup. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class TPSDownloadServiceForegroundTimeoutTest {

    @Test
    fun startingForegroundDownloadService_initializesManagerBeforeDispatch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = DownloadRequest.Builder(
            "foreground-timeout-reproduction",
            Uri.parse("https://example.com/foreground-timeout.m3u8")
        )
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        TPSDownloadService.sendDownload(context, request, foreground = true)

        assertNotNull(
            "DownloadManager must be initialized before the foreground service is dispatched",
            DownloadController.downloadManager
        )
    }
}
