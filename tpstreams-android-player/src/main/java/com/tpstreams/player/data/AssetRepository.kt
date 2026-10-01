package com.tpstreams.player.data

import com.tpstreams.player.TPStreamsSDK
import com.tpstreams.player.constants.PlaybackError
import com.tpstreams.player.constants.getErrorMessage
import com.tpstreams.player.constants.toPlaybackError
import com.tpstreams.player.data.network.model.AssetInfo
import com.tpstreams.player.util.ServerDateHeaderInterceptor
import com.tpstreams.player.util.toPlaybackErrorFromHttpStatus
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
        fun onError(failure: AssetFetchFailure)
    }

    data class AssetFetchFailure(
        val error: PlaybackError,
        val exception: Exception,
        val responseCode: Int?,
        val requestUrl: String,
    ) {
        fun getMessage(playerId: String): String = exception.getErrorMessage(playerId, responseCode)
    }

    private fun fetchAssetInfoInternal(
        orgId: String,
        assetId: String,
        accessToken: String,
        callback: AssetCallback,
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
                    handleApiError(response.code, assetApiUrl, callback)
                    return@launch
                }

                val body = response.body?.string() ?: run {
                    CoroutineScope(Dispatchers.Main).launch {
                        callback.onError(
                            AssetFetchFailure(
                                error = PlaybackError.UNSPECIFIED,
                                exception = Exception("Empty response from server"),
                                responseCode = null,
                                requestUrl = assetApiUrl,
                            )
                        )
                    }
                    return@launch
                }

                val json = JSONObject(body)
                val assetInfo = apiService.parseAsset(json)

                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(assetInfo)
                }
            } catch (e: Exception) {
                val url = runCatching { apiService.assetInfoUrl(orgId, assetId, accessToken) }.getOrNull() ?: ""
                handleException(e, url, callback)
            }
        }
    }

    internal fun fetchAssetInfo(
        assetId: String,
        accessToken: String,
        callback: AssetCallback,
    ) {
        fetchAssetInfoInternal(TPStreamsSDK.requireOrgId(), assetId, accessToken, callback)
    }

    private fun handleApiError(code: Int, url: String, callback: AssetCallback) {
        CoroutineScope(Dispatchers.Main).launch {
            callback.onError(
                AssetFetchFailure(
                    error = code.toPlaybackErrorFromHttpStatus(),
                    exception = Exception("Asset fetch API failed with HTTP status $code"),
                    responseCode = code,
                    requestUrl = url,
                )
            )
        }
    }

    private fun handleException(e: Exception, url: String, callback: AssetCallback) {
        CoroutineScope(Dispatchers.Main).launch {
            callback.onError(
                AssetFetchFailure(
                    error = e.toPlaybackError(),
                    exception = e,
                    responseCode = null,
                    requestUrl = url,
                )
            )
        }
    }
}
