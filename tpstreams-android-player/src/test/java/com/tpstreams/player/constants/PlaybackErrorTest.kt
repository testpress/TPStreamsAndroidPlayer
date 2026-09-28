package com.tpstreams.player.constants

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorTest {

    @Test
    fun `LiveStreamNotStartedException maps to LIVE_STREAM_NOT_STARTED and preserves message`() {
        val defaultException = LiveStreamNotStartedException("Live stream will begin soon")
        assertEquals(PlaybackError.LIVE_STREAM_NOT_STARTED, defaultException.toPlaybackError())
        assertEquals("Live stream will begin soon", defaultException.getErrorMessage("test_player_id", null))

        val customException = LiveStreamNotStartedException("Class starts at 5:00 PM")
        assertEquals(PlaybackError.LIVE_STREAM_NOT_STARTED, customException.toPlaybackError())
        assertEquals("Class starts at 5:00 PM", customException.getErrorMessage("test_player_id", null))
    }

    @Test
    fun `LiveStreamEndedException maps to LIVE_STREAM_ENDED and preserves message`() {
        val defaultException = LiveStreamEndedException("Live stream has ended")
        assertEquals(PlaybackError.LIVE_STREAM_ENDED, defaultException.toPlaybackError())
        assertEquals("Live stream has ended", defaultException.getErrorMessage("test_player_id", null))

        val customException = LiveStreamEndedException("Session completed. Recording will be available shortly.")
        assertEquals(PlaybackError.LIVE_STREAM_ENDED, customException.toPlaybackError())
        assertEquals("Session completed. Recording will be available shortly.", customException.getErrorMessage("test_player_id", null))
    }

    @Test
    fun `IOException maps to NETWORK_CONNECTION_FAILED with code 5004`() {
        val ioException = java.io.IOException("Socket closed")
        assertEquals(PlaybackError.NETWORK_CONNECTION_FAILED, ioException.toPlaybackError())
        val message = ioException.getErrorMessage("test_player_id", null)
        assertTrue(message.contains("5004"))
        assertTrue(message.contains("test_player_id"))
    }

    @Test
    fun `PlaybackException with ERROR_CODE_DECODER_INIT_FAILED maps to error code 4001 message with troubleshooting link`() {
        val exception = androidx.media3.common.PlaybackException(
            "Decoder init failed",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        )
        val message = exception.getErrorMessage("test_player_id")
        assertTrue(message.contains("4001"))
        assertTrue(message.contains("test_player_id"))
        assertTrue(message.contains("troubleshooting-steps-for-error-code-4001"))
    }

    @Test
    fun `PlaybackException with ERROR_CODE_TIMEOUT maps to error code 1003 message`() {
        val exception = androidx.media3.common.PlaybackException(
            "Detaching surface timed out",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT
        )
        val message = exception.getErrorMessage("test_player_id")
        assertTrue(message.contains("1003"))
        assertTrue(message.contains("test_player_id"))
    }
}
