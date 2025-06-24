package com.tpstreams.player.download

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import org.json.JSONObject

class DownloadClient private constructor(private val context: Context) {

    interface Listener {
        fun onDownloadsChanged()
    }

    private val listeners = mutableSetOf<Listener>()
    private val repository = DownloadRepository(context) {
        listeners.forEach { it.onDownloadsChanged() }
    }


    fun isDownloaded(assetId: String): Boolean = repository.getDownload(assetId)?.state == Download.STATE_COMPLETED

    fun isDownloading(assetId: String): Boolean = repository.getDownload(assetId)?.state == Download.STATE_DOWNLOADING

    fun isPaused(assetId: String): Boolean = repository.getDownload(assetId)?.state == Download.STATE_STOPPED

    fun getDownloadStatus(assetId: String): String {
        val download = repository.getDownload(assetId) ?: return "Not found"
        return when (download.state) {
            Download.STATE_DOWNLOADING -> "Downloading: ${download.percentDownloaded.toInt()}%"
            Download.STATE_COMPLETED -> "Downloaded"
            Download.STATE_STOPPED -> "Paused"
            else -> DownloadController.getStateString(download.state)
        }
    }

    fun getAllDownloads(): List<Download> = repository.getAllDownloads()

    fun getDownload(assetId: String): Download? = repository.getDownload(assetId)

    fun getDownloadRequest(assetId: String): DownloadRequest? = repository.getDownloadRequest(assetId)

    fun getAllDownloadItems(): List<DownloadItem> {
        return repository.getAllDownloads().map { createDownloadItem(it) }
    }

    fun createDownloadItem(download: Download): DownloadItem {
        val assetId = download.request.id
        val progressPercent = getSafeProgressPercentage(download.percentDownloaded)
        
        var title = "Unknown Title"
        var thumbnailUrl: String? = null
        
        try {
            val dataString = download.request.data?.toString(Charsets.UTF_8)
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


    fun startDownload(mediaItem: MediaItem, resolution: String) {
        DownloadController.startDownload(context, mediaItem, resolution)
    }

    fun pauseDownload(assetId: String) {
        DownloadController.pauseDownload(context, assetId)
    }

    fun resumeDownload(assetId: String) {
        DownloadController.resumeDownload(context, assetId)
    }

    fun removeDownload(assetId: String) {
        DownloadController.removeDownload(context, assetId)
        repository.clearFromCache(assetId)
        listeners.forEach { it.onDownloadsChanged() }
    }

    // endregion

    // region Listener Management

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private inner class DownloadRepository(
        private val context: Context,
        private val onChange: () -> Unit
    ) : DownloadManager.Listener {

        private val downloads = mutableMapOf<String, Download>()
        private val downloadManager = DownloadController.getDownloadManager(context).apply {
            addListener(this@DownloadRepository)
        }

        init {
            loadDownloads()
        }

        private fun loadDownloads() {
            downloadManager.downloadIndex.getDownloads().use { cursor ->
                downloads.clear()
                while (cursor.moveToNext()) {
                    downloads[cursor.download.request.id] = cursor.download
                }
                onChange()
            }
        }

        fun getDownload(id: String): Download? = downloads[id]

        fun getAllDownloads(): List<Download> = downloads.values.toList()

        fun getDownloadRequest(id: String): DownloadRequest? {
            val download = getDownload(id)
            return if (download?.state == Download.STATE_COMPLETED) download.request else null
        }

        fun clearFromCache(id: String) {
            try {
                DownloadController.downloadCache.removeResource(id)
                Log.d(TAG, "Cache resource removed for: $id")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing cache: ${e.message}", e)
            }
        }

        override fun onDownloadChanged(dm: DownloadManager, download: Download, ex: Exception?) {
            downloads[download.request.id] = download
            
            val progressPercent = if (download.percentDownloaded < 0) 0 else download.percentDownloaded.toInt()
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
                    Log.e(TAG, "Download failed: ${download.request.id}, Error: ${ex?.message}")
                }
                Download.STATE_STOPPED -> {
                    Log.d(TAG, "Download paused: ${download.request.id}, Progress: $progressPercent%")
                }
                else -> {
                    Log.d(TAG, "Download state changed: ${download.request.id}, State: ${DownloadController.getStateString(download.state)}, Progress: $progressPercent%")
                }
            }
            
            onChange()
        }

        override fun onDownloadRemoved(dm: DownloadManager, download: Download) {
            downloads.remove(download.request.id)
            Log.d(TAG, "Download removed: ${download.request.id}")
            onChange()
        }
    }

    companion object {
        private const val TAG = "DownloadClient"
        
        @Volatile private var instance: DownloadClient? = null

        fun getInstance(context: Context): DownloadClient {
            return instance ?: synchronized(this) {
                instance ?: DownloadClient(context.applicationContext).also { instance = it }
            }
        }
    }
} 