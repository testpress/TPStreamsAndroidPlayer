package com.tpstreams.player.constants

import androidx.media3.common.PlaybackException
import com.tpstreams.player.util.findHttpResponseCode
import com.tpstreams.player.util.httpStatusUserMessage
import com.tpstreams.player.util.toPlaybackErrorFromHttpStatus

enum class PlaybackError {
    NETWORK_CONNECTION_FAILED,
    NETWORK_CONNECTION_TIMEOUT,
    /**
     * The video service is unreachable due to a network intermediary (firewall, proxy, DNS filter).
     * Internally this covers 3 distinct root causes: DNS resolution failure, CDN blockage,
     * and upstream server blockage — all surfaced as VIDEO_SERVICE_BLOCKED to simplify
     * consumer-side handling. Use `NetworkDiagnostics` fields (dnsResolves, cdnReachable,
     * serverReachable) to distinguish the root cause when needed.
     */
    VIDEO_SERVICE_BLOCKED,
    INVALID_ASSETS_ID,
    INVALID_ACCESS_TOKEN_FOR_ASSETS,
    EXPIRED_ACCESS_TOKEN_FOR_ASSETS,
    INVALID_ACCESS_TOKEN_FOR_DRM_LICENSE,
    SERVER_ERROR,
    LIVE_STREAM_NOT_STARTED,
    LIVE_STREAM_ENDED,
    UNSPECIFIED
}

class LiveStreamNotStartedException(message: String) : Exception(message)
class LiveStreamEndedException(message: String) : Exception(message)

internal fun PlaybackException.toError(): PlaybackError {
    return when (this.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> PlaybackError.NETWORK_CONNECTION_FAILED
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackError.NETWORK_CONNECTION_TIMEOUT
        PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> PlaybackError.INVALID_ACCESS_TOKEN_FOR_DRM_LICENSE
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> toBadHttpStatusError()
        else -> PlaybackError.UNSPECIFIED
    }
}

private fun PlaybackException.toBadHttpStatusError(): PlaybackError {
    return findHttpResponseCode()?.toPlaybackErrorFromHttpStatus() ?: PlaybackError.UNSPECIFIED
}

internal fun PlaybackException.getErrorMessage(playerId: String): String {
    return when (this.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> 
            "Oops! It seems like you're not connected to the internet. Please check your connection and try again.\n Player code: ${this.errorCode}. Player Id: $playerId"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> 
            "The request took too long to process due to a slow or unstable network connection. Please try again.\n Player code: ${this.errorCode}. Player Id: $playerId"
        PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> 
            "There was an issue fetching the license key for this video. Please try again later.\n Player code: ${this.errorCode}. Player Id: $playerId"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
            findHttpResponseCode()?.httpStatusUserMessage(playerId)
                ?: "Oops! Something went wrong. Please contact support for assistance and provide details about the issue.\n Player code: ${this.errorCode}. Player Id: $playerId"
        }
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> 
            "<html><body><p>An error occurred while playing the video. Try restarting your device or playing another video. More help <a href='https://tpstreams.com/help/troubleshooting-steps-for-error-code-4001'>click here</a>.<br> Player code: ${this.errorCode}. Player Id: $playerId</p></body></html>"
        PlaybackException.ERROR_CODE_DECODING_FAILED -> 
            "<html><body><p>An error occurred while playing the video. Try restarting your device or selecting a different resolution. More help <a href='https://tpstreams.com/help/troubleshooting-steps-for-error-code-4001'>click here</a>.<br> Player code: ${this.errorCode}. Player Id: $playerId</p></body></html>"
        else -> 
            "Oops! Something went wrong. Please contact support for assistance and provide details about the issue.\n Player code: ${this.errorCode}. Player Id: $playerId"
    }
}


internal fun Exception.toPlaybackError(): PlaybackError {
    return when (this) {
        is LiveStreamNotStartedException -> PlaybackError.LIVE_STREAM_NOT_STARTED
        is LiveStreamEndedException -> PlaybackError.LIVE_STREAM_ENDED
        // Distinguish timeout from general connection failure so they are correctly
        // reported in diagnostics and to Sentry. SocketTimeoutException extends IOException,
        // so it must be checked first before the broader IOException branch.
        is java.net.SocketTimeoutException -> PlaybackError.NETWORK_CONNECTION_TIMEOUT
        is java.io.IOException -> PlaybackError.NETWORK_CONNECTION_FAILED
        else -> PlaybackError.UNSPECIFIED
    }
}
internal fun Exception.getErrorMessage(playerId: String, responseCode: Int?): String {
    return when {
        responseCode != null -> responseCode.httpStatusUserMessage(playerId)
            ?: "Oops! Something went wrong. Please contact support for assistance and provide details about the issue.\n Error code: 5100. Player Id: $playerId"
        this is java.io.IOException -> 
            "Oops! It seems like you're not connected to the internet. Please check your connection and try again.\n Error code: 5004. Player Id: $playerId"
        this is LiveStreamNotStartedException ->
            this.message ?: "Live stream will begin soon"
        this is LiveStreamEndedException ->
            this.message ?: "Live stream has ended"
        else -> 
            "Oops! Something went wrong. Please contact support for assistance and provide details about the issue.\n Error code: 5100. Player Id: $playerId"
    }
}

