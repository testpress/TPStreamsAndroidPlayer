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
        accessToken: String?,
        playerId: String
    ) {
        Sentry.captureMessage(
            "Player error" +
                    " Code: ${error.errorCode}" +
                    " Code name: ${error.errorCodeName}" +
                    " Message: ${error.message}" +
                    " Asset ID: $assetId"
        ) { scope ->
            scope.setTag("playerId", playerId)
            scope.setTag("assetId", assetId ?: "")
            scope.setContexts(
                "TPStreamsPlayer",
                mapOf(
                    "Error Type" to "Player error",
                    "Error Code" to error.errorCode,
                    "Error Code Name" to error.errorCodeName,
                    "Error Message" to (error.message ?: ""),
                    "Asset ID" to (assetId ?: ""),
                    "Player Id" to playerId
                )
            )
        }
    }

    fun logAPIException(
        exception: Exception,
        assetId: String?,
        accessToken: String?,
        responseCode: Int?,
        playerId: String
    ) {
        Sentry.captureMessage(
            "Server error" +
                    " Code: $responseCode" +
                    " Message: ${exception.message}" +
                    " Asset ID: $assetId"
        ) { scope ->
            scope.setTag("playerId", playerId)
            scope.setTag("assetId", assetId ?: "")
            scope.setContexts(
                "TPStreamsPlayer",
                mapOf(
                    "Error Type" to "Server error",
                    "Error Code" to (responseCode ?: 0),
                    "Error Message" to (exception.message ?: ""),
                    "Asset ID" to (assetId ?: ""),
                    "Player Id" to playerId
                )
            )
        }
    }
}

