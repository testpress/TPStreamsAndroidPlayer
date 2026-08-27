package com.tpstreams.player.util

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidevinePlaybackLevelResolverTest {

    @Test
    fun `isDrmPermanentFailure detects PROVISIONING_FAILED and DEVICE_REVOKED`() {
        val provisioningFailed = PlaybackException(
            "Provisioning failed",
            null,
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED
        )
        val deviceRevoked = PlaybackException(
            "Device revoked",
            null,
            PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED
        )
        val systemError = PlaybackException(
            "System error",
            null,
            PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR
        )

        assertTrue(WidevinePlaybackLevelResolver.isDrmPermanentFailure(provisioningFailed))
        assertTrue(WidevinePlaybackLevelResolver.isDrmPermanentFailure(deviceRevoked))
        assertFalse(WidevinePlaybackLevelResolver.isDrmPermanentFailure(systemError))
    }

    @Test
    fun `isDrmFallbackError identifies fallback-eligible DRM error codes`() {
        val eligibleCodes = listOf(
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
            PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
            PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        )

        for (code in eligibleCodes) {
            val error = PlaybackException("Error $code", null, code)
            assertTrue("Expected code $code to be eligible for DRM fallback", WidevinePlaybackLevelResolver.isDrmFallbackError(error))
        }

        val nonDrmError = PlaybackException("IO error", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        assertFalse(WidevinePlaybackLevelResolver.isDrmFallbackError(nonDrmError))
    }
}
