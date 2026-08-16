package com.tpstreams.player.util

import androidx.annotation.GuardedBy
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.DummyExoMediaDrm
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.UnsupportedDrmException
import com.google.common.primitives.Ints
import java.util.Objects

/**
 * Supplies [DrmSessionManager] instances using the cached playback level from
 * [WidevinePlaybackLevelResolver].
 */
@UnstableApi
internal class WidevineDrmSessionManagerProvider(
    private val drmHttpDataSourceFactory: DataSource.Factory,
    private val allowFallbackToL3: Boolean = false,
) : DrmSessionManagerProvider {

    private val lock = Any()

    private val defaultProvider = DefaultDrmSessionManagerProvider().apply {
        setDrmHttpDataSourceFactory(drmHttpDataSourceFactory)
    }

    @GuardedBy("lock")
    private var l3DrmConfiguration: MediaItem.DrmConfiguration? = null

    @GuardedBy("lock")
    private var l3Manager: DrmSessionManager? = null

    override fun get(mediaItem: MediaItem): DrmSessionManager {
        val isNativeL3 = WidevinePlaybackLevelResolver.isNativeL3()
        val shouldForceL3 = WidevinePlaybackLevelResolver.shouldForceL3()

        if (!isNativeL3 && (!allowFallbackToL3 || !shouldForceL3)) {
            return defaultProvider.get(mediaItem)
        }

        val drmConfiguration = mediaItem.localConfiguration?.drmConfiguration
            ?: return DrmSessionManager.DRM_UNSUPPORTED

        synchronized(lock) {
            if (!Objects.equals(drmConfiguration, l3DrmConfiguration)) {
                l3DrmConfiguration = drmConfiguration
                l3Manager = createL3Manager(drmConfiguration)
            }
            return checkNotNull(l3Manager)
        }
    }

    private fun createL3Manager(drmConfiguration: MediaItem.DrmConfiguration): DrmSessionManager {
        val httpDrmCallback = HttpMediaDrmCallback(
            drmConfiguration.licenseUri?.toString(),
            drmConfiguration.forceDefaultLicenseUri,
            drmHttpDataSourceFactory,
        )
        for ((key, value) in drmConfiguration.licenseRequestHeaders) {
            httpDrmCallback.setKeyRequestProperty(key, value)
        }

        val drmSessionManager = DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(drmConfiguration.scheme, L3_EXO_MEDIA_DRM_PROVIDER)
            .setMultiSession(drmConfiguration.multiSession)
            .setPlayClearSamplesWithoutKeys(drmConfiguration.playClearContentWithoutKey)
            .setUseDrmSessionsForClearContent(
                *Ints.toArray(drmConfiguration.forcedSessionTrackTypes),
            )
            .build(httpDrmCallback)
        drmSessionManager.setMode(DefaultDrmSessionManager.MODE_PLAYBACK, drmConfiguration.keySetId)
        return drmSessionManager
    }

    companion object {
        internal val L3_EXO_MEDIA_DRM_PROVIDER = ExoMediaDrm.Provider { uuid ->
            try {
                FrameworkMediaDrm.newInstance(uuid).apply {
                    if (uuid == C.WIDEVINE_UUID) {
                        setPropertyString("securityLevel", "L3")
                    }
                }
            } catch (_: UnsupportedDrmException) {
                DummyExoMediaDrm()
            }
        }

        fun createOfflineLicenseHelper(
            licenseUri: String,
            dataSourceFactory: DataSource.Factory,
            eventDispatcher: androidx.media3.exoplayer.drm.DrmSessionEventListener.EventDispatcher = androidx.media3.exoplayer.drm.DrmSessionEventListener.EventDispatcher()
        ): androidx.media3.exoplayer.drm.OfflineLicenseHelper {
            if (!WidevinePlaybackLevelResolver.shouldForceL3()) {
                return androidx.media3.exoplayer.drm.OfflineLicenseHelper.newWidevineInstance(
                    licenseUri,
                    false,
                    dataSourceFactory,
                    eventDispatcher
                )
            }
            val httpDrmCallback = HttpMediaDrmCallback(
                licenseUri,
                false,
                dataSourceFactory
            )
            val drmSessionManager = DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, L3_EXO_MEDIA_DRM_PROVIDER)
                .build(httpDrmCallback)
            return androidx.media3.exoplayer.drm.OfflineLicenseHelper(drmSessionManager, eventDispatcher)
        }
    }
}
