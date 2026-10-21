package com.tpstreams.player.analytics

import android.util.Log
import com.tpstreams.player.TPStreamsSDK
import com.tpstreams.player.data.network.api.TPStreamsApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

interface AnalyticsUploader {
    suspend fun start()
    suspend fun upload(durationSeconds: Long): Boolean
}

internal object AnalyticsDateUtil {
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun formatUtcIsoTimestamp(date: Date = Date()): String {
        return synchronized(isoDateFormat) {
            isoDateFormat.format(date)
        }
    }
}

class PresignedUrlAnalyticsUploader(
    val orgId: String,
    val assetId: String,
    val visitorId: String,
    val sessionId: String,
    val accessToken: String?,
    val userAgent: String,
    private val client: OkHttpClient,
    private val apiServiceProvider: () -> TPStreamsApiService = {
        TPStreamsSDK.apiService as? TPStreamsApiService
            ?: error("TPStreams analytics requires the TPStreams provider")
    }
) : AnalyticsUploader {

    companion object {
        private const val TAG = "TPStreamsAnalytics"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    var presignedPutUrl: String? = null
        internal set
    var ipAddress: String? = null
        internal set
    var location: String? = null
        internal set
    var lastUploadedDuration: Long = -1L
        internal set

    override suspend fun start() {
        withContext(Dispatchers.IO) {
            fetchPresignedUrls()
        }
    }

    suspend fun fetchPresignedUrls(): Boolean = withContext(Dispatchers.IO) {
        try {
            val apiService = apiServiceProvider()
            val url = apiService.analyticsPresignedUrlsUrl(orgId, assetId, visitorId, sessionId, accessToken)
            val requestBuilder = Request.Builder()
                .url(url)
                .post(ByteArray(0).toRequestBody(null))
                .addHeader("User-Agent", userAgent)

            TPStreamsSDK.getAuthHeaders().forEach { (k, v) ->
                requestBuilder.addHeader(k, v)
            }

            val response = client.newCall(requestBuilder.build()).execute()
            response.use {
                if (!it.isSuccessful) {
                    return@withContext false
                }
                val bodyString = it.body?.string() ?: return@withContext false
                val json = JSONObject(bodyString)
                presignedPutUrl = json.optString("presigned_put_url").takeIf { u -> u.isNotBlank() }
                ipAddress = json.optString("ip_address").takeIf { ip -> ip.isNotBlank() }
                location = json.optString("location").takeIf { loc -> loc.isNotBlank() }
                presignedPutUrl != null
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun upload(durationSeconds: Long): Boolean {
        return uploadInternal(durationSeconds, isRetry = false)
    }

    private suspend fun uploadInternal(durationSeconds: Long, isRetry: Boolean): Boolean = withContext(Dispatchers.IO) {
        val putUrl = presignedPutUrl ?: run {
            val refreshed = fetchPresignedUrls()
            if (!refreshed) return@withContext false
            presignedPutUrl ?: return@withContext false
        }

        val timestamp = AnalyticsDateUtil.formatUtcIsoTimestamp()
        val payload = JSONObject().apply {
            put("assetId", assetId)
            put("organization", orgId)
            put("visitor_id", visitorId)
            put("session_id", sessionId)
            put("duration", durationSeconds)
            put("user_agent", userAgent)
            put("time_stamp", timestamp)
            put("ip_address", ipAddress ?: "")
            put("location", location ?: "")
        }

        val request = Request.Builder()
            .url(putUrl)
            .put(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()
            response.use {
                if (it.code == 403 && !isRetry) {
                    Log.w(TAG, "Presigned PUT URL expired (403). Refreshing and retrying once...")
                    val refreshed = fetchPresignedUrls()
                    if (refreshed) {
                        return@withContext uploadInternal(durationSeconds, isRetry = true)
                    }
                    false
                } else if (it.isSuccessful) {
                    lastUploadedDuration = durationSeconds
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
