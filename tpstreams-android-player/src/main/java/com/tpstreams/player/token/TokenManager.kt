package com.tpstreams.player.token

import android.util.Log
import com.tpstreams.player.TPStreamsPlayer
import com.tpstreams.player.TPStreamsSDK
import com.tpstreams.player.util.ServerDateHeaderInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume

/**
 * Manages access token validation, expiry callbacks, and offline DRM license URL generation.
 */
internal class TokenManager(
    private val assetId: String,
    accessToken: String,
    private val offlineLicenseExpireTime: Long,
    private val httpClient: OkHttpClient = sharedHttpClient,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val listenerProvider: () -> TPStreamsPlayer.Listener?,
) {

    @Volatile
    private var currentAccessToken = accessToken

    suspend fun isTokenValid(targetAssetId: String = assetId): Boolean = withContext(Dispatchers.IO) {
        if (currentAccessToken.isEmpty() && TPStreamsSDK.getAuthHeaders().isEmpty()) {
            Log.d(TAG, "No current token available")
            return@withContext false
        }

        val orgId = TPStreamsSDK.orgId ?: run {
            Log.e(TAG, "organizationId is null during token check")
            return@withContext false
        }

        try {
            val assetApiUrl = TPStreamsSDK.apiService.tokenValidationUrl(orgId, targetAssetId, currentAccessToken)
            val requestBuilder = Request.Builder()
                .url(assetApiUrl)
                .head()

            TPStreamsSDK.getAuthHeaders().forEach { (name, value) ->
                requestBuilder.addHeader(name, value)
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val isValid = response.isSuccessful
            Log.d(TAG, "Token validation result: ${if (isValid) "valid" else "invalid"}")
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "Error checking token validity: ${e.message}")
            false
        }
    }

    suspend fun fetchFreshToken(targetAssetId: String = assetId): String = withContext(mainDispatcher) {
        val listener = listenerProvider() ?: run {
            Log.e(TAG, "No token listener available to request fresh token")
            return@withContext ""
        }

        withTimeoutOrNull(TOKEN_REFRESH_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                listener.onAccessTokenExpired(targetAssetId) { newToken ->
                    if (continuation.isActive) {
                        if (newToken.isNotEmpty()) {
                            Log.d(TAG, "Received fresh token")
                            currentAccessToken = newToken
                            continuation.resume(newToken)
                        } else {
                            Log.e(TAG, "Failed to get fresh token (empty response)")
                            continuation.resume("")
                        }
                    }
                }
            }
        } ?: run {
            Log.e(TAG, "Timed out waiting for a fresh access token")
            ""
        }
    }

    /** Returns the current token when valid, otherwise requests one from the host app. */
    suspend fun getTokenForRequest(
        targetAssetId: String = assetId,
        forceRefresh: Boolean = false,
    ): String? {
        if (!forceRefresh && isTokenValid(targetAssetId)) {
            Log.d(TAG, "Token is valid, using current token")
            return currentAccessToken
        }

        Log.d(TAG, "Requesting a fresh token")
        return fetchFreshToken(targetAssetId).takeIf(String::isNotEmpty)
    }

    suspend fun getDownloadDrmLicenseUrl(): String = withContext(Dispatchers.IO) {
        val orgId = TPStreamsSDK.orgId ?: run {
            Log.e(TAG, "organizationId is null, cannot build license URL")
            return@withContext ""
        }

        val tokenToUse = getTokenForRequest() ?: return@withContext ""

        val licenseUrl = TPStreamsSDK.apiService.drmLicenseUrl(
            orgId = orgId,
            assetId = assetId,
            accessToken = tokenToUse,
            download = true,
            licenseDurationSeconds = offlineLicenseExpireTime
        )
        Log.d(TAG, "Built license URL: $licenseUrl")
        licenseUrl
    }

    fun isTokenValid(targetAssetId: String, scope: CoroutineScope, callback: (Boolean) -> Unit) {
        scope.launch {
            val result = isTokenValid(targetAssetId)
            withContext(mainDispatcher) {
                callback(result)
            }
        }
    }

    fun getNewToken(targetAssetId: String, scope: CoroutineScope, callback: (String) -> Unit) {
        scope.launch {
            val result = fetchFreshToken(targetAssetId)
            withContext(mainDispatcher) {
                callback(result)
            }
        }
    }

    fun getDownloadDrmLicenseUrl(scope: CoroutineScope, callback: (String) -> Unit) {
        scope.launch {
            val result = getDownloadDrmLicenseUrl()
            withContext(mainDispatcher) {
                callback(result)
            }
        }
    }

    companion object {
        private const val TAG = "TokenManager"
        private const val TOKEN_REFRESH_TIMEOUT_MS = 10_000L

        private val sharedHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .addInterceptor(ServerDateHeaderInterceptor())
                .build()
        }
    }
}
