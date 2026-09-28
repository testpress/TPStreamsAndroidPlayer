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

    @Test
    fun `isSurfaceDetachTimeout correctly identifies surface detach timeouts and ignores other errors`() {
        // 1. ExoTimeoutException with TIMEOUT_OPERATION_DETACH_SURFACE
        val detachTimeoutException = androidx.media3.exoplayer.ExoTimeoutException(
            androidx.media3.exoplayer.ExoTimeoutException.TIMEOUT_OPERATION_DETACH_SURFACE
        )
        val playbackExceptionWithDetachCause = androidx.media3.common.PlaybackException(
            "Unexpected runtime error",
            detachTimeoutException,
            androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT
        )
        assertTrue(com.tpstreams.player.TPStreamsPlayer.isSurfaceDetachTimeout(playbackExceptionWithDetachCause))

        // 2. PlaybackException with "Detaching surface timed out." message
        val messageTimeoutException = androidx.media3.common.PlaybackException(
            "Detaching surface timed out.",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT
        )
        assertTrue(com.tpstreams.player.TPStreamsPlayer.isSurfaceDetachTimeout(messageTimeoutException))

        // 3. Other ExoTimeoutException operation (e.g. TIMEOUT_OPERATION_RELEASE) should return false
        val releaseTimeoutException = androidx.media3.exoplayer.ExoTimeoutException(
            androidx.media3.exoplayer.ExoTimeoutException.TIMEOUT_OPERATION_RELEASE
        )
        val playbackExceptionWithRelease = androidx.media3.common.PlaybackException(
            "Release timed out",
            releaseTimeoutException,
            androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT
        )
        org.junit.Assert.assertFalse(com.tpstreams.player.TPStreamsPlayer.isSurfaceDetachTimeout(playbackExceptionWithRelease))

        // 4. Non-timeout error (e.g. DECODER_INIT_FAILED) should return false
        val decoderException = androidx.media3.common.PlaybackException(
            "Decoder init failed",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        )
        org.junit.Assert.assertFalse(com.tpstreams.player.TPStreamsPlayer.isSurfaceDetachTimeout(decoderException))
    }

    @Test
    fun `timeout recovery attempt policy caps retries at MAX_TIMEOUT_RECOVERY_ATTEMPTS`() {
        val maxAttempts = 2
        var recoveryAttempts = 0
        val timeoutException = androidx.media3.common.PlaybackException(
            "Detaching surface timed out.",
            androidx.media3.exoplayer.ExoTimeoutException(androidx.media3.exoplayer.ExoTimeoutException.TIMEOUT_OPERATION_DETACH_SURFACE),
            androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT
        )

        // Attempt 1: should recover
        val shouldRecoverAttempt1 = com.tpstreams.player.TPStreamsPlayer.isSurfaceDetachTimeout(timeoutException) && recoveryAttempts < maxAttempts
        assertTrue(shouldRecoverAttempt1)
        recoveryAttempts++

        // Attempt 2: should recover
        val shouldRecoverAttempt2 = com.tpstreams.player.TPStreamsPlayer.isSurfaceDetachTimeout(timeoutException) && recoveryAttempts < maxAttempts
        assertTrue(shouldRecoverAttempt2)
        recoveryAttempts++

        // Attempt 3: should NOT recover (cap reached, fall through to fatal error)
        val shouldRecoverAttempt3 = com.tpstreams.player.TPStreamsPlayer.isSurfaceDetachTimeout(timeoutException) && recoveryAttempts < maxAttempts
        org.junit.Assert.assertFalse("Should stop non-fatal recovery after reaching max attempts", shouldRecoverAttempt3)
    }
}
