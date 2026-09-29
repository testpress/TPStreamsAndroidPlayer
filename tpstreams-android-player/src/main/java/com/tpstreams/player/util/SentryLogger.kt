package com.tpstreams.player.util

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.tpstreams.player.BuildConfig
import com.tpstreams.player.TPStreamsSDK
import com.tpstreams.player.data.PlayerDecoderState
import io.sentry.Breadcrumb
import io.sentry.IScope
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.Scopes
import io.sentry.SentryClient
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.protocol.SentryId

/**
 * Sentry reporting and scope ownership isolated to the TPStreams player SDK.
 *
 * See https://docs.sentry.io/platforms/android/configuration/shared-environments/
 */
internal class SentryLogger private constructor() {
    private var scopes: Scopes? = createScopes()

    @Synchronized
    fun captureException(
        throwable: Throwable,
        configureScope: (IScope) -> Unit,
    ): SentryId? = scopes?.captureException(throwable, ScopeCallback(configureScope))

    @Synchronized
    fun captureMessage(
        message: String,
        level: SentryLevel,
        configureScope: (IScope) -> Unit,
    ): SentryId? = scopes?.captureMessage(message, level, ScopeCallback(configureScope))

    @Synchronized
    fun addBreadcrumb(breadcrumb: Breadcrumb) {
        scopes?.addBreadcrumb(breadcrumb)
    }

    @Synchronized
    fun setTag(key: String, value: String) {
        scopes?.setTag(key, value)
    }

    @Synchronized
    fun close() {
        scopes?.close()
        scopes = null
    }

    private fun createScopes(): Scopes {
        val options = SentryOptions().apply {
            dsn = DSN
        }
        val globalScope = Scope(options).apply {
            bindClient(SentryClient(options))
        }
        return Scopes(
            Scope(options),
            Scope(options),
            globalScope,
            CREATOR,
        )
    }

    companion object {
        private const val DSN = "https://1a888cef4d504918b5b506f9b1decef7@sentry.testpress.in/23"
        private const val CREATOR = "TPStreamsPlayer.init"

        fun create(): SentryLogger = SentryLogger()
    }

    fun generatePlayerIdString(): String {
        return (1..10)
            .map { ('a'..'z').toList() + ('0'..'9').toList() }
            .map { it.random() }
            .joinToString("")
    }

    /**
     * Enriches the Sentry [scope] with device, network, storage, decoder, and player state.
     *
     * All providers are best-effort — if one throws, only that provider's data is lost.
     * Context-dependent providers ([StorageMemoryProvider], [NetworkInfoProvider]) are
     * skipped when [context] is null. Decoder state is sourced from [decoderState] which
     * should be the calling player's [PlayerDecoderState].
     */
    fun enrichScope(
        context: Context? = null,
        player: Player? = null,
        decoderState: PlayerDecoderState? = null,
        errorCategory: String? = null,
        scope: IScope
    ) {
        // Error category — high-level classification for triage
        errorCategory?.let { scope.setTag("error_category", it) }

        // SDK version — included on every event for searchability
        scope.setTag("sdkVersion", BuildConfig.SDK_VERSION)

        // Org code / subdomain
        TPStreamsSDK.orgId?.let { scope.setTag("orgCode", it) }

        // Device info (cached fields always work, screen resolution needs context)
        try {
            DeviceInfoProvider.getTags(context).forEach { (key, value) -> scope.setTag(key, value) }
            scope.setContexts("Device Info", DeviceInfoProvider.getContext(context))
        } catch (_: Exception) { /* best-effort */ }

        // App info (needs context)
        if (context != null) try {
            AppInfoProvider.getHostAppVersion(context)?.let { scope.setTag("client_app", it) }
        } catch (_: Exception) { /* best-effort */ }

        // Storage & memory (needs context) — single pass
        if (context != null) try {
            val info = StorageMemoryProvider.getStorageMemoryInfo(context)
            info.lowMemory?.let { scope.setTag("low_memory", it.toString()) }
            scope.setContexts("Storage & Memory", buildMap {
                info.availableRamMb?.let { put("available_ram_mb", it) }
                info.totalRamMb?.let { put("total_ram_mb", it) }
                info.availableStorageMb?.let { put("available_storage_mb", it) }
                info.totalStorageMb?.let { put("total_storage_mb", it) }
                info.lowMemory?.let { put("low_memory", it) }
            })
        } catch (_: Exception) { /* best-effort */ }

        // Network info (needs context) — single pass
        if (context != null) try {
            val info = NetworkInfoProvider.getNetworkInfo(context)
            info.networkType?.let { scope.setTag("network_type", it) }
            info.vpnActive?.let { scope.setTag("vpn_active", it.toString()) }
            info.networkValidated?.let { scope.setTag("network_validated", it.toString()) }
            info.operatorName?.let { scope.setTag("operator_name", it) }
            scope.setContexts("Network Info", buildMap {
                info.networkType?.let { put("network_type", it) }
                info.vpnActive?.let { put("vpn_active", it) }
                info.isRoaming?.let { put("is_roaming", it) }
                info.networkValidated?.let { put("network_validated", it) }
                info.activeNetworkMetered?.let { put("active_network_metered", it) }
                info.operatorName?.let { put("operator_name", it) }
            })
        } catch (_: Exception) { /* best-effort */ }

        // Player state snapshot
        try {
            val snapshot = PlayerStateSnapshot.capture(player)
            snapshot.getTags().forEach { (key, value) -> scope.setTag(key, value) }
            scope.setContexts("Player State", snapshot.getContext())
        } catch (_: Exception) { /* best-effort */ }

        // Decoder info
        try {
            DecoderInfoProvider.buildTags(decoderState).forEach { (key, value) -> scope.setTag(key, value) }
            scope.setContexts("Decoder Info", DecoderInfoProvider.buildContext(decoderState))
        } catch (_: Exception) { /* best-effort */ }
    }

    fun logPlaybackException(
        error: PlaybackException,
        assetId: String?,
        playerId: String,
        drmLicenseUrl: String? = null,
        rootCause: String? = null,
        context: Context? = null,
        player: Player? = null,
        decoderState: PlayerDecoderState? = null,
        drmSecurityLevel: String = "unknown"
    ): String? {
        return captureException(error) { scope ->
            val nowEpochMs = System.currentTimeMillis()
            ClockDriftDiagnostics.buildSentryClockTags(nowEpochMs).forEach { (key, value) ->
                scope.setTag(key, value)
            }
            scope.setTag("errorCode", error.errorCode.toString())
            scope.setTag("errorCodeName", error.errorCodeName)
            scope.setContexts("Clock Drift", ClockDriftDiagnostics.buildSentryClockContext(nowEpochMs))
            scope.setTag("playerId", playerId)
            assetId?.let { scope.setTag("assetId", it) }
            drmLicenseUrl?.takeIf { it.isNotEmpty() }?.let { scope.setTag("drmLicenseUrl", it) }
            scope.setTag("widevine_security_level", drmSecurityLevel)
            // Set rootCause: prefer explicit value, then auto-derive from DRM error code
            val derivedRootCause = rootCause ?: when {
                error.errorCodeName?.contains("DRM", ignoreCase = true) == true -> error.errorCodeName
                else -> null
            }
            derivedRootCause?.let { scope.setTag("rootCause", it) }
            scope.setContexts(
                "TPStreamsPlayer",
                mapOf(
                    "Error Code" to error.errorCode,
                    "Error Code Name" to error.errorCodeName,
                    "Asset ID" to (assetId ?: "N/A"),
                    "Player ID" to playerId,
                    "DRM License URL" to (drmLicenseUrl?.takeIf { it.isNotEmpty() } ?: "N/A")
                )
            )
            scope.setContexts(
                "Playback History",
                mapOf("Timeline" to PlaybackHistoryManager.getFullHistory())
            )
            val category = when {
                rootCause != null -> "NETWORK"
                error.errorCodeName?.contains("DRM", ignoreCase = true) == true -> "DRM"
                error.errorCodeName?.contains("DECODER", ignoreCase = true) == true -> "DECODER"
                else -> "PLAYBACK"
            }
            enrichScope(context = context, player = player, decoderState = decoderState, errorCategory = category, scope = scope)
        }?.toString()
    }

    fun logAPIException(
        exception: Exception,
        assetId: String?,
        responseCode: Int?,
        playerId: String,
        url: String? = null,
        context: Context? = null,
        player: Player? = null
    ): String? {
        return captureException(exception) { scope ->
            val nowEpochMs = System.currentTimeMillis()
            ClockDriftDiagnostics.buildSentryClockTags(nowEpochMs).forEach { (key, value) ->
                scope.setTag(key, value)
            }
            scope.setContexts("Clock Drift", ClockDriftDiagnostics.buildSentryClockContext(nowEpochMs))
            scope.setTag("playerId", playerId)
            assetId?.let { scope.setTag("assetId", it) }
            responseCode?.let { scope.setTag("responseCode", it.toString()) }
            url?.takeIf { it.isNotEmpty() }?.let { scope.setTag("requestUrl", it) }
            scope.setContexts(
                "TPStreamsPlayer",
                mapOf(
                    "Asset ID" to (assetId ?: "N/A"),
                    "Player ID" to playerId,
                    "Response Code" to (responseCode ?: "N/A"),
                    "Request URL" to (url?.takeIf { it.isNotEmpty() } ?: "N/A")
                )
            )
            enrichScope(context = context, player = player, errorCategory = "API", scope = scope)
        }?.toString()
    }

    fun logMessageWithEnrichment(
        message: String,
        level: SentryLevel = SentryLevel.WARNING,
        context: Context? = null,
        player: Player? = null,
        decoderState: PlayerDecoderState? = null,
        tags: Map<String, String> = emptyMap()
    ): String? {
        return captureMessage(message, level) { scope ->
            tags.forEach { (key, value) -> scope.setTag(key, value) }
            scope.setContexts(
                "Playback History",
                mapOf("Timeline" to PlaybackHistoryManager.getFullHistory())
            )
            val category = if (tags.containsKey("rootCause")) "NETWORK" else "UNKNOWN"
            enrichScope(context = context, player = player, decoderState = decoderState, errorCategory = category, scope = scope)
        }?.toString()
    }
}
