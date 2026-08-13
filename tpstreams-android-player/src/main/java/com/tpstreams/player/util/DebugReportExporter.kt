package com.tpstreams.player.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.BuildConfig
import com.tpstreams.player.TPStreamsPlayer
import com.tpstreams.player.data.PlayerDecoderState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds and exports a plain-text debug report for a finished player session.
 *
 * The report bundles every piece of diagnostic data the SDK collects — device,
 * storage/memory, battery, network, DRM, decoder, player state, analytics summary
 * and the full [FlightRecorder] event log — into a single `.txt` file that the
 * user can save or share with the development team.
 *
 * Secrets (access tokens) and token-carrying URLs are masked before writing.
 */
@OptIn(UnstableApi::class)
internal object DebugReportExporter {

    private const val TAG = "DebugReportExporter"
    private const val DIR_NAME = "debug_reports"

    /** Builds the full report text for the given finished [player] session. */
    fun buildReport(
        context: Context,
        player: TPStreamsPlayer,
        triggerReason: String,
        decoderState: PlayerDecoderState? = player.getDecoderState()
    ): String {
        val sb = StringBuilder()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

        sb.appendLine("=== TPStreams Player Debug Report ===")
        sb.appendLine("Generated at: $timestamp")
        sb.appendLine("Trigger reason: $triggerReason")
        sb.appendLine("Session ID: ${FlightRecorder.getSessionId() ?: "N/A"}")
        sb.appendLine("SDK version: ${BuildConfig.SDK_VERSION}")
        sb.appendLine("App package: ${context.packageName}")
        sb.appendLine("App version: ${appVersionName(context)} (${appVersionCode(context)})")
        sb.appendLine("Android build: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")

        sb.appendLine()
        sb.appendLine("=== Device Info ===")
        appendMap(sb, DeviceInfoProvider.getContext(context))

        sb.appendLine()
        sb.appendLine("=== Storage & Memory ===")
        runCatching {
            val info = StorageMemoryProvider.getStorageMemoryInfo(context)
            appendMap(
                sb,
                buildMap {
                    info.availableRamMb?.let { put("available_ram_mb", it) }
                    info.totalRamMb?.let { put("total_ram_mb", it) }
                    info.lowMemory?.let { put("low_memory", it) }
                    info.availableStorageMb?.let { put("available_storage_mb", it) }
                    info.totalStorageMb?.let { put("total_storage_mb", it) }
                }
            )
        }.onFailure { Log.d(TAG, "Failed to collect storage/memory info", it) }
        runCatching {
            appendMap(sb, FlightRecorder.getMemorySnapshot(context))
        }.onFailure { Log.d(TAG, "Failed to collect memory snapshot", it) }

        sb.appendLine()
        sb.appendLine("=== Device Status ===")
        runCatching {
            appendMap(sb, FlightRecorder.getDeviceStatus(context))
        }.onFailure { Log.d(TAG, "Failed to collect device status", it) }

        sb.appendLine()
        sb.appendLine("=== DRM ===")
        val deviceLevel = DecoderInfoProvider.getWidevineSecurityLevel() ?: "unknown"
        val enforcedLevel = DecoderInfoProvider.getEnforcedWidevineSecurityLevel() ?: "unknown (not applied)"
        sb.appendLine("Widevine security level (device default): $deviceLevel")
        sb.appendLine("Widevine security level enforced (player session): $enforcedLevel")
        sb.appendLine("Note: 'device default' is the device's capable level (often L1). The enforced level is what this session's MediaDrm actually used; check the Event Log for 'security_level_forced' or 'security_level_force_failed'.")
        runCatching {
            sb.appendLine("DRM enabled: ${player.isDrmContent}")
            sb.appendLine("License URL: ${sanitizeUrl(player.getDrmLicenseUrl())}")
        }.onFailure { Log.d(TAG, "Failed to collect DRM info", it) }

        sb.appendLine()
        sb.appendLine("=== Player State ===")
        runCatching {
            val snapshot = PlayerStateSnapshot.capture(player)
            appendMap(sb, snapshot.getContext())
            appendMap(sb, snapshot.getTags())
        }.onFailure { Log.d(TAG, "Failed to capture player state", it) }
        sb.appendLine("Asset ID: ${player.assetId}")

        sb.appendLine()
        sb.appendLine("=== Decoder Info ===")
        runCatching {
            appendMap(sb, DecoderInfoProvider.buildContext(decoderState))
        }.onFailure { Log.d(TAG, "Failed to collect decoder info", it) }
        sb.appendLine("  widevine_security_level_enforced: ${DecoderInfoProvider.getEnforcedWidevineSecurityLevel() ?: "unknown (not applied)"}")

        sb.appendLine()
        sb.appendLine("=== Network Info ===")
        runCatching {
            val info = NetworkInfoProvider.getNetworkInfo(context)
            appendMap(
                sb,
                buildMap {
                    info.networkType?.let { put("network_type", it) }
                    info.vpnActive?.let { put("vpn_active", it) }
                    info.isRoaming?.let { put("is_roaming", it) }
                    info.networkValidated?.let { put("network_validated", it) }
                    info.activeNetworkMetered?.let { put("active_network_metered", it) }
                    info.operatorName?.let { put("operator_name", it) }
                    info.simOperator?.let { put("sim_operator", it) }
                    info.networkOperator?.let { put("network_operator", it) }
                    info.signalStrengthDbm?.let { put("signal_strength_dbm", it) }
                    info.signalLevel?.let { put("signal_level", it) }
                    info.ipv4?.let { put("ipv4", it) }
                    info.ipv6?.let { put("ipv6", it) }
                    info.dnsServers?.let { put("dns_servers", it) }
                    info.isCaptivePortal?.let { put("captive_portal", it) }
                    info.simCountryIso?.let { put("sim_country_iso", it) }
                    info.networkCountryIso?.let { put("network_country_iso", it) }
                }
            )
        }.onFailure { Log.d(TAG, "Failed to collect network info", it) }

        sb.appendLine()
        sb.appendLine("=== Analytics Summary ===")
        sb.appendLine(FlightRecorder.getAnalyticsSummary())

        sb.appendLine()
        sb.appendLine("=== Event Log (Flight Recorder) ===")
        sb.appendLine(FlightRecorder.getFullDump())

        sb.appendLine()
        sb.appendLine("=== Clock Drift (Sentry context) ===")
        runCatching {
            appendMap(sb, ClockDriftDiagnostics.buildSentryClockContext())
        }.onFailure { Log.d(TAG, "Failed to collect clock drift info", it) }

        sb.appendLine()
        sb.appendLine("=== TPStreamsPlayer (Sentry context) ===")
        sb.appendLine("  Asset ID: ${player.assetId}")
        runCatching {
            sb.appendLine("  DRM enabled: ${player.isDrmContent}")
            sb.appendLine("  DRM License URL: ${sanitizeUrl(player.getDrmLicenseUrl())}")
        }.onFailure { Log.d(TAG, "Failed to collect Sentry player context", it) }

        sb.appendLine()
        sb.appendLine("=== Playback History (Sentry context) ===")
        val history = PlaybackHistoryManager.getFullHistory()
        if (history.isBlank()) {
            sb.appendLine("(no playback history recorded)")
        } else {
            sb.appendLine(history)
        }

        return sb.toString()
    }

    /** Writes [content] to a `.txt` file under the app's debug-reports directory. */
    fun writeReport(context: Context, content: String, sessionId: String? = FlightRecorder.getSessionId()): File? {
        return try {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val dir = File(baseDir, DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            val safeSession = sessionId?.takeIf { it.isNotBlank() }?.replace(Regex("[^a-zA-Z0-9_-]"), "_") ?: "unknown"
            val file = File(dir, "tpstreams_debug_${safeSession}.txt")
            file.writeText(content)
            Log.d(TAG, "Debug report written to ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write debug report", e)
            null
        }
    }

    /** Builds an [Intent.ACTION_SEND] intent sharing [file] via FileProvider. */
    fun buildShareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.debugfileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "TPStreams Player Debug Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Launches the Android share sheet for [file]. Safe to call from an application context. */
    fun launchShare(context: Context, file: File) {
        val shareIntent = buildShareIntent(context, file)
        try {
            context.startActivity(
                Intent.createChooser(shareIntent, "Share debug report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.e(TAG, "No activity available to share debug report", e)
        }
    }

    private fun appendMap(sb: StringBuilder, map: Map<String, *>) {
        if (map.isEmpty()) {
            sb.appendLine("(none)")
            return
        }
        map.forEach { (key, value) -> sb.appendLine("  $key: ${value ?: "N/A"}") }
    }

    /** Masks `access_token` / `tp_token` / `token` query parameter values in a URL. */
    private fun sanitizeUrl(url: String?): String {
        if (url.isNullOrBlank()) return "N/A"
        return url.replace(Regex("([?&](?:access_token|tp_token|token)=)[^&\\s]+"), "$1***MASKED***")
    }

    private fun appVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }

    private fun appVersionCode(context: Context): Long = try {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkg.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkg.versionCode.toLong()
        }
    } catch (_: Exception) {
        -1L
    }
}
