package com.tpstreams.player.util

import android.media.MediaCodecList
import androidx.annotation.OptIn
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.atomic.AtomicInteger

@OptIn(UnstableApi::class)
object CodecManager {
    private const val TAG = "CodecManager"
    private val activeDecoderCount = AtomicInteger(0)

    /**
     * Increments the count of active decoders and returns the new count.
     */
    fun onDecoderInitialized() {
        val count = activeDecoderCount.incrementAndGet()
        Log.d(TAG, "Decoder initialized. SDK Active Decoders: $count")
    }

    /**
     * Decrements the count of active decoders.
     */
    fun onDecoderReleased() {
        val count = activeDecoderCount.decrementAndGet()
        Log.d(TAG, "Decoder released. SDK Active Decoders: $count")
    }

    /**
     * Returns the current number of active decoders in the SDK.
     */
    fun getActiveDecoderCount(): Int = activeDecoderCount.get()

    /**
     * Attempts to find the maximum supported instances for a given codec and MIME type.
     * Note: This is an expensive system call; use only for diagnostics.
     */
    fun getMaxSupportedInstances(codecName: String, mimeType: String): Int {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            return -1
        }
        
        return try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            val info = codecList.codecInfos.find { it.name == codecName }
            val capabilities = info?.getCapabilitiesForType(mimeType)
            capabilities?.maxSupportedInstances ?: -1
        } catch (e: Exception) {
            -1
        }
    }
    
    /**
     * Logs the current codec status.
     */
    fun logCodecStatus(codecName: String, mimeType: String) {
        val max = getMaxSupportedInstances(codecName, mimeType)
        val active = getActiveDecoderCount()
        val capacityStr = if (max > 0) max.toString() else "Unknown"
        
        Log.d(TAG, "Codec Capacity Status - Codec: $codecName | Total Hardware Limit: $capacityStr | TPStreams Active: $active")
        
        // Also add to play history for Sentry
        PlaybackHistoryManager.recordLog("CODEC_STATUS: $codecName, Limit: $capacityStr, Active: $active")
    }
}
