package com.tpstreams.player.data

import com.tpstreams.player.TPStreamsSDK
import com.tpstreams.player.constants.PlaybackError
import com.tpstreams.player.constants.getErrorMessage
import com.tpstreams.player.constants.toPlaybackError
import com.tpstreams.player.data.network.model.AssetInfo
import com.tpstreams.player.util.ServerDateHeaderInterceptor
import com.tpstreams.player.util.SentryLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Repository for fetching and parsing asset metadata from configured backend providers.
 */
object AssetRepository {
    private val client = OkHttpClient.Builder()
        .addInterceptor(ServerDateHeaderInterceptor())
        .build()

    interface AssetCallback {
        fun onSuccess(assetInfo: AssetInfo)
        fun onError(error: PlaybackError, message: String)
    }

    fun fetchAssetInfo(
        orgId: String,
        assetId: String,
        accessToken: String,
        callback: AssetCallback
    ) {
        TPStreamsSDK.requireOrgId()
        val apiService = TPStreamsSDK.apiService
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val assetApiUrl = apiService.assetInfoUrl(orgId, assetId, accessToken)
                val requestBuilder = Request.Builder().url(assetApiUrl)
                TPStreamsSDK.getAuthHeaders().forEach { (name, value) ->
                    requestBuilder.addHeader(name, value)
                }
                val request = requestBuilder.build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    handleApiError(assetId, response.code, callback)
                    return@launch
                }

                val body = response.body?.string() ?: run {
                    CoroutineScope(Dispatchers.Main).launch {
                        callback.onError(PlaybackError.UNSPECIFIED, "Empty response from server")
                    }
                    return@launch
                }

                val json = JSONObject(body)
                val assetInfo = apiService.parseAsset(json)

                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(assetInfo)
                }
            } catch (e: Exception) {
                handleException(assetId, e, callback)
            }
        }
    }

    fun fetchAssetInfo(
        assetId: String,
        accessToken: String,
        callback: AssetCallback
    ) {
        fetchAssetInfo(TPStreamsSDK.requireOrgId(), assetId, accessToken, callback)
    }

    private fun handleApiError(assetId: String, code: Int, callback: AssetCallback) {
        val errorPlayerId = SentryLogger.generatePlayerIdString()
        SentryLogger.logAPIException(Exception("API request failed with code: $code"), assetId, code, errorPlayerId)

        val errorType = when (code) {
            404 -> PlaybackError.INVALID_ASSETS_ID
            401, 403 -> PlaybackError.INVALID_ACCESS_TOKEN_FOR_ASSETS
            else -> PlaybackError.UNSPECIFIED
        }

        val errorMessage = Exception().getErrorMessage(errorPlayerId, code)
        CoroutineScope(Dispatchers.Main).launch {
            callback.onError(errorType, errorMessage)
        }
    }

    private fun handleException(assetId: String, e: Exception, callback: AssetCallback) {
        val errorPlayerId = SentryLogger.generatePlayerIdString()
        SentryLogger.logAPIException(e, assetId, null, errorPlayerId)

        val errorType = e.toPlaybackError()
        val errorMessage = e.getErrorMessage(errorPlayerId, null)

        CoroutineScope(Dispatchers.Main).launch {
            callback.onError(errorType, errorMessage)
        }
    }
}
