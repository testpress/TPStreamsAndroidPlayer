package com.tpstreams.player.analytics

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.Player
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

class TPStreamsAnalyticsTracker(
    context: Context,
    val orgId: String,
    val assetId: String,
    val accessToken: String? = null,
    val visitorId: String = AnalyticsIdManager.getOrCreateVisitorId(context.applicationContext),
    val sessionId: String = AnalyticsIdManager.generateSessionId(),
    val userAgent: String = AnalyticsUserAgent.build(context.applicationContext),
    scope: CoroutineScope? = null,
    private val heartbeatIntervalMs: Long = DEFAULT_HEARTBEAT_INTERVAL_MS,
    private val timeProvider: () -> Long = { SystemClock.elapsedRealtime() },
    customUploader: AnalyticsUploader? = null
) {
    companion object {
        const val DEFAULT_HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
        private const val TAG = "TPStreamsAnalytics"
        private val sharedHttpClient = OkHttpClient()
    }

    val uploader: AnalyticsUploader = customUploader ?: PresignedUrlAnalyticsUploader(
        orgId = orgId,
        assetId = assetId,
        visitorId = visitorId,
        sessionId = sessionId,
        accessToken = accessToken,
        userAgent = userAgent,
        client = sharedHttpClient
    )

    private val lock = Any()
    private val ownsScope = scope == null
    private val trackerScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uploadMutex = Mutex()
    private val initializedDeferred = CompletableDeferred<Unit>()
    private var heartbeatJob: Job? = null
    private var highestUploadedDurationSeconds = -1L

    private var cumulativeDurationMs: Long = 0L
    private var lastPlayingStartTimeMs: Long = 0L
    var isPlaying: Boolean = false
        private set

    val cumulativeDurationSeconds: Long
        get() = synchronized(lock) {
            val additionalMs = if (isPlaying && lastPlayingStartTimeMs > 0L) {
                timeProvider() - lastPlayingStartTimeMs
            } else {
                0L
            }
            (cumulativeDurationMs + additionalMs) / 1000L
        }

    fun start() {
        trackerScope.launch {
            try {
                uploader.start()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing analytics", e)
            } finally {
                initializedDeferred.complete(Unit)
            }
        }
    }

    fun onIsPlayingChanged(playing: Boolean) {
        synchronized(lock) {
            if (playing) {
                if (!isPlaying) {
                    isPlaying = true
                    lastPlayingStartTimeMs = timeProvider()
                    startHeartbeat()
                }
            } else {
                if (isPlaying) {
                    stopPlayingTimerInternal()
                    triggerUpload()
                }
            }
        }
    }

    /** Flushes immediately when the user explicitly pauses, without treating buffering as a pause. */
    fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
        if (!playWhenReady) {
            synchronized(lock) {
                if (isPlaying) {
                    stopPlayingTimerInternal()
                }
                triggerUpload()
            }
        }
    }

    fun onPlaybackStateChanged(state: Int) {
        when (state) {
            Player.STATE_BUFFERING, Player.STATE_IDLE -> {
                synchronized(lock) {
                    if (isPlaying) {
                        stopPlayingTimerInternal()
                        triggerUpload()
                    }
                }
            }
            Player.STATE_ENDED -> {
                synchronized(lock) {
                    if (isPlaying) {
                        stopPlayingTimerInternal()
                    }
                    triggerUpload()
                }
            }
        }
    }

    fun onSeek() {
        synchronized(lock) {
            if (isPlaying) {
                val now = timeProvider()
                cumulativeDurationMs += (now - lastPlayingStartTimeMs)
                lastPlayingStartTimeMs = now
            }
            triggerUpload()
        }
    }

    fun onAppBackgrounded() {
        synchronized(lock) {
            if (isPlaying) {
                stopPlayingTimerInternal()
            }
            triggerUpload()
        }
    }

    fun onRelease() {
        synchronized(lock) {
            if (isPlaying) {
                stopPlayingTimerInternal()
            }
            stopHeartbeat()
        }
        val duration = cumulativeDurationSeconds
        trackerScope.launch(NonCancellable) {
            initializedDeferred.await()
            uploadIfNewer(duration)
            if (ownsScope) trackerScope.cancel()
        }
    }

    private fun stopPlayingTimerInternal() {
        val now = timeProvider()
        if (lastPlayingStartTimeMs > 0L) {
            cumulativeDurationMs += (now - lastPlayingStartTimeMs)
            lastPlayingStartTimeMs = 0L
        }
        isPlaying = false
        stopHeartbeat()
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = trackerScope.launch {
            while (isActive) {
                delay(heartbeatIntervalMs)
                synchronized(lock) {
                    if (isPlaying && lastPlayingStartTimeMs > 0L) {
                        val now = timeProvider()
                        cumulativeDurationMs += (now - lastPlayingStartTimeMs)
                        lastPlayingStartTimeMs = now
                    }
                }
                triggerUpload()
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun triggerUpload() {
        val duration = cumulativeDurationSeconds
        trackerScope.launch {
            initializedDeferred.await()
            uploadIfNewer(duration)
        }
    }

    private suspend fun uploadIfNewer(durationSeconds: Long) {
        if (durationSeconds <= 0L) {
            return
        }

        uploadMutex.withLock {
            if (durationSeconds <= highestUploadedDurationSeconds) {
                return
            }
            val uploaded = try {
                uploader.upload(durationSeconds)
            } catch (_: Exception) {
                false
            }
            if (uploaded) {
                highestUploadedDurationSeconds = durationSeconds
            }
        }
    }
}
