package com.tpstreams.player.data.network

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@UnstableApi
internal class ShieldedDashDataSourceFactory(
    private val assetId: String,
    mpd: String,
    private val segmentSeed: ByteArray,
    private val upstreamFactory: DataSource.Factory,
) : DataSource.Factory {
    private val manifestUri = Uri.parse("tpstreams://shielded/$assetId")
    private val manifestBytes = mpd.toByteArray(StandardCharsets.UTF_8)
    private val baseUrl = extractBaseUrl(mpd)

    override fun createDataSource(): DataSource {
        return ShieldedDashDataSource(
            manifestUri = manifestUri,
            manifestBytes = manifestBytes,
            baseUrl = baseUrl,
            segmentSeed = segmentSeed,
            upstreamFactory = upstreamFactory,
        )
    }

    fun manifestUri(): Uri = manifestUri

    private fun extractBaseUrl(mpd: String): String {
        val match = Regex("<BaseURL(?:\\s[^>]*)?>(.*?)</BaseURL>", RegexOption.DOT_MATCHES_ALL)
            .find(mpd)
            ?: error("Shielded MPD does not contain a BaseURL")
        return match.groupValues[1].trim()
    }
}

@UnstableApi
private class ShieldedDashDataSource(
    private val manifestUri: Uri,
    private val manifestBytes: ByteArray,
    private val baseUrl: String,
    private val segmentSeed: ByteArray,
    private val upstreamFactory: DataSource.Factory,
) : DataSource {
    private val transferListeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val dataSource = if (dataSpec.uri == manifestUri) {
            ByteArrayDataSource(manifestBytes)
        } else {
            upstreamFactory.createDataSource()
        }
        transferListeners.forEach(dataSource::addTransferListener)
        delegate = dataSource

        val resolvedSpec = if (dataSpec.uri == manifestUri) {
            dataSpec
        } else {
            dataSpec.buildUpon().setUri(rewriteSegmentUri(dataSpec.uri)).build()
        }
        return dataSource.open(resolvedSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return checkNotNull(delegate).read(buffer, offset, length)
    }

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> {
        return delegate?.responseHeaders ?: emptyMap()
    }

    override fun close() {
        delegate?.close()
        delegate = null
    }

    private fun rewriteSegmentUri(uri: Uri): Uri {
        val original = uri.toString()
        if (!original.startsWith(baseUrl) || !uri.path.orEmpty().matches(SEGMENT_PATH)) {
            return uri
        }

        val relativePath = original.removePrefix(baseUrl).substringBefore("?")
        val slash = relativePath.lastIndexOf('/')
        val directory = if (slash >= 0) relativePath.substring(0, slash + 1) else ""
        val fileName = if (slash >= 0) relativePath.substring(slash + 1) else relativePath
        val extension = fileName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        val secretName = hmacHex(relativePath).take(32) + extension
        val rewrittenPath = baseUrl + directory + secretName
        val query = uri.query
        return Uri.parse(if (query.isNullOrEmpty()) rewrittenPath else "$rewrittenPath?$query")
    }

    private fun hmacHex(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(segmentSeed, "HmacSHA256"))
        return mac.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private companion object {
        val SEGMENT_PATH = Regex(".*\\.(mp4|m4s)$", RegexOption.IGNORE_CASE)
    }
}
