package com.tpstreams.player.util

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.*

@UnstableApi
internal class TPStreamsMediaDrmCallback(
    private val licenseUrl: String,
    private val headers: Map<String, String>? = null
) : MediaDrmCallback {

    private val client = OkHttpClient()

    override fun executeProvisionRequest(uuid: UUID, request: ExoMediaDrm.ProvisionRequest): ByteArray {
        val url = "${request.defaultUrl}&signedRequest=${String(request.data)}"
        val okRequest = Request.Builder()
            .url(url)
            .build()
        
        return try {
            val response = client.newCall(okRequest).execute()
            val body = response.body?.bytes() ?: throw IOException("Empty response")
            
            ApiHistoryManager.recordLog(
                endpoint = url,
                method = "GET",
                responseCode = response.code,
                responseBody = "Provisioning response (binary data)"
            )
            
            body
        } catch (e: Exception) {
            ApiHistoryManager.recordLog(
                endpoint = url,
                method = "GET",
                responseBody = "Exception: ${e.message}"
            )
            throw e
        }
    }

    override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): ByteArray {
        var url = request.licenseServerUrl
        if (url.isNullOrEmpty()) {
            url = licenseUrl
        }
        
        val builder = Request.Builder()
            .url(url)
            .post(request.data.toRequestBody("application/octet-stream".toMediaType()))
        
        headers?.forEach { (key, value) ->
            builder.addHeader(key, value)
        }
        
        val okRequest = builder.build()
        
        // Log the request (encoding challenge as Base64 for readability)
        val requestChallenge = android.util.Base64.encodeToString(request.data, android.util.Base64.NO_WRAP)
        
        return try {
            val response = client.newCall(okRequest).execute()
            val responseCode = response.code
            val body = response.body?.bytes() ?: throw IOException("Empty response")
            
            // Log the response
            val responseData = android.util.Base64.encodeToString(body, android.util.Base64.NO_WRAP)
            
            ApiHistoryManager.recordLog(
                endpoint = url,
                method = "POST",
                requestBody = "Challenge (Base64): $requestChallenge",
                responseCode = responseCode,
                responseBody = "License (Base64): $responseData"
            )
            
            body
        } catch (e: Exception) {
            ApiHistoryManager.recordLog(
                endpoint = url,
                method = "POST",
                requestBody = "Challenge (Base64): $requestChallenge",
                responseBody = "Exception: ${e.message}"
            )
            throw e
        }
    }
}
