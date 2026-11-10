package com.tpstreams.player.util

import android.net.Uri
import android.util.Log
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import java.io.IOException
import android.os.SystemClock

/** Logs DRM license HTTP outcomes without logging query parameters or tokens. */
internal class DrmHttpDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
) : DataSource.Factory {

    override fun createDataSource(): DataSource = LoggingDataSource(upstreamFactory.createDataSource())

    private class LoggingDataSource(
        private val upstream: DataSource,
    ) : DataSource {
        private var currentUri: Uri? = null
        private var requestStartedAt: Long = 0

        override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
            upstream.addTransferListener(transferListener)
        }

        @Throws(IOException::class)
        override fun open(dataSpec: DataSpec): Long {
            currentUri = dataSpec.uri
            requestStartedAt = SystemClock.elapsedRealtime()
            return try {
                val result = upstream.open(dataSpec)
                Log.d(
                    TAG,
                    "License HTTP response: method=${dataSpec.httpMethod}, " +
                        "status=${(upstream as? HttpDataSource)?.responseCode ?: "unknown"}, " +
                        "durationMs=${SystemClock.elapsedRealtime() - requestStartedAt}, " +
                        "url=${safeUrl(dataSpec.uri)}"
                )
                result
            } catch (error: IOException) {
                Log.e(
                    TAG,
                    "License HTTP failure: status=${findResponseCode(error) ?: "unknown"}, " +
                        "durationMs=${SystemClock.elapsedRealtime() - requestStartedAt}, " +
                        "url=${safeUrl(dataSpec.uri)}, error=${error::class.java.simpleName}: ${error.message}"
                )
                throw error
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            upstream.read(buffer, offset, length)

        override fun getUri(): Uri? = upstream.uri ?: currentUri

        override fun close() {
            upstream.close()
        }

        private fun findResponseCode(error: Throwable): Int? {
            var cause: Throwable? = error
            repeat(5) {
                if (cause is HttpDataSource.InvalidResponseCodeException) {
                    return cause.responseCode
                }
                cause = cause?.cause
            }
            return null
        }

        private fun safeUrl(uri: Uri): String = uri.buildUpon().clearQuery().fragment(null).build().toString()

        private companion object {
            private const val TAG = "DrmHttpDataSource"
        }
    }
}
