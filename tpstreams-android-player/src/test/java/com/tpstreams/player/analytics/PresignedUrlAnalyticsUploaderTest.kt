package com.tpstreams.player.analytics

import com.tpstreams.player.TPStreamsSDK
import com.tpstreams.player.data.network.api.TPStreamsApiService
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PresignedUrlAnalyticsUploaderTest {

    private val orgId = "org_123"
    private val assetId = "asset_456"
    private val visitorId = "12345678901234567890123456789012"
    private val sessionId = "1234567890123456789012345678901234567890"
    private val accessToken = "token_abc"
    private val userAgent = "TestApp/1.0 (Android 14; Mobile)"

    @Before
    fun setUp() {
        TPStreamsSDK.init(orgId)
    }

    @Test
    fun `fetchPresignedUrls successfully parses response fields`() = runBlocking {
        val putUrl = "https://s3.wasabi.com/put-log.json"
        val ip = "103.1.2.3"
        val loc = "Chennai, India"

        var recordedUrl: String? = null
        var recordedMethod: String? = null
        var recordedUserAgent: String? = null

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                recordedUrl = request.url.toString()
                recordedMethod = request.method
                recordedUserAgent = request.header("User-Agent")

                val jsonResponse = """
                    {"presigned_put_url":"$putUrl","ip_address":"$ip","location":"$loc"}
                """.trimIndent()

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonResponse.toString().toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val uploader = PresignedUrlAnalyticsUploader(
            orgId = orgId,
            assetId = assetId,
            visitorId = visitorId,
            sessionId = sessionId,
            accessToken = accessToken,
            userAgent = userAgent,
            client = client,
            apiServiceProvider = { TPStreamsApiService() }
        )

        val success = uploader.fetchPresignedUrls()

        assertTrue(success)
        assertEquals(putUrl, uploader.presignedPutUrl)
        assertEquals(ip, uploader.ipAddress)
        assertEquals(loc, uploader.location)

        assertEquals("POST", recordedMethod)
        assertEquals(userAgent, recordedUserAgent)
        assertTrue(recordedUrl!!.contains("visitorId=$visitorId"))
        assertTrue(recordedUrl!!.contains("sessionId=$sessionId"))
        assertTrue(recordedUrl!!.contains("access_token=$accessToken"))
    }

    @Test
    fun `start fetches presigned URLs without reading the storage object`() = runBlocking {
        val putUrl = "https://s3.wasabi.com/put-log.json"
        var requestCount = 0

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                requestCount++
                val jsonResponse = """{"presigned_put_url":"$putUrl"}"""
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonResponse.toString().toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val uploader = PresignedUrlAnalyticsUploader(
            orgId = orgId,
            assetId = assetId,
            visitorId = visitorId,
            sessionId = sessionId,
            accessToken = null,
            userAgent = userAgent,
            client = client
        )

        uploader.start()
        assertEquals(1, requestCount)
    }

    @Test
    fun `upload sends PUT payload conforming to specification`() = runBlocking {
        val putUrl = "https://s3.wasabi.com/put-log.json"
        var uploadedBody: String? = null
        var uploadedContentType: String? = null
        var uploadedMethod: String? = null

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                uploadedMethod = request.method
                uploadedContentType = request.header("Content-Type")

                val buffer = okio.Buffer()
                request.body?.writeTo(buffer)
                uploadedBody = buffer.readUtf8()

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val uploader = PresignedUrlAnalyticsUploader(
            orgId = orgId,
            assetId = assetId,
            visitorId = visitorId,
            sessionId = sessionId,
            accessToken = null,
            userAgent = userAgent,
            client = client
        ).apply {
            presignedPutUrl = putUrl
            ipAddress = "1.2.3.4"
            location = "Chennai"
        }

        val success = uploader.upload(45)

        assertTrue(success)
        assertEquals("PUT", uploadedMethod)
        assertEquals("application/json", uploadedContentType)

        val body = uploadedBody!!
        assertJsonField(body, "assetId", assetId)
        assertJsonField(body, "organization", orgId)
        assertJsonField(body, "visitor_id", visitorId)
        assertJsonField(body, "session_id", sessionId)
        assertJsonField(body, "duration", "45")
        assertJsonField(body, "user_agent", userAgent)
        assertJsonField(body, "ip_address", "1.2.3.4")
        assertJsonField(body, "location", "Chennai")
        assertTrue(body.contains("\"time_stamp\":\""))
    }

    @Test
    fun `upload handles 403 Forbidden by refreshing URLs and retrying once`() = runBlocking {
        var putAttempts = 0
        var postAttempts = 0

        val initialPutUrl = "https://s3.wasabi.com/old-put-log.json"
        val freshPutUrl = "https://s3.wasabi.com/fresh-put-log.json"

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                if (request.method == "POST") {
                    postAttempts++
                    val jsonResponse = """{"presigned_put_url":"$freshPutUrl"}"""
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(jsonResponse.toString().toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    putAttempts++
                    if (putAttempts == 1) {
                        Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(403)
                            .message("Forbidden")
                            .body("{}".toResponseBody("application/json".toMediaType()))
                            .build()
                    } else {
                        Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("{}".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                }
            })
            .build()

        val uploader = PresignedUrlAnalyticsUploader(
            orgId = orgId,
            assetId = assetId,
            visitorId = visitorId,
            sessionId = sessionId,
            accessToken = null,
            userAgent = userAgent,
            client = client
        ).apply {
            presignedPutUrl = initialPutUrl
        }

        val success = uploader.upload(60)

        assertTrue(success)
        assertEquals(2, putAttempts)
        assertEquals(1, postAttempts)
        assertEquals(freshPutUrl, uploader.presignedPutUrl)
    }

    private fun assertJsonField(json: String, key: String, value: String) {
        assertTrue(json.contains("\"$key\":\"$value\"") || json.contains("\"$key\":$value"))
    }

}
