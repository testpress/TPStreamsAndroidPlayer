package com.tpstreams.player.token

import com.tpstreams.player.TPStreamsPlayer
import com.tpstreams.player.TPStreamsSDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `refreshed token becomes current token for subsequent requests`() = runBlocking {
        var requestedUrl = ""
        val httpClient = successfulHttpClient { requestedUrl = it }
        val listener = object : TPStreamsPlayer.Listener {
            override fun onAccessTokenExpired(videoId: String, callback: (String) -> Unit) {
                callback("renewed_secret_token")
            }

            override fun onError(error: com.tpstreams.player.constants.PlaybackError, message: String) {}
        }
        val tokenManager = TokenManager(
            assetId = "test_asset",
            accessToken = "expired_token",
            offlineLicenseExpireTime = 1000L,
            httpClient = httpClient,
            mainDispatcher = Dispatchers.Unconfined,
            listenerProvider = { listener },
        )

        assertEquals("renewed_secret_token", tokenManager.getTokenForRequest(forceRefresh = true))
        assertEquals("renewed_secret_token", tokenManager.getTokenForRequest())
        assertTrue(requestedUrl.contains("access_token=renewed_secret_token"))
    }

    @Test
    fun `download license URL supports auth header without access token`() = runBlocking {
        TPStreamsSDK.init("test_org", TPStreamsSDK.Provider.TestPress, authToken = "jwt_token")
        val tokenManager = TokenManager(
            assetId = "test_asset",
            accessToken = "",
            offlineLicenseExpireTime = 1000L,
            httpClient = successfulHttpClient(),
            mainDispatcher = Dispatchers.Unconfined,
            listenerProvider = { null },
        )

        val licenseUrl = tokenManager.getDownloadDrmLicenseUrl()

        assertTrue(licenseUrl.contains("/drm_license_key/test_asset/"))
    }

    private fun successfulHttpClient(onRequest: (String) -> Unit = {}): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                onRequest(chain.request().url.toString())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
}
