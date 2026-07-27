package com.tpstreams.player.telemetry

import androidx.media3.datasource.HttpDataSource
import java.io.EOFException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object PlaybackErrorClassifier {

    fun classify(
        error: Throwable?,
        expectedBytes: Long,
        actualBytesRead: Long
    ): ErrorClassification {
        if (error == null) return ErrorClassification.UNKNOWN

        var cause: Throwable? = error
        while (cause != null) {
            when (cause) {
                is UnknownHostException -> return ErrorClassification.DNS_FAILURE
                is SSLException -> return ErrorClassification.TLS_FAILURE
                is SocketTimeoutException -> return ErrorClassification.CONNECTION_TIMEOUT
                is HttpDataSource.InvalidResponseCodeException -> return ErrorClassification.HTTP_ERROR
                is EOFException -> {
                    // This is where we investigate the EOFException
                    return if (expectedBytes != -1L && actualBytesRead < expectedBytes) {
                        ErrorClassification.TRUNCATED_RESPONSE // CDN closed early or network drop
                    } else {
                        ErrorClassification.SOCKET_CLOSED
                    }
                }
                is SocketException -> {
                    if (cause.message?.contains("reset", ignoreCase = true) == true) {
                        return ErrorClassification.CONNECTION_RESET
                    }
                    return ErrorClassification.SOCKET_CLOSED
                }
            }
            cause = cause.cause
        }
        return ErrorClassification.UNKNOWN
    }
}
