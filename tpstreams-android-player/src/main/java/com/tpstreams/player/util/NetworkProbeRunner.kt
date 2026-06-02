package com.tpstreams.player.util

import android.util.Log
import com.tpstreams.player.constants.NetworkDiagnostics
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.ProxySelector
import java.net.URI
import java.net.URL
import javax.net.ssl.SSLException

internal class NetworkProbeRunner(
    private val diagnosticHost: String
) {
    suspend fun run(cdnHostname: String?, timeoutMs: Long = PROBE_TIMEOUT_MS): NetworkDiagnostics {
        return withContext(Dispatchers.IO) {
            coroutineScope {
                withTimeoutOrNull(timeoutMs) {
                    runProbes(cdnHostname)
                } ?: createTimeoutFallback(cdnHostname)
            }
        }
    }

    private suspend fun runProbes(cdnHostname: String?): NetworkDiagnostics = coroutineScope {
        val proxyConfigured = isProxyConfigured()

        val internetDef = async { probeInternet(proxyConfigured) }
        val dnsDef = async { probeDns(cdnHostname) }
        val serverDef = async { probeServer() }
        val cdnDef = async { probeCdn(cdnHostname) }

        val (internet, internetLatency) = internetDef.await()
        val (dns, dnsLatency) = dnsDef.await()
        val (serverOk, serverDetail, serverLatency) = serverDef.await()
        val (cdnOk, cdnDetailStr, cdnLatency) = cdnDef.await()

        NetworkDiagnostics(
            internetReachable = internet,
            internetLatencyMs = internetLatency,
            serverReachable = serverOk,
            serverLatencyMs = serverLatency,
            serverDetail = serverDetail,
            cdnReachable = cdnOk,
            cdnLatencyMs = cdnLatency,
            cdnDetail = cdnDetailStr,
            cdnHostname = cdnHostname,
            dnsResolves = dns,
            dnsLatencyMs = dnsLatency,
            proxyConfigured = proxyConfigured
        )
    }

    private fun probeInternet(proxyConfigured: Boolean): Pair<Boolean, Long?> {
        val start = System.currentTimeMillis()
        val ok = try {
            val conn = (URL("https://$INTERNET_PROBE_HTTP_HOST$INTERNET_PROBE_PATH").openConnection() as HttpURLConnection).apply {
                connectTimeout = SINGLE_PROBE_TIMEOUT_MS
                readTimeout = SINGLE_PROBE_TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                val code = conn.responseCode
                logDebug("internet HTTP probe to $INTERNET_PROBE_HTTP_HOST$INTERNET_PROBE_PATH → $code")
                code in 200..399
            } finally {
                conn.disconnect()
            }
        } catch (_: SSLException) {
            if (proxyConfigured) {
                logDebug("internet HTTP probe SSLException (intercepting proxy) — treating as reachable")
                true
            } else {
                logDebug("internet HTTP probe SSLException without proxy — falling back to TCP probe")
                tcpInternetProbe()
            }
        } catch (_: Exception) {
            if (!proxyConfigured) {
                tcpInternetProbe()
            } else {
                logDebug("internet HTTP probe failed and proxy is configured — proxy is broken")
                false
            }
        }

        val latency = if (ok) System.currentTimeMillis() - start else null
        logDebug("internet reachable=$ok latency=${latency}ms proxy=$proxyConfigured")
        return ok to latency
    }

    private fun isProxyConfigured(): Boolean {
        return try {
            val uri = URI("https://$diagnosticHost")
            ProxySelector.getDefault()?.select(uri)?.any {
                it.type() != java.net.Proxy.Type.DIRECT
            } == true
        } catch (_: Exception) {
            false
        }
    }

    private fun tcpInternetProbe(): Boolean {
        return try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(INTERNET_PROBE_HOST_PRIMARY, INTERNET_PROBE_PORT), SINGLE_PROBE_TIMEOUT_MS)
            }
            true
        } catch (_: Exception) {
            try {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(INTERNET_PROBE_HOST_FALLBACK, INTERNET_PROBE_PORT), SINGLE_PROBE_TIMEOUT_MS)
                }
                true
            } catch (e2: Exception) {
                logDebug("internet TCP fallback failed: ${e2::class.simpleName}: ${e2.message}")
                false
            }
        }
    }

    private fun probeDns(cdnHostname: String?): Pair<Boolean, Long?> {
        val start = System.currentTimeMillis()
        // Resolve the API host and, if provided, the CDN hostname.
        // Both must resolve for the DNS check to pass — this catches cases
        // where the CDN hostname doesn't resolve even when the API host does.
        val apiOk = try {
            java.net.InetAddress.getByName(diagnosticHost)
            true
        } catch (e: Exception) {
            logDebug("API host DNS resolution failed: ${e::class.simpleName}: ${e.message}")
            false
        }
        val cdnOk = if (cdnHostname != null) {
            try {
                java.net.InetAddress.getByName(cdnHostname)
                true
            } catch (e: Exception) {
                logDebug("CDN hostname DNS resolution failed: ${e::class.simpleName}: ${e.message}")
                false
            }
        } else {
            true // nothing to check
        }
        val ok = apiOk && cdnOk
        return ok to (if (ok) System.currentTimeMillis() - start else null)
    }

    private fun probeServer(): Triple<Boolean, String?, Long?> {
        val start = System.currentTimeMillis()
        // Hit the API root rather than the marketing/login page at the apex host.
        // The apex (https://$diagnosticHost/) returns a 302 to /accounts/login/ and
        // would create unnecessary access log noise on the auth server.
        val probeUrl = "https://$diagnosticHost$SERVER_PROBE_PATH"
        val (ok, detail) = try {
            val conn = (URL(probeUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = SINGLE_PROBE_TIMEOUT_MS
                readTimeout = SINGLE_PROBE_TIMEOUT_MS
                requestMethod = "HEAD"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                val code = conn.responseCode
                logDebug("$probeUrl HEAD responseCode=$code")
                when {
                    code in 200..399 -> true to null
                    code in 400..499 -> true to "$code (rejected)"
                    else -> false to "$code blocked"
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            val detail = when {
                e.message?.contains("timeout", ignoreCase = true) == true -> "timeout"
                e is java.net.UnknownHostException -> "unreachable"
                e is java.net.ConnectException -> "connection refused"
                e is javax.net.ssl.SSLException -> "ssl error"
                else -> "failed"
            }
            logDebug("$probeUrl HEAD failed: ${e::class.simpleName}: ${e.message}")
            false to detail
        }
        return Triple(ok, detail, if (ok) System.currentTimeMillis() - start else null)
    }

    private fun probeCdn(cdnHostname: String?): Triple<Boolean?, String?, Long?> {
        if (cdnHostname == null) {
            logDebug("no CDN hostname available, skipping probe")
            return Triple(null, null, null)
        }
        val start = System.currentTimeMillis()
        val (ok, detail) = try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(cdnHostname, CDN_PORT), SINGLE_PROBE_TIMEOUT_MS)
            }
            logDebug("CDN socket to $cdnHostname:$CDN_PORT succeeded")
            true to null
        } catch (e: Exception) {
            val detail = when {
                e.message?.contains("timeout", ignoreCase = true) == true -> "timeout"
                e is java.net.UnknownHostException -> "unreachable"
                e is java.net.ConnectException -> "connection refused"
                e is javax.net.ssl.SSLException -> "ssl error"
                else -> "failed"
            }
            logDebug("CDN socket to $cdnHostname:$CDN_PORT failed: ${e::class.simpleName}: ${e.message}")
            false to detail
        }
        return Triple(ok, detail, if (ok) System.currentTimeMillis() - start else null)
    }

    private fun createTimeoutFallback(cdnHostname: String?): NetworkDiagnostics {
        logDebug("probes timed out after $PROBE_TIMEOUT_MS ms, using fallback")
        return NetworkDiagnostics(
            internetReachable = false,
            internetLatencyMs = null,
            serverReachable = false,
            serverLatencyMs = null,
            serverDetail = "timeout",
            cdnHostname = cdnHostname,
            dnsResolves = false,
            dnsLatencyMs = null,
            proxyConfigured = isProxyConfigured(),
            cdnReachable = null
        )
    }

    private fun logDebug(message: String) {
        if (Log.isLoggable(DEBUG_TAG, Log.DEBUG)) {
            Log.d(DEBUG_TAG, "NETWORK_PROBE: $message")
        }
    }

    companion object {
        private const val DEBUG_TAG = "PLAYBACK_ERROR_DEBUG"
        private const val INTERNET_PROBE_HTTP_HOST = "connectivitycheck.gstatic.com"
        // Canonical Android connectivity check path — returns HTTP 204 No Content.
        private const val INTERNET_PROBE_PATH = "/generate_204"
        // Probes the API root, not the apex marketing/login page.
        private const val SERVER_PROBE_PATH = "/api/v1/"
        private const val INTERNET_PROBE_HOST_PRIMARY = "8.8.8.8"
        private const val INTERNET_PROBE_HOST_FALLBACK = "1.1.1.1"
        private const val INTERNET_PROBE_PORT = 443
        private const val CDN_PORT = 443
        private const val SINGLE_PROBE_TIMEOUT_MS = 3000
        private const val PROBE_TIMEOUT_MS = 8000L
        private const val USER_AGENT = "TPStreamsSDK/1.0 (diagnostic-probe)"
    }
}
