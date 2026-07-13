package com.tpstreams.player.util

import androidx.media3.common.Player

/**
 * Snapshot of the ExoPlayer state at a point in time.
 *
 * All fields nullable — player may be null or in an intermediate state.
 */
data class PlayerStateSnapshot(
    val playerState: String? = null,        // IDLE, BUFFERING, READY, ENDED
    val playWhenReady: Boolean? = null,
    val currentPositionMs: Long? = null,
    val bufferedPositionMs: Long? = null,
    val playbackSpeed: Float? = null,
    val volume: Float? = null,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val durationMs: Long? = null,
    val mediaItemId: String? = null,
    val droppedFrameCount: Int? = null
) {
    companion object {
        /**
         * Captures a snapshot of the given [player].
         * Pass [exoplayer] for advanced stats (dropped frames).
         * Returns a [PlayerStateSnapshot] with all fields populated if player is non-null.
         */
        fun capture(player: Player?, exoplayer: androidx.media3.exoplayer.ExoPlayer? = null): PlayerStateSnapshot {
            if (player == null) return PlayerStateSnapshot()

            return try {
                val stateName = when (player.playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }

                val videoSize = player.videoSize
                val tpPlayer = player as? com.tpstreams.player.TPStreamsPlayer

                PlayerStateSnapshot(
                    playerState = stateName,
                    playWhenReady = player.playWhenReady,
                    currentPositionMs = if (player.currentPosition >= 0) player.currentPosition else null,
                    bufferedPositionMs = if (player.bufferedPosition >= 0) player.bufferedPosition else null,
                    playbackSpeed = player.playbackParameters.speed,
                    volume = player.volume,
                    videoWidth = if (videoSize.width > 0) videoSize.width else null,
                    videoHeight = if (videoSize.height > 0) videoSize.height else null,
                    durationMs = if (player.duration > 0 && player.duration != androidx.media3.common.C.TIME_UNSET) player.duration else null,
                    mediaItemId = player.currentMediaItem?.mediaId,
                    droppedFrameCount = tpPlayer?.droppedFrameCount
                )
            } catch (_: Exception) {
                PlayerStateSnapshot()
            }
        }
    }

    /** Returns tags for Sentry events. */
    fun getTags(): Map<String, String> {
        return buildMap {
            playerState?.let { put("player_state", it) }
            playWhenReady?.let { put("play_when_ready", it.toString()) }
            mediaItemId?.let { put("media_item_id", it) }
        }
    }

    /** Returns the full player context for Sentry. */
    fun getContext(): Map<String, Any> {
        return buildMap {
            playerState?.let { put("player_state", it) }
            playWhenReady?.let { put("play_when_ready", it) }
            currentPositionMs?.let { put("current_position_ms", it) }
            bufferedPositionMs?.let { put("buffered_position_ms", it) }
            playbackSpeed?.let { put("playback_speed", it) }
            volume?.let { put("volume", it) }
            videoWidth?.let { put("video_width", it) }
            videoHeight?.let { put("video_height", it) }
            durationMs?.let { put("duration_ms", it) }
            mediaItemId?.let { put("media_item_id", it) }
            droppedFrameCount?.let { put("dropped_frame_count", it) }
        }
    }
}
