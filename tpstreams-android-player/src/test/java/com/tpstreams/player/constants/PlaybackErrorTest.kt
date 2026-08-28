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
}
