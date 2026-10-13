package com.tpstreams.player.download

import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Regression test for preparing the download manager before foreground-service startup. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class TPSDownloadServiceForegroundTimeoutTest {

    @Test
    fun foregroundServiceRequests_initializeManagerBeforeDispatch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = DownloadRequest.Builder(
            "foreground-timeout-reproduction",
            Uri.parse("https://example.com/foreground-timeout.m3u8")
        )
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        assertInitializesBeforeDispatch("add download") {
            TPSDownloadService.sendDownload(context, request, foreground = true)
        }
        assertInitializesBeforeDispatch("remove download") {
            TPSDownloadService.removeDownload(context, request.id)
        }
        assertInitializesBeforeDispatch("remove all downloads") {
            TPSDownloadService.removeAllDownloads(context)
        }
        assertInitializesBeforeDispatch("pause downloads") {
            TPSDownloadService.pauseDownloads(context)
        }
        assertInitializesBeforeDispatch("resume downloads") {
            TPSDownloadService.resumeDownloads(context)
        }
    }

    private fun assertInitializesBeforeDispatch(
        operation: String,
        dispatch: () -> Unit
    ) {
        DownloadController.resetForTesting()
        var initializedBeforeDispatch = false
        TPSDownloadService.foregroundDispatchOverrideForTesting = {
            initializedBeforeDispatch = DownloadController.isInitializedForTesting()
        }
        try {
            dispatch()
        } finally {
            TPSDownloadService.foregroundDispatchOverrideForTesting = null
        }
        assertTrue("$operation must initialize DownloadController before dispatch", initializedBeforeDispatch)
    }
}
