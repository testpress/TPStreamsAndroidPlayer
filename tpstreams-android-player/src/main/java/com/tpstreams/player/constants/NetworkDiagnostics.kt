package com.tpstreams.player.constants

data class NetworkDiagnostics(
    val internetReachable: Boolean,
    val internetLatencyMs: Long?,
    val serverReachable: Boolean,
    val serverLatencyMs: Long?,
    val serverDetail: String?,
    val cdnReachable: Boolean? = null,
    val cdnLatencyMs: Long? = null,
    val cdnDetail: String? = null,
    val cdnHostname: String? = null,
    val dnsResolves: Boolean,
    val dnsLatencyMs: Long?,
    val proxyConfigured: Boolean = false,
    val retryAttempt: Int = 0,
    val maxRetries: Int = 3,
    val playerId: String? = null
)
