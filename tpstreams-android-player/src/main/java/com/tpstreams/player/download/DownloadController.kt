package com.tpstreams.player.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.core.app.NotificationCompat
import com.tpstreams.player.R
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors


@UnstableApi
object DownloadController {
    private const val TAG = "DownloadController"
    const val DOWNLOAD_CONTENT_DIRECTORY = "downloads"
    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "tpstreams_download_channel"
    
    // Download stop reasons
    private const val STOP_REASON_NONE = 0
    private const val STOP_REASON_PAUSED = 1

    lateinit var downloadManager: DownloadManager
        private set
    
    lateinit var downloadCache: Cache
        private set
    
    lateinit var httpDataSourceFactory: DataSource.Factory
        private set
    
    lateinit var databaseProvider: DatabaseProvider
        private set
    
    private var isInitialized = false
    private lateinit var notificationHelper: DownloadNotificationHelper
    
    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return
        
        val appContext = context.applicationContext
        
        createNotificationChannel(appContext)
        
        databaseProvider = StandaloneDatabaseProvider(appContext)

        val downloadContentDirectory = File(appContext.getExternalFilesDir(null), DOWNLOAD_CONTENT_DIRECTORY)
        if (!downloadContentDirectory.exists()) {
            downloadContentDirectory.mkdirs()
        }
        
        downloadCache = SimpleCache(
            downloadContentDirectory,
            NoOpCacheEvictor(),
            databaseProvider
        )
        
        httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(Util.getUserAgent(appContext, "TPStreams Player"))
        
        notificationHelper = DownloadNotificationHelper(
            appContext,
            DOWNLOAD_NOTIFICATION_CHANNEL_ID
        )
        
        downloadManager = DownloadManager(
            appContext,
            databaseProvider,
            downloadCache,
            DefaultDataSource.Factory(appContext, httpDataSourceFactory),
            Executors.newFixedThreadPool(6)
        ).apply {
            maxParallelDownloads = 3
        }
        
        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                Log.d(TAG, "Download state changed: ${getStateString(download.state)}")
            }
        })
        
        isInitialized = true
        Log.d(TAG, "Download manager initialized")
    }
    
    fun getStateString(state: Int): String {
        return when (state) {
            Download.STATE_DOWNLOADING -> "Downloading"
            Download.STATE_COMPLETED -> "Completed"
            Download.STATE_FAILED -> "Failed"
            Download.STATE_REMOVING -> "Removing"
            Download.STATE_RESTARTING -> "Restarting"
            Download.STATE_STOPPED -> "Paused"
            else -> "Unknown"
        }
    }
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.download_notification_channel_name)
            val description = context.getString(R.string.download_notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(
                DOWNLOAD_NOTIFICATION_CHANNEL_ID, 
                name, 
                importance
            ).apply {
                this.description = description
                enableVibration(true)
                enableLights(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "Notification channel created")
        }
    }
    
    fun getDownloadManager(context: Context): DownloadManager {
        if (!isInitialized) {
            initialize(context)
        }
        return downloadManager
    }
    
    fun getDataSourceFactory(context: Context): DataSource.Factory {
        if (!isInitialized) {
            initialize(context)
        }
        return DefaultDataSource.Factory(context, httpDataSourceFactory)
    }
    
    fun startDownload(context: Context, mediaItem: MediaItem, resolution: String) {
        Log.d(TAG, "Preparing download for: ${mediaItem.mediaId}")
        
        val helper = DownloadHelper.forMediaItem(
            context,
            mediaItem,
            null,
            getDataSourceFactory(context)
        )
        
        helper.prepare(object : DownloadHelper.Callback {
            override fun onPrepared(helper: DownloadHelper) {
                Log.d(TAG, "Download prepared for: ${mediaItem.mediaId}")
                val request = helper.getDownloadRequest(mediaItem.mediaId.toByteArray())
                
                TPSDownloadService.sendDownload(context, request, true)
                Log.d(TAG, "Download started for: ${mediaItem.mediaId}, resolution: $resolution")
            }
            
            override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                Log.e(TAG, "Error preparing download: ${e.message}", e)
            }
        })
    }
    
    fun pauseDownload(context: Context, id: String) {
        Log.d(TAG, "Pausing download: $id")
        if (isInitialized) {
            downloadManager.setStopReason(id, STOP_REASON_PAUSED)
        }
        TPSDownloadService.pauseDownloads(context)
    }
    
    fun resumeDownload(context: Context, id: String) {
        Log.d(TAG, "Resuming download: $id")
        if (isInitialized) {
            downloadManager.setStopReason(id, STOP_REASON_NONE)
        }
        TPSDownloadService.resumeDownloads(context)
    }
    
    fun removeDownload(context: Context, id: String) {
        Log.d(TAG, "Removing download: $id")
        TPSDownloadService.removeDownload(context, id)
    }
    
    fun removeAllDownloads(context: Context) {
        Log.d(TAG, "Removing all downloads")
        TPSDownloadService.removeAllDownloads(context)
    }
    
    @Synchronized
    fun releaseResources() {
        if (!isInitialized) return
        
        downloadManager.release()
        downloadCache.release()
        
        isInitialized = false
        Log.d(TAG, "Download resources released")
    }
} 