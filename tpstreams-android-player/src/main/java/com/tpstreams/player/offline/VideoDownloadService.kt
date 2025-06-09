package com.tpstreams.player.offline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.DownloadService
import com.tpstreams.player.R
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "TPStreamsDownloadService"

@UnstableApi
class VideoDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    0
) {
    
    private lateinit var notificationManager: NotificationManager
    private val activeDownloads = ConcurrentHashMap<String, Float>()
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VideoDownloadService created")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }
    
    override fun getDownloadManager(): androidx.media3.exoplayer.offline.DownloadManager {
        Log.d(TAG, "Getting DownloadManager")
        return VideoDownloadManager.getDownloadManager(this)
    }
    
    override fun getScheduler(): androidx.media3.exoplayer.scheduler.Scheduler {
        Log.d(TAG, "Getting Scheduler")
        return VideoDownloadManager.getScheduler(this)
    }
    
    override fun getForegroundNotification(
        downloads: List<androidx.media3.exoplayer.offline.Download>,
        notMetRequirements: Int
    ): Notification {
        val downloadNotificationHelper = DownloadNotificationHelper(this)
        
        if (downloads.isEmpty()) {
            Log.d(TAG, "Creating notification for empty downloads list")
            return downloadNotificationHelper.buildProgressNotification(
                "Preparing download...",
                0f
            )
        }
        
        val download = downloads[0]
        val contentId = download.request.id
        val progress = download.percentDownloaded
        
        Log.d(TAG, "Creating notification for download: $contentId, state: ${download.state}, progress: $progress")
        
        return when (download.state) {
            androidx.media3.exoplayer.offline.Download.STATE_COMPLETED -> 
                downloadNotificationHelper.buildCompletedNotification(contentId)
            androidx.media3.exoplayer.offline.Download.STATE_FAILED -> {
                Log.e(TAG, "Download failed: $contentId")
                downloadNotificationHelper.buildFailedNotification(contentId)
            }
            else -> 
                downloadNotificationHelper.buildProgressNotification(contentId, progress)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.download_channel_name)
            val description = getString(R.string.download_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                this.description = description
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $CHANNEL_ID")
        }
    }
    
    override fun onDestroy() {
        Log.d(TAG, "VideoDownloadService destroyed")
        super.onDestroy()
    }
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val COMPLETION_NOTIFICATION_ID = 2
        private const val CHANNEL_ID = VideoDownloadManager.DOWNLOAD_NOTIFICATION_CHANNEL_ID
        private const val DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL = 1000L
        
        fun startDownload(context: Context, downloadRequest: androidx.media3.exoplayer.offline.DownloadRequest) {
            Log.d(TAG, "Starting download for: ${downloadRequest.id}, URI: ${downloadRequest.uri}")
            try {
                val intent = buildAddDownloadIntent(
                    context,
                    VideoDownloadService::class.java,
                    downloadRequest,
                    /* foreground= */ false
                )
                Util.startForegroundService(context, intent)
                Log.d(TAG, "Download service started")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting download service", e)
            }
        }
    }
} 