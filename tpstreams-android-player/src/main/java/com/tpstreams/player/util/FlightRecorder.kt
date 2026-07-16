package com.tpstreams.player.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.tpstreams.player.data.NetworkInfo
import io.sentry.Attachment
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object FlightRecorder {
    private const val TAG = "FlightRecorder"
    private const val MAX_EVENTS = 2000

    private val ringBuffer = ConcurrentLinkedDeque<String>()
    private val eventCount = AtomicInteger(0)
    private val overflowCount = AtomicInteger(0)
    
    private val hasSent = AtomicBoolean(false)
    private var sessionActive = AtomicBoolean(false)
    private var sessionId: String? = null
    
    private var sessionStartTimestamp = 0L
    private var firstFrameTimeMs = -1L
    private val rebuffers = AtomicInteger(0)
    private val errors = AtomicInteger(0)
    
    private var hasReachedReadyState = false
    
    // New Advanced Metrics
    private val totalBufferingTimeMs = AtomicInteger(0)
    private var lastBufferStartTime = -1L
    
    private val networkRequests = AtomicInteger(0)
    private val failedRequests = AtomicInteger(0)
    
    private val droppedFramesTotal = AtomicInteger(0)
    private val drmLicenseTimeMs = AtomicInteger(0)
    private var drmRequestStartTime = -1L
    
    private var highestResolution = 0
    private var lowestResolution = Int.MAX_VALUE
    private var averageBitrateSum = 0L
    private var averageBitrateCount = 0
    private var decoderName: String = "Unknown"

    private var lastConnectivity: NetworkInfo? = null

    // Currently-selected track format (updated by onDownstreamFormatChanged)
    private var selectedVideoHeight: Int = 0
    private var selectedVideoWidth: Int = 0
    private var selectedVideoBitrate: Long = 0L
    private var selectedVideoCodec: String = "unknown"
    private var selectedAudioBitrate: Long = 0L
    private var selectedAudioCodec: String = "unknown"
    private var selectedAudioLanguage: String = "unknown"

    // #13 Sequential event IDs
    private val eventSequenceNumber = AtomicInteger(0)

    // #1 Enhanced decoder tracking
    private var audioDecoderName: String = "Unknown"
    private var videoDecoderIsHardware: Boolean? = null
    private var audioDecoderIsHardware: Boolean? = null
    private var videoDecoderIsSecure: Boolean = false
    private var audioDecoderIsSecure: Boolean = false

    // #6 Seek tracking
    private val seekCount = AtomicInteger(0)
    private var lastSeekFromPosition = 0L
    private var lastSeekToPosition = 0L

    // #3 DRM timing breakdown
    private var drmSessionAcquiredTimeMs = -1L
    private var drmKeysLoadedTimeMs = -1L
    private var drmTotalSessionTimeMs = AtomicInteger(0)

    // #11 Cache / bytes tracking
    private val totalBytesDownloaded = AtomicLong(0)
    private val cacheHits = AtomicInteger(0)
    private val cacheMisses = AtomicInteger(0)

    var positionProvider: (() -> Pair<Long, Long>)? = null
    
    @Synchronized
    fun startSession(sessionId: String) {
        this.sessionId = sessionId
        this.sessionActive.set(true)
        this.sessionStartTimestamp = SystemClock.elapsedRealtime()
        this.firstFrameTimeMs = -1L
        this.rebuffers.set(0)
        this.errors.set(0)
        this.hasReachedReadyState = false
        this.totalBufferingTimeMs.set(0)
        this.lastBufferStartTime = -1L
        this.networkRequests.set(0)
        this.failedRequests.set(0)
        this.droppedFramesTotal.set(0)
        this.drmLicenseTimeMs.set(0)
        this.drmRequestStartTime = -1L
        this.highestResolution = 0
        this.lowestResolution = Int.MAX_VALUE
        this.averageBitrateSum = 0L
        this.averageBitrateCount = 0
        this.decoderName = "Unknown"
        this.eventSequenceNumber.set(0)
        this.audioDecoderName = "Unknown"
        this.videoDecoderIsHardware = null
        this.audioDecoderIsHardware = null
        this.videoDecoderIsSecure = false
        this.audioDecoderIsSecure = false
        this.seekCount.set(0)
        this.lastSeekFromPosition = 0L
        this.lastSeekToPosition = 0L
        this.drmSessionAcquiredTimeMs = -1L
        this.drmKeysLoadedTimeMs = -1L
        this.drmTotalSessionTimeMs.set(0)
        this.totalBytesDownloaded.set(0)
        this.cacheHits.set(0)
        this.cacheMisses.set(0)
        this.selectedVideoHeight = 0
        this.selectedVideoWidth = 0
        this.selectedVideoBitrate = 0L
        this.selectedVideoCodec = "unknown"
        this.selectedAudioBitrate = 0L
        this.selectedAudioCodec = "unknown"
        this.selectedAudioLanguage = "unknown"
        log("SESSION", "started", mapOf("sessionId" to sessionId, "timestamp" to sessionStartTimestamp))
    }

    @Synchronized
    fun stopSession() {
        if (sessionActive.get()) {
            log("SESSION", "ended", emptyMap())
            // Auto-append session summary to ring buffer for Sentry attachment
            val summary = getAnalyticsSummary()
            ringBuffer.addLast("\n$summary")
            eventCount.incrementAndGet()
            sessionActive.set(false)
        }
    }

    fun log(stage: String, event: String, data: Map<String, Any> = emptyMap()) {
        val timestamp = SystemClock.elapsedRealtime()
        val elapsed = if (sessionStartTimestamp > 0) timestamp - sessionStartTimestamp else 0
        val thread = Thread.currentThread().name
        
        if (event.contains("error", ignoreCase = true) || stage.contains("error", ignoreCase = true)) {
            errors.incrementAndGet()
        }
        
        if (stage == "PLAYBACK_STATE") {
            if (event == "STATE_READY") {
                hasReachedReadyState = true
                if (lastBufferStartTime > 0) {
                    totalBufferingTimeMs.addAndGet((timestamp - lastBufferStartTime).toInt())
                    lastBufferStartTime = -1L
                }
            } else if (event == "STATE_BUFFERING") {
                if (hasReachedReadyState) {
                    rebuffers.incrementAndGet()
                }
                if (lastBufferStartTime == -1L) {
                    lastBufferStartTime = timestamp
                }
            }
        }
        
        // Track API/Network Calls
        if (stage == "NETWORK") {
            if (event == "request_started") {
                networkRequests.incrementAndGet()
            } else if (event == "onLoadError" || event == "request_failed") {
                failedRequests.incrementAndGet()
            }
        }
        
        // Track Track Selection
        if (event == "onTracksChanged" || event == "onDownstreamFormatChanged") {
            val height = data["height"] as? Int ?: 0
            if (height > 0) {
                if (height > highestResolution) highestResolution = height
                if (height < lowestResolution) lowestResolution = height
            }
            val bitrate = (data["bitrate"] as? Number)?.toLong() ?: 0L
            if (bitrate > 0) {
                averageBitrateSum += bitrate
                averageBitrateCount++
            }
        }
        
        // Track Decoder
        if (event == "onVideoDecoderInitialized") {
            (data["decoderName"] as? String)?.let { decoderName = it }
        }
        
        // Track Dropped Frames
        if (event == "onDroppedVideoFrames") {
            val dropped = data["droppedFrames"] as? Int ?: 0
            droppedFramesTotal.addAndGet(dropped)
        }
        
        // Track DRM Time
        if (event == "License request started") {
            drmRequestStartTime = timestamp
        } else if (event == "License response received" && drmRequestStartTime > 0) {
            drmLicenseTimeMs.addAndGet((timestamp - drmRequestStartTime).toInt())
            drmRequestStartTime = -1L
        }

        if (event == "onRenderedFirstFrame" && firstFrameTimeMs == -1L) {
            firstFrameTimeMs = elapsed
        }

        // #6 Track Seek events
        if (stage == "SEEK") {
            seekCount.incrementAndGet()
            lastSeekFromPosition = (data["from"] as? Number)?.toLong() ?: 0L
            lastSeekToPosition = (data["to"] as? Number)?.toLong() ?: 0L
        }

        // #3 Track DRM session timing
        if (stage == "DRM") {
            when (event) {
                "onDrmSessionAcquired" -> drmSessionAcquiredTimeMs = timestamp
                "onDrmKeysLoaded" -> {
                    if (drmSessionAcquiredTimeMs > 0) {
                        drmKeysLoadedTimeMs = timestamp
                        drmTotalSessionTimeMs.addAndGet((timestamp - drmSessionAcquiredTimeMs).toInt())
                    }
                }
            }
        }

        // #1 Track audio decoder init
        if (event == "onAudioDecoderInitialized") {
            (data["decoderName"] as? String)?.let { audioDecoderName = it }
            (data["isHardware"] as? Boolean)?.let { audioDecoderIsHardware = it }
            (data["isSecure"] as? Boolean)?.let { audioDecoderIsSecure = it }
        }

        // #1 Track video decoder properties
        if (event == "onVideoDecoderInitialized") {
            (data["isHardware"] as? Boolean)?.let { videoDecoderIsHardware = it }
            (data["isSecure"] as? Boolean)?.let { videoDecoderIsSecure = it }
        }

        // #11 Track bytes downloaded
        if (event == "response_received") {
            val bytes = (data["bytesRead"] as? Number)?.toLong() ?: 0L
            if (bytes > 0) totalBytesDownloaded.addAndGet(bytes)
        }

        // #11 Track cache events
        if (stage == "CACHE") {
            when (event) {
                "cache_hit" -> cacheHits.incrementAndGet()
                "cache_miss" -> cacheMisses.incrementAndGet()
            }
        }

        val finalData = data.toMutableMap()
        if (event == "onRenderedFirstFrame" && firstFrameTimeMs > 0) {
            finalData["startupTimeMs"] = firstFrameTimeMs
        }
        positionProvider?.invoke()?.let { (pos, buf) ->
            finalData["position"] = pos
            finalData["buffered"] = buf
        }
        val dataStr = if (finalData.isNotEmpty()) " " + finalData.entries.joinToString(", ") { "${it.key}=${it.value}" } else ""
        val eventNumber = eventSequenceNumber.incrementAndGet()
        val logLine = "#${String.format("%04d", eventNumber)} [$timestamp] [${elapsed}ms] [$thread] [$stage] [$event]$dataStr"
        
        ringBuffer.addLast(logLine)
        eventCount.incrementAndGet()
        
        while (eventCount.get() > MAX_EVENTS) {
            if (ringBuffer.pollFirst() != null) {
                eventCount.decrementAndGet()
                overflowCount.incrementAndGet()
            } else {
                break
            }
        }
        
        if (sessionActive.get()) {
            Log.d(TAG, logLine)
        }
    }

    fun getFullDump(): String {
        return ringBuffer.joinToString("\n")
    }

    fun getLastEvents(count: Int): List<String> {
        val list = ringBuffer.toList()
        return list.takeLast(count)
    }

    fun getTimelineSummary(): Map<String, Any> {
        return mapOf(
            "firstFrameTimeMs" to firstFrameTimeMs,
            "rebuffers" to rebuffers.get(),
            "errors" to errors.get()
        )
    }

    fun getStats(): Map<String, Any> {
        val duration = if (sessionStartTimestamp > 0) {
            SystemClock.elapsedRealtime() - sessionStartTimestamp
        } else 0
        return mapOf(
            "eventsRecorded" to eventCount.get(),
            "eventsDropped" to overflowCount.get(),
            "sessionDurationMs" to duration,
            "overflowCount" to overflowCount.get()
        )
    }
    
    fun getAnalyticsSummary(): String {
        val duration = if (sessionStartTimestamp > 0) SystemClock.elapsedRealtime() - sessionStartTimestamp else 0
        val avgBitrate = if (averageBitrateCount > 0) averageBitrateSum / averageBitrateCount else 0
        val conn = lastConnectivity

        return """
            === SESSION SUMMARY ===
            Duration: ${duration}ms
            Startup Time: ${if (firstFrameTimeMs > 0) "${firstFrameTimeMs}ms" else "N/A"}
            Rebuffers: ${rebuffers.get()}
            Total Buffering: ${totalBufferingTimeMs.get()}ms
            Seek Count: ${seekCount.get()}
            Selected Video: ${if (selectedVideoHeight > 0) "${selectedVideoWidth}x${selectedVideoHeight} (${selectedVideoBitrate / 1000}kbps, $selectedVideoCodec)" else "N/A"}
            Selected Audio: ${if (selectedAudioBitrate > 0) "${selectedAudioBitrate / 1000}kbps, $selectedAudioCodec, $selectedAudioLanguage" else "N/A"}
            Average Bitrate: ${avgBitrate / 1000} kbps
            Highest Resolution: ${if (highestResolution > 0) "${highestResolution}p" else "Unknown"}
            Lowest Resolution: ${if (lowestResolution < Int.MAX_VALUE) "${lowestResolution}p" else "Unknown"}
            Network Requests: ${networkRequests.get()}
            Failed Requests: ${failedRequests.get()}
            Bytes Downloaded: ${totalBytesDownloaded.get()}
            Cache Hits: ${cacheHits.get()}
            Cache Misses: ${cacheMisses.get()}
            Video Decoder: $decoderName (hw=${videoDecoderIsHardware ?: "unknown"}, secure=$videoDecoderIsSecure)
            Audio Decoder: $audioDecoderName (hw=${audioDecoderIsHardware ?: "unknown"}, secure=$audioDecoderIsSecure)
            Dropped Frames: ${droppedFramesTotal.get()}
            DRM License Time: ${if (drmLicenseTimeMs.get() > 0) "${drmLicenseTimeMs.get()}ms" else "N/A"}
            DRM Session Time: ${if (drmTotalSessionTimeMs.get() > 0) "${drmTotalSessionTimeMs.get()}ms" else "N/A"}
            Carrier: ${conn?.simOperator ?: conn?.operatorName ?: "N/A"}
            Signal: ${conn?.signalStrengthDbm?.let { "$it dBm (lvl ${conn?.signalLevel})" } ?: "N/A"}
            IPv4: ${conn?.ipv4 ?: "N/A"}
            IPv6: ${conn?.ipv6 ?: "N/A"}
            DNS: ${conn?.dnsServers ?: "N/A"}
            Playback Errors: ${errors.get()}
            Total Events: ${eventSequenceNumber.get()}
        """.trimIndent()
    }

    fun getSentryAttachment(): Attachment {
        val dump = getFullDump().toByteArray(StandardCharsets.UTF_8)
        return Attachment(dump, "flight_recorder.txt")
    }

    fun sendAndCheck(): Boolean {
        return hasSent.compareAndSet(false, true)
    }

    @Synchronized
    fun clear() {
        ringBuffer.clear()
        eventCount.set(0)
        overflowCount.set(0)
        hasSent.set(false)
        sessionActive.set(false)
        sessionStartTimestamp = 0L
        firstFrameTimeMs = -1L
        rebuffers.set(0)
        errors.set(0)
        hasReachedReadyState = false
        totalBufferingTimeMs.set(0)
        lastBufferStartTime = -1L
        networkRequests.set(0)
        failedRequests.set(0)
        droppedFramesTotal.set(0)
        drmLicenseTimeMs.set(0)
        drmRequestStartTime = -1L
        highestResolution = 0
        lowestResolution = Int.MAX_VALUE
        averageBitrateSum = 0L
        averageBitrateCount = 0
        decoderName = "Unknown"
        this.eventSequenceNumber.set(0)
        this.audioDecoderName = "Unknown"
        this.videoDecoderIsHardware = null
        this.audioDecoderIsHardware = null
        this.videoDecoderIsSecure = false
        this.audioDecoderIsSecure = false
        this.seekCount.set(0)
        this.lastSeekFromPosition = 0L
        this.lastSeekToPosition = 0L
        this.drmSessionAcquiredTimeMs = -1L
        this.drmKeysLoadedTimeMs = -1L
        this.drmTotalSessionTimeMs.set(0)
        this.totalBytesDownloaded.set(0)
        this.cacheHits.set(0)
        this.cacheMisses.set(0)
        this.selectedVideoHeight = 0
        this.selectedVideoWidth = 0
        this.selectedVideoBitrate = 0L
        this.selectedVideoCodec = "unknown"
        this.selectedAudioBitrate = 0L
        this.selectedAudioCodec = "unknown"
        this.selectedAudioLanguage = "unknown"
        sessionId = null
        lastConnectivity = null
    }
    
    fun isActive(): Boolean = sessionActive.get()
    
    fun getSessionId(): String? = sessionId

    // === Convenience Logging Methods ===

    // #1 Decoder initialization logging
    fun logDecoderInit(type: String, name: String, mimeType: String?, isHardware: Boolean, isSecure: Boolean, initDurationMs: Long) {
        log(
            stage = "DECODER",
            event = "decoder_initialized",
            data = mapOf(
                "type" to type,
                "decoderName" to name,
                "mimeType" to (mimeType ?: "unknown"),
                "isHardware" to isHardware,
                "isSecure" to isSecure,
                "initDurationMs" to initDurationMs
            )
        )
    }

    // #2 Complete player error logging
    fun logPlayerError(error: PlaybackExceptionWrapper) {
        val details = mutableMapOf<String, Any>(
            "errorCode" to error.errorCodeName,
            "errorCodeInt" to error.errorCode,
            "message" to (error.message ?: "none"),
            "cause" to (error.causeMessage ?: "none"),
            "recoverable" to error.recoverable,
            "isBehindLiveWindow" to error.isBehindLiveWindow
        )
        error.rendererIndex?.let { details["rendererIndex"] = it }
        error.rendererName?.let { details["rendererName"] = it }
        error.errorType?.let { details["type"] = it }
        if (error.stacktrace.isNotEmpty()) {
            details["stacktrace"] = error.stacktrace
        }
        log(stage = "ERROR", event = "PlaybackException", data = details)
    }

    // #3 DRM phase timing
    fun logDRMPhase(phase: String, data: Map<String, Any> = emptyMap()) {
        log(stage = "DRM", event = phase, data = data)
    }

    // #5 Surface lifecycle
    fun logSurfaceEvent(event: String, surfaceType: String, data: Map<String, Any> = emptyMap()) {
        val allData = mutableMapOf<String, Any>("surfaceType" to surfaceType)
        allData.putAll(data)
        log(stage = "SURFACE", event = event, data = allData)
    }

    // #6 Seek with position tracking
    fun logSeek(from: Long, to: Long) {
        log(stage = "SEEK", event = "seek_request", data = mapOf("from" to from, "to" to to))
    }

    fun logSeekCompleted(from: Long, to: Long) {
        log(stage = "SEEK", event = "seek_completed", data = mapOf("from" to from, "to" to to))
    }

    // #7 Audio focus
    fun logAudioFocus(event: String, data: Map<String, Any> = emptyMap()) {
        log(stage = "AUDIO_FOCUS", event = event, data = data)
    }

    // #8 App lifecycle
    fun logLifecycle(event: String, data: Map<String, Any> = emptyMap()) {
        log(stage = "LIFECYCLE", event = event, data = data)
    }

    // #9 Memory snapshot
    fun getMemorySnapshot(context: Context): Map<String, Any> {
        val runtime = Runtime.getRuntime()
        val heapUsed = runtime.totalMemory() - runtime.freeMemory()
        val heapMax = runtime.maxMemory()

        var nativeHeap = 0L
        var availableMemory = 0L
        var lowMemory = false

        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memInfo)
            nativeHeap = android.os.Debug.getNativeHeapAllocatedSize()
            availableMemory = memInfo.availMem
            lowMemory = memInfo.lowMemory
        } catch (_: Exception) {}

        return mapOf(
            "heapUsedMB" to (heapUsed / (1024 * 1024)),
            "heapMaxMB" to (heapMax / (1024 * 1024)),
            "nativeHeapMB" to (nativeHeap / (1024 * 1024)),
            "availableMemoryMB" to (availableMemory / (1024 * 1024)),
            "lowMemory" to lowMemory
        )
    }

    // #10 Device thermal/battery info
    fun getDeviceStatus(context: Context): Map<String, Any> {
        val result = mutableMapOf<String, Any>()

        try {
            val batteryFilter = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryFilter?.let { intent ->
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (scale > 0) (level * 100 / scale) else -1
                result["batteryPct"] = batteryPct

                val charging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                result["charging"] = (charging == BatteryManager.BATTERY_STATUS_CHARGING ||
                        charging == BatteryManager.BATTERY_STATUS_FULL)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                result["thermalStatus"] = powerManager?.currentThermalStatus ?: "unknown"
                result["powerSaveMode"] = powerManager?.isPowerSaveMode ?: false
            }
        } catch (_: Exception) {}

        return result
    }

    // #4 HTTP response header logging
    fun logHttpResponse(url: String, responseCode: Int, headers: Map<String, String>, durationMs: Long, bytesRead: Long) {
        log(
            stage = "NETWORK",
            event = "response_headers",
            data = mutableMapOf<String, Any>(
                "url" to url,
                "responseCode" to responseCode,
                "durationMs" to durationMs,
                "bytesRead" to bytesRead
            ).apply { putAll(headers) }
        )
    }

    // #11 Cache event
    fun logCacheEvent(event: String, data: Map<String, Any> = emptyMap()) {
        log(stage = "CACHE", event = event, data = data)
    }

    fun logConnectivitySnapshot(info: NetworkInfo) {
        lastConnectivity = info
        log(stage = "CONNECTIVITY", event = "snapshot", data = mapOf(
            "network_type" to (info.networkType ?: "unknown"),
            "carrier" to (info.operatorName ?: "unknown"),
            "sim_operator" to (info.simOperator ?: "unknown"),
            "network_operator" to (info.networkOperator ?: "unknown"),
            "signal_dbm" to (info.signalStrengthDbm?.toString() ?: "unknown"),
            "signal_level" to (info.signalLevel?.toString() ?: "unknown"),
            "ipv4" to (info.ipv4 ?: "unknown"),
            "ipv6" to (info.ipv6 ?: "unknown"),
            "dns_servers" to (info.dnsServers ?: "unknown"),
            "vpn_active" to (info.vpnActive?.toString() ?: "unknown"),
            "is_roaming" to (info.isRoaming?.toString() ?: "unknown"),
            "network_validated" to (info.networkValidated?.toString() ?: "unknown"),
            "metered" to (info.activeNetworkMetered?.toString() ?: "unknown"),
            "captive_portal" to (info.isCaptivePortal?.toString() ?: "unknown")
        ))
    }

    fun updateSelectedVideoFormat(height: Int, width: Int, bitrate: Long, codec: String) {
        // Check if this is a resolution switch (ABR change)
        val oldHeight = selectedVideoHeight
        val oldBitrate = selectedVideoBitrate
        
        selectedVideoHeight = height
        selectedVideoWidth = width
        selectedVideoBitrate = bitrate
        selectedVideoCodec = codec

        // Log ABR switch if we had a previous format with a different resolution
        if (oldHeight > 0 && height > 0 && oldHeight != height) {
            log(
                stage = "ABR",
                event = "resolution_switch",
                data = mapOf(
                    "from_height" to oldHeight,
                    "to_height" to height,
                    "from_bitrate" to oldBitrate,
                    "to_bitrate" to bitrate
                )
            )
        }
    }

    fun updateSelectedAudioFormat(bitrate: Long, codec: String, language: String) {
        selectedAudioBitrate = bitrate
        selectedAudioCodec = codec
        selectedAudioLanguage = language
    }
}

// Wrapper for structured error logging
data class PlaybackExceptionWrapper(
    val errorCodeName: String,
    val errorCode: Int,
    val message: String?,
    val causeMessage: String?,
    val recoverable: Boolean,
    val isBehindLiveWindow: Boolean,
    val rendererIndex: Int?,
    val rendererName: String?,
    val errorType: String?,
    val stacktrace: String
)
