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
import com.tpstreams.player.data.PlayerDecoderState
import com.tpstreams.player.data.network.model.AssetInfo
import com.tpstreams.player.download.DownloadPlaybackHandler
import com.tpstreams.player.drm.DrmHandler
import com.tpstreams.player.tracks.TextTrackManager
import com.tpstreams.player.util.MediaItemUtils
import com.tpstreams.player.util.NetworkDiagnosticsManager
import com.tpstreams.player.util.SentryLogger
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
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
    private val getDecoderState: () -> PlayerDecoderState,
    private val onMediaPrepared: () -> Unit,
    private val shouldPlayOnPrepared: () -> Boolean,
    private val onLiveStreamStatusChanged: (Boolean) -> Unit,
    private val onError: (PlaybackError, String) -> Unit,
    private val logDebug: (String) -> Unit,
) {

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
                    val safeHost = try { Uri.parse(assetInfo.mediaUrl).host } catch (_: Exception) { "unknown" }
                    logDebug("fetchAndPrepare SUCCESS — cdnHost=$safeHost")
                    preparePlayer(assetInfo)
                }

                override fun onError(error: PlaybackError, message: String) {
                    logDebug("fetchAndPrepare onError — error=$error, message=$message")
                    if (error == PlaybackError.NETWORK_CONNECTION_FAILED ||
                        error == PlaybackError.NETWORK_CONNECTION_TIMEOUT) {
                        playerScope.launch {
                            networkDiagnosticsManager.handleError(
                                error,
                                cdnHostname = cdnHostname,
                                decoderState = getDecoderState(),
                                mediaUrl = mediaUrl
                            )
                        }
                    } else {
                        if (error != PlaybackError.LIVE_STREAM_NOT_STARTED && error != PlaybackError.LIVE_STREAM_ENDED) {
                            SentryLogger.logMessageWithEnrichment(
                                message = "Non-network error from asset fetch: $error",
                                level = SentryLevel.WARNING,
                                context = context,
                                player = exoPlayer,
                                decoderState = getDecoderState(),
                                tags = mapOf("assetId" to assetId, "errorType" to error.name)
                            )
                        }
                        Sentry.addBreadcrumb(Breadcrumb().apply {
                            setMessage("Non-network error from asset fetch")
                            setData("error_type", error.name)
                            setData("error_message", message)
                            setData("player_id", SentryLogger.generatePlayerIdString())
                            setData("asset_id", assetId)
                        })
                        playerScope.launch {
                            onError(error, message)
                        }
                    }
                }
            }, context = context)
        }
    }

    private fun preparePlayer(assetInfo: AssetInfo) {
        val orgId = TPStreamsSDK.requireOrgId()
        cdnHostname = try {
            Uri.parse(assetInfo.mediaUrl).host?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
        logDebug("CDN hostname extracted: $cdnHostname")

        val result = MediaItemUtils.buildMediaItem(assetInfo, assetInfo.title, orgId, assetId, accessToken)
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
            logDebug("Player PREPARE")
            exoPlayer.prepare()
            onMediaPrepared()

            if (shouldPlayOnPrepared()) {
                exoPlayer.play()
            }
        }
    }
}
