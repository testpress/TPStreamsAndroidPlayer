package com.tpstreams.player.offline

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tpstreams.player.R

private const val TAG = "TPStreamsDownloadNotification"

class DownloadNotificationHelper(private val context: Context) {
    
    init {
        createNotificationChannel()
    }
    
    @SuppressLint("LongLogTag")
    fun buildProgressNotification(
        contentId: String,
        progress: Float,
        smallIcon: Int = R.drawable.ic_download
    ): Notification {
        Log.d(TAG, "Building progress notification for $contentId: ${progress.toInt()}%")
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle("Downloading video")
            .setContentText("$contentId: ${progress.toInt()}%")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(getContentIntent())
        
        if (progress > 0) {
            builder.setProgress(100, progress.toInt(), false)
        } else {
            builder.setProgress(100, 0, true)
        }
        
        return builder.build()
    }
    
    fun buildCompletedNotification(
        contentId: String,
        smallIcon: Int = R.drawable.ic_download
    ): Notification {
        Log.d(TAG, "Building completed notification for $contentId")
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle("Download complete")
            .setContentText(contentId)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(getContentIntent())
            .build()
    }
    
    fun buildFailedNotification(
        contentId: String,
        smallIcon: Int = R.drawable.ic_download
    ): Notification {
        Log.d(TAG, "Building failed notification for $contentId")
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle("Download failed")
            .setContentText("Failed to download $contentId")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(getContentIntent())
            .build()
    }
    
    private fun getContentIntent(): PendingIntent {
        // Create a generic intent that will be handled by the app's main activity
        val intent = Intent().apply {
            setPackage(context.packageName)
            action = "com.tpstreams.player.action.OPEN_DOWNLOADS"
        }
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        return PendingIntent.getActivity(context, 0, intent, flags)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.download_channel_name)
            val description = context.getString(R.string.download_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                this.description = description
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }
    
    companion object {
        const val CHANNEL_ID = VideoDownloadManager.DOWNLOAD_NOTIFICATION_CHANNEL_ID
        const val NOTIFICATION_ID = 1
        const val COMPLETION_NOTIFICATION_ID = 2
    }
} 