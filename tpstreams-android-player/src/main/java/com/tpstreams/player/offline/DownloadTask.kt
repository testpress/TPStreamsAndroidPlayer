package com.tpstreams.player.offline

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.StreamKey
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector
import com.tpstreams.player.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.regex.Pattern

private const val TAG = "TPStreamsDownloadTask"

@UnstableApi
class DownloadTask(private val context: Context) {

    // Set this to true to show debug information about available qualities
    private val DEBUG_QUALITIES = true

    /**
     * Start the download process
     * @param videoUrl The URL of the video to download
     * @param contentId A unique identifier for the content
     * @param selectedQuality Optional quality to use for download
     */
    fun startDownload(
        videoUrl: String, 
        contentId: String, 
        selectedQuality: String? = null
    ) {
        Log.d(TAG, "Starting download preparation for URL: $videoUrl")
        
        // Check if the URL is valid and is a supported stream type
        if (!isValidStreamUrl(videoUrl)) {
            Log.e(TAG, "Invalid stream URL: $videoUrl")
            return
        }
        
        // Determine the MIME type based on the URL
        val mimeType = when {
            videoUrl.endsWith(".m3u8") || videoUrl.contains(".m3u8?") -> "application/x-mpegURL"
            videoUrl.endsWith(".mpd") || videoUrl.contains(".mpd?") -> "application/dash+xml"
            else -> "video/*" // Generic fallback
        }
        
        // Create a media item for the video with appropriate MIME type
        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMediaId(contentId)
            .setMimeType(mimeType)
            .build()
        
        // Verify that the URI is valid
        if (mediaItem.localConfiguration?.uri == null) {
            Log.e(TAG, "Invalid URI in MediaItem")
            return
        }
        
        Log.d(TAG, "MediaItem created successfully with URI: ${mediaItem.localConfiguration?.uri}, MIME type: $mimeType")
        
        // For DASH streams, use a different approach
        if (isDashStream(videoUrl)) {
            val defaultQualities = createDashQualities(videoUrl)
            val selectedTrackInfo = if (selectedQuality != null) {
                defaultQualities.find { it.quality.startsWith(selectedQuality) }
            } else {
                defaultQualities.firstOrNull()
            }
            
            if (selectedTrackInfo != null) {
                downloadVariant(mediaItem, selectedTrackInfo)
                return
            }
            
            handleDashStream(videoUrl, contentId, mediaItem, selectedQuality)
            return
        }
        
        // For HLS streams, try to parse the manifest
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Try to fetch and parse the master playlist
                val manifestContent = withContext(Dispatchers.IO) {
                    fetchManifestContent(videoUrl)
                }
                
                if (manifestContent.isNotEmpty()) {
                    Log.d(TAG, "Successfully fetched manifest content, length: ${manifestContent.length}")
                    
                    val trackInfoList = parseM3U8Content(manifestContent, videoUrl)
                    
                    if (trackInfoList.isNotEmpty()) {
                        Log.d(TAG, "Found ${trackInfoList.size} qualities from direct m3u8 parsing")
                        
                        // Use the selected quality or default to highest
                        val selectedTrackInfo = if (selectedQuality != null) {
                            trackInfoList.find { it.quality.startsWith(selectedQuality) }
                        } else {
                            trackInfoList.firstOrNull()
                        }
                        
                        if (selectedTrackInfo != null) {
                            downloadVariant(mediaItem, selectedTrackInfo)
                        } else {
                            downloadVariant(mediaItem, trackInfoList.first())
                        }
                    } else {
                        Log.e(TAG, "No qualities found in the stream")
                        // Fallback to default qualities for HLS
                        val defaultQualities = createDefaultQualities(videoUrl)
                        
                        // Use the selected quality or default to highest
                        val selectedTrackInfo = if (selectedQuality != null) {
                            defaultQualities.find { it.quality.startsWith(selectedQuality) }
                        } else {
                            defaultQualities.firstOrNull()
                        }
                        
                        if (selectedTrackInfo != null) {
                            downloadVariant(mediaItem, selectedTrackInfo)
                        } else {
                            downloadVariant(mediaItem, defaultQualities.first())
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to fetch manifest content")
                    // Fallback to default qualities
                    val defaultQualities = createDefaultQualities(videoUrl)
                    
                    // Use the selected quality or default to highest
                    val selectedTrackInfo = if (selectedQuality != null) {
                        defaultQualities.find { it.quality.startsWith(selectedQuality) }
                    } else {
                        defaultQualities.firstOrNull()
                    }
                    
                    if (selectedTrackInfo != null) {
                        downloadVariant(mediaItem, selectedTrackInfo)
                    } else {
                        downloadVariant(mediaItem, defaultQualities.first())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing manifest: ${e.message}", e)
                // Fallback to default qualities
                val defaultQualities = createDefaultQualities(videoUrl)
                
                // Use the selected quality or default to highest
                val selectedTrackInfo = if (selectedQuality != null) {
                    defaultQualities.find { it.quality.startsWith(selectedQuality) }
                } else {
                    defaultQualities.firstOrNull()
                }
                
                if (selectedTrackInfo != null) {
                    downloadVariant(mediaItem, selectedTrackInfo)
                } else {
                    downloadVariant(mediaItem, defaultQualities.first())
                }
            }
        }
    }
    
    /**
     * Handle DASH stream download
     */
    private fun handleDashStream(
        videoUrl: String, 
        contentId: String, 
        mediaItem: MediaItem,
        selectedQuality: String? = null
    ) {
        Log.d(TAG, "Handling DASH stream: $videoUrl")
        
        // For DASH streams, we'll use a set of default quality options
        val defaultQualities = createDashQualities(videoUrl)
        
        // Use the selected quality or default to highest
        val selectedTrackInfo = if (selectedQuality != null) {
            defaultQualities.find { it.quality.startsWith(selectedQuality) }
        } else {
            defaultQualities.firstOrNull()
        }
        
        if (selectedTrackInfo != null) {
            downloadVariant(mediaItem, selectedTrackInfo)
        } else {
            downloadVariant(mediaItem, defaultQualities.first())
        }
    }
    
    /**
     * Create default quality options for DASH streams
     */
    private fun createDashQualities(videoUrl: String): List<TrackInfo> {
        return listOf(
            TrackInfo(0, 0, 0, 0, "1080p", 4500000, "~20 MB/min", videoUrl),
            TrackInfo(0, 0, 1, 1, "720p", 2500000, "~11 MB/min", videoUrl),
            TrackInfo(0, 0, 2, 2, "480p", 1100000, "~5 MB/min", videoUrl),
            TrackInfo(0, 0, 3, 3, "360p", 730000, "~3.5 MB/min", videoUrl),
            TrackInfo(0, 0, 4, 4, "240p", 365000, "~1.7 MB/min", videoUrl)
        )
    }
    
    /**
     * Create default quality options when manifest parsing fails
     */
    private fun createDefaultQualities(videoUrl: String): List<TrackInfo> {
        return listOf(
            TrackInfo(0, 0, 0, 0, "720p", 2500000, "~11 MB/min", videoUrl),
            TrackInfo(0, 0, 1, 1, "480p", 1100000, "~5 MB/min", videoUrl),
            TrackInfo(0, 0, 2, 2, "360p", 730000, "~3.5 MB/min", videoUrl),
            TrackInfo(0, 0, 3, 3, "240p", 365000, "~1.7 MB/min", videoUrl)
        )
    }
    
    /**
     * Check if the URL is a valid stream URL (HLS or DASH)
     */
    private fun isValidStreamUrl(url: String): Boolean {
        return url.isNotEmpty() && (isHlsStream(url) || isDashStream(url))
    }
    
    /**
     * Check if the URL is an HLS stream
     */
    private fun isHlsStream(url: String): Boolean {
        return url.endsWith(".m3u8") || url.contains(".m3u8?")
    }
    
    /**
     * Check if the URL is a DASH stream
     */
    private fun isDashStream(url: String): Boolean {
        return url.endsWith(".mpd") || url.contains(".mpd?")
    }
    
    /**
     * Legacy method for backward compatibility
     */
    private fun isValidHlsUrl(url: String): Boolean {
        return isValidStreamUrl(url)
    }
    
    private suspend fun fetchManifestContent(url: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching manifest from: $url")
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "TPStreamsPlayer")
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val content = reader.readText()
                    reader.close()
                    connection.disconnect()
                    content
                } else {
                    Log.e(TAG, "Failed to fetch M3U8 manifest: HTTP ${connection.responseCode}")
                    ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching manifest: ${e.message}", e)
                ""
            }
        }
    }
    
    private fun parseM3U8Content(content: String, masterUrl: String): List<TrackInfo> {
        val trackInfoList = mutableListOf<TrackInfo>()
        
        try {
            // Check if this is a master playlist or a media playlist
            if (!content.contains("#EXT-X-STREAM-INF")) {
                Log.d(TAG, "This appears to be a media playlist, not a master playlist")
                return emptyList()
            }
            
            Log.d(TAG, "Parsing master playlist content")
            val lines = content.lines()
            var lineIndex = 0
            var currentBandwidth = 0
            var currentResolution = ""
            
            // Regular expressions to extract information
            val bandwidthPattern = Pattern.compile("BANDWIDTH=(\\d+)")
            val resolutionPattern = Pattern.compile("RESOLUTION=(\\d+x\\d+)")
            
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                if (line.startsWith("#EXT-X-STREAM-INF:")) {
                    Log.d(TAG, "Found stream info: $line")
                    // Extract bandwidth
                    val bandwidthMatcher = bandwidthPattern.matcher(line)
                    if (bandwidthMatcher.find()) {
                        currentBandwidth = bandwidthMatcher.group(1)?.toIntOrNull() ?: 0
                        Log.d(TAG, "Extracted bandwidth: $currentBandwidth")
                    }
                    
                    // Extract resolution
                    val resolutionMatcher = resolutionPattern.matcher(line)
                    if (resolutionMatcher.find()) {
                        currentResolution = resolutionMatcher.group(1) ?: ""
                        Log.d(TAG, "Extracted resolution: $currentResolution")
                    }
                    
                    // Look for the next non-comment line, which should be the variant URL
                    var variantUrl = ""
                    var j = i + 1
                    while (j < lines.size) {
                        if (!lines[j].startsWith("#")) {
                            variantUrl = lines[j]
                            Log.d(TAG, "Found variant URL: $variantUrl")
                            break
                        }
                        j++
                    }
                    
                    if (variantUrl.isNotEmpty()) {
                        // Resolve relative URL if needed
                        val fullUrl = if (variantUrl.startsWith("http")) {
                            variantUrl
                        } else {
                            // Handle relative URLs
                            val baseUrl = masterUrl.substring(0, masterUrl.lastIndexOf("/") + 1)
                            baseUrl + variantUrl
                        }
                        
                        // Only add if we have a resolution
                        if (currentResolution.isNotEmpty()) {
                            // Calculate quality label
                            val resolution = currentResolution.split("x")
                            val width = resolution.getOrNull(0)?.toIntOrNull() ?: 0
                            val height = resolution.getOrNull(1)?.toIntOrNull() ?: 0
                            
                            // Create user-friendly quality label
                            val qualityLabel = when {
                                height >= 2160 -> "$currentResolution (4K)"
                                height >= 1440 -> "$currentResolution (2K)"
                                height >= 1080 -> "$currentResolution (Full HD)"
                                height >= 720 -> "$currentResolution (HD)"
                                height >= 480 -> "$currentResolution (SD)"
                                else -> currentResolution
                            }
                            
                            // Calculate approximate size
                            val bitrateInMbps = currentBandwidth / 8.0 / 1024.0 / 1024.0 * 60.0
                            val approxSizeMB = if (bitrateInMbps > 0) {
                                String.format("%.1f MB/min", bitrateInMbps)
                            } else {
                                "Unknown size"
                            }
                            
                            trackInfoList.add(
                                TrackInfo(
                                    periodIndex = 0,
                                    rendererIndex = 0,
                                    groupIndex = lineIndex,
                                    trackIndex = lineIndex,
                                    quality = qualityLabel,
                                    bitrate = currentBandwidth,
                                    approxSize = approxSizeMB,
                                    variantUrl = fullUrl
                                )
                            )
                            
                            lineIndex++
                        }
                    }
                }
                i++
            }
            
            // Sort by resolution (height)
            return trackInfoList.sortedByDescending { 
                val heightStr = it.quality.split("x").getOrNull(1)?.split(" ")?.getOrNull(0) ?: "0"
                heightStr.toIntOrNull() ?: 0 
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing M3U8 content: ${e.message}", e)
            return emptyList()
        }
    }
    
    private fun downloadVariant(mediaItem: MediaItem, selectedTrack: TrackInfo) {
        Log.d(TAG, "Starting download for quality: ${selectedTrack.quality}")
        
        // For a variant stream, we need to use the specific variant URL if available
        val downloadUri = if (selectedTrack.variantUrl.isNotEmpty()) {
            Log.d(TAG, "Using specific variant URL: ${selectedTrack.variantUrl}")
            selectedTrack.variantUrl
        } else {
            Log.d(TAG, "Using original URL: ${mediaItem.localConfiguration?.uri}")
            mediaItem.localConfiguration?.uri.toString()
        }
        
        Log.d(TAG, "Using download URI: $downloadUri")
        
        try {
            // Create stream keys for the selection
            val streamKeys = mutableListOf<StreamKey>()
            streamKeys.add(StreamKey(selectedTrack.periodIndex, selectedTrack.rendererIndex, selectedTrack.trackIndex))
            
            // Build download request
            val request = VideoDownloadManager.createDownloadRequest(
                mediaItem.mediaId,
                downloadUri,
                streamKeys
            )
            
            // Start download
            VideoDownloadService.startDownload(context, request)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting download: ${e.message}", e)
        }
    }
    
    /**
     * Format bitrate in a human-readable format
     */
    private fun formatBitrate(bitrate: Int): String {
        return when {
            bitrate <= 0 -> "Unknown bitrate"
            bitrate < 1000000 -> String.format("%.0f Kbps", bitrate / 1000.0)
            else -> String.format("%.1f Mbps", bitrate / 1000000.0)
        }
    }
    
    data class TrackInfo(
        val periodIndex: Int,
        val rendererIndex: Int,
        val groupIndex: Int,
        val trackIndex: Int,
        val quality: String,
        val bitrate: Int,
        val approxSize: String = "Unknown size",
        val variantUrl: String = ""
    )
} 