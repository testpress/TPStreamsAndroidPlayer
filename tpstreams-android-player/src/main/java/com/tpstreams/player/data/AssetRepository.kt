package com.tpstreams.player.data

import android.util.Log
import com.tpstreams.player.constants.LiveStreamEndedException
import com.tpstreams.player.constants.LiveStreamNotStartedException
import com.tpstreams.player.constants.PlaybackError
import com.tpstreams.player.constants.toPlaybackError
import com.tpstreams.player.constants.getErrorMessage
import com.tpstreams.player.util.SentryLogger
import com.tpstreams.player.util.ApiHistoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Repository for fetching and parsing asset metadata from the TPStreams API.
 */
object AssetRepository {
    private val client = OkHttpClient()
    private const val BASE_URL = "https://app.tpstreams.com/api/v1"

    interface AssetCallback {
        fun onSuccess(assetInfo: AssetInfo, title: String)
        fun onError(error: PlaybackError, message: String)
    }

    fun fetchAssetInfo(
        orgId: String,
        assetId: String,
        accessToken: String,
        callback: AssetCallback
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val assetApiUrl = "$BASE_URL/$orgId/assets/$assetId/?access_token=$accessToken"
            try {
                val request = Request.Builder().url(assetApiUrl).build()
                val response = client.newCall(request).execute()
                val responseCode = response.code
                val body = response.body?.string()

                ApiHistoryManager.recordLog(
                    endpoint = assetApiUrl,
                    responseCode = responseCode,
                    responseBody = body
                )

                if (!response.isSuccessful) {
                    handleApiError(assetId, responseCode, callback)
                    return@launch
                }

                if (body.isNullOrEmpty()) {
                    CoroutineScope(Dispatchers.Main).launch {
                        callback.onError(PlaybackError.UNSPECIFIED, "Empty response from server")
                    }
                    return@launch
                }

                val json = JSONObject(body)
                val title = json.optString("title", "Undefined")
                val assetInfo = parseAssetInfo(json)

                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(assetInfo, title)
                }
            } catch (e: Exception) {
                ApiHistoryManager.recordLog(
                    endpoint = assetApiUrl,
                    responseBody = "Exception: ${e.message}"
                )
                handleException(assetId, e, callback)
            }
        }
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

    private fun parseAssetInfo(json: JSONObject): AssetInfo {
        val assetType = json.optString("type", "video")
        val isLiveStream = assetType == "livestream"

        return if (isLiveStream && json.has("live_stream") && !json.isNull("live_stream")) {
            parseLiveStreamAssetInfo(json)
        } else {
            parseVideoAssetInfo(json)
        }
    }

    private fun parseLiveStreamAssetInfo(json: JSONObject): AssetInfo {
        val liveStreamObj = json.getJSONObject("live_stream")
        val liveStreamStatus = liveStreamObj.optString("status", "")

        return when (liveStreamStatus.uppercase(java.util.Locale.ROOT)) {
            "NOT STARTED" -> throw LiveStreamNotStartedException("Live stream will begin soon")
            "COMPLETED" -> {
                if (json.has("video") && !json.isNull("video")) {
                    val videoObj = json.getJSONObject("video")
                    val videoStatus = videoObj.optString("status", "")

                    if (videoStatus.equals("Completed", ignoreCase = true)) {
                        val enableDrm = videoObj.optBoolean("enable_drm", false)
                        val mediaUrl = if (enableDrm) {
                            videoObj.optString("dash_url")
                        } else {
                            videoObj.optString("playback_url")
                        }
                        val thumbnailUrl = videoObj.optJSONArray("thumbnails")?.optString(0) ?: ""
                        AssetInfo(mediaUrl, enableDrm, thumbnailUrl, videoObj, isLiveStream = false, durationSeconds = videoObj.optDouble("duration", 0.0))
                    } else {
                        throw LiveStreamEndedException("Live stream has ended")
                    }
                } else {
                    throw LiveStreamEndedException("Live stream has ended")
                }
            }
            else -> {
                val enableDrm = liveStreamObj.optBoolean("enable_drm", false)
                val mediaUrl = if (enableDrm) {
                    liveStreamObj.optString("dash_url")
                } else {
                    liveStreamObj.optString("hls_url")
                }
                AssetInfo(mediaUrl, enableDrm, "", null, isLiveStream = true, durationSeconds = liveStreamObj.optDouble("duration", 0.0))
            }
        }
    }

    private fun parseVideoAssetInfo(json: JSONObject): AssetInfo {
        val videoObj = json.getJSONObject("video")
        val enableDrm = videoObj.optBoolean("enable_drm", false)
        val mediaUrl = if (enableDrm) {
            videoObj.optString("dash_url")
        } else {
            videoObj.optString("playback_url")
        }
        val thumbnailUrl = videoObj.optJSONArray("thumbnails")?.optString(0) ?: ""
        val duration = videoObj.optDouble("duration", 0.0)
        return AssetInfo(mediaUrl, enableDrm, thumbnailUrl, videoObj, isLiveStream = false, durationSeconds = duration)
    }

}
