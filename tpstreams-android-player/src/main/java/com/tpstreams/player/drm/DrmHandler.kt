package com.tpstreams.player.drm

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.tpstreams.player.download.DownloadClient
import com.tpstreams.player.util.WidevinePlaybackLevelResolver
import io.sentry.Breadcrumb
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles Widevine DRM playback, L1/L3 level resolution, and L3 fallback retries.
 */
@OptIn(UnstableApi::class)
internal class DrmHandler(
    private val exoPlayer: ExoPlayer,
    private val playerScope: CoroutineScope,
    private val context: Context,
    private val assetId: String,
    private val isLiveStream: () -> Boolean,
    private val onRenewOfflineLicense: () -> Unit,
) {

    var licenseUrl: String? = null
        private set

    val isProtected: Boolean
        get() = licenseUrl != null ||
                exoPlayer.currentMediaItem?.localConfiguration?.drmConfiguration != null

    val nativeSecurityLevel: String by lazy {
        WidevinePlaybackLevelResolver.getNativeWidevineLevel() ?: "unknown"
    }

    @Volatile
    private var fallbackAttempted = false

    fun onLicenseResolved(url: String?) {
        licenseUrl = url
        fallbackAttempted = false
    }

    fun resetFallbackAttempt() {
        fallbackAttempted = false
    }

    fun handleError(error: PlaybackException): Boolean {
        if (handleExpiredOfflineLicense(error)) return true
        if (handleL3Fallback(error)) return true
        return false
    }

    fun getPlaybackLevel(): String =
        if (WidevinePlaybackLevelResolver.shouldUseL3Drm()) "L3" else "L1"

    fun getNativeLevel(): String? = WidevinePlaybackLevelResolver.getNativeWidevineLevel()

    private fun handleExpiredOfflineLicense(error: PlaybackException): Boolean {
        if (error.errorCode != PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED) return false
        if (!isDownloadedAsset()) return false

        Log.d(TAG, "DRM license expired for downloaded asset: $assetId — triggering renewal")
        onRenewOfflineLicense()
        return true
    }

    private fun handleL3Fallback(error: PlaybackException): Boolean {
        if (!WidevinePlaybackLevelResolver.isFallbackAllowed()) return false
        if (!isProtected) return false
        if (!WidevinePlaybackLevelResolver.isDrmFallbackError(error)) return false
        if (fallbackAttempted) return false
        if (WidevinePlaybackLevelResolver.isAlreadyOnL3PlaybackLevel()) return false

        if (WidevinePlaybackLevelResolver.isDrmPermanentFailure(error)) {
            Log.w(TAG, "Permanent DRM failure — persisting L3 for all future sessions: $assetId")
            WidevinePlaybackLevelResolver.persistForceL3(context)
        } else {
            Log.w(TAG, "Transient DRM failure — falling back to L3 for this session: $assetId")
            WidevinePlaybackLevelResolver.forceL3ForSession()
        }

        return retryAtL3(error)
    }

    private fun retryAtL3(error: PlaybackException): Boolean {
        val currentMediaItem = exoPlayer.currentMediaItem ?: return false
        val retryMediaItem = buildRetryMediaItem(currentMediaItem) ?: run {
            Log.w(TAG, "L3 retry aborted — could not preserve DRM configuration for: $assetId")
            return false
        }

        fallbackAttempted = true
        val resumePosition = exoPlayer.currentPosition
        val shouldPlayWhenReady = exoPlayer.playWhenReady

        playerScope.launch(Dispatchers.Main) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()

            val startPositionMs = if (!isLiveStream() && resumePosition > 0) {
                resumePosition
            } else {
                C.TIME_UNSET
            }

            exoPlayer.setMediaItem(retryMediaItem, startPositionMs)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = shouldPlayWhenReady
        }

        Sentry.addBreadcrumb(Breadcrumb().apply {
            setMessage("L3 DRM fallback triggered")
            setData("asset_id", assetId)
            setData("error_code", error.errorCodeName ?: "unknown")
            setData("is_permanent_failure", WidevinePlaybackLevelResolver.isDrmPermanentFailure(error).toString())
            setData("native_security_level", nativeSecurityLevel)
        })

        Log.i(TAG, "L3 fallback retry scheduled for asset: $assetId")
        return true
    }

    private fun buildRetryMediaItem(mediaItem: MediaItem): MediaItem? {
        val localConfiguration = mediaItem.localConfiguration ?: return mediaItem

        val drmConfiguration = localConfiguration.drmConfiguration
        if (isProtected && drmConfiguration == null) {
            return null
        }

        return mediaItem.buildUpon()
            .also { builder -> drmConfiguration?.let { builder.setDrmConfiguration(it) } }
            .build()
    }

    private fun isDownloadedAsset(): Boolean = try {
        DownloadClient.getInstance(context).getDownload(assetId) != null
    } catch (_: Exception) {
        false
    }

    private companion object {
        private const val TAG = "DrmHandler"
    }
}
