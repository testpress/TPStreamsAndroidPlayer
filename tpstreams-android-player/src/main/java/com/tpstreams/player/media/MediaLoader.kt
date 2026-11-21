package com.tpstreams.player.media

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.ExoPlayer
import com.tpstreams.player.TPStreamsSDK
import com.tpstreams.player.constants.PlaybackError
import com.tpstreams.player.data.AssetRepository
import com.tpstreams.player.data.network.model.AssetInfo
import com.tpstreams.player.data.network.ShieldedDashDataSourceFactory
import com.tpstreams.player.data.network.ShieldedManifestLoader
import com.tpstreams.player.download.DownloadPlaybackHandler
import com.tpstreams.player.download.DownloadController
import com.tpstreams.player.drm.DrmHandler
import com.tpstreams.player.tracks.TextTrackManager
import com.tpstreams.player.util.MediaItemUtils
import com.tpstreams.player.util.NetworkDiagnosticsManager
import com.tpstreams.player.util.WidevineDrmSessionManagerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val SHIELDED_METRICS_TAG = "TPSTREAMS_SHIELDED_METRICS"

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

            val playbackLoadStartNs = SystemClock.elapsedRealtimeNanos()
            Log.d(
                SHIELDED_METRICS_TAG,
                "PLAYBACK_TIMING stage=load_start assetId=$assetId",
            )
            AssetRepository.fetchAssetInfo(assetId, accessToken, object : AssetRepository.AssetCallback {
                override fun onSuccess(assetInfo: AssetInfo) {
                    Log.d(
                        SHIELDED_METRICS_TAG,
                        "PLAYBACK_TIMING stage=asset_response " +
                            "elapsed_ms=${elapsedMs(playbackLoadStartNs)}",
                    )
                    val safeHost = try { Uri.parse(assetInfo.mediaUrl).host } catch (_: Exception) { "unknown" }
                    logDebug(
                        "fetchAndPrepare SUCCESS — cdnHost=$safeHost, " +
                            "shieldedManifestUrl=${assetInfo.shieldedManifestUrl ?: "<absent>"}"
                    )
                    if (assetInfo.shieldedManifestUrl != null) {
                        Log.d(SHIELDED_METRICS_TAG, "SHIELDED MANIFEST FLOW START — assetId=$assetId")
                        playerScope.launch(Dispatchers.IO) {
                            runCatching {
                                ShieldedManifestLoader.load(assetInfo, assetId, accessToken)
                            }.onSuccess { manifest ->
                                if (manifest != null) {
                                    Log.d(
                                        SHIELDED_METRICS_TAG,
                                        "SHIELDED MANIFEST DECRYPTED — mpdChars=${manifest.mpd.length}, " +
                                            "seedBytes=${manifest.segmentSeed.size}"
                                    )
                                    preparePlayer(assetInfo, manifest, "shielded", playbackLoadStartNs)
                                }
                            }.onFailure { error ->
                                Log.e(
                                    SHIELDED_METRICS_TAG,
                                    "SHIELDED MANIFEST DECRYPT FAILED — ${error.message}",
                                    error,
                                )
                                playerScope.launch {
                                    onPlaybackError(
                                        PlaybackError.UNSPECIFIED,
                                        "Unable to prepare shielded manifest: ${error.message}",
                                    )
                                }
                            }
                        }
                    } else {
                        Log.d(
                            SHIELDED_METRICS_TAG,
                            "SHIELDED MANIFEST FLOW SKIPPED — shielded_manifest_url is absent",
                        )
                        preparePlayer(assetInfo, null, "baseline", playbackLoadStartNs)
                    }
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

    private fun preparePlayer(
        assetInfo: AssetInfo,
        shieldedManifest: com.tpstreams.player.data.network.DecryptedShieldedManifest? = null,
        playbackMode: String = "baseline",
        playbackLoadStartNs: Long? = null,
    ) {
        val prepareStartNs = SystemClock.elapsedRealtimeNanos()
        val orgId = TPStreamsSDK.requireOrgId()
        cdnHostname = try {
            Uri.parse(assetInfo.mediaUrl).host?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
        logDebug("CDN hostname extracted: $cdnHostname")

        val shieldedDataSourceFactory = shieldedManifest?.let {
            ShieldedDashDataSourceFactory(
                assetId = assetId,
                mpd = it.mpd,
                segmentSeed = it.segmentSeed,
                upstreamFactory = DownloadController.getDataSourceFactory(context),
            )
        }
        val result = MediaItemUtils.buildMediaItem(
            assetInfo = assetInfo,
            title = assetInfo.title,
            orgId = orgId,
            assetId = assetId,
            accessToken = accessToken,
            playbackUriOverride = shieldedDataSourceFactory?.manifestUri(),
        )
        val shieldedMediaSource = shieldedDataSourceFactory?.let { factory ->
            DashMediaSource.Factory(factory)
                .setDrmSessionManagerProvider(
                    WidevineDrmSessionManagerProvider(DownloadController.httpDataSourceFactory)
                )
                .createMediaSource(result.mediaItem)
        }
        if (shieldedMediaSource != null) {
            Log.d(
                SHIELDED_METRICS_TAG,
                "SHIELDED_TIMING media_source_build_ms=" +
                    ((SystemClock.elapsedRealtimeNanos() - prepareStartNs) / 1_000_000L)
            )
        }
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
            if (shieldedMediaSource != null) {
                Log.d(
                    SHIELDED_METRICS_TAG,
                    "SHIELDED MEDIA SOURCE SET — manifest served from memory",
                )
                exoPlayer.setMediaSource(shieldedMediaSource)
            } else {
                exoPlayer.setMediaItem(result.mediaItem)
            }
            logDebug("Player PREPARE")
            exoPlayer.prepare()
            playbackLoadStartNs?.let {
                Log.d(
                    SHIELDED_METRICS_TAG,
                    "PLAYBACK_TIMING stage=player_prepare mode=$playbackMode " +
                        "elapsed_ms=${elapsedMs(it)}",
                )
            }
            if (shieldedMediaSource != null) {
                Log.d(
                    SHIELDED_METRICS_TAG,
                    "SHIELDED_TIMING manifest_ready_to_player_prepare_ms=" +
                        ((SystemClock.elapsedRealtimeNanos() - prepareStartNs) / 1_000_000L)
                )
            }
            onMediaPrepared()

            if (shouldPlayOnPrepared()) {
                exoPlayer.play()
            }
        }
    }

    private fun elapsedMs(startNs: Long): Long {
        return (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L
    }
}
