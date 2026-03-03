package com.tpstreams.player.util

object DownloadUtils {
    /**
     * Calculates the estimated download size of a video based on its bitrate and duration.
     * 
     * @param bitrate The bitrate of the video in bits per second.
     * @param durationSeconds The duration of the video in seconds.
     * @return The estimated size in bytes.
     */
    fun calculateDownloadSize(bitrate: Long, durationSeconds: Double): Long {
        if (bitrate <= 0 || durationSeconds <= 0.0) return 0
        return (bitrate * durationSeconds / 8.0).toLong()
    }
}
