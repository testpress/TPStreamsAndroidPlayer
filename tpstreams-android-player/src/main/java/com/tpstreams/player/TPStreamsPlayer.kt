package com.tpstreams.player

import android.content.Context
import android.media.MediaCodec
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.DrmConfiguration
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
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
    val downloadMetadata: Map<String, String>? = null,
    val offlineLicenseExpireTime: Long = DownloadConstants.FIFTEEN_DAYS_IN_SECONDS
) : Player by exoPlayer {

    interface Listener {
        fun onAccessTokenExpired(videoId: String, callback: (String) -> Unit)
        fun onError(error: PlaybackError, message: String)
    }

    private var isPrepared = false
    private var requestedPlay = false
    private var hasSeekedToStartAt = false
    private var subtitleMetadata = mapOf<String, Boolean>()
    
    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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

        val org = organizationId
            ?: throw IllegalStateException("TPStreamsPlayer.init(organizationId) must be called before using the player.")
        fetchAndPrepare(org, assetId, accessToken)
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

    private fun fetchAndPrepare(orgId: String, assetId: String, accessToken: String) {
        CoroutineScope(Dispatchers.IO).launch {

            if (playFromDownload(assetId)) return@launch
            
            try {
                val assetApiUrl = "https://app.tpstreams.com/api/v1/$orgId/assets/$assetId/?access_token=$accessToken"
                
                val request = Request.Builder().url(assetApiUrl).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorPlayerId = SentryLogger.generatePlayerIdString()
                    val exception = Exception("API request failed with code: ${response.code}")
                    SentryLogger.logAPIException(exception, assetId, response.code, errorPlayerId)
                    
                    val errorMessage = exception.getErrorMessage(errorPlayerId, response.code)
                    val errorType = when (response.code) {
                        404 -> PlaybackError.INVALID_ASSETS_ID
                        401, 403 -> PlaybackError.INVALID_ACCESS_TOKEN_FOR_ASSETS
                        else -> PlaybackError.UNSPECIFIED
                    }
                    
                    launch(Dispatchers.Main) {
                        _listener?.onError(errorType, errorMessage)
                    }
                    
                    Log.d("TPStreamsPlayer", "API returned ${response.code} for asset $assetId")
                    return@launch
                }

                val body = response.body?.string() ?: return@launch
                
                val json = JSONObject(body)
                val title = json.optString("title", "Undefined")
                val videoObj = json.getJSONObject("video")
                val enableDrm = videoObj.optBoolean("enable_drm", false)
                val mediaUrl = if (enableDrm) {
                    videoObj.getString("dash_url")
                } else {
                    videoObj.getString("playback_url")
                }
                val thumbnailsArray = videoObj.optJSONArray("thumbnails")
                val thumbnailUrl = thumbnailsArray?.optString(0) ?: ""
                
                Log.d("TPStreamsPlayer", "Extracted metadata - title: $title, thumbnail: $thumbnailUrl")

                // Extract subtitle tracks from metadata
                val subtitleConfigurations = mutableListOf<MediaItem.SubtitleConfiguration>()
                val subtitleMetadata = mutableMapOf<String, Boolean>()
                
                if (videoObj.has("tracks")) {
                    val tracks = videoObj.getJSONArray("tracks")
                    for (i in 0 until tracks.length()) {
                        val track = tracks.getJSONObject(i)
                        if (track.getString("type") == "Subtitle") {
                            val language = track.getString("language")
                            val url = track.getString("url")
                            
                            // Get the name/label for the subtitle
                            val name = if (track.has("name") && !track.getString("name").isNullOrEmpty()) {
                                track.getString("name")
                            } else {
                                language.replaceFirstChar { it.uppercase() }
                            }
                            
                            val isAutoGenerated = track.has("subtitle_type") && 
                                track.getString("subtitle_type") == "Auto Generated"
                            
                            subtitleMetadata[language] = isAutoGenerated
                            
                            try {
                                // Create subtitle configuration
                                val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
                                    .setLanguage(language)
                                    .setLabel(name)
                                    .setMimeType(MimeTypes.TEXT_VTT)
                                    .build()
                                
                                subtitleConfigurations.add(subtitleConfig)
                            } catch (e: Exception) {
                                Log.e("TPStreamsPlayer", "Error adding subtitle track: ${e.message}", e)
                            }
                        }
                    }
                }
                
                setSubtitleMetadata(subtitleMetadata)

                // Create the MediaItem builder 
                val mediaItem = MediaItem.Builder()
                    .setUri(mediaUrl)
                    .setMediaId(assetId)
                    .apply {
                        val metadata = MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtworkUri(if (thumbnailUrl.isNotEmpty()) Uri.parse(thumbnailUrl) else null)
                            .build()
                        setMediaMetadata(metadata)
                        
                        if (enableDrm) {
                            val licenseUrl = "https://app.tpstreams.com/api/v1/$orgId/assets/$assetId/drm_license/?access_token=$accessToken"
                            val drmHeaders = mapOf("Authorization" to "Bearer $accessToken")
                            
                            val drmConfig = DrmConfiguration.Builder(C.WIDEVINE_UUID)
                                .setLicenseUri(licenseUrl)
                                .setLicenseRequestHeaders(drmHeaders)
                                .setMultiSession(true)
                                .build()
                            
                            setDrmConfiguration(drmConfig)
                        }
                        
                        if (subtitleConfigurations.isNotEmpty()) {
                            setSubtitleConfigurations(subtitleConfigurations)
                        }
                    }
                    .build()

                launch(Dispatchers.Main) {
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    isPrepared = true

                    if (shouldAutoPlay || requestedPlay) {
                        exoPlayer.play()
                    }
                }
            } catch (e: Exception) {
                val errorPlayerId = SentryLogger.generatePlayerIdString()
                SentryLogger.logAPIException(e, assetId, null, errorPlayerId)
                
                val errorMessage = e.getErrorMessage(errorPlayerId, null)
                val errorType = if (e is java.io.IOException) {
                    PlaybackError.NETWORK_CONNECTION_FAILED
                } else {
                    PlaybackError.UNSPECIFIED
                }
                
                launch(Dispatchers.Main) {
                    _listener?.onError(errorType, errorMessage)
                }
                
                Log.e("TPStreamsPlayer", "Error preparing video: ${e.message}", e)
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

    @OptIn(UnstableApi::class)
    fun refreshPlaybackWithDownloadMediaItem(mediaItem: MediaItem) {
        playerScope.launch {
            val currentPosition = exoPlayer.currentPosition
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            
            exoPlayer.setMediaItem(mediaItem)
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
        playerScope.cancel()
        exoPlayer.release()
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
            if (accessToken.isEmpty()) {
                Log.d("TPStreamsPlayer", "No current token available")
                callback(false)
                return@launch
            }
            
            val orgId = organizationId ?: run {
                callback(false)
                return@launch
            }
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val assetApiUrl = "https://app.tpstreams.com/api/v1/$orgId/assets/$assetId/?access_token=$accessToken"
                    val request = Request.Builder()
                        .url(assetApiUrl)
                        .head()
                        .build()
                    
                    val response = client.newCall(request).execute()
                    val isValid = response.isSuccessful
                    Log.d("TPStreamsPlayer", "Token validation result: ${if (isValid) "valid" else "invalid"}")
                    
                    launch(Dispatchers.Main) {
                        callback(isValid)
                    }
                } catch (e: Exception) {
                    Log.e("TPStreamsPlayer", "Error checking token validity: ${e.message}")
                    launch(Dispatchers.Main) {
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
        private var organizationId: String? = null
        private val client = OkHttpClient()
        private const val LICENSE_URL_TEMPLATE = "https://app.tpstreams.com/api/v1/%s/assets/%s/drm_license/?access_token=%s&download=%s&license_duration_seconds=%s"


        internal fun init(orgId: String) {
            organizationId = orgId
        }

        @OptIn(UnstableApi::class)
        private fun createExoPlayer(context: Context): Pair<ExoPlayer, DefaultTrackSelector> {
            val trackSelector = DefaultTrackSelector(context).apply {
                parameters = DefaultTrackSelector.Parameters.Builder()
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .build()
            }

            DownloadController.initialize(context)

            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(DownloadController.downloadCache)
                .setUpstreamDataSourceFactory(DownloadController.httpDataSourceFactory)
                .setCacheWriteDataSinkFactory(null)
            
            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(cacheDataSourceFactory)

            return ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .setTrackSelector(trackSelector)
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
                            organizationId?.let {
                                val licenseUrl = LICENSE_URL_TEMPLATE.format(
                                    it,
                                    assetId,
                                    newToken,
                                    "true",
                                    offlineLicenseExpireTime.toString()
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
                    organizationId?.let {
                        val licenseUrl = LICENSE_URL_TEMPLATE.format(
                            it,
                            assetId,
                            accessToken,
                            "true",
                            offlineLicenseExpireTime.toString()
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


