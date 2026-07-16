package com.tpstreams.player.telemetry

import android.net.Uri
import androidx.media3.common.C

data class NetworkEventContext(
    val uri: Uri?,
    val method: String? = null,
    val requestHeaders: Map<String, String>? = null,
    val responseHeaders: Map<String, List<String>>? = null,
    val httpStatus: Int? = null,
    val expectedContentLength: Long = C.LENGTH_UNSET.toLong(),
    val actualBytesRead: Long = 0L,
    val isEofBeforeExpected: Boolean = false,
    val transferDurationMs: Long = 0L,
    val exception: Exception? = null,
    val classification: ErrorClassification = ErrorClassification.UNKNOWN
)

enum class ErrorClassification {
    DNS_FAILURE,
    TLS_FAILURE,
    CONNECTION_TIMEOUT,
    SOCKET_CLOSED,
    CONNECTION_RESET,
    HTTP_ERROR,
    TRUNCATED_RESPONSE,
    CORRUPTED_FRAGMENT,
    DRM_FAILURE,
    DECODER_FAILURE,
    UNKNOWN
}
