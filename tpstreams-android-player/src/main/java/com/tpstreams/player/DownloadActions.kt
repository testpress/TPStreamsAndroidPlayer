package com.tpstreams.player

import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import com.tpstreams.player.download.DownloadPermissionHandler
import com.tpstreams.player.download.DownloadClient
import androidx.media3.common.C
import com.tpstreams.player.download.DownloadConstants

@UnstableApi
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
        
        val activity = view.getActivity() ?: return
        
        if (activity is FragmentActivity) {
            DownloadPermissionHandler.requestPermissionIfNeeded(activity) {
                startDownload(mediaItem, resolution)
            }
        } else {
            // Fallback for non-FragmentActivity (should be rare)
            startDownload(mediaItem, resolution)
        }
    }
    
    private fun startDownload(mediaItem: MediaItem, resolution: String) {
        val downloadClient = DownloadClient.getInstance(view.context)
        val tpsPlayer = view.getPlayer() ?: return
        val assetId = tpsPlayer.assetId
        val metadata = tpsPlayer.downloadMetadata ?: emptyMap()
        val offlineLicenseExpireTime = tpsPlayer.offlineLicenseExpireTime
        
        val trackBitrates = tpsPlayer.getResolutionBitrates()
        val durationMs = tpsPlayer.duration

        val totalSize = getDownloadSize(trackBitrates, resolution, durationMs)

        tpsPlayer.isTokenValid(assetId) { isValid ->
            if (!isValid) {
                tpsPlayer.getNewToken(assetId) { token ->
                    if (token.isEmpty()) {
                        showToast("Failed to authorize download", true)
                        return@getNewToken
                    }
    
                    val updatedMediaItem = mediaItem.updateMediaItemDrmConfig(token)
                    downloadClient.startDownload(updatedMediaItem, resolution, metadata, totalSize, offlineLicenseExpireTime)
                    showToast("Starting download for $resolution", false)
                }
                return@isTokenValid
            }
    
            downloadClient.startDownload(mediaItem, resolution, metadata, totalSize, offlineLicenseExpireTime)
            showToast("Starting download for $resolution", false)
        }
    }
    
    private fun MediaItem.updateMediaItemDrmConfig(token: String): MediaItem {
        val drm = localConfiguration?.drmConfiguration ?: return this
        val uri = drm.licenseUri ?: return this
    
        val newUri = uri.buildUpon()
            .clearQuery()
            .appendQueryParameter("access_token", token)
            .build()
    
        val newDrm = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
            .setLicenseUri(newUri)
            .setLicenseRequestHeaders(mapOf("Authorization" to "Bearer $token"))
            .setMultiSession(true)
            .build()
    
        return buildUpon().setDrmConfiguration(newDrm).build()
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
        showToast("Download deleted", false)
    }
    
    fun pauseCurrentDownload() {
        val tpsPlayer = view.getPlayer() ?: return
        val mediaItem = tpsPlayer.currentMediaItem ?: return
        val assetId = mediaItem.mediaId
        
        DownloadClient.getInstance(view.context).pauseDownload(assetId)
        showToast("Download paused", false)
    }
    
    fun resumeCurrentDownload() {
        val tpsPlayer = view.getPlayer() ?: return
        val mediaItem = tpsPlayer.currentMediaItem ?: return
        val assetId = mediaItem.mediaId
        
        DownloadClient.getInstance(view.context).resumeDownload(assetId)
        showToast("Download resumed", false)
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

    private fun getDownloadSize(trackBitrates: Map<String, Int>, resolution: String, durationMs: Long): Long {
        val bitrate = trackBitrates[resolution] ?: return 0
        if (bitrate <= 0 || durationMs <= 0) return 0

        val durationSeconds = durationMs / 1000.0
        val sizeBytes = (bitrate.toLong() * durationSeconds / 8.0).toLong()
        return sizeBytes
    }
} 