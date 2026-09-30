package com.tpstreams.player.data

import android.content.Context
import com.tpstreams.player.TPStreamsSDK
import com.tpstreams.player.constants.LiveStreamEndedException
import com.tpstreams.player.constants.LiveStreamNotStartedException
import com.tpstreams.player.constants.PlaybackError
import com.tpstreams.player.constants.getErrorMessage
import com.tpstreams.player.constants.toPlaybackError
import com.tpstreams.player.data.network.model.AssetInfo
import com.tpstreams.player.util.ServerDateHeaderInterceptor
import com.tpstreams.player.util.SentryLogger
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
    // Process-lifetime standalone logger for fetching asset info without an active player instance.
    // Restricted to one-shot captures to prevent breadcrumbs from piling up across unrelated requests.
    private val sentryLogger by lazy(SentryLogger::create)

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
        fetchAssetInfoInternal(orgId, assetId, accessToken, callback, context, sentryLogger)
    }

    private fun fetchAssetInfoInternal(
        orgId: String,
        assetId: String,
        accessToken: String,
        callback: AssetCallback,
        context: Context?,
        sentryLogger: SentryLogger,
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
                    handleApiError(assetId, response.code, assetApiUrl, callback, context, sentryLogger)
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
                val url = runCatching { apiService.assetInfoUrl(orgId, assetId, accessToken) }.getOrNull() ?: ""
                handleException(assetId, e, url, callback, context, sentryLogger)
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

    internal fun fetchAssetInfo(
        assetId: String,
        accessToken: String,
        callback: AssetCallback,
        context: Context?,
        sentryLogger: SentryLogger,
    ) {
        fetchAssetInfoInternal(TPStreamsSDK.requireOrgId(), assetId, accessToken, callback, context, sentryLogger)
    }

    private fun handleApiError(assetId: String, code: Int, url: String, callback: AssetCallback, context: Context?, sentryLogger: SentryLogger) {
        val errorPlayerId = sentryLogger.playerId
        sentryLogger.logAPIException(Exception("API request failed with code: $code"), assetId, code, url, context = context)

        val errorType = code.toPlaybackErrorFromHttpStatus()

        val errorMessage = Exception().getErrorMessage(errorPlayerId, code)
        CoroutineScope(Dispatchers.Main).launch {
            callback.onError(errorType, errorMessage)
        }
    }

    private fun handleException(assetId: String, e: Exception, url: String, callback: AssetCallback, context: Context?, sentryLogger: SentryLogger) {
        val errorPlayerId = sentryLogger.playerId
        if (e !is LiveStreamNotStartedException && e !is LiveStreamEndedException) {
            sentryLogger.logAPIException(e, assetId, null, url, context = context)
        }

        val errorType = e.toPlaybackError()
        val errorMessage = e.getErrorMessage(errorPlayerId, null)

        CoroutineScope(Dispatchers.Main).launch {
            callback.onError(errorType, errorMessage)
        }
    }
}
