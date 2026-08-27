package com.tpstreams.player.download

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.tpstreams.player.drm.DrmHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles offline download playback setup and player refreshment when switching to downloaded content.
 */
@OptIn(UnstableApi::class)
internal class DownloadPlaybackHandler(
    private val context: Context,
    private val exoPlayer: ExoPlayer,
    private val playerScope: CoroutineScope,
    private val drmHandler: DrmHandler,
    private val onMediaPrepared: () -> Unit,
    private val shouldPlayOnPrepared: () -> Boolean,
    private val logDebug: (String) -> Unit,
) {

    fun playFromDownload(assetId: String): Boolean {
        return try {
            val downloadClient = DownloadClient.getInstance(context)
            val download = downloadClient.getDownload(assetId)

            if (download != null) {
                Log.d(TAG, "Found downloaded content for $assetId, using local version")

                val downloadedMediaItem = DownloadController.buildMediaItemFromDownload(download)
                if (downloadedMediaItem == null) {
                    Log.e(TAG, "Failed to build media item from download for $assetId")
                    return false
                }

                playerScope.launch(Dispatchers.Main) {
                    val isDrm = download.request.keySetId != null
                    val audioAttributes = buildAudioAttributes(isDrm)

                    exoPlayer.setAudioAttributes(audioAttributes, true)
                    drmHandler.resetFallbackAttempt()
                    exoPlayer.setMediaItem(downloadedMediaItem)
                    exoPlayer.prepare()
                    onMediaPrepared()

                    if (shouldPlayOnPrepared()) {
                        exoPlayer.play()
                    }
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for downloads: ${e.message}", e)
            false
        }
    }

    fun refreshPlaybackWithDownloadMediaItem(mediaItem: MediaItem) {
        playerScope.launch(Dispatchers.Main) {
            val isDrm = mediaItem.localConfiguration?.drmConfiguration != null
            val audioAttributes = buildAudioAttributes(isDrm)

            val currentPosition = exoPlayer.currentPosition
            exoPlayer.stop()
            exoPlayer.setAudioAttributes(audioAttributes, true)
            exoPlayer.clearMediaItems()

            logDebug("MediaItem SET (Download) - ${mediaItem.mediaId}")
            drmHandler.resetFallbackAttempt()
            exoPlayer.setMediaItem(mediaItem)
            logDebug("Player PREPARE")
            exoPlayer.prepare()
            val duration = exoPlayer.duration
            if ((currentPosition > 0) && (duration == C.TIME_UNSET || currentPosition < duration)) {
                exoPlayer.seekTo(currentPosition)
            }
            exoPlayer.play()
        }
    }

    fun buildAudioAttributes(isDrm: Boolean): AudioAttributes {
        val capturePolicy = if (isDrm) {
            C.ALLOW_CAPTURE_BY_NONE
        } else {
            C.ALLOW_CAPTURE_BY_ALL
        }

        return AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setAllowedCapturePolicy(capturePolicy)
            .build()
    }

    private companion object {
        private const val TAG = "DownloadPlaybackHandler"
    }
}
