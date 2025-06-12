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

@UnstableApi
class DownloadTracker private constructor(private val context: Context) {
    
    interface Listener {
        fun onDownloadsChanged()
    }
    
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val downloads = mutableMapOf<Uri, Download>()
    private var downloadManager: DownloadManager? = null
    
    init {
        downloadManager = DownloadController.getDownloadManager(context)
        downloadManager?.addListener(DownloadManagerListener())
        loadDownloads()
    }
    
    private fun loadDownloads() {
        val downloadCursor = downloadManager?.downloadIndex?.getDownloads() ?: return
        downloads.clear()
        
        while (downloadCursor.moveToNext()) {
            val download = downloadCursor.download
            downloads[download.request.uri] = download
        }
        
        notifyListeners()
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
    
    fun getDownload(uri: Uri): Download? {
        return downloads[uri]
    }
    
    /**
     * Get all downloads currently tracked
     * @return List of all downloads
     */
    fun getAllDownloads(): List<Download> {
        return downloads.values.toList()
    }
    
    /**
     * Get the download request for a URI if it exists and is downloaded
     * @param uri The URI to check
     * @return The DownloadRequest if the content is downloaded, null otherwise
     */
    fun getDownloadRequest(uri: Uri): DownloadRequest? {
        val download = getDownload(uri)
        return if (download != null && download.state == Download.STATE_COMPLETED) {
            download.request
        } else {
            null
        }
    }
    
    fun isDownloaded(uri: Uri): Boolean {
        val download = downloads[uri] ?: return false
        return download.state == Download.STATE_COMPLETED
    }
    
    fun isDownloading(uri: Uri): Boolean {
        val download = downloads[uri] ?: return false
        return download.state == Download.STATE_DOWNLOADING
    }
    
    fun isPaused(uri: Uri): Boolean {
        val download = downloads[uri] ?: return false
        return download.state == Download.STATE_STOPPED
    }
    
    fun startDownload(mediaItem: MediaItem, resolution: String) {
        Log.d(TAG, "Starting download for: ${mediaItem.mediaId}, resolution: $resolution")
        DownloadController.startDownload(context, mediaItem, resolution)
    }
    
    fun pauseDownload(uri: Uri) {
        val download = downloads[uri] ?: return
        
        Log.d(TAG, "Pausing download: ${download.request.id}")
        
        DownloadController.pauseDownload(context, download.request.id)
    }
    
    fun resumeDownload(uri: Uri) {
        val download = downloads[uri] ?: return
        
        Log.d(TAG, "Resuming download: ${download.request.id}")
        
        DownloadController.resumeDownload(context, download.request.id)
    }
    
    fun removeDownload(uri: Uri) {
        val download = downloads[uri] ?: return
        
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
    
    fun getDownloadPercentage(uri: Uri): Float {
        val download = downloads[uri] ?: return 0f
        return TPSDownloadService.calculateProgressPercent(download).toFloat()
    }
    
    fun getDownloadStatus(uri: Uri): String {
        val download = downloads[uri] ?: return "Not found"
        
        return when (download.state) {
            Download.STATE_DOWNLOADING -> {
                val percentage = getDownloadPercentage(uri)
                "Downloading: ${percentage.toInt()}%"
            }
            Download.STATE_COMPLETED -> "Downloaded"
            Download.STATE_STOPPED -> "Paused"
            else -> DownloadController.getStateString(download.state)
        }
    }
    
    private inner class DownloadManagerListener : DownloadManager.Listener {
        override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
            downloads[download.request.uri] = download
            
            val progressPercent = TPSDownloadService.calculateProgressPercent(download)
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
            downloads.remove(download.request.uri)
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