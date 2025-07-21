package com.tpstreams.player

import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import com.tpstreams.player.download.DownloadPermissionHandler
import com.tpstreams.player.download.DownloadClient
import androidx.media3.common.C

class DownloadActions(private val view: TPStreamsPlayerView) {
    companion object {
        private const val TAG = "DownloadActions"
    }

    fun onDownloadSelected() {
        val tpsPlayer = view.getPlayer() ?: return
        val mediaItem = tpsPlayer.currentMediaItem ?: return
        val assetId = mediaItem.mediaId
        
        val activity = view.getActivity() ?: return
        val downloadClient = DownloadClient.getInstance(view.context)
        
        when {
            downloadClient.isDownloaded(assetId) -> {
                view.downloadActionBottomSheet.setDownloadAssetId(assetId)
                view.downloadActionBottomSheet.setDownloadState(Download.STATE_COMPLETED)
                view.downloadActionBottomSheet.show(activity.supportFragmentManager)
            }
            downloadClient.isDownloading(assetId) -> {
                view.downloadActionBottomSheet.setDownloadAssetId(assetId)
                view.downloadActionBottomSheet.setDownloadState(Download.STATE_DOWNLOADING)
                view.downloadActionBottomSheet.show(activity.supportFragmentManager)
            }
            downloadClient.isPaused(assetId) -> {
                view.downloadActionBottomSheet.setDownloadAssetId(assetId)
                view.downloadActionBottomSheet.setDownloadState(Download.STATE_STOPPED)
                view.downloadActionBottomSheet.show(activity.supportFragmentManager)
            }
            else -> {
                view.downloadOptionsBottomSheet.setDownloadSelectionListener(view)
                
                // Get available resolutions
                val availableHeights = tpsPlayer.getAvailableVideoResolutions()
                val resolutionStrings = availableHeights.map { "${it}p" }
                view.downloadOptionsBottomSheet.setAvailableResolutions(resolutionStrings)
                view.downloadOptionsBottomSheet.setMediaItem(mediaItem, tpsPlayer.duration)
                view.downloadOptionsBottomSheet.show(activity.supportFragmentManager)
                
                val trackBitrates = tpsPlayer.getResolutionBitrates()
                view.downloadOptionsBottomSheet.setTrackBitrates(trackBitrates)
            }
        }
    }

    fun onDownloadResolutionSelected(resolution: String) {
        Log.d(TAG, "Download requested for resolution: $resolution")
        
        val tpsPlayer = view.getPlayer() ?: return
        val mediaItem = tpsPlayer.currentMediaItem ?: return
        
        val activity = view.getActivity()
        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!DownloadPermissionHandler.hasNotificationPermission(view.context)) {
                DownloadPermissionHandler.requestNotificationPermission(activity)
                return
            }
        }
        startDownload(mediaItem, resolution)
    }
    
    private fun startDownload(mediaItem: MediaItem, resolution: String) {
        val downloadClient = DownloadClient.getInstance(view.context)
        val tpsPlayer = view.getPlayer() ?: return
        val assetId = mediaItem.mediaId
        
        showToast("Preparing download...", false)
        
        tpsPlayer.getAccessTokenForDownload(assetId) { token ->
            if (token.isEmpty()) {
                showToast("Failed to authorize download", true)
                return@getAccessTokenForDownload
            }
            
            val updatedMediaItem = createMediaItemWithToken(mediaItem, token)
            downloadClient.startDownload(updatedMediaItem, resolution)
            showToast("Starting download for $resolution", false)
        }
    }
    
    private fun createMediaItemWithToken(mediaItem: MediaItem, token: String): MediaItem {
        val oldDrmConfig = mediaItem.localConfiguration?.drmConfiguration ?: return mediaItem
        
        val licenseUri = oldDrmConfig.licenseUri ?: return mediaItem
        val updatedUri = licenseUri.buildUpon()
            .clearQuery()
            .appendQueryParameter("access_token", token)
            .build()
        
        val newDrmConfig = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
            .setLicenseUri(updatedUri)
            .setLicenseRequestHeaders(mapOf("Authorization" to "Bearer $token"))
            .setMultiSession(true)
            .build()
        
        return mediaItem.buildUpon()
            .setDrmConfiguration(newDrmConfig)
            .build()
    }
    
    private fun showToast(message: String, isLong: Boolean) {
        Toast.makeText(
            view.context,
            message,
            if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        ).show()
    }

    fun deleteCurrentDownload() {
        val tpsPlayer = view.getPlayer() ?: return
        val mediaItem = tpsPlayer.currentMediaItem ?: return
        val assetId = mediaItem.mediaId
        
        DownloadClient.getInstance(view.context).removeDownload(assetId)
    }
    
    fun pauseCurrentDownload() {
        val tpsPlayer = view.getPlayer() ?: return
        val mediaItem = tpsPlayer.currentMediaItem ?: return
        val assetId = mediaItem.mediaId
        
        DownloadClient.getInstance(view.context).pauseDownload(assetId)
    }
    
    fun resumeCurrentDownload() {
        val tpsPlayer = view.getPlayer() ?: return
        val mediaItem = tpsPlayer.currentMediaItem ?: return
        val assetId = mediaItem.mediaId
        
        DownloadClient.getInstance(view.context).resumeDownload(assetId)
    }

    fun getCurrentDownloadStatus(): String {
        val tpsPlayer = view.getPlayer() ?: return "Download"
        val mediaItem = tpsPlayer.currentMediaItem ?: return "Download"
        val assetId = mediaItem.mediaId
        
        val downloadClient = DownloadClient.getInstance(view.context)
        return when {
            downloadClient.isDownloaded(assetId) -> "Downloaded"
            downloadClient.isDownloading(assetId) -> "Downloading"
            downloadClient.isPaused(assetId) -> "Downloading Paused"
            else -> "Download"
        }
    }
    
    fun getDownloadIcon(): Int {
        val tpsPlayer = view.getPlayer() ?: return R.drawable.ic_download
        val mediaItem = tpsPlayer.currentMediaItem ?: return R.drawable.ic_download
        val assetId = mediaItem.mediaId
        
        val downloadClient = DownloadClient.getInstance(view.context)
        return when {
            downloadClient.isDownloaded(assetId) -> R.drawable.ic_download_done
            downloadClient.isDownloading(assetId) -> R.drawable.ic_download_progress
            downloadClient.isPaused(assetId) -> R.drawable.ic_download_progress
            else -> R.drawable.ic_download
        }
    }
} 