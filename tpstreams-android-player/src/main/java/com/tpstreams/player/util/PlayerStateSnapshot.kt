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
    val volume: Float? = null
) {
    companion object {
        /**
         * Captures a snapshot of the given [player].
         * Returns a [PlayerStateSnapshot] with all fields populated if player is non-null.
         */
        fun capture(player: Player?): PlayerStateSnapshot {
            if (player == null) return PlayerStateSnapshot()

            return try {
                val stateName = when (player.playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }

                PlayerStateSnapshot(
                    playerState = stateName,
                    playWhenReady = player.playWhenReady,
                    currentPositionMs = if (player.currentPosition >= 0) player.currentPosition else null,
                    bufferedPositionMs = if (player.bufferedPosition >= 0) player.bufferedPosition else null,
                    playbackSpeed = player.playbackParameters.speed,
                    volume = player.volume
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
        }
    }
}
