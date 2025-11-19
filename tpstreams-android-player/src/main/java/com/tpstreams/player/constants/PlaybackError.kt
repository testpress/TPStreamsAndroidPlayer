package com.tpstreams.player.constants

import androidx.media3.common.PlaybackException

enum class PlaybackError {
    NETWORK_CONNECTION_FAILED,
    NETWORK_CONNECTION_TIMEOUT,
    INVALID_ASSETS_ID,
    INVALID_ACCESS_TOKEN_FOR_ASSETS,
    EXPIRED_ACCESS_TOKEN_FOR_ASSETS,
    INVALID_ACCESS_TOKEN_FOR_DRM_LICENSE,
    UNSPECIFIED
}

internal fun PlaybackException.toError(): PlaybackError {
    return when (this.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> PlaybackError.NETWORK_CONNECTION_FAILED
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackError.NETWORK_CONNECTION_TIMEOUT
        PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> PlaybackError.INVALID_ACCESS_TOKEN_FOR_DRM_LICENSE
        else -> PlaybackError.UNSPECIFIED
    }
}

internal fun PlaybackException.getErrorMessage(playerId: String): String {
    return when (this.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> 
            "Oops! It seems like you're not connected to the internet. Please check your connection and try again.\n Player code: ${this.errorCode}. Player Id: $playerId"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> 
            "The request took too long to process due to a slow or unstable network connection. Please try again.\n Player code: ${this.errorCode}. Player Id: $playerId"
        PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> 
            "There was an issue fetching the license key for this video. Please try again later.\n Player code: ${this.errorCode}. Player Id: $playerId"
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> 
            "<html><body><p>An error occurred while playing the video. Try restarting your device or playing another video. More help <a href='https://tpstreams.com/help/troubleshooting-steps-for-error-code-4001'>click here</a>.<br> Player code: ${this.errorCode}. Player Id: $playerId</p></body></html>"
        PlaybackException.ERROR_CODE_DECODING_FAILED -> 
            "<html><body><p>An error occurred while playing the video. Try restarting your device or selecting a different resolution. More help <a href='https://tpstreams.com/help/troubleshooting-steps-for-error-code-4001'>click here</a>.<br> Player code: ${this.errorCode}. Player Id: $playerId</p></body></html>"
        else -> 
            "Oops! Something went wrong. Please contact support for assistance and provide details about the issue.\n Player code: ${this.errorCode}. Player Id: $playerId"
    }
}

internal fun Exception.getErrorMessage(playerId: String, responseCode: Int?): String {
    return when {
        responseCode == 404 -> 
            "The video is not available. Please try another one.\n Error code: 5001. Player Id: $playerId"
        responseCode == 401 || responseCode == 403 -> 
            "Sorry, you don't have permission to access this video. Please check your credentials and try again.\n Error code: 5002. Player Id: $playerId"
        responseCode != null && responseCode >= 500 -> 
            "We're sorry, but there's an issue on our server. Please try again later.\n Error code: 5005. Player Id: $playerId"
        this is java.io.IOException -> 
            "Oops! It seems like you're not connected to the internet. Please check your connection and try again.\n Error code: 5004. Player Id: $playerId"
        else -> 
            "Oops! Something went wrong. Please contact support for assistance and provide details about the issue.\n Error code: 5100. Player Id: $playerId"
    }
}

