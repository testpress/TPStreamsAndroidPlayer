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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal interface AnalyticsUploader {
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

internal class PresignedUrlAnalyticsUploader(
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
                presignedPutUrl = readJsonString(bodyString, "presigned_put_url")
                    ?.takeIf(String::isNotBlank)
                ipAddress = readJsonString(bodyString, "ip_address")
                    ?.takeIf(String::isNotBlank)
                location = readJsonString(bodyString, "location")
                    ?.takeIf(String::isNotBlank)
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
        val payload = buildAnalyticsPayload(durationSeconds, timestamp)

        val request = Request.Builder()
            .url(putUrl)
            .put(payload.toRequestBody(JSON_MEDIA_TYPE))
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

    private fun buildAnalyticsPayload(durationSeconds: Long, timestamp: String): String = buildString {
        append('{')
        appendJsonField("assetId", assetId)
        append(',')
        appendJsonField("organization", orgId)
        append(',')
        appendJsonField("visitor_id", visitorId)
        append(',')
        appendJsonField("session_id", sessionId)
        append(",\"duration\":").append(durationSeconds)
        append(',')
        appendJsonField("user_agent", userAgent)
        append(',')
        appendJsonField("time_stamp", timestamp)
        append(',')
        appendJsonField("ip_address", ipAddress ?: "")
        append(',')
        appendJsonField("location", location ?: "")
        append('}')
    }

    private fun StringBuilder.appendJsonField(name: String, value: String) {
        append(jsonString(name)).append(':').append(jsonString(value))
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                in '\u0000'..'\u001F' -> append("\\u%04x".format(character.code))
                else -> append(character)
            }
        }
        append('"')
    }

    private fun readJsonString(json: String, key: String): String? {
        val escapedKey = Regex.escape(key)
        val match = Regex("\\\"$escapedKey\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"")
            .find(json)
            ?: return null
        return unescapeJsonString(match.groupValues[1])
    }

    private fun unescapeJsonString(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character != '\\' || index + 1 >= value.length) {
                append(character)
                index++
                continue
            }

            when (val escaped = value[index + 1]) {
                '"' -> append('"')
                '\\' -> append('\\')
                '/' -> append('/')
                'b' -> append('\b')
                'f' -> append('\u000C')
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                'u' -> {
                    if (index + 5 < value.length) {
                        value.substring(index + 2, index + 6).toIntOrNull(16)?.let(::append)
                        index += 4
                    } else {
                        append('\\').append(escaped)
                    }
                }
                else -> append('\\').append(escaped)
            }
            index += 2
        }
    }
}
