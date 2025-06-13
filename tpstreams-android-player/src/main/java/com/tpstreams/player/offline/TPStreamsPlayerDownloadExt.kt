package com.tpstreams.player.offline

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.TPStreamsPlayer

/**
 * Extension functions for TPStreamsPlayer to support offline playback
 */
@UnstableApi
object TPStreamsPlayerDownloadExt {
    
    private var isInitialized = false
    
    /**
     * Initialize the download manager for offline playback
     * @param context Application context
     */
    fun initializeDownloadManager(context: Context) {
        if (!isInitialized) {
            try {
                Log.d(TAG, "Initializing download manager")
                VideoDownloadManager.initialize(context)
                isInitialized = true
                Log.d(TAG, "Download manager initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize download manager", e)
            }
        }
    }
    
    /**
     * Start downloading a video
     * @param context Application context
     * @param videoUrl The URL of the video to download
     * @param contentId A unique identifier for the content
     * @param selectedQuality Optional quality to use for download
     */
    fun startDownload(
        context: Context, 
        videoUrl: String, 
        contentId: String,
        selectedQuality: String? = null
    ) {
        Log.d(TAG, "Starting download for contentId: $contentId, URL: $videoUrl")
        if (videoUrl.isEmpty() || contentId.isEmpty()) {
            Log.e(TAG, "Cannot download: Video URL or Content ID is empty")
            return
        }
        
        // Check if this is a DRM-protected DASH stream (typically .mpd files)
        if (videoUrl.endsWith(".mpd") || videoUrl.contains(".mpd?")) {
            Log.e(TAG, "Cannot download DRM-protected content: $videoUrl")
            // Show a toast on the main thread
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    context,
                    "DRM-protected content cannot be downloaded",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            return
        }
        
        try {
            ensureInitialized(context)
            val downloadTask = DownloadTask(context)
            downloadTask.startDownload(videoUrl, contentId, selectedQuality)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting download", e)
        }
    }
    
    /**
     * Check if a video is already downloaded
     * @param context Application context
     * @param contentId The unique identifier of the content
     * @return true if the video is downloaded, false otherwise
     */
    fun isDownloaded(context: Context, contentId: String): Boolean {
        if (contentId.isEmpty()) {
            Log.d(TAG, "Cannot check download status: Content ID is empty")
            return false
        }
        
        try {
            ensureInitialized(context)
            return VideoDownloadManager.isDownloaded(context, contentId)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking download status", e)
            return false
        }
    }
    
    /**
     * Get the download percentage of a video
     * @param context Application context
     * @param contentId The unique identifier of the content
     * @return The download percentage (0-100), or 0 if not downloading
     */
    fun getDownloadPercentage(context: Context, contentId: String): Float {
        if (contentId.isEmpty()) {
            return 0f
        }
        
        try {
            ensureInitialized(context)
            return VideoDownloadManager.getDownloadPercentage(context, contentId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting download percentage", e)
            return 0f
        }
    }
    
    /**
     * Remove a downloaded video
     * @param context Application context
     * @param contentId The unique identifier of the content
     */
    fun removeDownload(context: Context, contentId: String) {
        if (contentId.isEmpty()) {
            return
        }
        
        try {
            ensureInitialized(context)
            VideoDownloadManager.getDownloadManager(context).removeDownload(contentId)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing download", e)
        }
    }
    
    /**
     * Get all downloads
     * @param context Application context
     * @return List of all downloads
     */
    fun getDownloads(context: Context): List<androidx.media3.exoplayer.offline.Download> {
        try {
            ensureInitialized(context)
            return VideoDownloadManager.getDownloads(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting downloads", e)
            return emptyList()
        }
    }
    
    /**
     * Ensure the download manager is initialized
     */
    private fun ensureInitialized(context: Context) {
        if (!isInitialized) {
            initializeDownloadManager(context)
        }
    }
    
    /**
     * Pause a download
     * @param context Application context
     * @param contentId The unique identifier of the content
     */
    fun pauseDownload(context: Context, contentId: String) {
        if (contentId.isEmpty()) {
            Log.d(TAG, "Cannot pause download: Content ID is empty")
            return
        }
        
        try {
            ensureInitialized(context)
            VideoDownloadManager.pauseDownload(context, contentId)
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing download: ${e.message}", e)
        }
    }
    
    /**
     * Resume a paused download
     * @param context Application context
     * @param contentId The unique identifier of the content
     */
    fun resumeDownload(context: Context, contentId: String) {
        if (contentId.isEmpty()) {
            Log.d(TAG, "Cannot resume download: Content ID is empty")
            return
        }
        
        try {
            ensureInitialized(context)
            VideoDownloadManager.resumeDownload(context, contentId)
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming download: ${e.message}", e)
        }
    }
    
    /**
     * Check if a download is paused
     * @param context Application context
     * @param contentId The unique identifier of the content
     * @return true if the download is paused, false otherwise
     */
    fun isDownloadPaused(context: Context, contentId: String): Boolean {
        if (contentId.isEmpty()) {
            return false
        }
        
        try {
            ensureInitialized(context)
            return VideoDownloadManager.isDownloadPaused(context, contentId)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if download is paused: ${e.message}", e)
            return false
        }
    }
    
    private const val TAG = "TPStreamsPlayerDownloadExt"
} 