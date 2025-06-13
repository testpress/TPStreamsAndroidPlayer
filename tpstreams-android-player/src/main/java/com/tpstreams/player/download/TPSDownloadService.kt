package com.tpstreams.player.download

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.tpstreams.player.R

/**
 * Service for downloading video content in the background
 */
@UnstableApi
class TPSDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DownloadController.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.download_notification_channel_name,
    0
) {
    companion object {
        private const val TAG = "TPSDownloadService"
        private const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val JOB_ID = 1002
        private const val FOREGROUND_NOTIFICATION_UPDATE_INTERVAL = 1000L
        
        private var notificationHelper: DownloadNotificationHelper? = null
        
        fun sendDownload(context: Context, downloadRequest: DownloadRequest, foreground: Boolean = true) {
            Log.d(TAG, "Sending download request: ${downloadRequest.id}, foreground: $foreground")
            sendAddDownload(
                context,
                TPSDownloadService::class.java,
                downloadRequest,
                foreground
            )
        }
            
        fun removeDownload(context: Context, id: String) {
            Log.d(TAG, "Removing download: $id")
            sendRemoveDownload(
                context,
                TPSDownloadService::class.java,
                id,
                true
            )
        }
        
        fun removeAllDownloads(context: Context) {
            Log.d(TAG, "Removing all downloads")
            sendRemoveAllDownloads(
                context,
                TPSDownloadService::class.java,
                true
            )
        }
        
        fun pauseDownloads(context: Context) {
            Log.d(TAG, "Pausing all downloads")
            sendPauseDownloads(
                context,
                TPSDownloadService::class.java,
                true
            )
        }
        
        fun resumeDownloads(context: Context) {
            Log.d(TAG, "Resuming all downloads")
            sendResumeDownloads(
                context,
                TPSDownloadService::class.java,
                true
            )
        }
        
        fun calculateProgressPercent(download: Download): Int {
            return if (download.contentLength > 0) {
                (download.bytesDownloaded * 100 / download.contentLength).toInt()
            } else if (download.state == Download.STATE_COMPLETED) {
                100 // If completed but no content length, it's 100%
            } else {
                0 // Otherwise default to 0
            }
        }
    }
    
    override fun onCreate() {
        Log.d(TAG, "Download service onCreate()")
        if (notificationHelper == null) {
            notificationHelper = DownloadNotificationHelper(
                this,
                DownloadController.DOWNLOAD_NOTIFICATION_CHANNEL_ID
            )
            Log.d(TAG, "Notification helper initialized")
        }
        super.onCreate()
        Log.d(TAG, "Download service created")
    }

    override fun getDownloadManager(): DownloadManager {
        Log.d(TAG, "Getting download manager from DownloadController")
        return DownloadController.getDownloadManager(this)
    }
    
    override fun getScheduler(): Scheduler? {
        Log.d(TAG, "Getting scheduler")
        return PlatformScheduler(this, JOB_ID)
    }
    
    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int
    ): Notification {
        return notificationHelper!!.buildProgressNotification(
            this,
            R.drawable.ic_download,
            null,
            null,
            downloads,
            notMetRequirements
        )
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Download service onDestroy()")
        super.onDestroy()
    }
} 