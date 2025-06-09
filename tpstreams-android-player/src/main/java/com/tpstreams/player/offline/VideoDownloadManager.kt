package com.tpstreams.player.offline

import android.content.Context
import android.util.Log
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import java.io.File
import java.util.concurrent.Executors

private const val TAG = "TPStreamsDownloadManager"

@UnstableApi
object VideoDownloadManager {
    const val DOWNLOAD_CONTENT_DIRECTORY = "tpstreams_downloads"
    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "tpstreams_download_channel"
    private const val DOWNLOAD_JOB_ID = 1000
    
    private var isInitialized = false
    private var downloadManager: DownloadManager? = null
    
    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "VideoDownloadManager already initialized")
            return
        }
        
        try {
            Log.d(TAG, "Initializing VideoDownloadManager")
            
            // Create download manager right away to ensure everything is initialized
            createDownloadManager(context)
            
            isInitialized = true
            Log.d(TAG, "VideoDownloadManager initialization complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing VideoDownloadManager", e)
        }
    }
    
    @Synchronized
    private fun createDownloadManager(context: Context) {
        if (downloadManager != null) {
            return
        }
        
        Log.d(TAG, "Creating DownloadManager")
        val downloadExecutor = Executors.newFixedThreadPool(3)
        
        try {
            // Get the shared cache and database provider
            val downloadCache = SharedCacheUtil.getCache(context)
            val databaseProvider = SharedCacheUtil.getDatabaseProvider(context)
            
            val dataSourceFactory = DefaultDataSource.Factory(
                context,
                getHttpDataSourceFactory(context)
            )
            
            downloadManager = DownloadManager(
                context,
                databaseProvider,
                downloadCache,
                dataSourceFactory,
                downloadExecutor
            ).apply {
                maxParallelDownloads = 3
                minRetryCount = 5  // Increase retry count for better reliability
                Log.d(TAG, "DownloadManager created with maxParallelDownloads=3, minRetryCount=5")
            }
            
            downloadManager?.addListener(DownloadManagerListener())
        } catch (e: Exception) {
            Log.e(TAG, "Error creating DownloadManager", e)
        }
    }
    
    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        if (!isInitialized || downloadManager == null) {
            initialize(context)
        }
        
        return downloadManager ?: throw IllegalStateException("DownloadManager could not be initialized")
    }
    
    fun getHttpDataSourceFactory(context: Context): DataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent("TPStreamsPlayer")
            .setConnectTimeoutMs(30000)  // 30 seconds
            .setReadTimeoutMs(30000)     // 30 seconds
            .setAllowCrossProtocolRedirects(true)
    }
    
    fun getCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        try {
            val downloadCache = SharedCacheUtil.getCache(context)
            
            return CacheDataSource.Factory()
                .setCache(downloadCache)
                .setUpstreamDataSourceFactory(getHttpDataSourceFactory(context))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating CacheDataSourceFactory", e)
            throw e
        }
    }
    
    fun getScheduler(context: Context): Scheduler {
        return PlatformScheduler(context, DOWNLOAD_JOB_ID)
    }
    
    fun isDownloaded(context: Context, mediaId: String): Boolean {
        if (!isInitialized) {
            initialize(context)
        }
        
        try {
            val manager = getDownloadManager(context)
            val downloadIndex = manager.downloadIndex
            val download = downloadIndex.getDownload(mediaId)
            return download != null && download.state == Download.STATE_COMPLETED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if media is downloaded", e)
            return false
        }
    }
    
    fun getDownloadPercentage(context: Context, mediaId: String): Float {
        if (!isInitialized) {
            initialize(context)
        }
        
        try {
            val manager = getDownloadManager(context)
            val downloadIndex = manager.downloadIndex
            val download = downloadIndex.getDownload(mediaId)
            if (download != null) {
                return download.percentDownloaded.toFloat()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting download percentage", e)
        }
        return 0f
    }
    
    fun getDownloads(context: Context): List<Download> {
        if (!isInitialized) {
            initialize(context)
        }
        
        val downloads = mutableListOf<Download>()
        
        try {
            val manager = getDownloadManager(context)
            val cursor = manager.downloadIndex.getDownloads()
            
            while (cursor.moveToNext()) {
                downloads.add(cursor.download)
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting downloads", e)
        }
        
        return downloads
    }
    
    fun createDownloadRequest(mediaId: String, uri: String, streamKeys: List<StreamKey>): DownloadRequest {
        Log.d(TAG, "Creating download request for mediaId: $mediaId, uri: $uri, streamKeys: $streamKeys")
        
        // Determine the MIME type based on the URL
        val mimeType = when {
            uri.endsWith(".m3u8") || uri.contains(".m3u8?") -> "application/x-mpegURL"
            uri.endsWith(".mpd") || uri.contains(".mpd?") -> "application/dash+xml"
            else -> "video/*" // Generic fallback
        }
        
        Log.d(TAG, "Using MIME type: $mimeType for URI: $uri")
        
        // For HLS streams, make sure we have at least one stream key
        val finalStreamKeys = if (streamKeys.isEmpty() && (uri.contains(".m3u8") || uri.contains(".mpd"))) {
            Log.d(TAG, "No stream keys provided, adding default stream key")
            listOf(StreamKey(0, 0, 0))
        } else {
            streamKeys
        }
        
        return DownloadRequest.Builder(mediaId, android.net.Uri.parse(uri))
            .setStreamKeys(finalStreamKeys)
            .setData(mediaId.toByteArray())
            .setMimeType(mimeType)
            .build()
    }
    
    fun pauseDownload(context: Context, mediaId: String) {
        if (!isInitialized) {
            initialize(context)
        }
        
        try {
            Log.d(TAG, "Pausing download for mediaId: $mediaId")
            val manager = getDownloadManager(context)
            
            // Ensure the download exists before trying to pause it
            val download = manager.downloadIndex.getDownload(mediaId)
            if (download == null) {
                Log.e(TAG, "Cannot pause download: Download not found for mediaId: $mediaId")
                return
            }
            
            // Set the stop reason for pausing the download
            // Use a custom stop reason value (any non-zero value)
            manager.setStopReason(mediaId, 1) // Using 1 as custom STOP_REASON_PAUSED
            
            // Additional logging to verify the pause operation
            val updatedDownload = manager.downloadIndex.getDownload(mediaId)
            if (updatedDownload != null) {
                Log.d(TAG, "Download state after pause: ${getDownloadStateString(updatedDownload.state)}, stopReason: ${updatedDownload.stopReason}")
            }
            
            Log.d(TAG, "Download paused for mediaId: $mediaId")
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing download: ${e.message}", e)
        }
    }
    
    fun resumeDownload(context: Context, mediaId: String) {
        if (!isInitialized) {
            initialize(context)
        }
        
        try {
            Log.d(TAG, "Resuming download for mediaId: $mediaId")
            val manager = getDownloadManager(context)
            
            // Ensure the download exists before trying to resume it
            val download = manager.downloadIndex.getDownload(mediaId)
            if (download == null) {
                Log.e(TAG, "Cannot resume download: Download not found for mediaId: $mediaId")
                return
            }
            
            // Clear the stop reason to resume the download
            manager.setStopReason(mediaId, 0) // STOP_REASON_NONE = 0
            
            // Additional logging to verify the resume operation
            val updatedDownload = manager.downloadIndex.getDownload(mediaId)
            if (updatedDownload != null) {
                Log.d(TAG, "Download state after resume: ${getDownloadStateString(updatedDownload.state)}, stopReason: ${updatedDownload.stopReason}")
            }
            
            Log.d(TAG, "Download resumed for mediaId: $mediaId")
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming download: ${e.message}", e)
        }
    }
    
    private fun getDownloadStateString(state: Int): String {
        return when (state) {
            Download.STATE_QUEUED -> "QUEUED"
            Download.STATE_STOPPED -> "STOPPED"
            Download.STATE_DOWNLOADING -> "DOWNLOADING"
            Download.STATE_COMPLETED -> "COMPLETED"
            Download.STATE_FAILED -> "FAILED"
            Download.STATE_REMOVING -> "REMOVING"
            Download.STATE_RESTARTING -> "RESTARTING"
            else -> "UNKNOWN"
        }
    }
    
    fun isDownloadPaused(context: Context, mediaId: String): Boolean {
        if (!isInitialized) {
            initialize(context)
        }
        
        try {
            val manager = getDownloadManager(context)
            val download = manager.downloadIndex.getDownload(mediaId)
            
            if (download == null) {
                Log.d(TAG, "isDownloadPaused: Download not found for mediaId: $mediaId")
                return false
            }
            
            // Check if download is stopped with our custom pause reason
            val isPaused = download.state == Download.STATE_STOPPED && download.stopReason == 1
            Log.d(TAG, "isDownloadPaused: mediaId=$mediaId, state=${getDownloadStateString(download.state)}, stopReason=${download.stopReason}, isPaused=$isPaused")
            return isPaused
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if download is paused: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Restart a download that might be in a problematic state
     * This method will remove the download and then add it back
     * @param context Application context
     * @param mediaId The unique identifier of the media
     */
    fun restartDownload(context: Context, mediaId: String) {
        if (!isInitialized) {
            initialize(context)
        }
        
        try {
            Log.d(TAG, "Restarting download for mediaId: $mediaId")
            val manager = getDownloadManager(context)
            
            // Get the download to preserve its request
            val download = manager.downloadIndex.getDownload(mediaId)
            if (download == null) {
                Log.e(TAG, "Cannot restart download: Download not found for mediaId: $mediaId")
                return
            }
            
            // Save the request
            val request = download.request
            
            // Remove the download
            manager.removeDownload(mediaId)
            Log.d(TAG, "Removed download for mediaId: $mediaId")
            
            // Wait a moment for the removal to complete
            Thread.sleep(1000)
            
            // Add the download back
            manager.addDownload(request)
            Log.d(TAG, "Re-added download for mediaId: $mediaId")
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting download: ${e.message}", e)
        }
    }
    
    private class DownloadManagerListener : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            val state = when (download.state) {
                Download.STATE_COMPLETED -> "COMPLETED"
                Download.STATE_DOWNLOADING -> "DOWNLOADING (${download.percentDownloaded.toInt()}%)"
                Download.STATE_FAILED -> "FAILED"
                Download.STATE_QUEUED -> "QUEUED"
                Download.STATE_REMOVING -> "REMOVING"
                Download.STATE_RESTARTING -> "RESTARTING"
                Download.STATE_STOPPED -> "STOPPED"
                else -> "UNKNOWN"
            }
            
            Log.d(TAG, "Download changed - ID: ${download.request.id}, State: $state")
            
            if (finalException != null) {
                Log.e(TAG, "Download error", finalException)
            }
        }
        
        override fun onDownloadRemoved(
            downloadManager: DownloadManager,
            download: Download
        ) {
            Log.d(TAG, "Download removed - ID: ${download.request.id}")
        }
    }
} 