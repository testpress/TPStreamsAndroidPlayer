package com.tpstreams.player

import android.content.Context
import android.media.AudioManager
import android.media.MediaCodec
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.DrmConfiguration
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import androidx.media3.common.Format
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import com.tpstreams.player.download.DownloadController
import com.tpstreams.player.download.DownloadClient
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.trackselection.MappingTrackSelector
import com.tpstreams.player.download.DownloadConstants
import com.tpstreams.player.util.SentryLogger
import com.tpstreams.player.constants.NetworkDiagnostics
import com.tpstreams.player.constants.PlaybackError
import com.tpstreams.player.constants.toError
import com.tpstreams.player.constants.getErrorMessage
import com.tpstreams.player.constants.LiveStreamNotStartedException
import com.tpstreams.player.constants.LiveStreamEndedException
import com.tpstreams.player.constants.toPlaybackError
import com.tpstreams.player.data.network.model.AssetInfo
import com.tpstreams.player.data.AssetRepository
import com.tpstreams.player.util.NetworkDiagnosticsManager
import com.tpstreams.player.util.MediaItemUtils
import com.tpstreams.player.util.network.*
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.DecoderReuseEvaluation
import com.tpstreams.player.util.PlaybackHistoryManager
import com.tpstreams.player.util.CodecManager
import com.tpstreams.player.util.ServerDateHeaderInterceptor
import com.tpstreams.player.util.DecoderInfoProvider
import com.tpstreams.player.data.PlayerDecoderState
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import com.tpstreams.player.util.FlightRecorder
import com.tpstreams.player.util.PlaybackExceptionWrapper
import com.tpstreams.player.util.DiagnosticSender
import com.tpstreams.player.util.LoggingMediaDrmCallback
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.LoadEventInfo
import com.tpstreams.player.util.NetworkMonitor
import java.io.IOException




class TPStreamsPlayer @OptIn(UnstableApi::class)
private constructor(
    private val context: Context,
    private val exoPlayer: ExoPlayer,
    private val trackSelector: DefaultTrackSelector,
    val assetId: String,
    val accessToken: String,
    private val shouldAutoPlay: Boolean = true,
    private val startAt: Long = 0,
    val enableDownload: Boolean = false,
    private val showDefaultCaptions: Boolean = false,
    val startInFullscreen: Boolean = false,
    val downloadMetadata: Map<String, String>? = null,
    val offlineLicenseExpireTime: Long = DownloadConstants.FIFTEEN_DAYS_IN_SECONDS
) : Player by exoPlayer {

    val playbackSessionId = (1..6)
        .map { (('a'..'z') + ('0'..'9')).random() }
        .joinToString("")

    private fun debugLog(message: String) {
        val fullMessage = "[$playbackSessionId] $message"
        Log.d(DEBUG_TAG, fullMessage)
        PlaybackHistoryManager.recordLog(fullMessage)
        FlightRecorder.log("SDK", "debugLog", mapOf("message" to message))
    }

    interface Listener {
        fun onAccessTokenExpired(videoId: String, callback: (String) -> Unit)
        fun onError(error: PlaybackError, message: String)
        fun onNetworkError(error: PlaybackError, message: String, diagnostics: NetworkDiagnostics) {
            onError(error, message)
        }
        /**
         * Called immediately when a network error is detected, before diagnostics
         * probes complete. The UI can use this to show a "Diagnosing…" state.
         */
        fun onNetworkDiagnosticsStarted() {}
    }

    private var isPrepared = false
    private var drmLicenseUrl: String? = null
    private var requestedPlay = false
    private var hasSeekedToStartAt = false
    private var subtitleMetadata = mapOf<String, Boolean>()
    private var _isLiveStream = false
    
    val isLiveStream: Boolean
        get() = _isLiveStream
    
    @Volatile
    private var released = false

    internal var onLiveStreamStatusChanged: ((Boolean) -> Unit)? = null
    
    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val networkRecoveryHandler = NetworkRecoveryHandler(context)
    @Volatile
    private var cdnHostname: String? = null

    // Per-player decoder state (not global — avoids cross-player corruption)
    @Volatile
    private var decoderState = PlayerDecoderState()
    internal fun getDecoderState(): PlayerDecoderState = decoderState

    // Cached position/buffered for thread-safe FlightRecorder access
    @Volatile private var cachedPosition = 0L
    @Volatile private var cachedBufferedPosition = 0L
    @Volatile private var lastSeekTargetMs = 0L

    private val networkDiagnosticsManager = NetworkDiagnosticsManager(
        playerScope = playerScope,
        assetId = assetId,
        exoPlayer = exoPlayer,
        context = context,
        networkRecoveryHandler = networkRecoveryHandler,
        listener = { error, message, diagnostics ->
            _listener?.onNetworkError(error, message, diagnostics)
        },
        retryPlayback = { retryPlayback() },
        onDiagnosticsStarted = {
            _listener?.onNetworkDiagnosticsStarted()
        }
    )

    private val networkMonitor = NetworkMonitor(context)

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val event = when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
            AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> "GAIN_TRANSIENT"
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> "GAIN_TRANSIENT_MAY_DUCK"
            else -> "UNKNOWN($focusChange)"
        }
        FlightRecorder.logAudioFocus(event, mapOf("focusChange" to focusChange))
    }

    fun retry() {
        if (released) return
        networkDiagnosticsManager.onManualRetry()
        retryPlayback()
    }

    private fun retryPlayback() {
        if (released) return
        playerScope.launch {
            try {
                if (!isPrepared) {
                    val org = TPStreamsSDK.orgId
                    if (org != null) {
                        Log.d("TPStreamsPlayer", "Retrying initial fetchAndPrepare")
                        fetchAndPrepare(assetId, accessToken)
                    }
                } else {
                    debugLog("Player PREPARE (Retry)")
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            } catch (e: Exception) {
                Log.e("TPStreamsPlayer", "Error resuming playback", e)
            }
        }
    }

    private var _listener: Listener? = null
    var listener: Listener?
        get() = _listener
        set(value) {
            _listener = value
            if (value != null) {
                Log.d("TPStreamsPlayer", "Player listener set")
            }
        }

    init {
        FlightRecorder.positionProvider = {
            Pair(cachedPosition, cachedBufferedPosition)
        }
        networkMonitor.start()
        audioManager.requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        
        debugLog("Player INIT - Instance created for assetId: $assetId")
        synchronized(TPStreamsPlayer::class.java) {
            activePlayerCount++
            debugLog("Active Player COUNT: $activePlayerCount")
        }

        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                CodecManager.onDecoderInitialized()
                val isHardware = DecoderInfoProvider.isDecoderHardware(decoderName)
                decoderState = decoderState.copy(
                    videoDecoderName = decoderName,
                    videoDecoderIsHardware = isHardware
                )
                CodecManager.logCodecStatus(decoderName, "video/avc")
                FlightRecorder.logDecoderInit(
                    type = "video",
                    name = decoderName,
                    mimeType = null,
                    isHardware = isHardware,
                    isSecure = false,
                    initDurationMs = initializationDurationMs
                )
            }

            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                val isHardware = DecoderInfoProvider.isDecoderHardware(decoderName)
                decoderState = decoderState.copy(
                    audioDecoderName = decoderName,
                    audioDecoderIsHardware = isHardware
                )
                FlightRecorder.logDecoderInit(
                    type = "audio",
                    name = decoderName,
                    mimeType = null,
                    isHardware = isHardware,
                    isSecure = false,
                    initDurationMs = initializationDurationMs
                )
            }

            override fun onAudioDecoderReleased(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String
            ) {
                CodecManager.onDecoderReleased()
                decoderState = decoderState.copy(
                    audioDecoderName = null,
                    audioDecoderIsHardware = null,
                    audioMimeType = null
                )
                debugLog("Audio decoder RELEASED - Codec: $decoderName")
            }

            override fun onVideoDecoderReleased(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String
            ) {
                CodecManager.onDecoderReleased()
                // Clear only video fields — audio decoder may still be active
                decoderState = decoderState.copy(
                    videoDecoderName = null,
                    videoDecoderIsHardware = null,
                    videoMimeType = null
                )
                debugLog("Video decoder RELEASED - Codec: $decoderName")
            }

            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                debugLog("Decoder Format - ${format.sampleMimeType}, Res: ${format.width}x${format.height}, Bitrate: ${format.bitrate}")
                decoderState = decoderState.copy(videoMimeType = format.sampleMimeType)
                if (decoderReuseEvaluation != null) {
                    val resultLabel = when (decoderReuseEvaluation.result) {
                        DecoderReuseEvaluation.REUSE_RESULT_NO -> "NO"
                        DecoderReuseEvaluation.REUSE_RESULT_YES_WITH_FLUSH -> "YES_WITH_FLUSH"
                        DecoderReuseEvaluation.REUSE_RESULT_YES_WITH_RECONFIGURATION -> "YES_WITH_RECONFIGURATION"
                        DecoderReuseEvaluation.REUSE_RESULT_YES_WITHOUT_RECONFIGURATION -> "YES_WITHOUT_RECONFIGURATION"
                        else -> "UNKNOWN (${decoderReuseEvaluation.result})"
                    }
                    debugLog("Decoder RE-INIT / REPLACEMENT - Result: $resultLabel")
                }
            }

            override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
                cachedPosition = eventTime.currentPlaybackPositionMs
                cachedBufferedPosition = eventTime.totalBufferedDurationMs
                val stateName = when (state) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                debugLog("Playback STATE CHANGE - $stateName")
                FlightRecorder.log("PLAYBACK_STATE", "STATE_$stateName")
            }

            override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
                cachedPosition = eventTime.currentPlaybackPositionMs
                cachedBufferedPosition = eventTime.totalBufferedDurationMs
                FlightRecorder.log("PLAYER", "onRenderedFirstFrame", mapOf("renderTimeMs" to renderTimeMs))
                debugLog("First Frame Rendered")
            }

            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                decoderState = decoderState.copy(audioMimeType = format.sampleMimeType)
            }

            override fun onSurfaceSizeChanged(eventTime: AnalyticsListener.EventTime, width: Int, height: Int) {
                if (width == 0 && height == 0) {
                    debugLog("Surface DESTROYED (0x0) — ExoPlayer will handle internally")
                } else {
                    debugLog("Surface SIZE CHANGED - ${width}x${height}")
                }
            }

            override fun onDrmKeysLoaded(eventTime: AnalyticsListener.EventTime) {
                FlightRecorder.logDRMPhase("keys_loaded")
                debugLog("DRM KEYS LOADED")
            }

            override fun onMediaItemTransition(
                eventTime: AnalyticsListener.EventTime,
                mediaItem: MediaItem?,
                reason: Int
            ) {
                if (mediaItem != null) {
                    val transitionReason = when (reason) {
                        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
                        Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
                        Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
                        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
                        else -> "UNKNOWN ($reason)"
                    }
                    debugLog("MediaItem TRANSITION - ${mediaItem.mediaId}, Reason: $transitionReason")
                }
            }
            override fun onBandwidthEstimate(
                eventTime: AnalyticsListener.EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long
            ) {
                FlightRecorder.log("NETWORK", "onBandwidthEstimate", mapOf("bitrateEstimate" to bitrateEstimate, "totalBytes" to totalBytesLoaded))
            }

            override fun onDownstreamFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                mediaLoadData: MediaLoadData
            ) {
                val format = mediaLoadData.trackFormat
                FlightRecorder.log("NETWORK", "onDownstreamFormatChanged", mapOf(
                    "trackType" to mediaLoadData.trackType,
                    "mimeType" to (format?.sampleMimeType ?: "unknown"),
                    "height" to (format?.height ?: 0),
                    "width" to (format?.width ?: 0),
                    "bitrate" to (format?.bitrate ?: 0)
                ))

                // Update selected track tracking in FlightRecorder
                if (format != null) {
                    when (mediaLoadData.trackType) {
                        androidx.media3.common.C.TRACK_TYPE_VIDEO -> {
                            FlightRecorder.updateSelectedVideoFormat(
                                height = format.height,
                                width = format.width,
                                bitrate = format.bitrate.toLong(),
                                codec = format.sampleMimeType ?: "unknown"
                            )
                        }
                        androidx.media3.common.C.TRACK_TYPE_AUDIO -> {
                            FlightRecorder.updateSelectedAudioFormat(
                                bitrate = format.bitrate.toLong(),
                                codec = format.sampleMimeType ?: "unknown",
                                language = format.language ?: "unknown"
                            )
                        }
                    }
                }
            }

            override fun onLoadStarted(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData
            ) {
                FlightRecorder.log("NETWORK", "onLoadStarted", mapOf("uri" to loadEventInfo.uri.toString()))
            }

            override fun onLoadCompleted(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData
            ) {
                FlightRecorder.log("NETWORK", "onLoadCompleted", mapOf("uri" to loadEventInfo.uri.toString(), "bytesLoaded" to loadEventInfo.bytesLoaded))
            }

            override fun onLoadError(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData,
                error: IOException,
                wasCanceled: Boolean
            ) {
                FlightRecorder.log("NETWORK", "onLoadError", mapOf("uri" to loadEventInfo.uri.toString(), "error" to error.toString(), "wasCanceled" to wasCanceled))
            }

            override fun onLoadCanceled(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData
            ) {
                FlightRecorder.log("NETWORK", "onLoadCanceled", mapOf("uri" to loadEventInfo.uri.toString()))
            }

            override fun onDrmSessionAcquired(eventTime: AnalyticsListener.EventTime, state: Int) {
                FlightRecorder.log("DRM", "onDrmSessionAcquired", mapOf("state" to state))
            }

            override fun onDrmKeysRemoved(eventTime: AnalyticsListener.EventTime) {
                FlightRecorder.log("DRM", "onDrmKeysRemoved")
            }

            override fun onDrmSessionManagerError(eventTime: AnalyticsListener.EventTime, error: Exception) {
                FlightRecorder.log("DRM", "onDrmSessionManagerError", mapOf("error" to error.toString()))
            }

            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                FlightRecorder.log("DECODER", "onDroppedVideoFrames", mapOf("droppedFrames" to droppedFrames, "elapsedMs" to elapsedMs))
            }

            override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: androidx.media3.common.VideoSize) {
                FlightRecorder.log("DECODER", "onVideoSizeChanged", mapOf("width" to videoSize.width, "height" to videoSize.height))
            }

            override fun onTimelineChanged(eventTime: AnalyticsListener.EventTime, reason: Int) {
                FlightRecorder.log("PLAYBACK_STATE", "onTimelineChanged", mapOf("reason" to reason))
            }



            override fun onSeekStarted(eventTime: AnalyticsListener.EventTime) {
                FlightRecorder.logSeek(from = eventTime.currentPlaybackPositionMs, to = lastSeekTargetMs)
                debugLog("SEEK STARTED from ${eventTime.currentPlaybackPositionMs}ms to ${lastSeekTargetMs}ms")
            }

            fun onSeekProcessed(eventTime: AnalyticsListener.EventTime) {
                FlightRecorder.logSeekCompleted(from = lastSeekTargetMs, to = eventTime.currentPlaybackPositionMs)
                debugLog("SEEK COMPLETED at ${eventTime.currentPlaybackPositionMs}ms")
            }

            override fun onPlaybackSuppressionReasonChanged(eventTime: AnalyticsListener.EventTime, playbackSuppressionReason: Int) {
                FlightRecorder.log("PLAYBACK_STATE", "SuppressionReasonChanged", mapOf("reason" to playbackSuppressionReason))
            }

            override fun onTracksChanged(eventTime: AnalyticsListener.EventTime, tracks: Tracks) {
                val videoFormat = tracks.groups.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }?.getTrackFormat(0)
                val audioFormat = tracks.groups.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }?.getTrackFormat(0)
                FlightRecorder.log("TRACKS", "onTracksChanged", mapOf(
                    "videoResolution" to "${videoFormat?.width}x${videoFormat?.height}",
                    "videoBitrate" to (videoFormat?.bitrate ?: 0),
                    "videoCodec" to (videoFormat?.sampleMimeType ?: "unknown"),
                    "audioLanguage" to (audioFormat?.language ?: "unknown"),
                    "audioBitrate" to (audioFormat?.bitrate ?: 0),
                    "audioCodec" to (audioFormat?.sampleMimeType ?: "unknown")
                ))
            }
            
            override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
                val stacktrace = error.stackTraceToString()
                val causeMessage = error.cause?.message
                var rendererIndex: Int? = null
                var rendererName: String? = null
                var errorType: String? = null
                var recoverable = error is androidx.media3.exoplayer.ExoPlaybackException && error.rendererIndex >= 0
                var isBehindLiveWindow = error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW

                if (error is androidx.media3.exoplayer.ExoPlaybackException) {
                    errorType = when (error.type) {
                        androidx.media3.exoplayer.ExoPlaybackException.TYPE_SOURCE -> "SOURCE"
                        androidx.media3.exoplayer.ExoPlaybackException.TYPE_RENDERER -> "RENDERER"
                        androidx.media3.exoplayer.ExoPlaybackException.TYPE_UNEXPECTED -> "UNEXPECTED"
                        androidx.media3.exoplayer.ExoPlaybackException.TYPE_REMOTE -> "REMOTE"
                        else -> "UNKNOWN"
                    }
                    rendererName = error.rendererName
                    rendererIndex = error.rendererIndex
                }

                FlightRecorder.logPlayerError(
                    PlaybackExceptionWrapper(
                        errorCodeName = error.errorCodeName,
                        errorCode = error.errorCode,
                        message = error.message,
                        causeMessage = causeMessage,
                        recoverable = recoverable,
                        isBehindLiveWindow = isBehindLiveWindow,
                        rendererIndex = rendererIndex,
                        rendererName = rendererName,
                        errorType = errorType,
                        stacktrace = stacktrace
                    )
                )
            }
        })

        Log.d("TPStreamsPlayer", "Initializing TPStreamsPlayer with assetId: $assetId")
        
        exoPlayer.addListener(object : Player.Listener {
            @OptIn(UnstableApi::class)
            override fun onTracksChanged(tracks: Tracks) {
                val textTracks = getAvailableTextTracks()
                debugLog("Tracks changed. Text tracks available: ${textTracks.size}")
                
                if (showDefaultCaptions && isPrepared && textTracks.isNotEmpty()) {
                    enableDefaultCaptions()
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                debugLog("Player state changed: state=$playbackState, playWhenReady=${exoPlayer.playWhenReady}")
                seekToStartAt()
            }
            
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                debugLog("Play when ready changed: $playWhenReady, reason=$reason")
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                debugLog("Is playing changed: $isPlaying")
                if (isPlaying) networkDiagnosticsManager.onPlaybackRecovered()
            }
            
            override fun onPlayerError(error: PlaybackException) {
                if (FlightRecorder.isActive()) {
                    sendDiagnostics("error", "unknown")
                }
                if (isDrmLicenseExpiredError(error)) {
                    Log.d("TPStreamsPlayer", "DRM error detected for asset: $assetId")
                    DownloadController.renewDrmLicense(context, assetId, this@TPStreamsPlayer)  
                    return
                }

                if (isPlaylistStuckException(error)) {
                    // This error is raised when the HLS playlist stops advancing (e.g., the live stream is paused).
                    // We safely suppress this error so the player naturally shows the buffering spinner instead of crashing.
                    // Playback can resume once the stream starts advancing again or the user seeks to a previous duration.
                    return
                }

                if (isNetworkError(error)) {
                    networkDiagnosticsManager.handleError(error.toError(), error, cdnHostname, decoderState)
                    return
                }
                
                // Non-network errors go directly to _listener?.onError() (not onNetworkError).
                // Network errors route through handleError → manager → _listener?.onNetworkError().
                debugLog("Player ERROR - ${error.errorCodeName}")
                val errorPlayerId = SentryLogger.generatePlayerIdString()
                SentryLogger.logPlaybackException(error, assetId, errorPlayerId, drmLicenseUrl = drmLicenseUrl, context = context, player = exoPlayer, decoderState = decoderState)
                
                val errorType = error.toError()
                val errorMessage = error.getErrorMessage(errorPlayerId)
                
                Log.e("TPStreamsPlayer", "Player error: ${error.errorCodeName}", error)
                _listener?.onError(errorType, errorMessage)
            }
            
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                debugLog("Playback parameters changed: speed=${playbackParameters.speed}")
            }
            
            override fun onIsLoadingChanged(isLoading: Boolean) {
                debugLog("Is loading changed: $isLoading")
            }
        })

        TPStreamsSDK.requireOrgId()
        fetchAndPrepare(assetId, accessToken)
    }

    private fun enableDefaultCaptions() {
        val textTracks = getAvailableTextTracks()
        val defaultTrack = textTracks.firstOrNull()
        defaultTrack?.let {
            setTextTrackByLanguage(it.first)
        }
    }

    private fun isDrmLicenseExpiredError(error: PlaybackException): Boolean {
        val cause = error.cause
        return error.errorCode == PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED ||
               error.errorCode == PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION ||
                error.errorCode == PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR ||
                cause is MediaCodec.CryptoException
    }

    @OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun isPlaylistStuckException(error: PlaybackException): Boolean {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker.PlaylistStuckException) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun fetchAndPrepare(assetId: String, accessToken: String) {
        FlightRecorder.log("PLAYER", "fetchAndPrepare started", mapOf("assetId" to assetId))
        val fetchStartTime = android.os.SystemClock.elapsedRealtime()
        
        CoroutineScope(Dispatchers.IO).launch {
            if (playFromDownload(assetId)) return@launch

            AssetRepository.fetchAssetInfo(assetId, accessToken, object : AssetRepository.AssetCallback {
                override fun onSuccess(assetInfo: AssetInfo) {
                    val latencyMs = android.os.SystemClock.elapsedRealtime() - fetchStartTime
                    FlightRecorder.log("NETWORK", "Config received", mapOf(
                        "latencyMs" to latencyMs,
                        "status" to 200,
                        "streamType" to when (assetInfo.videoObj?.optString("type")) {
                            "hls" -> "HLS"
                            "dash" -> "DASH"
                            else -> "MP4"
                        },
                        "drmEnabled" to assetInfo.enableDrm
                    ))
                    
                    val safeHost = try { Uri.parse(assetInfo.mediaUrl).host } catch (_: Exception) { "unknown" }
                    debugLog("fetchAndPrepare SUCCESS — cdnHost=$safeHost")
                    preparePlayer(assetInfo, assetId, accessToken)
                }

                override fun onError(error: PlaybackError, message: String) {
                    val latencyMs = android.os.SystemClock.elapsedRealtime() - fetchStartTime
                    FlightRecorder.log("NETWORK", "Config failed", mapOf("latencyMs" to latencyMs, "error" to error.name, "message" to message))
                    
                    debugLog("fetchAndPrepare onError — error=$error, message=$message")
                    if (error == PlaybackError.NETWORK_CONNECTION_FAILED || 
                        error == PlaybackError.NETWORK_CONNECTION_TIMEOUT) {
                        playerScope.launch {
                            networkDiagnosticsManager.handleError(error, cdnHostname = cdnHostname, decoderState = decoderState)
                        }
                    } else {
                        SentryLogger.logMessageWithEnrichment(
                            message = "Non-network error from asset fetch: $error",
                            level = SentryLevel.WARNING,
                            context = context,
                            player = exoPlayer,
                            decoderState = decoderState,
                            tags = mapOf("assetId" to assetId, "errorType" to error.name)
                        )
                        Sentry.addBreadcrumb(Breadcrumb().apply {
                            setMessage("Non-network error from asset fetch")
                            setData("error_type", error.name)
                            setData("error_message", message)
                            setData("player_id", SentryLogger.generatePlayerIdString())
                            setData("asset_id", assetId)
                        })
                        playerScope.launch {
                            _listener?.onError(error, message)
                        }
                    }
                }
            }, context = context)
        }
    }

    @OptIn(UnstableApi::class)
    private fun preparePlayer(assetInfo: AssetInfo, assetId: String, accessToken: String) {
        val orgId = TPStreamsSDK.requireOrgId()
        cdnHostname = try {
            Uri.parse(assetInfo.mediaUrl).host?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
        debugLog("CDN hostname extracted: $cdnHostname")

        val result = MediaItemUtils.buildMediaItem(assetInfo, assetInfo.title, orgId, assetId, accessToken)
        drmLicenseUrl = result.drmLicenseUrl
        setSubtitleMetadata(result.subtitleMetadata)

        playerScope.launch(Dispatchers.Main) {
            _isLiveStream = assetInfo.isLiveStream
            onLiveStreamStatusChanged?.invoke(_isLiveStream)

            networkDiagnosticsManager.onMediaLoaded()

            val audioAttributes = buildAudioAttributes()
            exoPlayer.setAudioAttributes(audioAttributes, true)
            debugLog("MediaItem SET - ${result.mediaItem.mediaId}")
            exoPlayer.setMediaItem(result.mediaItem)
            debugLog("Player PREPARE")
            exoPlayer.prepare()
            isPrepared = true

            if (shouldAutoPlay || requestedPlay) {
                exoPlayer.play()
            }
        }
    }

    private fun seekToStartAt() {
        if (playbackState == Player.STATE_READY && !hasSeekedToStartAt && startAt > 0) {
            val duration = exoPlayer.duration
            if (duration > 0 && duration != C.TIME_UNSET) {
                val seekPosition = minOf(startAt * 1000, maxOf(0, duration - 1000))
                seekTo(seekPosition)
                hasSeekedToStartAt = true
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun playFromDownload(assetId: String): Boolean {
        try {
            val downloadClient = DownloadClient.getInstance(context)
            val download = downloadClient.getDownload(assetId)
            
            if (download != null) {
                Log.d("TPStreamsPlayer", "Found downloaded content for $assetId, using local version")

                val downloadedMediaItem = DownloadController.buildMediaItemFromDownload(download)
                if (downloadedMediaItem == null) {
                    Log.e("TPStreamsPlayer", "Failed to build media item from download for $assetId")
                    return false
                }

                playerScope.launch {
                    val audioAttributes = buildAudioAttributes()

                    exoPlayer.setAudioAttributes(audioAttributes, true)
                    exoPlayer.setMediaItem(downloadedMediaItem)
                    exoPlayer.prepare()
                    isPrepared = true
                    if (shouldAutoPlay || requestedPlay) {
                        exoPlayer.play()
                    }
                }
                return true
            }
        } catch (e: Exception) {
            Log.e("TPStreamsPlayer", "Error checking for downloads: ${e.message}", e)
        }
        
        return false
    }

    private fun buildAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_NONE)
            .build()
    }

    @OptIn(UnstableApi::class)
    fun refreshPlaybackWithDownloadMediaItem(mediaItem: MediaItem) {
        playerScope.launch {
            val audioAttributes = buildAudioAttributes()

            val currentPosition = exoPlayer.currentPosition
            exoPlayer.stop()
            exoPlayer.setAudioAttributes(audioAttributes, true)
            exoPlayer.clearMediaItems()
            
            debugLog("MediaItem SET (Download) - ${mediaItem.mediaId}")
            exoPlayer.setMediaItem(mediaItem)
            debugLog("Player PREPARE")
            exoPlayer.prepare()
            val duration = exoPlayer.duration
            if ((currentPosition > 0) && (duration == C.TIME_UNSET || currentPosition < duration)) {
                exoPlayer.seekTo(currentPosition)
            }
            exoPlayer.play()
        }
    }

    override fun play() {
        if (isPrepared) {
            if (exoPlayer.playbackState == Player.STATE_IDLE) {
                exoPlayer.prepare()
            }
            exoPlayer.play()
        } else {
            requestedPlay = true
        }
    }

    override fun pause() {
        playerScope.launch {
            exoPlayer.pause()
        }
    }

    /**
     * Seeks to a specific position in the current media item.
     * @param positionMs The position in milliseconds to seek to
     */
    override fun seekTo(positionMs: Long) {
        lastSeekTargetMs = positionMs
        playerScope.launch {
            exoPlayer.seekTo(positionMs)
        }
    }

    /**
     * Returns whether the player is currently playing.
     * @return True if the player is playing, false otherwise
     */
    override fun isPlaying(): Boolean = exoPlayer.isPlaying

    /**
     * Returns the current playback position in milliseconds.
     * @return The current position in milliseconds
     */
    override fun getCurrentPosition(): Long = exoPlayer.currentPosition

    /**
     * Returns the duration of the current media item in milliseconds.
     * @return The duration in milliseconds, or C.TIME_UNSET if unknown
     */
    override fun getDuration(): Long = exoPlayer.duration

    /**
     * Sets the playback speed for the player.
     * @param speed The playback speed factor (1.0f is normal speed)
     */
    override fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
    }

    /**
     * Gets the current playback speed.
     * @return The current playback speed factor
     */
    fun getPlaybackSpeed(): Float = exoPlayer.playbackParameters.speed

    /**
     * Gets the current playback state.
     * @return One of the Player.STATE_* constants
     */
    override fun getPlaybackState(): Int = exoPlayer.playbackState

    /**
     * Explicitly releases the video surface from the ExoPlayer's video renderer.
     * Must be called before setPlayer(null) during fullscreen transitions to prevent
     * MediaTek secure decoder NO_MEMORY crashes — the codec retains a surface reference
     * even after setPlayer(null), and rapid detach/reattach creates a new codec before
     * the old one is fully released.
     */
    fun releaseVideoSurface() {
        if (released) return
        debugLog("Surface CLEAR (pre-transition)")
        exoPlayer.clearVideoSurface()
    }

    override fun release() {
        debugLog("Surface DETACH (Player Released)")
        debugLog("Player RELEASE - assetId: $assetId")
        synchronized(TPStreamsPlayer::class.java) {
            activePlayerCount--
            debugLog("Active Player COUNT: $activePlayerCount")
        }
        if (released) return
        released = true

        FlightRecorder.logLifecycle("release", mapOf(
            "assetId" to (assetId ?: "unknown"),
            "playbackPositionMs" to cachedPosition,
            "reason" to "user_exit"
        ))

        networkMonitor.stop()
        playerScope.cancel()
        networkDiagnosticsManager.onRelease()
        audioManager.abandonAudioFocus(audioFocusListener)
        // Clear surface binding before releasing the player.
        // Prevents codec crashes on MediaTek secure decoders (NO_MEMORY)
        // where the codec retains a reference to a released surface.
        exoPlayer.clearVideoSurface()
        exoPlayer.release()
        networkRecoveryHandler.stopMonitoring()
        // Clear decoder state — audio decoder info would otherwise persist forever
        decoderState = PlayerDecoderState()
    }

    @OptIn(UnstableApi::class)
    fun getTrackSelector(): DefaultTrackSelector = trackSelector

    @OptIn(UnstableApi::class)
    fun getAvailableVideoResolutions(): List<Int> {
        val resolutions = mutableSetOf<Int>()

        val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: return emptyList()
        for (rendererIndex in 0 until mappedTrackInfo.rendererCount) {
            if (mappedTrackInfo.getRendererType(rendererIndex) == C.TRACK_TYPE_VIDEO) {
                val trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex)
                for (groupIndex in 0 until trackGroups.length) {
                    val group = trackGroups.get(groupIndex)
                    for (trackIndex in 0 until group.length) {
                        val format = group.getFormat(trackIndex)
                        if (format.height != Format.NO_VALUE && format.height <= maxAllowedResolution) {
                            resolutions.add(format.height)
                        }
                    }
                }
            }
        }

        return resolutions.sortedDescending()
    }

    @OptIn(UnstableApi::class)
    fun getResolutionBitrates(): Map<String, Int> {
        val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: return emptyMap()
        
        val resolutionBitrateMap = mutableMapOf<Int, Int>()
        
        for (rendererIndex in 0 until mappedTrackInfo.rendererCount) {
            if (mappedTrackInfo.getRendererType(rendererIndex) == C.TRACK_TYPE_VIDEO) {
                val trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex)
                for (groupIndex in 0 until trackGroups.length) {
                    val group = trackGroups.get(groupIndex)
                    for (trackIndex in 0 until group.length) {
                        val format = group.getFormat(trackIndex)
                        if (format.height != Format.NO_VALUE && format.bitrate != Format.NO_VALUE && format.height <= maxAllowedResolution) {
                            // Store the resolution and its corresponding bitrate
                            resolutionBitrateMap[format.height] = format.bitrate
                        }
                    }
                }
            }
        }
        
        // Convert to final map format with string keys
        val combinedBitrates = mutableMapOf<String, Int>()
        for ((resolution, bitrate) in resolutionBitrateMap) {
            combinedBitrates["${resolution}p"] = bitrate
        }
        
        Log.d("TPStreamsPlayer", "Resolution-bitrate map: $combinedBitrates")
        return combinedBitrates
    }

    @OptIn(UnstableApi::class)
    fun getAvailableTextTracks(): List<Pair<String, String>> {
        val tracks = mutableListOf<Pair<String, String>>()
        
        // Get text tracks directly from the player's current tracks
        val currentTracks = exoPlayer.currentTracks
        
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    if (group.isTrackSupported(i)) {
                        val format = group.getTrackFormat(i)
                        if (format.sampleMimeType == MimeTypes.APPLICATION_CEA608) {
                            continue
                        }
                        val language = format.language ?: "unknown"
                        val label = format.label ?: language
                        tracks.add(Pair(language, label))
                    }
                }
            }
        }
        
        return tracks
    }
    
    @OptIn(UnstableApi::class)
    fun setTextTrackByLanguage(language: String?) {
        val parametersBuilder = trackSelector.buildUponParameters()
        
        if (language == null) {
            parametersBuilder.setPreferredTextLanguage(null)
            parametersBuilder.setSelectUndeterminedTextLanguage(false)
            Log.d("TPStreamsPlayer", "Disabling text tracks")
        } else {
            parametersBuilder.setPreferredTextLanguage(language)
            parametersBuilder.setSelectUndeterminedTextLanguage(true)
            parametersBuilder.setDisabledTextTrackSelectionFlags(0)
            Log.d("TPStreamsPlayer", "Setting preferred text language to: $language")
        }
        
        trackSelector.parameters = parametersBuilder.build()
        
        val currentPosition = exoPlayer.currentPosition
        if (exoPlayer.isPlaying) {
            exoPlayer.seekTo(currentPosition)
        }
    }

    private var maxAllowedResolution: Int = Int.MAX_VALUE
    private var userPreferredResolution: Int = Int.MAX_VALUE

    @OptIn(UnstableApi::class)
    fun setMaxResolution(height: Int) {
        Log.d("TPStreamsPlayer", "Setting hard max video height to $height")
        maxAllowedResolution = height
        applyResolutionConstraints()
    }

    @OptIn(UnstableApi::class)
    internal fun setUserResolutionPreference(height: Int) {
        Log.d("TPStreamsPlayer", "User preferred max video height set to $height")
        userPreferredResolution = height
        applyResolutionConstraints()
    }

    @OptIn(UnstableApi::class)
    private fun applyResolutionConstraints() {
        val effectiveMax = minOf(maxAllowedResolution, userPreferredResolution)
        val parametersBuilder = trackSelector.buildUponParameters()
        
        if (effectiveMax == Int.MAX_VALUE) {
            parametersBuilder.clearVideoSizeConstraints()
        } else {
            parametersBuilder.setMaxVideoSize(Int.MAX_VALUE, effectiveMax) // Unlimited width, constrained height
        }
        
        trackSelector.parameters = parametersBuilder.build()
    }

    /**
     * Get the currently active text track, if any
     * @return Pair of (language, label) for the active track, or null if no track is active
     */
    @OptIn(UnstableApi::class)
    fun getActiveTextTrack(): Pair<String, String>? {
        val currentTracks = exoPlayer.currentTracks
        
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    if (group.isTrackSelected(i)) {
                        val format = group.getTrackFormat(i)
                        val language = format.language ?: "unknown"
                        val label = format.label ?: language
                        return Pair(language, label)
                    }
                }
            }
        }
        
        return null
    }

    private fun setSubtitleMetadata(metadata: Map<String, Boolean>) {
        subtitleMetadata = metadata
    }
    
    /**
     * Check if a subtitle track is auto-generated
     */
    fun isSubtitleAutoGenerated(language: String): Boolean {
        return subtitleMetadata[language] ?: false
    }

    fun isTokenValid(assetId: String, callback: (Boolean) -> Unit) {
        Log.d("TPStreamsPlayer", "Checking if token is valid for asset: $assetId")
        
        CoroutineScope(Dispatchers.Main).launch {
            if (accessToken.isEmpty() && TPStreamsSDK.getAuthHeaders().isEmpty()) {
                Log.d("TPStreamsPlayer", "No current token available")
                callback(false)
                return@launch
            }
            
            val orgId = TPStreamsSDK.orgId ?: run {
                callback(false)
                return@launch
            }
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val assetApiUrl = TPStreamsSDK.apiService.tokenValidationUrl(orgId, assetId, accessToken)
                    val requestBuilder = Request.Builder()
                        .url(assetApiUrl)
                        .head()

                    TPStreamsSDK.getAuthHeaders().forEach { (name, value) ->
                        requestBuilder.addHeader(name, value)
                    }

                    val request = requestBuilder.build()
                    
                    val response = client.newCall(request).execute()
                    val isValid = response.isSuccessful
                    Log.d("TPStreamsPlayer", "Token validation result: ${if (isValid) "valid" else "invalid"}")
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        callback(isValid)
                    }
                } catch (e: Exception) {
                    Log.e("TPStreamsPlayer", "Error checking token validity: ${e.message}")
                    CoroutineScope(Dispatchers.Main).launch {
                        callback(false)
                    }
                }
            }
        }
    }

    fun getNewToken(assetId: String, callback: (String) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
                listener?.onAccessTokenExpired(assetId) { newToken ->
                    if (newToken.isNotEmpty()) {
                        Log.d("TPStreamsPlayer", "Received fresh token for download")
                        callback(newToken)
                    } else {
                        Log.e("TPStreamsPlayer", "Failed to get fresh token for download")
                        callback("")
                    }
                } ?: run {
                    Log.e("TPStreamsPlayer", "No token listener available")
                callback("")
            }
        }
    }

    companion object {
        private var activePlayerCount = 0
        internal const val DEBUG_TAG = "PLAYBACK_ERROR_DEBUG"
        private const val DEFAULT_SEEK_INCREMENT_MS = 10000L
        private val client = OkHttpClient.Builder()
            .addInterceptor(ServerDateHeaderInterceptor())
            .build()



        @OptIn(UnstableApi::class)
        private fun createExoPlayer(
            context: Context,
            seekBackIncrementMs: Long = DEFAULT_SEEK_INCREMENT_MS,
            seekForwardIncrementMs: Long = DEFAULT_SEEK_INCREMENT_MS
        ): Pair<ExoPlayer, DefaultTrackSelector> {
            require(seekBackIncrementMs > 0) { "seekBackIncrementMs must be greater than 0, was $seekBackIncrementMs" }
            require(seekForwardIncrementMs > 0) { "seekForwardIncrementMs must be greater than 0, was $seekForwardIncrementMs" }
            val trackSelector = DefaultTrackSelector(context).apply {
                parameters = DefaultTrackSelector.Parameters.Builder()
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .build()
            }

            val renderersFactory = DefaultRenderersFactory(context.applicationContext)
                .setEnableDecoderFallback(true)

            DownloadController.initialize(context)

            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(DownloadController.downloadCache)
                .setUpstreamDataSourceFactory(DownloadController.httpDataSourceFactory)
                .setCacheWriteDataSinkFactory(null)

            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(cacheDataSourceFactory)

            return ExoPlayer.Builder(context, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setTrackSelector(trackSelector)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_NONE)
                        .build(), 
                    true
                )
                .setSeekBackIncrementMs(seekBackIncrementMs)
                .setSeekForwardIncrementMs(seekForwardIncrementMs)
                .build() to trackSelector
        }

        @OptIn(UnstableApi::class)
        fun create(
            context: Context,
            assetId: String,
            accessToken: String,
            shouldAutoPlay: Boolean = true,
            startAt: Long = 0,
            enableDownload: Boolean = false,
            showDefaultCaptions: Boolean = false,
            startInFullscreen: Boolean = false,
            downloadMetadata: Map<String, String>? = null,
            offlineLicenseExpireTime: Long = DownloadConstants.FIFTEEN_DAYS_IN_SECONDS,
            seekBackIncrementMs: Long = DEFAULT_SEEK_INCREMENT_MS,
            seekForwardIncrementMs: Long = DEFAULT_SEEK_INCREMENT_MS
        ): TPStreamsPlayer {
            val (exo, trackSelector) = createExoPlayer(context, seekBackIncrementMs, seekForwardIncrementMs)
            return TPStreamsPlayer(
                context,
                exo,
                trackSelector,
                assetId,
                accessToken,
                shouldAutoPlay,
                startAt,
                enableDownload,
                showDefaultCaptions,
                startInFullscreen,
                downloadMetadata,
                offlineLicenseExpireTime)
        }
    }

    
    fun getDownloadDrmLicenseUrl(callback: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            isTokenValid(assetId) { isValid ->
                if (!isValid) {
                    Log.d("TPStreamsPlayer", "Token expired, getting fresh token")
                    listener?.onAccessTokenExpired(assetId) { newToken ->
                        if (newToken.isNotEmpty()) {
                            Log.d("TPStreamsPlayer", "Received fresh token")
                            TPStreamsSDK.orgId?.let {
                                val licenseUrl = TPStreamsSDK.apiService.drmLicenseUrl(
                                    orgId = it,
                                    assetId = assetId,
                                    accessToken = newToken,
                                    download = true,
                                    licenseDurationSeconds = offlineLicenseExpireTime
                                )
                                Log.d("TPStreamsPlayer", "Built license URL with fresh token: $licenseUrl")
                                callback(licenseUrl)
                            } ?: run {
                                Log.e("TPStreamsPlayer", "organizationId is null, cannot build license URL")
                                callback("")
                            }
                        } else {
                            Log.e("TPStreamsPlayer", "Failed to get fresh token")
                            callback("")
                        }
                    } ?: run {
                        Log.e("TPStreamsPlayer", "No token listener available")
                        callback("")
                    }
                } else {
                    Log.d("TPStreamsPlayer", "Token is valid, using current token")
                    TPStreamsSDK.orgId?.let {
                        val licenseUrl = TPStreamsSDK.apiService.drmLicenseUrl(
                            orgId = it,
                            assetId = assetId,
                            accessToken = accessToken,
                            download = true,
                            licenseDurationSeconds = offlineLicenseExpireTime
                        )
                        Log.d("TPStreamsPlayer", "Built license URL with current token: $licenseUrl")
                        callback(licenseUrl)
                    } ?: run {
                        Log.e("TPStreamsPlayer", "organizationId is null, cannot build license URL")
                        callback("")
                    }
                }
            }
        }
    }

    fun startDiagnosticSession(playbackType: String) {
        FlightRecorder.clear()
        FlightRecorder.startSession(playbackSessionId)
    }

    fun sendDiagnostics(triggerReason: String, playbackType: String) {
        DiagnosticSender.send(
            player = this,
            context = context,
            triggerReason = triggerReason,
            playbackType = playbackType,
            assetId = assetId,
            decoderState = decoderState
        )
    }
}
