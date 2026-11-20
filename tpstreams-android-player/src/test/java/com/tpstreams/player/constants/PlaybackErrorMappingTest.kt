package com.tpstreams.player.constants

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
