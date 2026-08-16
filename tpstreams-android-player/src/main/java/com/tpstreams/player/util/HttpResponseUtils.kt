package com.tpstreams.player.util

import androidx.media3.datasource.HttpDataSource
import com.tpstreams.player.constants.PlaybackError

internal const val HTTP_STATUS_NOT_FOUND = 404
internal const val HTTP_STATUS_UNAUTHORIZED = 401
internal const val HTTP_STATUS_FORBIDDEN = 403
internal const val HTTP_STATUS_SERVER_ERROR_MIN = 500
internal const val HTTP_STATUS_SERVER_ERROR_MAX = 599

internal const val DISPLAY_ERROR_ASSET_NOT_FOUND = 5001
internal const val DISPLAY_ERROR_ACCESS_DENIED = 5002
internal const val DISPLAY_ERROR_SERVER = 5005

internal fun Throwable.findHttpResponseCode(): Int? {
    var cause: Throwable? = this
    while (cause != null) {
        if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
        cause = cause.cause
    }
    return null
}

internal fun Throwable.isAuthOrContentHttpFailure(): Boolean {
    return when (findHttpResponseCode()) {
        HTTP_STATUS_UNAUTHORIZED, HTTP_STATUS_FORBIDDEN, HTTP_STATUS_NOT_FOUND -> true
        else -> false
    }
}

internal fun Int.toPlaybackErrorFromHttpStatus(): PlaybackError = when (this) {
    HTTP_STATUS_NOT_FOUND -> PlaybackError.INVALID_ASSETS_ID
    HTTP_STATUS_UNAUTHORIZED, HTTP_STATUS_FORBIDDEN -> PlaybackError.INVALID_ACCESS_TOKEN_FOR_ASSETS
    in HTTP_STATUS_SERVER_ERROR_MIN..HTTP_STATUS_SERVER_ERROR_MAX -> PlaybackError.SERVER_ERROR
    else -> PlaybackError.UNSPECIFIED
}

internal fun Int.httpStatusUserMessage(playerId: String): String? = when (this) {
    HTTP_STATUS_NOT_FOUND ->
        "The video is not available. Please try another one.\n Error code: $DISPLAY_ERROR_ASSET_NOT_FOUND. Player Id: $playerId"
    HTTP_STATUS_UNAUTHORIZED, HTTP_STATUS_FORBIDDEN ->
        "Sorry, you don't have permission to access this video. Please check your credentials and try again.\n Error code: $DISPLAY_ERROR_ACCESS_DENIED. Player Id: $playerId"
    in HTTP_STATUS_SERVER_ERROR_MIN..HTTP_STATUS_SERVER_ERROR_MAX ->
        "We're sorry, but there's an issue on our server. Please try again later.\n Error code: $DISPLAY_ERROR_SERVER. Player Id: $playerId"
    else -> null
}
