package com.tpstreams.player.token

import com.tpstreams.player.TPStreamsPlayer
import com.tpstreams.player.TPStreamsSDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class TokenManagerTest {

    @Before
    fun setUp() {
        TPStreamsSDK.init("test_org")
    }

    @Test
    fun `isTokenValid returns false immediately when accessToken and auth headers are empty`() = runBlocking {
        TPStreamsSDK.init("test_org", TPStreamsSDK.Provider.TestPress, null)
        val tokenManager = TokenManager(
            assetId = "test_asset",
            accessToken = "",
            offlineLicenseExpireTime = 1000L,
            mainDispatcher = Dispatchers.Unconfined,
            listenerProvider = { null }
        )

        val isValid = tokenManager.isTokenValid("test_asset")
        assertFalse(isValid)
    }

    @Test
    fun `fetchFreshToken returns empty string when no listener is available`() = runBlocking {
        val tokenManager = TokenManager(
            assetId = "test_asset",
            accessToken = "token",
            offlineLicenseExpireTime = 1000L,
            mainDispatcher = Dispatchers.Unconfined,
            listenerProvider = { null }
        )

        val freshToken = tokenManager.fetchFreshToken("test_asset")
        assertEquals("", freshToken)
    }

    @Test
    fun `fetchFreshToken successfully receives and returns new token from listener callback`() = runBlocking {
        val mockListener = object : TPStreamsPlayer.Listener {
            override fun onAccessTokenExpired(videoId: String, callback: (String) -> Unit) {
                callback("renewed_secret_token")
            }

            override fun onError(error: com.tpstreams.player.constants.PlaybackError, message: String) {}
        }

        val tokenManager = TokenManager(
            assetId = "test_asset",
            accessToken = "expired_token",
            offlineLicenseExpireTime = 1000L,
            mainDispatcher = Dispatchers.Unconfined,
            listenerProvider = { mockListener }
        )

        val freshToken = tokenManager.fetchFreshToken("test_asset")
        assertEquals("renewed_secret_token", freshToken)
    }
}
