package com.tpstreams.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.tpstreams.player.constants.NetworkDiagnostics
import com.tpstreams.player.constants.PlaybackError
import com.tpstreams.player.constants.getErrorMessage
import com.tpstreams.player.constants.toError
import com.tpstreams.player.data.PlayerDecoderState
import com.tpstreams.player.download.DownloadConstants
import com.tpstreams.player.download.DownloadController
import com.tpstreams.player.download.DownloadPlaybackHandler
import com.tpstreams.player.drm.DrmHandler
import com.tpstreams.player.media.MediaLoader
import com.tpstreams.player.token.TokenManager
import com.tpstreams.player.tracks.ResolutionManager
import com.tpstreams.player.tracks.TextTrackManager
import com.tpstreams.player.util.CodecManager
import com.tpstreams.player.util.DecoderInfoProvider
import com.tpstreams.player.util.NetworkDiagnosticsManager
import com.tpstreams.player.util.PlaybackHistoryManager
import com.tpstreams.player.util.SentryLogger
import com.tpstreams.player.util.WidevineDrmSessionManagerProvider
import com.tpstreams.player.util.WidevinePlaybackLevelResolver
import com.tpstreams.player.util.isLiveStreamEndHttpError
import com.tpstreams.player.util.network.NetworkRecoveryHandler
import com.tpstreams.player.util.network.isNetworkError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
    val offlineLicenseExpireTime: Long = DownloadConstants.FIFTEEN_DAYS_IN_SECONDS,
    val userId: String? = null
) : Player by exoPlayer {

    val playbackSessionId = (1..6)
        .map { (('a'..'z') + ('0'..'9')).random() }
        .joinToString("")

    private fun debugLog(message: String) {
        val fullMessage = "[$playbackSessionId] $message"
        Log.d(DEBUG_TAG, fullMessage)
        PlaybackHistoryManager.recordLog(fullMessage)
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
    private var requestedPlay = false
    private var hasSeekedToStartAt = false

    val isLiveStream: Boolean
        get() = mediaLoader.isLiveStream
    
    @Volatile
    private var released = false

    internal var onLiveStreamStatusChanged: ((Boolean) -> Unit)? = null
    
    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val networkRecoveryHandler = NetworkRecoveryHandler(context)

    private val textTrackManager: TextTrackManager by lazy {
        TextTrackManager(exoPlayer, trackSelector)
    }
    private val resolutionManager: ResolutionManager by lazy {
        ResolutionManager(exoPlayer, trackSelector)
    }
    private val tokenManager: TokenManager by lazy {
        TokenManager(assetId, accessToken, offlineLicenseExpireTime) { _listener }
    }

    /**
     * Handles Widevine DRM playback, L1/L3 level resolution, and L3 fallback retries.
     */
    private val drmHandler = DrmHandler(
        exoPlayer = exoPlayer,
        playerScope = playerScope,
        context = context,
        assetId = assetId,
        isLiveStream = { isLiveStream },
        onRenewOfflineLicense = {
            DownloadController.renewDrmLicense(context, assetId, this@TPStreamsPlayer)
        }
    )

    private val downloadPlaybackHandler = DownloadPlaybackHandler(
        context = context,
        exoPlayer = exoPlayer,
        playerScope = playerScope,
        drmHandler = drmHandler,
        onMediaPrepared = { isPrepared = true },
        shouldPlayOnPrepared = { shouldAutoPlay || requestedPlay },
        logDebug = { debugLog(it) }
    )

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
        },
        diagnosticHostProvider = ::resolveDiagnosticHost,
        serverProbePathProvider = ::resolveServerProbePath
    )

    private val mediaLoader = MediaLoader(
        context = context,
        exoPlayer = exoPlayer,
        playerScope = playerScope,
        assetId = assetId,
        accessToken = accessToken,
        drmHandler = drmHandler,
        textTrackManager = textTrackManager,
        downloadPlaybackHandler = downloadPlaybackHandler,
        networkDiagnosticsManager = networkDiagnosticsManager,
        getDecoderState = { decoderState },
        onMediaPrepared = { isPrepared = true },
        shouldPlayOnPrepared = { shouldAutoPlay || requestedPlay },
        onLiveStreamStatusChanged = { onLiveStreamStatusChanged?.invoke(it) },
        onError = { error, message -> _listener?.onError(error, message) },
        logDebug = { debugLog(it) }
    )

    /**
     * True when the current asset is DRM-protected.
     *
     * Covers two cases:
     * - Online streaming: license URL is set during [preparePlayer].
     * - Offline downloads: [preparePlayer] is bypassed, DRM is detected via the media item.
     *
     * Note: [TPStreamsPlayerView] applies FLAG_SECURE for all playback.
     * This property is exposed for host-app analytics or conditional UI.
     */
    val isDrmContent: Boolean
        get() = drmHandler.isProtected

    /** DRM license URL for the current asset, or null when not DRM-protected / not yet prepared. */
    fun getDrmLicenseUrl(): String? = drmHandler.licenseUrl

    /** Resolved Widevine playback level in use: `"L1"` or `"L3"`. */
    fun getDrmPlaybackLevel(): String = drmHandler.getPlaybackLevel()

    /** Device-reported native Widevine security level, or null if unavailable. */
    fun getNativeWidevineLevel(): String? = drmHandler.getNativeLevel()

    // Per-player decoder state (not global — avoids cross-player corruption)
    @Volatile
    private var decoderState = PlayerDecoderState()
    internal fun getDecoderState(): PlayerDecoderState = decoderState

    private fun resolveDiagnosticHost(): String {
        return try {
            val url = diagnosticAssetInfoUrl()
            Uri.parse(url).host ?: NetworkDiagnosticsManager.DIAGNOSTIC_HOST_DEFAULT
        } catch (_: Exception) {
            NetworkDiagnosticsManager.DIAGNOSTIC_HOST_DEFAULT
        }
    }

    private fun resolveServerProbePath(): String {
        return try {
            val path = Uri.parse(diagnosticAssetInfoUrl()).path
                ?: return NetworkDiagnosticsManager.DEFAULT_SERVER_PROBE_PATH
            SERVER_PROBE_PATH_REGEX.find(path)?.value
                ?: NetworkDiagnosticsManager.DEFAULT_SERVER_PROBE_PATH
        } catch (_: Exception) {
            NetworkDiagnosticsManager.DEFAULT_SERVER_PROBE_PATH
        }
    }

    private fun diagnosticAssetInfoUrl(): String {
        return TPStreamsSDK.apiService.assetInfoUrl(
            TPStreamsSDK.requireOrgId(),
            DIAGNOSTIC_DUMMY_ASSET_ID,
            ""
        )
    }

    private val resumePlaybackManager: ResumePlaybackManager? =
        userId?.let { ResumePlaybackManager(this, assetId, it) }

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
                        Log.d("TPStreamsPlayer", "Retrying initial media load")
                        mediaLoader.load()
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
        WidevinePlaybackLevelResolver.initialize(
            context.applicationContext,
            TPStreamsSDK.allowFallbackToL3,
        )
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
                val stateName = when (state) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                debugLog("Playback STATE CHANGE - $stateName")
            }

            override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
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
        })

        Log.d("TPStreamsPlayer", "Initializing TPStreamsPlayer with assetId: $assetId")
        
        exoPlayer.addListener(object : Player.Listener {
            @OptIn(UnstableApi::class)
            override fun onTracksChanged(tracks: Tracks) {
                val textTracks = getAvailableTextTracks()
                Log.d("TPStreamsPlayer", "Tracks changed. Text tracks available: ${textTracks.size}")
                
                if (showDefaultCaptions && isPrepared && textTracks.isNotEmpty()) {
                    enableDefaultCaptions()
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d("TPStreamsPlayer", "Player state changed: state=$playbackState, playWhenReady=${exoPlayer.playWhenReady}")
                seekToStartAt()
                if (playbackState == Player.STATE_READY) {
                    if (startAt <= 0 && !isLiveStream) {
                        resumePlaybackManager?.onPlayerReady()
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    resumePlaybackManager?.onVideoEnded()
                }
            }
            
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                Log.d("TPStreamsPlayer", "Play when ready changed: $playWhenReady, reason=$reason")
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("TPStreamsPlayer", "Is playing changed: $isPlaying")
                if (isPlaying) {
                    networkDiagnosticsManager.onPlaybackRecovered()
                } else {
                    resumePlaybackManager?.onPaused()
                }
            }
            
            override fun onPlayerError(error: PlaybackException) {
                // Suppress HLS playlist-stuck errors — raised when a live stream pauses.
                // The player naturally shows a buffering spinner; playback resumes when the
                // stream advances again or the user seeks back.
                if (isPlaylistStuckException(error)) return

                // --- DRM error handling ---
                // Covers expired offline licenses, L1→L3 fallback, and license acquisition
                // failures. Returns true when the error has been handled and playback will
                // resume (either via renewal or retry), so we stop further processing.
                if (drmHandler.handleError(error)) return

                if (isLiveStream && error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && error.isLiveStreamEndHttpError()) {
                    debugLog("Live stream source returned bad HTTP status — stream likely ended")
                    _listener?.onError(PlaybackError.LIVE_STREAM_ENDED, "Live stream has ended")
                    return
                }

                if (isNetworkError(error)) {
                    networkDiagnosticsManager.handleError(error.toError(), error, mediaLoader.cdnHostname, decoderState, mediaLoader.mediaUrl)
                    return
                }

                // Non-network errors go directly to _listener?.onError() (not onNetworkError).
                // Network errors route through handleError → manager → _listener?.onNetworkError().
                debugLog("Player ERROR - ${error.errorCodeName}")
                val errorPlayerId = SentryLogger.generatePlayerIdString()
                SentryLogger.logPlaybackException(
                    error,
                    assetId,
                    errorPlayerId,
                    drmLicenseUrl = drmHandler.licenseUrl,
                    context = context,
                    player = exoPlayer,
                    decoderState = decoderState,
                    drmSecurityLevel = drmHandler.nativeSecurityLevel
                )
                
                val errorType = error.toError()
                val errorMessage = error.getErrorMessage(errorPlayerId)
                
                Log.e("TPStreamsPlayer", "Player error: ${error.errorCodeName}", error)
                _listener?.onError(errorType, errorMessage)
            }
            
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                Log.d("TPStreamsPlayer", "Playback parameters changed: speed=${playbackParameters.speed}")
            }
            
            override fun onIsLoadingChanged(isLoading: Boolean) {
                Log.d("TPStreamsPlayer", "Is loading changed: $isLoading")
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                discontinuityReason: Int
            ) {
                if (discontinuityReason == Player.DISCONTINUITY_REASON_SEEK) {
                    resumePlaybackManager?.onSeeked()
                }
            }
        })

        TPStreamsSDK.requireOrgId()
        mediaLoader.load()
    }

    private fun enableDefaultCaptions() = textTrackManager.enableDefaultCaptions()

    @OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun isPlaylistStuckException(error: PlaybackException): Boolean {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker.PlaylistStuckException) return true
            cause = cause.cause
        }
        return false
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
    fun refreshPlaybackWithDownloadMediaItem(mediaItem: MediaItem) {
        downloadPlaybackHandler.refreshPlaybackWithDownloadMediaItem(mediaItem)
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
        resumePlaybackManager?.onRelease()
        synchronized(TPStreamsPlayer::class.java) {
            activePlayerCount--
            debugLog("Active Player COUNT: $activePlayerCount")
        }
        released = true
        playerScope.cancel()
        networkDiagnosticsManager.onRelease()
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
    fun getAvailableVideoResolutions(): List<Int> = resolutionManager.getAvailableVideoResolutions()

    @OptIn(UnstableApi::class)
    fun getResolutionBitrates(): Map<String, Int> = resolutionManager.getResolutionBitrates()

    @OptIn(UnstableApi::class)
    fun getAvailableTextTracks(): List<Pair<String, String>> = textTrackManager.getAvailableTextTracks()
    
    @OptIn(UnstableApi::class)
    fun setTextTrackByLanguage(language: String?) = textTrackManager.setTextTrackByLanguage(language)

    @OptIn(UnstableApi::class)
    fun setMaxResolution(height: Int) = resolutionManager.setMaxResolution(height)

    @OptIn(UnstableApi::class)
    internal fun setUserResolutionPreference(height: Int) = resolutionManager.setUserResolutionPreference(height)

    /**
     * Get the currently active text track, if any.
     * @return Pair of (language, label) for the active track, or null if no track is active.
     */
    @OptIn(UnstableApi::class)
    fun getActiveTextTrack(): Pair<String, String>? = textTrackManager.getActiveTextTrack()
    
    /**
     * Check if a subtitle track is auto-generated
     */
    fun isSubtitleAutoGenerated(language: String): Boolean = textTrackManager.isSubtitleAutoGenerated(language)

    fun isTokenValid(assetId: String, callback: (Boolean) -> Unit) {
        tokenManager.isTokenValid(assetId, playerScope, callback)
    }

    fun getNewToken(assetId: String, callback: (String) -> Unit) {
        tokenManager.getNewToken(assetId, playerScope, callback)
    }

    companion object {
        private var activePlayerCount = 0
        internal const val DEBUG_TAG = "PLAYBACK_ERROR_DEBUG"
        private const val DEFAULT_SEEK_INCREMENT_MS = 10000L
        private const val DIAGNOSTIC_DUMMY_ASSET_ID = "00000000000"
        private val SERVER_PROBE_PATH_REGEX = Regex("^/api/[^/]+/")



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

            val drmSessionManagerProvider = WidevineDrmSessionManagerProvider(
                DownloadController.httpDataSourceFactory,
            )

            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(cacheDataSourceFactory)
                .setDrmSessionManagerProvider(drmSessionManagerProvider)

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

        @JvmOverloads
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
            seekForwardIncrementMs: Long = DEFAULT_SEEK_INCREMENT_MS,
            userId: String? = null
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
                offlineLicenseExpireTime,
                userId)
        }
    }

    
    fun getDownloadDrmLicenseUrl(callback: (String) -> Unit) {
        tokenManager.getDownloadDrmLicenseUrl(playerScope, callback)
    }
}
