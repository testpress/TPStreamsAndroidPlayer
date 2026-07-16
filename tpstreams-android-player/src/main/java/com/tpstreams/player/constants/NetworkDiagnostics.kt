package com.tpstreams.player.constants

data class NetworkDiagnostics(
    val internetReachable: Boolean,
    val internetLatencyMs: Long?,
    val internetDetail: String?,              // failure reason: timeout, SSL error, HTTP 502, etc.
    val serverReachable: Boolean,
    val serverLatencyMs: Long?,
    val serverDetail: String?,
    val cdnReachable: Boolean? = null,
    val cdnLatencyMs: Long? = null,
    val cdnDetail: String? = null,
    val cdnHostname: String? = null,
    val dnsResolves: Boolean,
    val dnsLatencyMs: Long?,
    val dnsDetail: String?,                   // which host failed and why: "API: timeout, CDN: NXDOMAIN"
    val proxyConfigured: Boolean = false,
    val tlsVersion: String? = null,            // negotiated TLS version: "TLSv1.2", "TLSv1.3", etc.
    val retryAttempt: Int = 0,
    val maxRetries: Int = 3,
    val playerId: String? = null
)
