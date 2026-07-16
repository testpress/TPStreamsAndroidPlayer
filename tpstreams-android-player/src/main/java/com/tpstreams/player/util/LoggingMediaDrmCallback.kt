package com.tpstreams.player.util

import android.media.DeniedByServerException
import android.media.MediaDrm
import android.media.NotProvisionedException
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallback

import java.util.UUID

/**
 * Wraps [HttpMediaDrmCallback] to log all DRM HTTP requests (license + provisioning)
 * to [FlightRecorder] for diagnostics.
 */
@OptIn(UnstableApi::class)
internal class LoggingMediaDrmCallback(
    private val delegate: HttpMediaDrmCallback = HttpMediaDrmCallback(
        "",
        DefaultHttpDataSource.Factory()
    )
) : MediaDrmCallback {

    private fun classifyDrmError(e: Exception): String = when (e) {
        is MediaDrm.MediaDrmStateException -> "MEDIA_DRM_STATE"
        is DeniedByServerException -> "DRM_DENIED_BY_SERVER"
        is NotProvisionedException -> "DRM_NOT_PROVISIONED"
        else -> e.javaClass.simpleName
    }

    override fun executeProvisionRequest(
        uuid: UUID,
        request: ExoMediaDrm.ProvisionRequest
    ): ByteArray {
        val url = request.defaultUrl
        FlightRecorder.log(
            stage = "DRM",
            event = "provision_request_started",
            data = mapOf("url" to url, "requestSize" to request.data.size)
        )
        val startTime = SystemClock.elapsedRealtime()
        return try {
            val response = delegate.executeProvisionRequest(uuid, request)
            val elapsed = SystemClock.elapsedRealtime() - startTime
            FlightRecorder.log(
                stage = "DRM",
                event = "provision_request_completed",
                data = mapOf(
                    "url" to url,
                    "durationMs" to elapsed,
                    "responseSize" to response.size
                )
            )
            response
        } catch (e: Exception) {
            val elapsed = SystemClock.elapsedRealtime() - startTime
            FlightRecorder.log(
                stage = "DRM",
                event = "provision_request_failed",
                data = mapOf(
                    "url" to url,
                    "durationMs" to elapsed,
                    "error" to (e.message ?: e.javaClass.simpleName),
                    "drm_error_type" to classifyDrmError(e)
                )
            )
            throw e
        }
    }

    override fun executeKeyRequest(
        uuid: UUID,
        request: ExoMediaDrm.KeyRequest
    ): ByteArray {
        val url = request.licenseServerUrl
        FlightRecorder.log(
            stage = "DRM",
            event = "license_request_started",
            data = mapOf(
                "url" to url,
                "requestType" to request.requestType,
                "requestSize" to request.data.size
            )
        )
        val startTime = SystemClock.elapsedRealtime()
        return try {
            val response = delegate.executeKeyRequest(uuid, request)
            val elapsed = SystemClock.elapsedRealtime() - startTime
            FlightRecorder.log(
                stage = "DRM",
                event = "license_request_completed",
                data = mapOf(
                    "url" to url,
                    "durationMs" to elapsed,
                    "responseSize" to response.size
                )
            )
            response
        } catch (e: Exception) {
            val elapsed = SystemClock.elapsedRealtime() - startTime
            FlightRecorder.log(
                stage = "DRM",
                event = "license_request_failed",
                data = mapOf(
                    "url" to url,
                    "durationMs" to elapsed,
                    "error" to (e.message ?: e.javaClass.simpleName),
                    "drm_error_type" to classifyDrmError(e)
                )
            )
            throw e
        }
    }
}
