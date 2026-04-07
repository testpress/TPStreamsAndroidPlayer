package com.tpstreams.player.util

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider

@UnstableApi
internal class TPStreamsDrmSessionManagerProvider : DrmSessionManagerProvider {

    override fun get(mediaItem: MediaItem): DrmSessionManager {
        val drmConfig = mediaItem.localConfiguration?.drmConfiguration
            ?: return DrmSessionManager.DRM_UNSUPPORTED

        val licenseUri = drmConfig.licenseUri ?: return DrmSessionManager.DRM_UNSUPPORTED

        val callback = TPStreamsMediaDrmCallback(
            licenseUrl = licenseUri.toString(),
            headers = drmConfig.licenseRequestHeaders
        )

        return DefaultDrmSessionManager.Builder()
            .setMultiSession(drmConfig.multiSession)
            .build(callback)
    }
}
