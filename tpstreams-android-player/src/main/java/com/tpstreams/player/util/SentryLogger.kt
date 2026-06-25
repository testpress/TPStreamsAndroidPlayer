package com.tpstreams.player.util

import androidx.media3.common.PlaybackException
import io.sentry.Sentry

internal object SentryLogger {

    fun generatePlayerIdString(): String {
        return (1..10)
            .map { ('a'..'z').toList() + ('0'..'9').toList() }
            .map { it.random() }
            .joinToString("")
    }

    fun logPlaybackException(
        error: PlaybackException,
        assetId: String?,
        playerId: String,
        drmLicenseUrl: String? = null
    ) {
        Sentry.captureException(error) { scope ->
            val nowEpochMs = System.currentTimeMillis()
            ClockDriftDiagnostics.buildSentryClockTags(nowEpochMs).forEach { (key, value) ->
                scope.setTag(key, value)
            }
            scope.setContexts("Clock Drift", ClockDriftDiagnostics.buildSentryClockContext(nowEpochMs))
            scope.setTag("playerId", playerId)
            assetId?.let { scope.setTag("assetId", it) }
            drmLicenseUrl?.takeIf { it.isNotEmpty() }?.let { scope.setTag("drmLicenseUrl", it) }
            scope.setContexts(
                "TPStreamsPlayer",
                mapOf(
                    "Error Code" to error.errorCode,
                    "Error Code Name" to error.errorCodeName,
                    "Asset ID" to (assetId ?: "N/A"),
                    "Player ID" to playerId,
                    "DRM License URL" to (drmLicenseUrl?.takeIf { it.isNotEmpty() } ?: "N/A")
                )
            )
            scope.setContexts(
                "Playback History",
                mapOf("Timeline" to PlaybackHistoryManager.getFullHistory())
            )
        }?.toString()
    }

    fun logAPIException(
        exception: Exception,
        assetId: String?,
        responseCode: Int?,
        playerId: String,
        url: String? = null
    ) {
        Sentry.captureException(exception) { scope ->
            val nowEpochMs = System.currentTimeMillis()
            ClockDriftDiagnostics.buildSentryClockTags(nowEpochMs).forEach { (key, value) ->
                scope.setTag(key, value)
            }
            scope.setContexts("Clock Drift", ClockDriftDiagnostics.buildSentryClockContext(nowEpochMs))
            scope.setTag("playerId", playerId)
            assetId?.let { scope.setTag("assetId", it) }
            responseCode?.let { scope.setTag("responseCode", it.toString()) }
            url?.takeIf { it.isNotEmpty() }?.let { scope.setTag("requestUrl", it) }
            scope.setContexts(
                "TPStreamsPlayer",
                mapOf(
                    "Asset ID" to (assetId ?: "N/A"),
                    "Player ID" to playerId,
                    "Response Code" to (responseCode ?: "N/A"),
                    "Request URL" to (url?.takeIf { it.isNotEmpty() } ?: "N/A")
                )
            )
        }?.toString()
    }
}

