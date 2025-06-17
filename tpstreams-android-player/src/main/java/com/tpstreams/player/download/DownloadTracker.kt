package com.tpstreams.player.download

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

@UnstableApi
class DownloadTracker private constructor(private val context: Context) {
    
    interface Listener {
        fun onDownloadsChanged()
    }
    
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val downloads = ConcurrentHashMap<String, Download>()
    private var downloadManager: DownloadManager? = null
    
    init {
        downloadManager = DownloadController.getDownloadManager(context)
        downloadManager?.addListener(DownloadManagerListener())
        loadDownloads()
    }
    
    private fun loadDownloads() {
        downloadManager?.downloadIndex?.getDownloads()?.use { downloadCursor ->
            downloads.clear()
            
            while (downloadCursor.moveToNext()) {
                val download = downloadCursor.download
                downloads[download.request.id] = download
            }
            
            notifyListeners()
        }
    }
    
    fun addListener(listener: Listener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }
    
    private fun notifyListeners() {
        for (listener in listeners) {
            listener.onDownloadsChanged()
        }
    }
    
    fun getDownload(assetId: String): Download? {
        return downloads[assetId]
    }
    
    /**
     * Get all downloads currently tracked
     * @return List of all downloads
     */
    fun getAllDownloads(): List<Download> {
        return downloads.values.toList()
    }
    
    /**
     * Get all downloads as DownloadItems
     * @return List of DownloadItem objects
     */
    fun getAllDownloadItems(): List<DownloadItem> {
        return downloads.values.map { createDownloadItem(it) }
    }
    
    /**
     * Create a DownloadItem from a Download object
     */
    fun createDownloadItem(download: Download): DownloadItem {
        val assetId = download.request.id
        val progressPercent = getSafeProgressPercentage(download.percentDownloaded)
        
        // Extract metadata from download request data
        var title = "Unknown Title"
        var thumbnailUrl: String? = null
        
        try {
            val dataString = download.request.data?.toString(Charsets.UTF_8)
            Log.d(TAG, "dataString: $dataString")
            if (!dataString.isNullOrEmpty()) {
                val json = JSONObject(dataString)
                title = json.optString("title", title)
                thumbnailUrl = json.optString("thumbnailUrl", null)
                    .takeIf { it?.isNotEmpty() == true }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting metadata from download: ${e.message}")
        }
        
        return DownloadItem(
            assetId = assetId,
            title = title,
            thumbnailUrl = thumbnailUrl,
            totalBytes = download.contentLength,
            downloadedBytes = download.bytesDownloaded,
            progressPercentage = progressPercent.toFloat(),
            state = download.state
        )
    }
    
    private fun getSafeProgressPercentage(percentDownloaded: Float): Int {
        return if (percentDownloaded < 0) 0 else percentDownloaded.toInt()
    }
    
    /**
     * Get the download request for an assetId if it exists and is downloaded
     * @param assetId The assetId to check
     * @return The DownloadRequest if the content is downloaded, null otherwise
     */
    fun getDownloadRequest(assetId: String): DownloadRequest? {
        val download = getDownload(assetId)
        return if (download != null && download.state == Download.STATE_COMPLETED) {
            download.request
        } else {
            null
        }
    }
    
    fun isDownloaded(assetId: String): Boolean {
        val download = downloads[assetId] ?: return false
        return download.state == Download.STATE_COMPLETED
    }
    
    fun isDownloading(assetId: String): Boolean {
        val download = downloads[assetId] ?: return false
        return download.state == Download.STATE_DOWNLOADING
    }
    
    fun isPaused(assetId: String): Boolean {
        val download = downloads[assetId] ?: return false
        return download.state == Download.STATE_STOPPED
    }
    
    fun startDownload(mediaItem: MediaItem, resolution: String) {
        Log.d(TAG, "Starting download for: ${mediaItem.mediaId}, resolution: $resolution")
        DownloadController.startDownload(context, mediaItem, resolution)
    }
    
    fun pauseDownload(assetId: String) {
        val download = downloads[assetId] ?: return
        
        Log.d(TAG, "Pausing download: ${download.request.id}")
        
        DownloadController.pauseDownload(context, download.request.id)
    }
    
    fun resumeDownload(assetId: String) {
        val download = downloads[assetId] ?: return
        
        Log.d(TAG, "Resuming download: ${download.request.id}")
        
        DownloadController.resumeDownload(context, download.request.id)
    }
    
    fun removeDownload(assetId: String) {
        val download = downloads[assetId] ?: return
        
        Log.d(TAG, "Removing download: ${download.request.id}")
        
        DownloadController.removeDownload(context, download.request.id)
        
        try {
            DownloadController.downloadCache.removeResource(download.request.uri.toString())
            Log.d(TAG, "Cache resource removed for: ${download.request.id}")
            
            val downloadDirectory = File(context.getExternalFilesDir(null), DownloadController.DOWNLOAD_CONTENT_DIRECTORY)
            val downloadFiles = downloadDirectory.listFiles { file -> 
                file.name.contains(download.request.id.replace("/", "_")) 
            }
            
            downloadFiles?.forEach { file ->
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d(TAG, "Deleting file ${file.absolutePath}: ${if (deleted) "success" else "failed"}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing resources: ${e.message}", e)
        }

        notifyListeners()
    }
    
    fun getDownloadStatus(assetId: String): String {
        val download = downloads[assetId] ?: return "Not found"
        
        return when (download.state) {
            Download.STATE_DOWNLOADING -> {
                val percentage = getSafeProgressPercentage(download.percentDownloaded)
                "Downloading: $percentage%"
            }
            Download.STATE_COMPLETED -> "Downloaded"
            Download.STATE_STOPPED -> "Paused"
            else -> DownloadController.getStateString(download.state)
        }
    }
    
    private inner class DownloadManagerListener : DownloadManager.Listener {
        override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
            downloads[download.request.id] = download
            
            val progressPercent = getSafeProgressPercentage(download.percentDownloaded)
            val bytesDownloaded = download.bytesDownloaded
            val contentLength = download.contentLength
            
            when (download.state) {
                Download.STATE_DOWNLOADING -> {
                    Log.d(TAG, "Downloading: ${download.request.id}, Progress: $progressPercent%, " +
                        "Speed: ${bytesDownloaded / 1024}KB/${if (contentLength > 0) contentLength / 1024 else "?"}KB")
                }
                Download.STATE_COMPLETED -> {
                    Log.d(TAG, "Download completed: ${download.request.id}, Progress: $progressPercent%, " +
                        "Size: ${bytesDownloaded / 1024}KB")
                }
                Download.STATE_FAILED -> {
                    Log.e(TAG, "Download failed: ${download.request.id}, Error: ${finalException?.message}")
                }
                Download.STATE_STOPPED -> {
                    Log.d(TAG, "Download paused: ${download.request.id}, Progress: $progressPercent%")
                }
                else -> {
                    Log.d(TAG, "Download state changed: ${download.request.id}, State: ${DownloadController.getStateString(download.state)}, Progress: $progressPercent%")
                }
            }
            
            notifyListeners()
        }
        
        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            downloads.remove(download.request.id)
            Log.d(TAG, "Download removed: ${download.request.id}")
            notifyListeners()
        }
    }
    
    companion object {
        private const val TAG = "DownloadTracker"
        
        @Volatile
        private var instance: DownloadTracker? = null
        
        fun getInstance(context: Context): DownloadTracker {
            return instance ?: synchronized(this) {
                instance ?: DownloadTracker(context.applicationContext).also { instance = it }
            }
        }
    }
} 