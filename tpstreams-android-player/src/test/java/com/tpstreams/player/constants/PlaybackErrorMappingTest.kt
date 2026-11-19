package com.tpstreams.player.constants

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.tpstreams.player.util.WidevinePlaybackLevelResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.IOException
import java.net.SocketTimeoutException

@OptIn(UnstableApi::class)
class PlaybackErrorMappingTest {

    @Test
    fun unsupportedDrmScheme_mapsToWidevineUnsupported() {
        val error = PlaybackException(
            "Unsupported Widevine",
            null,
            PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
        )

        assertEquals(PlaybackError.DRM_SCHEME_UNSUPPORTED, error.toError())
        assertEquals(
            "This device doesn't support playback of this video.\n Player code: 6001. Player Id: player-id",
            error.getErrorMessage("player-id"),
        )
    }

    @Test
    fun drmLicenseHttp401_mapsToAccessDeniedAndSkipsFallback() {
        val error = drmLicenseFailure(httpFailure(401))

        assertEquals(PlaybackError.INVALID_ACCESS_TOKEN_FOR_DRM_LICENSE, error.toError())
        assertTrue(error.getErrorMessage("player-id").contains("Error code: 5002."))
        assertFalse(WidevinePlaybackLevelResolver.isDrmFallbackError(error))
    }

    @Test
    fun drmLicenseHttp403_keeps6004AndSkipsFallback() {
        val error = drmLicenseFailure(httpFailure(403))

        assertEquals(PlaybackError.INVALID_ACCESS_TOKEN_FOR_DRM_LICENSE, error.toError())
        assertTrue(error.getErrorMessage("player-id").contains("Player code: 6004."))
        assertFalse(WidevinePlaybackLevelResolver.isDrmFallbackError(error))
    }

    @Test
    fun otherDrmLicenseFailures_keep6004AndSkipFallback() {
        for (cause in listOf(httpFailure(-1), httpFailure(400), httpFailure(500), SocketTimeoutException())) {
            val error = drmLicenseFailure(cause)

            assertEquals(PlaybackError.INVALID_ACCESS_TOKEN_FOR_DRM_LICENSE, error.toError())
            assertTrue(error.getErrorMessage("player-id").contains("Player code: 6004."))
            assertFalse(WidevinePlaybackLevelResolver.isDrmFallbackError(error))
        }
    }

    private fun drmLicenseFailure(cause: IOException) = PlaybackException(
        "Source error",
        IOException("DRM license request failed", cause),
        PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
    )

    private fun httpFailure(status: Int) = HttpDataSource.InvalidResponseCodeException(
        status, null, null, emptyMap(), mock(DataSpec::class.java), byteArrayOf(),
    )
}
