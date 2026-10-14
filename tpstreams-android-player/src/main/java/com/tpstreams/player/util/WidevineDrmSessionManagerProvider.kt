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
import android.util.Log
import com.google.common.primitives.Ints
import java.util.Objects
import com.tpstreams.player.TPStreamsSDK
import androidx.media3.exoplayer.drm.MediaDrmCallback

/**
 * Supplies [DrmSessionManager] instances using the cached playback level from
 * [WidevinePlaybackLevelResolver].
 */
@UnstableApi
internal class WidevineDrmSessionManagerProvider(
    private val drmHttpDataSourceFactory: DataSource.Factory,
) : DrmSessionManagerProvider {

    private val lock = Any()

    private val defaultProvider = DefaultDrmSessionManagerProvider().apply {
        setDrmHttpDataSourceFactory(drmHttpDataSourceFactory)
    }

    @GuardedBy("lock")
    private var cachedDrmConfiguration: MediaItem.DrmConfiguration? = null

    @GuardedBy("lock")
    private var cachedManager: DrmSessionManager? = null

    @GuardedBy("lock")
    private var cachedShouldUseL3: Boolean? = null

    @GuardedBy("lock")
    private var cachedEncryptionEnabled: Boolean? = null

    @GuardedBy("lock")
    private var cachedEncryptionKey: String? = null

    override fun get(mediaItem: MediaItem): DrmSessionManager {
        val drmConfiguration = mediaItem.localConfiguration?.drmConfiguration
            ?: return DrmSessionManager.DRM_UNSUPPORTED

        val explicitLevel = drmConfiguration.licenseRequestHeaders["X-TPStreams-Security-Level"]
        val shouldUseL3 = when (explicitLevel) {
            "L3" -> true
            "L1" -> false
            else -> {
                if (drmConfiguration.keySetId != null) {
                    false
                } else {
                    WidevinePlaybackLevelResolver.shouldUseL3Drm()
                }
            }
        }

        // If license payload encryption is not enabled and not using L3, keep default Media3 provider
        if (!shouldUseL3 && !TPStreamsSDK.isLicensePayloadEncryptionEnabled()) {
            return defaultProvider.get(mediaItem)
        }

        val exoMediaDrmProvider = if (shouldUseL3) {
            L3_EXO_MEDIA_DRM_PROVIDER
        } else {
            FrameworkMediaDrm.DEFAULT_PROVIDER
        }

        val currentEncryptionEnabled = TPStreamsSDK.isLicensePayloadEncryptionEnabled()
        val currentEncryptionKey = TPStreamsSDK.licensePayloadEncryptionKey

        synchronized(lock) {
            if (!Objects.equals(drmConfiguration, cachedDrmConfiguration) ||
                cachedShouldUseL3 != shouldUseL3 ||
                cachedEncryptionEnabled != currentEncryptionEnabled ||
                !Objects.equals(cachedEncryptionKey, currentEncryptionKey)
            ) {
                cachedDrmConfiguration = drmConfiguration
                cachedShouldUseL3 = shouldUseL3
                cachedEncryptionEnabled = currentEncryptionEnabled
                cachedEncryptionKey = currentEncryptionKey
                cachedManager = createManager(drmConfiguration, exoMediaDrmProvider)
            }
            return checkNotNull(cachedManager)
        }
    }

    private fun createManager(
        drmConfiguration: MediaItem.DrmConfiguration,
        exoMediaDrmProvider: ExoMediaDrm.Provider
    ): DrmSessionManager {
        val httpDrmCallback = HttpMediaDrmCallback(
            drmConfiguration.licenseUri?.toString(),
            drmConfiguration.forceDefaultLicenseUri,
            drmHttpDataSourceFactory,
        )
        for ((key, value) in drmConfiguration.licenseRequestHeaders) {
            httpDrmCallback.setKeyRequestProperty(key, value)
        }

        val drmCallback: MediaDrmCallback = if (TPStreamsSDK.isLicensePayloadEncryptionEnabled()) {
            val key = checkNotNull(TPStreamsSDK.licensePayloadEncryptionKey)
            AesEncryptedDrmCallback(httpDrmCallback, LicensePayloadEncryptor(key))
        } else {
            httpDrmCallback
        }

        val drmSessionManager = DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(drmConfiguration.scheme, exoMediaDrmProvider)
            .setMultiSession(drmConfiguration.multiSession)
            .setPlayClearSamplesWithoutKeys(drmConfiguration.playClearContentWithoutKey)
            .setUseDrmSessionsForClearContent(
                *Ints.toArray(drmConfiguration.forcedSessionTrackTypes),
            )
            .build(drmCallback)
        drmSessionManager.setMode(DefaultDrmSessionManager.MODE_PLAYBACK, drmConfiguration.keySetId)
        return drmSessionManager
    }

    companion object {
        private const val TAG = "WidevineDrmSessionMgr"

        internal val L3_EXO_MEDIA_DRM_PROVIDER = ExoMediaDrm.Provider { uuid ->
            try {
                FrameworkMediaDrm.newInstance(uuid).apply {
                    if (uuid == C.WIDEVINE_UUID) {
                        setPropertyString("securityLevel", "L3")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create L3 MediaDrm for uuid=$uuid; playback will fail at DRM init", e)
                DummyExoMediaDrm()
            }
        }

        fun createOfflineLicenseHelper(
            licenseUri: String,
            dataSourceFactory: DataSource.Factory,
            eventDispatcher: androidx.media3.exoplayer.drm.DrmSessionEventListener.EventDispatcher = androidx.media3.exoplayer.drm.DrmSessionEventListener.EventDispatcher(),
            securityLevel: String? = null
        ): androidx.media3.exoplayer.drm.OfflineLicenseHelper {
            val exoMediaDrmProvider = if (securityLevel == "L3") {
                L3_EXO_MEDIA_DRM_PROVIDER
            } else {
                FrameworkMediaDrm.DEFAULT_PROVIDER
            }

            val httpDrmCallback = HttpMediaDrmCallback(
                licenseUri,
                false,
                dataSourceFactory
            )
            val drmCallback: MediaDrmCallback = if (TPStreamsSDK.isLicensePayloadEncryptionEnabled()) {
                val key = checkNotNull(TPStreamsSDK.licensePayloadEncryptionKey)
                AesEncryptedDrmCallback(httpDrmCallback, LicensePayloadEncryptor(key))
            } else {
                httpDrmCallback
            }

            val drmSessionManager = DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, exoMediaDrmProvider)
                .build(drmCallback)
            return androidx.media3.exoplayer.drm.OfflineLicenseHelper(drmSessionManager, eventDispatcher)
        }
    }
}
