package com.tpstreams.player.data

import android.content.Context
import android.os.SystemClock
import com.tpstreams.player.TPStreamsSDK
import com.tpstreams.player.constants.PlaybackError
import com.tpstreams.player.constants.getErrorMessage
import com.tpstreams.player.constants.toPlaybackError
import com.tpstreams.player.data.network.model.AssetInfo
import com.tpstreams.player.util.FlightRecorder
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
        callback: AssetCallback,
        context: Context? = null
    ) {
        TPStreamsSDK.requireOrgId()
        val apiService = TPStreamsSDK.apiService
        CoroutineScope(Dispatchers.IO).launch {
            val startTime = SystemClock.elapsedRealtime()
            val requestUrl: String
            try {
                val assetApiUrl = apiService.assetInfoUrl(orgId, assetId, accessToken)
                requestUrl = assetApiUrl
                val requestBuilder = Request.Builder().url(assetApiUrl)
                TPStreamsSDK.getAuthHeaders().forEach { (name, value) ->
                    requestBuilder.addHeader(name, value)
                }
                val request = requestBuilder.build()

                FlightRecorder.log(
                    stage = "API",
                    event = "asset_info_request_started",
                    data = mapOf("url" to assetApiUrl, "assetId" to assetId)
                )

                val response = client.newCall(request).execute()
                val elapsed = SystemClock.elapsedRealtime() - startTime

                if (!response.isSuccessful) {
                    FlightRecorder.log(
                        stage = "API",
                        event = "asset_info_request_failed",
                        data = mapOf(
                            "url" to assetApiUrl,
                            "responseCode" to response.code,
                            "durationMs" to elapsed,
                            "assetId" to assetId
                        )
                    )
                    handleApiError(assetId, response.code, assetApiUrl, callback, context)
                    return@launch
                }

                val body = response.body?.string() ?: run {
                    FlightRecorder.log(
                        stage = "API",
                        event = "asset_info_request_failed",
                        data = mapOf(
                            "url" to assetApiUrl,
                            "responseCode" to response.code,
                            "durationMs" to elapsed,
                            "error" to "Empty response",
                            "assetId" to assetId
                        )
                    )
                    CoroutineScope(Dispatchers.Main).launch {
                        callback.onError(PlaybackError.UNSPECIFIED, "Empty response from server")
                    }
                    return@launch
                }

                val bodySize = body.length.toLong()
                val json = JSONObject(body)
                val assetInfo = apiService.parseAsset(json)

                FlightRecorder.log(
                    stage = "API",
                    event = "asset_info_request_completed",
                    data = mapOf(
                        "url" to assetApiUrl,
                        "responseCode" to response.code,
                        "durationMs" to elapsed,
                        "bodySizeBytes" to bodySize,
                        "assetId" to assetId
                    )
                )

                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(assetInfo)
                }
            } catch (e: Exception) {
                val elapsed = SystemClock.elapsedRealtime() - startTime
                val url = runCatching { apiService.assetInfoUrl(orgId, assetId, accessToken) }.getOrNull() ?: ""
                FlightRecorder.log(
                    stage = "API",
                    event = "asset_info_request_failed",
                    data = mapOf(
                        "url" to url,
                        "durationMs" to elapsed,
                        "error" to (e.message ?: e.javaClass.simpleName),
                        "assetId" to assetId
                    )
                )
                handleException(assetId, e, url, callback, context)
            }
        }
    }

    fun fetchAssetInfo(
        assetId: String,
        accessToken: String,
        callback: AssetCallback,
        context: Context? = null
    ) {
        fetchAssetInfo(TPStreamsSDK.requireOrgId(), assetId, accessToken, callback, context)
    }

    private fun handleApiError(assetId: String, code: Int, url: String, callback: AssetCallback, context: Context? = null) {
        val errorPlayerId = SentryLogger.generatePlayerIdString()
        SentryLogger.logAPIException(Exception("API request failed with code: $code"), assetId, code, errorPlayerId, url, context = context)

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

    private fun handleException(assetId: String, e: Exception, url: String, callback: AssetCallback, context: Context? = null) {
        val errorPlayerId = SentryLogger.generatePlayerIdString()
        SentryLogger.logAPIException(e, assetId, null, errorPlayerId, url, context = context)

        val errorType = e.toPlaybackError()
        val errorMessage = e.getErrorMessage(errorPlayerId, null)

        CoroutineScope(Dispatchers.Main).launch {
            callback.onError(errorType, errorMessage)
        }
    }
}
