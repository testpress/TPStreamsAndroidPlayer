package com.tpstreams.player.media

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.tpstreams.player.TPStreamsSDK
import com.tpstreams.player.constants.PlaybackError
import com.tpstreams.player.data.AssetRepository
import com.tpstreams.player.data.network.model.AssetInfo
import com.tpstreams.player.download.DownloadPlaybackHandler
import com.tpstreams.player.drm.DrmHandler
import com.tpstreams.player.tracks.TextTrackManager
import com.tpstreams.player.util.MediaItemUtils
import com.tpstreams.player.util.NetworkDiagnosticsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles fetching asset metadata, building MediaItems, and preparing ExoPlayer.
 */
@OptIn(UnstableApi::class)
internal class MediaLoader(
    private val context: Context,
    private val exoPlayer: ExoPlayer,
    private val playerScope: CoroutineScope,
    private val assetId: String,
    private val accessToken: String,
    private val drmHandler: DrmHandler,
    private val textTrackManager: TextTrackManager,
    private val downloadPlaybackHandler: DownloadPlaybackHandler,
    private val networkDiagnosticsManager: NetworkDiagnosticsManager,
    private val onMediaPrepared: () -> Unit,
    private val shouldPlayOnPrepared: () -> Boolean,
    private val onLiveStreamStatusChanged: (Boolean) -> Unit,
    private val onAssetFetchError: (AssetRepository.AssetFetchFailure) -> String,
    private val onPlaybackError: (PlaybackError, String) -> Unit,
    private val logDebug: (String) -> Unit,
) {

    @Volatile
    private var loadedAssetInfo: AssetInfo? = null
    @Volatile
    var cdnHostname: String? = null
        private set

    @Volatile
    var mediaUrl: String? = null
        private set

    @Volatile
    var isLiveStream: Boolean = false
        private set

    fun load() {
        CoroutineScope(Dispatchers.IO).launch {
            if (downloadPlaybackHandler.playFromDownload(assetId)) return@launch

            AssetRepository.fetchAssetInfo(assetId, accessToken, object : AssetRepository.AssetCallback {
                override fun onSuccess(assetInfo: AssetInfo) {
                    loadedAssetInfo = assetInfo
                    val safeHost = try { Uri.parse(assetInfo.mediaUrl).host } catch (_: Exception) { "unknown" }
                    logDebug("fetchAndPrepare SUCCESS — cdnHost=$safeHost")
                    preparePlayer(assetInfo)
                }

                override fun onError(failure: AssetRepository.AssetFetchFailure) {
                    val message = onAssetFetchError(failure)
                    logDebug("fetchAndPrepare onError — error=${failure.error}, message=$message")
                    if (failure.error == PlaybackError.NETWORK_CONNECTION_FAILED ||
                        failure.error == PlaybackError.NETWORK_CONNECTION_TIMEOUT) {
                        playerScope.launch {
                            networkDiagnosticsManager.handleError(
                                failure.error,
                                cdnHostname = cdnHostname,
                                mediaUrl = mediaUrl
                            )
                        }
                    } else {
                        playerScope.launch {
                            onPlaybackError(failure.error, message)
                        }
                    }
                }
            })
        }
    }

    /** Rebuilds the current DRM media item after the host app supplies a fresh token. */
    fun retryWithAccessToken(newAccessToken: String): Boolean {
        val assetInfo = loadedAssetInfo ?: return false
        val resumePositionMs = exoPlayer.currentPosition
        val resumePlayWhenReady = exoPlayer.playWhenReady
        preparePlayer(assetInfo, newAccessToken, resumePositionMs, resumePlayWhenReady)
        return true
    }

    private fun preparePlayer(
        assetInfo: AssetInfo,
        mediaAccessToken: String = accessToken,
        resumePositionMs: Long? = null,
        resumePlayWhenReady: Boolean? = null,
    ) {
        val orgId = TPStreamsSDK.requireOrgId()
        cdnHostname = try {
            Uri.parse(assetInfo.mediaUrl).host?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
        logDebug("CDN hostname extracted: $cdnHostname")

        val result = MediaItemUtils.buildMediaItem(
            assetInfo,
            assetInfo.title,
            orgId,
            assetId,
            mediaAccessToken,
        )
        drmHandler.onLicenseResolved(result.drmLicenseUrl)
        textTrackManager.updateSubtitleMetadata(result.subtitleMetadata)
        mediaUrl = result.mediaItem.localConfiguration?.uri?.toString()

        playerScope.launch(Dispatchers.Main) {
            isLiveStream = assetInfo.isLiveStream
            onLiveStreamStatusChanged(isLiveStream)

            networkDiagnosticsManager.onMediaLoaded()

            val audioAttributes = downloadPlaybackHandler.buildAudioAttributes(assetInfo.enableDrm)
            exoPlayer.setAudioAttributes(audioAttributes, true)
            logDebug("MediaItem SET - ${result.mediaItem.mediaId}")
            exoPlayer.setMediaItem(result.mediaItem)
            if (resumePositionMs != null) {
                exoPlayer.seekTo(resumePositionMs)
                exoPlayer.playWhenReady = resumePlayWhenReady == true
            }
            logDebug("Player PREPARE")
            exoPlayer.prepare()
            onMediaPrepared()

            if (resumePlayWhenReady != null) {
                if (resumePlayWhenReady) {
                    exoPlayer.play()
                } else {
                    exoPlayer.pause()
                }
            } else if (shouldPlayOnPrepared()) {
                exoPlayer.play()
            }
        }
    }
}
