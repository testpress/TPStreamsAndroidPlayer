package com.tpstreams.player.download

import android.content.Context
import android.util.Log
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest

internal class DownloadRepository(
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

    companion object {
        private const val TAG = "DownloadRepository"
    }
} 