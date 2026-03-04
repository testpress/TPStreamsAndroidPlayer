package com.tpstreams.player.download

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import org.json.JSONObject
import com.tpstreams.player.TPStreamsSDK
import com.tpstreams.player.data.AssetInfo
import com.tpstreams.player.data.AssetRepository
import com.tpstreams.player.constants.PlaybackError
import com.tpstreams.player.DownloadOptionsBottomSheet
import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.common.util.Util
import android.net.Uri
import com.tpstreams.player.util.MediaItemUtils
import com.tpstreams.player.util.DownloadUtils

@UnstableApi
class DownloadClient private constructor(private val context: Context) {

    interface Listener {
        fun onDownloadsChanged()
        fun onDownloadStateChanged(downloadItem: DownloadItem, error: Exception? = null)
        fun onDownloadStarted(downloadItem: DownloadItem) {}
        fun onDownloadResumed(downloadItem: DownloadItem) {}
        fun onDownloadCompleted(downloadItem: DownloadItem) {}
        fun onDownloadFailed(downloadItem: DownloadItem, error: Exception) {}
        fun onDownloadDeleted(assetId: String) {}
    }

    private val listeners = mutableSetOf<Listener>()
    private val repository = DownloadRepository(context) {
        listeners.forEach { it.onDownloadsChanged() }
    }
    
    private val PROGRESS_UPDATE_INTERVAL_MS = 1000L
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            listeners.forEach { listener ->
                try {
                    listener.onDownloadsChanged()
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying listener: ${e.message}", e)
                }
            }

            progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
        }
    }

    fun isDownloaded(assetId: String): Boolean = repository.getDownload(assetId)?.state == Download.STATE_COMPLETED

    fun isDownloading(assetId: String): Boolean = repository.getDownload(assetId)?.state == Download.STATE_DOWNLOADING

    fun isPaused(assetId: String): Boolean = repository.getDownload(assetId)?.state == Download.STATE_STOPPED

    fun getDownloadStatus(assetId: String): String {
        val download = repository.getDownload(assetId) ?: return "Not found"
        return DownloadController.getStateString(download.state)
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
        val customMetadata = mutableMapOf<String, String>()
        var computedSize: Long = 0
        
        try {
            val dataString = download.request.data?.toString(Charsets.UTF_8)
            if (!dataString.isNullOrEmpty()) {
                val json = JSONObject(dataString)
                title = json.optString(DownloadConstants.KEY_TITLE, title)
                thumbnailUrl = json.optString(DownloadConstants.KEY_THUMBNAIL_URL, null)
                    .takeIf { it?.isNotEmpty() == true }
                computedSize = json.optLong(DownloadConstants.KEY_CALCULATED_SIZE_BYTES, 0)

                val metadataObj = json.optJSONObject(DownloadConstants.KEY_CUSTOM_METADATA)
                metadataObj?.let {
                    val map = it.keys().asSequence().associateWith { key -> it.getString(key) }
                    customMetadata.putAll(map)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting metadata from download: ${e.message}")
        }

        val totalBytes = if (download.contentLength > 0) download.contentLength else computedSize
        
        return DownloadItem(
            assetId = assetId,
            title = title,
            thumbnailUrl = thumbnailUrl,
            totalBytes = totalBytes,
            downloadedBytes = download.bytesDownloaded,
            progressPercentage = progressPercent.toFloat(),
            state = download.state,
            metadata = customMetadata
        )
    }

    private fun getSafeProgressPercentage(percentDownloaded: Float): Int {
        return if (percentDownloaded < 0) 0 else percentDownloaded.toInt()
    }


    fun getAssetInfo(
        assetId: String,
        accessToken: String,
        callback: AssetRepository.AssetCallback
    ) {
        val orgId = TPStreamsSDK.orgId ?: throw IllegalStateException("TPStreamsSDK.init(orgId) must be called first")
        AssetRepository.fetchAssetInfo(orgId, assetId, accessToken, callback)
    }

    fun startDownload(
        context: Context,
        assetId: String,
        accessToken: String,
        resolution: String? = null,
        metadata: Map<String, String>? = null
    ) {
        val orgId = TPStreamsSDK.orgId ?: throw IllegalStateException("TPStreamsSDK.init(orgId) must be called first")

        getAssetInfo(assetId, accessToken, object : AssetRepository.AssetCallback {
            override fun onSuccess(assetInfo: AssetInfo, title: String) {
                if (resolution != null) {
                    performStartDownload(assetInfo, title, orgId, assetId, accessToken, resolution, metadata)
                } else {
                    if (context is androidx.fragment.app.FragmentActivity) {
                        showDownloadOptions(context, assetInfo, title, orgId, assetId, accessToken, metadata)
                    } else {
                        Log.e(TAG, "Resolution is required for non-activity contexts")
                    }
                }
            }

            override fun onError(error: PlaybackError, message: String) {
                val title = "Video $assetId"
                val failedItem = DownloadItem(assetId, title, null, 0, 0, 0f, Download.STATE_FAILED, metadata ?: emptyMap())
                listeners.toList().forEach { it.onDownloadFailed(failedItem, Exception("Failed to fetch asset info: $message")) }
            }
        })
    }

    private fun showDownloadOptions(
        activity: androidx.fragment.app.FragmentActivity,
        assetInfo: AssetInfo,
        title: String,
        orgId: String,
        assetId: String,
        accessToken: String,
        metadata: Map<String, String>?
    ) {
        val mediaItem = MediaItemUtils.buildMediaItem(assetInfo, title, orgId, assetId, accessToken).mediaItem
        val bottomSheet = DownloadOptionsBottomSheet()
        bottomSheet.setDuration((assetInfo.durationSeconds * 1000).toLong())
        
        DownloadController.getAvailableResolutions(context, mediaItem) { resolutions, bitrates ->
            if (activity.isFinishing || activity.isDestroyed) return@getAvailableResolutions

            if (resolutions.isEmpty()) {
                val failedItem = DownloadItem(assetId, title, null, 0, 0, 0f, Download.STATE_FAILED, metadata ?: emptyMap())
                listeners.toList().forEach { it.onDownloadFailed(failedItem, Exception("No download qualities available for this asset")) }
                return@getAvailableResolutions
            }
            
            bottomSheet.setAvailableResolutions(resolutions)
            bottomSheet.setTrackBitrates(bitrates)
            bottomSheet.setDownloadSelectionListener(object : DownloadOptionsBottomSheet.DownloadSelectionListener {
                override fun onDownloadResolutionSelected(resolution: String) {
                    val bitrate = bitrates[resolution] ?: 0
                    val totalSize = DownloadUtils.calculateDownloadSize(bitrate.toLong(), assetInfo.durationSeconds)
                    performStartDownload(assetInfo, title, orgId, assetId, accessToken, resolution, metadata, totalSize)
                }
            })
            bottomSheet.show(activity.supportFragmentManager)
        }
    }

    private fun performStartDownload(
        assetInfo: AssetInfo,
        title: String,
        orgId: String,
        assetId: String,
        accessToken: String,
        resolution: String,
        metadata: Map<String, String>?,
        totalSize: Long = 0
    ) {
        val mediaItem = MediaItemUtils.buildMediaItem(assetInfo, title, orgId, assetId, accessToken).mediaItem
        startDownload(mediaItem, resolution, metadata ?: emptyMap(), totalSize)
    }



    internal fun startDownload(mediaItem: MediaItem, resolution: String, metadata: Map<String, String>, totalSize: Long = 0, offlineLicenseExpireTime: Long = DownloadConstants.FIFTEEN_DAYS_IN_SECONDS) {
        if (!hasEnoughStorage(totalSize)) {
            val title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown Title"
            val failedItem = DownloadItem(mediaItem.mediaId, title, null, totalSize, 0, 0f, Download.STATE_FAILED, metadata)
            listeners.toList().forEach { it.onDownloadFailed(failedItem, Exception("Insufficient storage space")) }
            return
        }
        
        DownloadController.startDownload(context, mediaItem, resolution, metadata, totalSize, offlineLicenseExpireTime)
    }
    
    fun resumeDownload(assetId: String) {
        val downloadItem = getAllDownloadItems().find { it.assetId == assetId }
        if (downloadItem == null) {
            Log.w(TAG, "Cannot resume download, item not found for assetId: $assetId")
            return
        }

        val remainingBytes = downloadItem.totalBytes - downloadItem.downloadedBytes
        if (!hasEnoughStorage(remainingBytes)) {
            listeners.toList().forEach { it.onDownloadFailed(downloadItem, Exception("Insufficient storage space to resume")) }
            return
        }
        
        DownloadController.resumeDownload(context, assetId)
    }
    
    fun pauseDownload(assetId: String) {
        DownloadController.pauseDownload(context, assetId)
    }
    
    private fun hasEnoughStorage(requiredBytes: Long): Boolean {
        val availableSpace = getAvailableStorageSpace()
        val pendingDownloadsSize = getPendingDownloadsSize()
        val actualAvailable = availableSpace - pendingDownloadsSize
        
        val requiredWithBuffer = (requiredBytes * STORAGE_BUFFER_MULTIPLIER).toLong()
        
        return actualAvailable >= requiredWithBuffer
    }
    
    private fun getAvailableStorageSpace(): Long {
        return try {
            val dir = context.getExternalFilesDir(null)
            val path = dir?.absolutePath ?: return 0L
            
            val stat = StatFs(path)
            stat.availableBytes
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available storage space: ${e.message}", e)
            0L
        }
    }
    
    private fun getPendingDownloadsSize(): Long {
        val pendingDownloads = getAllDownloadItems()
            .filter { it.state !in listOf(Download.STATE_COMPLETED, Download.STATE_FAILED, Download.STATE_REMOVING) }
        
        return pendingDownloads.sumOf { it.totalBytes }
    }

    fun removeDownload(assetId: String) {
        DownloadController.removeDownload(context, assetId)
        repository.clearFromCache(assetId)
        listeners.forEach { it.onDownloadsChanged() }
    }


    fun addListener(listener: Listener) {
        Log.d(TAG, "addListener: $listener. Total listeners before add: ${listeners.size}")
        listeners.add(listener)
        if (listeners.size == 1) {
            startProgressUpdates()
        }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            stopProgressUpdates()
        }
    }
    
    private fun startProgressUpdates() {
        progressHandler.post(progressRunnable)
    }
    
    private fun stopProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable)
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
                    val download = cursor.download
                    downloads[download.request.id] = download
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
            val id = download.request.id
            val previousDownload = downloads[id]
            val previousState = previousDownload?.state
            val currentState = download.state

            Log.d(TAG, "onDownloadChanged: ID=$id, Transition=[${DownloadController.getStateString(previousState ?: -1)} -> ${DownloadController.getStateString(currentState)}]")

            downloads[id] = download
            
            val downloadItem = createDownloadItem(download)
            listeners.toList().forEach { listener ->
                listener.onDownloadStateChanged(downloadItem, ex)

                when (currentState) {
                    Download.STATE_QUEUED -> {
                        if (previousState == null) {
                            listener.onDownloadStarted(downloadItem)
                        } else if (previousState == Download.STATE_STOPPED) {
                            listener.onDownloadResumed(downloadItem)
                        }
                    }
                    Download.STATE_DOWNLOADING -> {
                        // If we skipped QUEUED or this is the first time we see it
                        if (previousState == null) {
                            listener.onDownloadStarted(downloadItem)
                        } else if (previousState == Download.STATE_STOPPED) {
                            listener.onDownloadResumed(downloadItem)
                        }
                    }
                    Download.STATE_COMPLETED -> {
                        if (previousState != Download.STATE_COMPLETED) {
                            listener.onDownloadCompleted(downloadItem)
                        }
                    }
                    Download.STATE_FAILED -> {
                        if (previousState != Download.STATE_FAILED) {
                            val failureMessage = when (download.failureReason) {
                                Download.FAILURE_REASON_UNKNOWN -> "Unknown error occurred during download."
                                else -> "Download failed (error code: ${download.failureReason})"
                            }
                            listener.onDownloadFailed(downloadItem, ex ?: Exception(failureMessage))
                        }
                    }
                }
            }
            
            onChange()
        }

        override fun onDownloadRemoved(dm: DownloadManager, download: Download) {
            val id = download.request.id
            if (downloads.containsKey(id)) {
                downloads.remove(id)
                Log.d(TAG, "Download removed: $id")
                listeners.toList().forEach { it.onDownloadDeleted(id) }
                onChange()
            }
        }
    }

    companion object {
        private const val TAG = "DownloadClient"
        private const val STORAGE_BUFFER_MULTIPLIER = 1.2f
        
        @Volatile private var instance: DownloadClient? = null

        fun getInstance(context: Context): DownloadClient {
            return instance ?: synchronized(this) {
                instance ?: DownloadClient(context.applicationContext).also { instance = it }
            }
        }
    }
} 