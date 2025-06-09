package com.tpstreams.player

import android.content.Context
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
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.database.StandaloneDatabaseProvider
import java.io.File

class TPStreamsPlayer @OptIn(UnstableApi::class)
private constructor(
    internal val exoPlayer: ExoPlayer,
    private val trackSelector: DefaultTrackSelector,
    private val context: Context,
    private var currentAssetId: String,
    private var accessToken: String,
    private var shouldAutoPlay: Boolean
) : Player by exoPlayer {

    private var isPrepared = false
    private var requestedPlay = false
    private var videoUrl: String = ""

    private var subtitleMetadata = mapOf<String, Boolean>()
    
    init {
        exoPlayer.addListener(object : Player.Listener {
            @OptIn(UnstableApi::class)
            override fun onTracksChanged(tracks: Tracks) {
                val textTracks = getAvailableTextTracks()
                Log.d("TPStreamsPlayer", "Tracks changed. Text tracks available: ${textTracks.size}")
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d("TPStreamsPlayer", "Player state changed: state=$playbackState, playWhenReady=${exoPlayer.playWhenReady}")
            }
            
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                Log.d("TPStreamsPlayer", "Play when ready changed: $playWhenReady, reason=$reason")
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("TPStreamsPlayer", "Is playing changed: $isPlaying")
            }
            
            override fun onPlayerError(error: PlaybackException) {
                Log.e("TPStreamsPlayer", "Player error: ${error.message}", error)
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
        fetchAndPrepare(org, currentAssetId, accessToken)
    }

    private fun fetchAndPrepare(orgId: String, assetId: String, accessToken: String) {
        Log.d("TPStreamsPlayer", "Starting fetchAndPrepare for assetId: $assetId")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val assetApiUrl =
                    "https://app.tpstreams.com/api/v1/$orgId/assets/$assetId/?access_token=$accessToken"

                val request = Request.Builder().url(assetApiUrl).build()
                val response = OkHttpClient().newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e("TPStreamsPlayer", "Failed to fetch asset metadata: ${response.code}")
                    return@launch
                }

                val body = response.body?.string() ?: return@launch
                
                val json = JSONObject(body)
                val videoObj = json.getJSONObject("video")
                val enableDrm = videoObj.optBoolean("enable_drm", false)
                val mediaUrl = if (enableDrm) {
                    videoObj.getString("dash_url")
                } else {
                    videoObj.getString("playback_url")
                }
                
                // Store the video URL for download functionality
                videoUrl = mediaUrl
                Log.d("TPStreamsPlayer", "Video URL set: $videoUrl")
                Log.d("TPStreamsPlayer", "Asset ID: $currentAssetId")

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
                                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
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
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(mediaUrl)

                // Add DRM configuration only if DRM is enabled
                if (enableDrm) {
                    val licenseUrl =
                        "https://app.tpstreams.com/api/v1/$orgId/assets/$assetId/drm_license/?access_token=$accessToken"
                    val drmHeaders = mapOf("Authorization" to "Bearer $accessToken")

                    val drmConfig = DrmConfiguration.Builder(C.WIDEVINE_UUID)
                        .setLicenseUri(licenseUrl)
                        .setLicenseRequestHeaders(drmHeaders)
                        .setMultiSession(true)
                        .build()
                    
                    mediaItemBuilder.setDrmConfiguration(drmConfig)
                }
                
                if (subtitleConfigurations.isNotEmpty()) {
                    mediaItemBuilder.setSubtitleConfigurations(subtitleConfigurations)
                }
                
                val mediaItem = mediaItemBuilder.build()

                launch(Dispatchers.Main) {
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    isPrepared = true
                    if (shouldAutoPlay || requestedPlay) {
                        exoPlayer.play()
                    }
                }
            } catch (e: Exception) {
                Log.e("TPStreamsPlayer", "Error preparing video: ${e.message}", e)
            }
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

    override fun release() = exoPlayer.release()

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
            parametersBuilder.setDisabledTextTrackSelectionFlags(C.SELECTION_FLAG_DEFAULT)
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

    /**
     * Get the current video URL for downloading
     * @return The URL of the current video
     */
    fun getVideoUrl(): String {
        Log.d("TPStreamsPlayer", "getVideoUrl called, returning: $videoUrl")
        return videoUrl
    }
    
    /**
     * Get the current asset ID
     * @return The ID of the current asset
     */
    fun getAssetId(): String {
        Log.d("TPStreamsPlayer", "getAssetId called, returning: $currentAssetId")
        return currentAssetId
    }

    /**
     * Get the player context
     * @return The context
     */
    fun getContext(): Context {
        return context
    }

    /**
     * Get the ExoPlayer instance
     * @return The ExoPlayer instance
     */
    fun getExoPlayer(): androidx.media3.exoplayer.ExoPlayer {
        return exoPlayer
    }

    /**
     * Play offline content using its content ID
     * @param contentId The unique identifier of the content
     * @param context The context to use for accessing the download manager
     * @return true if successful
     */
    @OptIn(UnstableApi::class)
    fun playOfflineContent(contentId: String, context: Context? = null): Boolean {
        try {
            Log.d("TPStreamsPlayer", "Playing offline content: $contentId")
            
            // Get the download from the download manager
            val appContext = context ?: this.context
            val downloads = com.tpstreams.player.offline.VideoDownloadManager.getDownloads(appContext)
            val download = downloads.find { it.request.id == contentId }
            
            if (download == null) {
                Log.e("TPStreamsPlayer", "Download not found for contentId: $contentId")
                return false
            }
            
            // Get the URI from the download request
            val uri = download.request.uri
            
            // Create a media source factory with the cache
            val cache = com.tpstreams.player.offline.SharedCacheUtil.getCache(appContext)
            val httpDataSourceFactory = com.tpstreams.player.offline.VideoDownloadManager.getHttpDataSourceFactory(appContext)
            
            val dataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setCacheWriteDataSinkFactory(null) // Disable writing to cache during playback
                .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            
            // Create a media source based on the content type
            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
            
            // Create a media item with the content ID
            val mediaItemBuilder = androidx.media3.common.MediaItem.Builder()
                .setMediaId(contentId)
                .setUri(uri)
                
            // Add DRM configuration only for DRM content
            val uriString = uri.toString()
            if (uriString.endsWith(".mpd") || uriString.contains(".mpd?")) {
                // DASH content is likely DRM-protected
                mediaItemBuilder.setDrmConfiguration(
                    androidx.media3.common.MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                        .setForceDefaultLicenseUri(false)
                        .build()
                )
            }
                
            val mediaItem = mediaItemBuilder.build()
            
            // Create the media source directly
            val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
            
            // Set the media source and prepare the player
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            isPrepared = true
            
            // Update current asset ID for tracking
            currentAssetId = contentId
            videoUrl = uri.toString()
            
            if (shouldAutoPlay) {
                exoPlayer.play()
            }
            
            return true
        } catch (e: Exception) {
            Log.e("TPStreamsPlayer", "Error playing offline content: ${e.message}", e)
            return false
        }
    }

    companion object {
        private var organizationId: String? = null

        fun init(orgId: String, context: Context? = null) {
            organizationId = orgId
            
            // Initialize download manager if context is provided
            if (context != null) {
                try {
                    Log.d("TPStreamsPlayer", "Automatically initializing download manager")
                    com.tpstreams.player.offline.VideoDownloadManager.initialize(context)
                } catch (e: Exception) {
                    Log.e("TPStreamsPlayer", "Failed to initialize download manager", e)
                }
            }
        }

        @OptIn(UnstableApi::class)
        private fun createExoPlayer(context: Context): Pair<ExoPlayer, DefaultTrackSelector> {
            val trackSelector = DefaultTrackSelector(context).apply {
                parameters = DefaultTrackSelector.Parameters.Builder()
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .build()
            }

            // Create HTTP data source factory with cross-protocol redirects enabled
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("TPStreamsPlayer")
                .setAllowCrossProtocolRedirects(true)

            // Use cache data source factory for offline playback support
            val dataSourceFactory = try {
                // Get the shared cache instance
                val cache = com.tpstreams.player.offline.SharedCacheUtil.getCache(context)
                
                CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(httpDataSourceFactory)
                    .setCacheWriteDataSinkFactory(null) // Disable writing to cache during playback
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            } catch (e: Exception) {
                Log.e("TPStreamsPlayer", "Error creating cache data source, falling back to HTTP", e)
                httpDataSourceFactory
            }

            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)

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
            shouldAutoPlay: Boolean = true
        ): TPStreamsPlayer {
            val (exo, trackSelector) = createExoPlayer(context)
            return TPStreamsPlayer(exo, trackSelector, context, assetId, accessToken, shouldAutoPlay)
        }
    }
}

