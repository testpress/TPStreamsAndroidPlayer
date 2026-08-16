package com.tpstreams.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.download.DownloadConstants

object TestpressPlayer {

    @OptIn(UnstableApi::class)
    @JvmStatic
    @JvmOverloads
    fun create(
        context: Context,
        assetId: String,
        accessToken: String = "",
        shouldAutoPlay: Boolean = true,
        startAt: Long = 0,
        enableDownload: Boolean = false,
        showDefaultCaptions: Boolean = false,
        startInFullscreen: Boolean = false,
        downloadMetadata: Map<String, String>? = null,
        offlineLicenseExpireTime: Long = DownloadConstants.FIFTEEN_DAYS_IN_SECONDS
    ): TPStreamsPlayer {
        return TPStreamsPlayer.create(
            context,
            assetId,
            accessToken,
            shouldAutoPlay,
            startAt,
            enableDownload,
            showDefaultCaptions,
            startInFullscreen,
            downloadMetadata,
            offlineLicenseExpireTime
        )
    }
}
