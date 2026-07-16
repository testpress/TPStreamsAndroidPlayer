package com.tpstreams.player.util

import android.net.Uri
import android.os.SystemClock
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener

internal class LoggingDataSourceFactory(
    private val delegate: DataSource.Factory
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return LoggingDataSource(delegate.createDataSource())
    }
}

internal class LoggingDataSource(
    private val delegate: DataSource
) : DataSource {

    private var dataSpec: DataSpec? = null
    private var startTimeMs = 0L
    private var bytesRead = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        this.startTimeMs = SystemClock.elapsedRealtime()
        this.bytesRead = 0L

        FlightRecorder.log(
            stage = "NETWORK",
            event = "request_started",
            data = mapOf(
                "url" to dataSpec.uri.toString(),
                "method" to dataSpec.httpMethod,
                "position" to dataSpec.position,
                "length" to dataSpec.length
            )
        )

        try {
            val length = delegate.open(dataSpec)
            val finalUri = delegate.uri
            if (finalUri != null && finalUri != dataSpec.uri) {
                FlightRecorder.log(
                    stage = "NETWORK",
                    event = "redirect",
                    data = mapOf(
                        "from" to dataSpec.uri.toString(),
                        "to" to finalUri.toString()
                    )
                )
            }
            return length
        } catch (e: Exception) {
            FlightRecorder.log(
                stage = "NETWORK",
                event = "request_error",
                data = mapOf(
                    "url" to dataSpec.uri.toString(),
                    "error" to (e.message ?: e.javaClass.simpleName)
                )
            )
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = delegate.read(buffer, offset, length)
        if (read > 0) {
            bytesRead += read
        }
        return read
    }

    override fun getUri(): Uri? {
        return delegate.uri
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        return delegate.responseHeaders
    }

    override fun close() {
        val elapsedMs = SystemClock.elapsedRealtime() - startTimeMs
        
        var responseCode = -1
        if (delegate is HttpDataSource) {
            responseCode = delegate.responseCode
        }

        // #4 Log response headers
        val headers = delegate.responseHeaders
        val selectedHeaders = mutableMapOf<String, String>()
        listOf("Content-Length", "Content-Type", "Cache-Control", "Server", "X-Cache", "CF-Cache-Status", "CF-Ray", "X-Amz-Cf-Pop", "X-Amz-Cf-Id", "X-Request-ID", "Via").forEach { key ->
            headers[key]?.firstOrNull()?.let { selectedHeaders[key] = it }
        }
        if (selectedHeaders.isNotEmpty()) {
            FlightRecorder.log(
                stage = "NETWORK",
                event = "response_headers",
                data = mutableMapOf<String, Any>(
                    "url" to (dataSpec?.uri?.toString() ?: "unknown"),
                    "responseCode" to responseCode
                ).apply { putAll(selectedHeaders) }
            )
        }

        // Extract HTTP protocol version from status line (e.g., "HTTP/1.1 200 OK" -> "1.1")
        // HttpURLConnection returns the status line as a null-key entry; access via Java Map.
        val httpProtocol = try {
            @Suppress("UNCHECKED_CAST")
            (headers as java.util.Map<String?, List<String>>)[null]?.firstOrNull()?.let { statusLine ->
                val prefix = "HTTP/"
                if (statusLine.startsWith(prefix)) {
                    statusLine.substring(prefix.length).substringBefore(" ").takeIf { it.isNotEmpty() }
                } else null
            }
        } catch (_: Exception) { null }
        if (httpProtocol != null) {
            FlightRecorder.log(
                stage = "NETWORK",
                event = "http_protocol",
                data = mapOf(
                    "url" to (dataSpec?.uri?.toString() ?: "unknown"),
                    "httpProtocol" to httpProtocol
                )
            )
        }

        FlightRecorder.log(
            stage = "NETWORK",
            event = "response_received",
            data = mapOf(
                "url" to (dataSpec?.uri?.toString() ?: "unknown"),
                "durationMs" to elapsedMs,
                "bytesRead" to bytesRead,
                "responseCode" to responseCode
            ).filterValues { it != -1 }
        )

        delegate.close()
    }
}
