package com.tpstreams.player.util

import androidx.media3.common.PlaybackException
import com.tpstreams.player.TPStreamsPlayer
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
        playerId: String
    ) {
        Sentry.captureException(error) { scope ->
            scope.setTag("playerId", playerId)
            assetId?.let { scope.setTag("assetId", it) }
            scope.setContexts(
                "TPStreamsPlayer",
                mapOf(
                    "Error Code" to error.errorCode,
                    "Error Code Name" to error.errorCodeName,
                    "Asset ID" to (assetId ?: "N/A"),
                    "Player ID" to playerId
                )
            )
        }
    }

    fun logAPIException(
        exception: Exception,
        assetId: String?,
        responseCode: Int?,
        playerId: String
    ) {
        Sentry.captureException(exception) { scope ->
            scope.setTag("playerId", playerId)
            assetId?.let { scope.setTag("assetId", it) }
            responseCode?.let { scope.setTag("responseCode", it.toString()) }
            scope.setContexts(
                "TPStreamsPlayer",
                mapOf(
                    "Asset ID" to (assetId ?: "N/A"),
                    "Player ID" to playerId,
                    "Response Code" to (responseCode ?: "N/A")
                )
            )
        }
    }
}

