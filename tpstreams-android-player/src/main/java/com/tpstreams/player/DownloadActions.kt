package com.tpstreams.player

import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import com.tpstreams.player.download.DownloadPermissionHandler
import com.tpstreams.player.download.DownloadTracker

class DownloadActions(private val view: TPStreamsPlayerView) {
    companion object {
        private const val TAG = "DownloadActions"
    }

    fun onDownloadSelected() {
        val tpsPlayer = view.getPlayer() ?: return
        val mediaItem = tpsPlayer.currentMediaItem ?: return
        val uri = mediaItem.localConfiguration?.uri ?: return
        
        val activity = view.getActivity() ?: return
        val downloadTracker = DownloadTracker.getInstance(view.context)
        
        if (uri != null) {
            when {
                downloadTracker.isDownloaded(uri) -> {
                    view.downloadActionBottomSheet.setDownloadUri(uri)
                    view.downloadActionBottomSheet.setDownloadState(Download.STATE_COMPLETED)
                    view.downloadActionBottomSheet.show(activity.supportFragmentManager)
                }
                downloadTracker.isDownloading(uri) -> {
                    view.downloadActionBottomSheet.setDownloadUri(uri)
                    view.downloadActionBottomSheet.setDownloadState(Download.STATE_DOWNLOADING)
                    view.downloadActionBottomSheet.show(activity.supportFragmentManager)
                }
                downloadTracker.isPaused(uri) -> {
                    view.downloadActionBottomSheet.setDownloadUri(uri)
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
                    
                    val trackBitrates = tpsPlayer.getVideoTrackBitrates()
                    view.downloadOptionsBottomSheet.setTrackBitrates(trackBitrates)
                }
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
        val downloadTracker = DownloadTracker.getInstance(view.context)
        downloadTracker.startDownload(mediaItem, resolution)
        
        Toast.makeText(
            view.context,
            "Starting download for $resolution",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun deleteCurrentDownload() {
        val tpsPlayer = view.getPlayer() ?: return
        val mediaItem = tpsPlayer.currentMediaItem ?: return
        val uri = mediaItem.localConfiguration?.uri ?: return
        
        DownloadTracker.getInstance(view.context).removeDownload(uri)
    }
    
    fun pauseCurrentDownload() {
        val tpsPlayer = view.getPlayer() ?: return
        val mediaItem = tpsPlayer.currentMediaItem ?: return
        val uri = mediaItem.localConfiguration?.uri ?: return
        
        DownloadTracker.getInstance(view.context).pauseDownload(uri)
    }
    
    fun resumeCurrentDownload() {
        val tpsPlayer = view.getPlayer() ?: return
        val mediaItem = tpsPlayer.currentMediaItem ?: return
        val uri = mediaItem.localConfiguration?.uri ?: return
        
        DownloadTracker.getInstance(view.context).resumeDownload(uri)
    }

    fun getCurrentDownloadStatus(): String {
        val tpsPlayer = view.getPlayer() ?: return "Download"
        val mediaItem = tpsPlayer.currentMediaItem ?: return "Download"
        val uri = mediaItem.localConfiguration?.uri ?: return "Download"
        
        val downloadTracker = DownloadTracker.getInstance(view.context)
        return when {
            downloadTracker.isDownloaded(uri) -> "Downloaded"
            downloadTracker.isDownloading(uri) -> "Downloading"
            downloadTracker.isPaused(uri) -> "Paused"
            else -> "Download"
        }
    }
    
    fun getDownloadIcon(): Int {
        val tpsPlayer = view.getPlayer() ?: return R.drawable.ic_download
        val mediaItem = tpsPlayer.currentMediaItem ?: return R.drawable.ic_download
        val uri = mediaItem.localConfiguration?.uri ?: return R.drawable.ic_download
        
        val downloadTracker = DownloadTracker.getInstance(view.context)
        return when {
            downloadTracker.isDownloaded(uri) -> R.drawable.ic_download_done
            downloadTracker.isDownloading(uri) -> R.drawable.ic_download_progress
            downloadTracker.isPaused(uri) -> R.drawable.ic_download_progress
            else -> R.drawable.ic_download
        }
    }
} 