package com.tpstreams.player

import android.content.Context
import android.media.MediaCodec
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.DrmConfiguration
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
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
import com.tpstreams.player.constants.PlaybackError
import com.tpstreams.player.constants.toError
import com.tpstreams.player.constants.getErrorMessage
import com.tpstreams.player.constants.LiveStreamNotStartedException
import com.tpstreams.player.constants.LiveStreamEndedException
import com.tpstreams.player.constants.toPlaybackError
import com.tpstreams.player.data.network.model.AssetInfo
import com.tpstreams.player.data.AssetRepository
import com.tpstreams.player.util.MediaItemUtils
import com.tpstreams.player.util.network.*
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.DecoderReuseEvaluation
import com.tpstreams.player.util.PlaybackHistoryManager
import com.tpstreams.player.util.CodecManager



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
    }

    interface Listener {
        fun onAccessTokenExpired(videoId: String, callback: (String) -> Unit)
        fun onError(error: PlaybackError, message: String)
    }

    private var isPrepared = false
    private var requestedPlay = false
    private var hasSeekedToStartAt = false
    private var subtitleMetadata = mapOf<String, Boolean>()
    private var _isLiveStream = false
    
    val isLiveStream: Boolean
        get() = _isLiveStream
    
    internal var onLiveStreamStatusChanged: ((Boolean) -> Unit)? = null
    
    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val networkRecoveryHandler = NetworkRecoveryHandler(context)

    private fun retryPlayback() {
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
                CodecManager.logCodecStatus(decoderName, "video/avc")
            }

            override fun onVideoDecoderReleased(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String
            ) {
                CodecManager.onDecoderReleased()
                debugLog("Decoder RELEASED - Codec: $decoderName")
            }

            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                debugLog("Decoder Format - ${format.sampleMimeType}, Res: ${format.width}x${format.height}, Bitrate: ${format.bitrate}")
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

            override fun onSurfaceSizeChanged(eventTime: AnalyticsListener.EventTime, width: Int, height: Int) {
                debugLog("Surface SIZE CHANGED - ${width}x${height}")
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
            }
            
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                Log.d("TPStreamsPlayer", "Play when ready changed: $playWhenReady, reason=$reason")
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("TPStreamsPlayer", "Is playing changed: $isPlaying")
            }
            
            override fun onPlayerError(error: PlaybackException) {
                if (isDrmLicenseExpiredError(error)) {
                    Log.d("TPStreamsPlayer", "DRM error detected for asset: $assetId")
                    DownloadController.renewDrmLicense(context, assetId, this@TPStreamsPlayer)  
                    return
                }

                if (isNetworkError(error)) {
                     networkRecoveryHandler.startMonitoring { retryPlayback() }
                }
                
                debugLog("Player ERROR - ${error.errorCodeName}")
                val errorPlayerId = SentryLogger.generatePlayerIdString()
                SentryLogger.logPlaybackException(error, assetId, errorPlayerId)
                
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

    private fun fetchAndPrepare(assetId: String, accessToken: String) {
        CoroutineScope(Dispatchers.IO).launch {
            if (playFromDownload(assetId)) return@launch

            AssetRepository.fetchAssetInfo(assetId, accessToken, object : AssetRepository.AssetCallback {
                override fun onSuccess(assetInfo: AssetInfo) {
                    preparePlayer(assetInfo, assetId, accessToken)
                }

                override fun onError(error: PlaybackError, message: String) {
                    if (error == PlaybackError.NETWORK_CONNECTION_FAILED || 
                        error == PlaybackError.NETWORK_CONNECTION_TIMEOUT) {
                        networkRecoveryHandler.startMonitoring { retryPlayback() }
                    }
                    _listener?.onError(error, message)
                }
            })
        }
    }

    @OptIn(UnstableApi::class)
    private fun preparePlayer(assetInfo: AssetInfo, assetId: String, accessToken: String) {
        val orgId = TPStreamsSDK.requireOrgId()
        _isLiveStream = assetInfo.isLiveStream
        onLiveStreamStatusChanged?.invoke(_isLiveStream)

        val result = MediaItemUtils.buildMediaItem(assetInfo, assetInfo.title, orgId, assetId, accessToken)
        setSubtitleMetadata(result.subtitleMetadata)

        playerScope.launch(Dispatchers.Main) {
                    val audioAttributes = buildAudioAttributes(assetInfo.enableDrm)

                    exoPlayer.setAudioAttributes(audioAttributes, true)
                    debugLog("MediaItem SET - ${result.mediaItem.mediaId}")
                    exoPlayer.setMediaItem(result.mediaItem)
                    debugLog("Player PREPARE")
                    exoPlayer.prepare()
                    isPrepared = true
                }

        if (shouldAutoPlay || requestedPlay) {
            exoPlayer.play()
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
                    val isDrm = download.request.keySetId != null
                    val audioAttributes = buildAudioAttributes(isDrm)

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

    private fun buildAudioAttributes(isDrm: Boolean): AudioAttributes {
        val capturePolicy = if (isDrm) {
            C.ALLOW_CAPTURE_BY_NONE
        } else {
            C.ALLOW_CAPTURE_BY_ALL
        }

        return AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setAllowedCapturePolicy(capturePolicy)
            .build()
    }

    @OptIn(UnstableApi::class)
    fun refreshPlaybackWithDownloadMediaItem(mediaItem: MediaItem) {
        playerScope.launch {
            val isDrm = mediaItem.localConfiguration?.drmConfiguration != null
            val audioAttributes = buildAudioAttributes(isDrm)

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
            exoPlayer.play()
        } else {
            requestedPlay = true
        }
    }

    override fun pause() = exoPlayer.pause()

    /**
     * Seeks to a specific position in the current media item.
     * @param positionMs The position in milliseconds to seek to
     */
    override fun seekTo(positionMs: Long) = exoPlayer.seekTo(positionMs)

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

    override fun release() {
        debugLog("Surface DETACH (Player Released)")
        debugLog("Player RELEASE - assetId: $assetId")
        synchronized(TPStreamsPlayer::class.java) {
            activePlayerCount--
            debugLog("Active Player COUNT: $activePlayerCount")
        }
        playerScope.cancel()
        exoPlayer.release()
        networkRecoveryHandler.stopMonitoring()
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
                        if (format.height != Format.NO_VALUE) {
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
                        if (format.height != Format.NO_VALUE && format.bitrate != Format.NO_VALUE) {
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

    @OptIn(UnstableApi::class)
    fun setVideoResolution(height: Int) {
        Log.d("TPStreamsPlayer", "Setting max video height to $height")
        val parametersBuilder = trackSelector.buildUponParameters()
            .setMaxVideoSize(Int.MAX_VALUE, height) // Unlimited width, constrained height
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
        private val client = OkHttpClient()



        @OptIn(UnstableApi::class)
        private fun createExoPlayer(context: Context): Pair<ExoPlayer, DefaultTrackSelector> {
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
            offlineLicenseExpireTime: Long = DownloadConstants.FIFTEEN_DAYS_IN_SECONDS
        ): TPStreamsPlayer {
            val (exo, trackSelector) = createExoPlayer(context)
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
}
