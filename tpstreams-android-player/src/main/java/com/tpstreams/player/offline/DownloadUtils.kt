package com.tpstreams.player.offline

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import com.tpstreams.player.TPStreamsPlayer
import com.tpstreams.player.TPStreamsPlayerView

/**
 * Utility class providing download functionality for app developers
 */
@UnstableApi
object DownloadUtils {
    
    private const val TAG = "DownloadUtils"
    
    /**
     * Get a list of all downloaded videos
     * @param context Application context
     * @return List of Download objects
     */
    fun getDownloads(context: Context): List<Download> {
        TPStreamsPlayerDownloadExt.initializeDownloadManager(context)
        return TPStreamsPlayerDownloadExt.getDownloads(context)
    }
    
    /**
     * Check if a specific video is downloaded
     * @param context Application context
     * @param contentId The unique identifier of the content
     * @return true if the video is downloaded, false otherwise
     */
    fun isDownloaded(context: Context, contentId: String): Boolean {
        return TPStreamsPlayerDownloadExt.isDownloaded(context, contentId)
    }
    
    /**
     * Verify if a download is complete and valid
     * @param context Application context
     * @param contentId The unique identifier of the content
     * @return true if the download is complete and valid, false otherwise
     */
    fun verifyDownload(context: Context, contentId: String): Boolean {
        try {
            // Check if download exists
            if (!isDownloaded(context, contentId)) {
                Log.d(TAG, "Download not found for contentId: $contentId")
                return false
            }
            
            // Get the download and check its state
            val downloads = getDownloads(context)
            val download = downloads.find { it.request.id == contentId }
            
            if (download == null) {
                Log.d(TAG, "Download object not found for contentId: $contentId")
                return false
            }
            
            // Check if download is complete
            if (download.state != Download.STATE_COMPLETED) {
                Log.d(TAG, "Download not complete for contentId: $contentId, state: ${download.state}")
                return false
            }
            
            // Check if download percentage is 100%
            if (download.percentDownloaded < 100) {
                Log.d(TAG, "Download not 100% for contentId: $contentId, progress: ${download.percentDownloaded}%")
                return false
            }
            
            // Check if the download URI is valid
            val uri = download.request.uri
            if (uri == null || uri.toString().isEmpty()) {
                Log.d(TAG, "Download URI is null or empty for contentId: $contentId")
                return false
            }
            
            // Check if we have a valid cache entry for this download
            try {
                val cache = SharedCacheUtil.getCache(context)
                val keys = cache.keys
                var hasCacheEntry = false
                
                // Look for cache entries that match this download's ID
                for (key in keys) {
                    if (key.contains(contentId) || key.contains(uri.toString())) {
                        hasCacheEntry = true
                        break
                    }
                }
                
                if (!hasCacheEntry) {
                    Log.d(TAG, "No cache entry found for contentId: $contentId")
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking cache for contentId: $contentId", e)
                // Don't fail just because we couldn't check the cache
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying download: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Get the download progress percentage for a specific video
     * @param context Application context
     * @param contentId The unique identifier of the content
     * @return Download percentage (0-100), or 0 if not downloading
     */
    fun getDownloadPercentage(context: Context, contentId: String): Float {
        return TPStreamsPlayerDownloadExt.getDownloadPercentage(context, contentId)
    }
    
    /**
     * Delete a downloaded video
     * @param context Application context
     * @param contentId The unique identifier of the content
     */
    fun deleteDownload(context: Context, contentId: String) {
        TPStreamsPlayerDownloadExt.removeDownload(context, contentId)
    }
    
    /**
     * Start downloading a video
     * @param context Application context
     * @param videoUrl The URL of the video to download
     * @param contentId A unique identifier for the content
     * @param quality Optional quality to download (e.g. "720p")
     */
    fun startDownload(context: Context, videoUrl: String, contentId: String, quality: String? = null) {
        TPStreamsPlayerDownloadExt.startDownload(
            context,
            videoUrl,
            contentId,
            selectedQuality = quality
        )
    }
    
    /**
     * Launch the built-in download list activity
     * @param context Application context
     */
    fun showDownloadListActivity(context: Context) {
        val intent = Intent(context, DownloadListActivity::class.java)
        context.startActivity(intent)
    }
    
    /**
     * Get a human-readable status string for a download
     * @param download The download object
     * @return A string representing the download status
     */
    fun getDownloadStatusString(download: Download): String {
        return when (download.state) {
            Download.STATE_COMPLETED -> "Completed"
            Download.STATE_DOWNLOADING -> "Downloading ${download.percentDownloaded.toInt()}%"
            Download.STATE_FAILED -> "Failed"
            Download.STATE_QUEUED -> "Queued"
            Download.STATE_REMOVING -> "Removing"
            Download.STATE_RESTARTING -> "Restarting"
            Download.STATE_STOPPED -> "Stopped"
            else -> "Unknown"
        }
    }
    
    /**
     * Data class representing a simplified download item for app developers
     */
    data class DownloadItem(
        val contentId: String,
        val status: String,
        val progress: Int,
        val isComplete: Boolean
    )
    
    /**
     * Get a list of simplified download items
     * @param context Application context
     * @return List of DownloadItem objects
     */
    fun getDownloadItems(context: Context): List<DownloadItem> {
        return getDownloads(context).map { download ->
            DownloadItem(
                contentId = download.request.id,
                status = getDownloadStatusString(download),
                progress = download.percentDownloaded.toInt(),
                isComplete = download.state == Download.STATE_COMPLETED
            )
        }
    }
    
    /**
     * Play a downloaded video
     * @param player The TPStreamsPlayer instance
     * @param contentId The unique identifier of the content
     * @param context Optional context to use for accessing the download manager
     * @return true if the video was found and playback started, false otherwise
     */
    fun playOfflineContent(player: TPStreamsPlayer, contentId: String, context: Context? = null): Boolean {
        return try {
            // First verify the download is complete and valid
            val appContext = context ?: player.getContext()
            
            if (!verifyDownload(appContext, contentId)) {
                Log.e(TAG, "Cannot play offline content: Download verification failed for $contentId")
                return false
            }
            
            player.playOfflineContent(contentId, appContext)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error playing offline content: ${e.message}", e)
            false
        }
    }
    
    /**
     * Play a downloaded video with a player view
     * @param playerView The TPStreamsPlayerView instance
     * @param contentId The unique identifier of the content
     * @return true if the video was found and playback started, false otherwise
     */
    fun playOfflineContent(playerView: TPStreamsPlayerView, contentId: String): Boolean {
        val player = playerView.player as? TPStreamsPlayer ?: return false
        return playOfflineContent(player, contentId, playerView.context)
    }
    
    /**
     * Pause a download
     * @param context Application context
     * @param contentId The unique identifier of the content
     */
    fun pauseDownload(context: Context, contentId: String) {
        VideoDownloadManager.pauseDownload(context, contentId)
    }
    
    /**
     * Resume a paused download
     * @param context Application context
     * @param contentId The unique identifier of the content
     */
    fun resumeDownload(context: Context, contentId: String) {
        VideoDownloadManager.resumeDownload(context, contentId)
    }
    
    /**
     * Check if a download is paused
     * @param context Application context
     * @param contentId The unique identifier of the content
     * @return true if the download is paused
     */
    fun isDownloadPaused(context: Context, contentId: String): Boolean {
        return VideoDownloadManager.isDownloadPaused(context, contentId)
    }
    
    /**
     * Restart a download that might be in a problematic state
     * @param context Application context
     * @param contentId The unique identifier of the content
     */
    fun restartDownload(context: Context, contentId: String) {
        VideoDownloadManager.restartDownload(context, contentId)
    }
} 