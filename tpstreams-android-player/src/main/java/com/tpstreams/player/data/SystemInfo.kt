package com.tpstreams.player.data

/**
 * Runtime storage and memory stats. Values are in MB.
 */
data class StorageMemoryInfo(
    val availableRamMb: Long? = null,
    val totalRamMb: Long? = null,
    val lowMemory: Boolean? = null,
    val availableStorageMb: Long? = null,
    val totalStorageMb: Long? = null
)

/**
 * Network connectivity details, collected permission-free where noted.
 */
data class NetworkInfo(
    val networkType: String? = null,       // WIFI, CELLULAR, ETHERNET, VPN, UNKNOWN
    val vpnActive: Boolean? = null,
    val isRoaming: Boolean? = null,
    val networkValidated: Boolean? = null,
    val activeNetworkMetered: Boolean? = null,
    val operatorName: String? = null,      // Requires READ_PHONE_STATE
    val simOperator: String? = null,       // MCC-MNC e.g. "40438", requires READ_PHONE_STATE
    val networkOperator: String? = null,   // MCC-MNC, requires READ_PHONE_STATE
    val signalStrengthDbm: Int? = null,    // requires READ_PHONE_STATE
    val signalLevel: Int? = null,          // 0-4, requires READ_PHONE_STATE
    val ipv4: String? = null,              // from LinkProperties, permission-free
    val ipv6: String? = null,              // from LinkProperties, permission-free
    val dnsServers: String? = null,         // comma-separated, from LinkProperties, permission-free
    val isCaptivePortal: Boolean? = null    // from NetworkCapabilities, permission-free
)

/**
 * Current decoder state for a single player instance.
 * Stored per-player in [TPStreamsPlayer] and passed to [DecoderInfoProvider]
 * for Sentry enrichment — avoids the global-state race where one player's
 * decoder info overwrites another's.
 */
internal data class PlayerDecoderState(
    val videoDecoderName: String? = null,
    val audioDecoderName: String? = null,
    val videoDecoderIsHardware: Boolean? = null,
    val audioDecoderIsHardware: Boolean? = null,
    val videoMimeType: String? = null,
    val audioMimeType: String? = null
)


