package com.tpstreams.player.util

import android.content.Context
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import com.tpstreams.player.constants.NetworkDiagnostics
import com.tpstreams.player.constants.PlaybackError
import com.tpstreams.player.data.PlayerDecoderState
import com.tpstreams.player.util.network.NetworkRecoveryHandler
import io.sentry.Breadcrumb
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

internal class NetworkDiagnosticsManager(
    private val playerScope: CoroutineScope,
    private val assetId: String,
    private val exoPlayer: Player,
    context: Context? = null,
    private val networkRecoveryHandler: NetworkRecoveryHandler,
    private val listener: (PlaybackError, String, NetworkDiagnostics) -> Unit,
    private val retryPlayback: () -> Unit,
    private val onDiagnosticsStarted: (() -> Unit)? = null,
    private val diagnosticHostProvider: () -> String = { DIAGNOSTIC_HOST_DEFAULT },
    private val serverProbePathProvider: () -> String = { DEFAULT_SERVER_PROBE_PATH },
) {
    // Application context to avoid Activity leaks
    private val appContext: Context? = context?.applicationContext
    private val probeRunner = NetworkProbeRunner(diagnosticHostProvider, serverProbePathProvider)

    private var networkErrorJob: Job? = null
    // Generation counter to invalidate in-flight probe results when a new error arrives
    // or the user triggers a manual retry. Distinct from autoRetryCount, which tracks
    // actual retry attempts against the max-retry budget.
    private var probeGeneration = 0
    private var autoRetryCount = 0
    private var autoRetryJob: Job? = null
    private var hasPendingError = false

    private fun logDebug(message: String) {
        if (Log.isLoggable(DEBUG_TAG, Log.DEBUG)) {
            Log.d(DEBUG_TAG, message)
        }
    }

    companion object {
        internal const val DEBUG_TAG = "PLAYBACK_ERROR_DEBUG"
        private const val NETWORK_ERROR_DEBOUNCE_MS = 500L
        private const val MAX_AUTO_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 2000L
        internal const val DIAGNOSTIC_HOST_DEFAULT = "app.tpstreams.com"
        internal const val DEFAULT_SERVER_PROBE_PATH = "/api/v1/"
    }

    fun onManualRetry() {
        // Cancel any ongoing diagnostics or auto-retry jobs.
        // Increment the probe generation to invalidate in‑flight probes.
        // Also reset the auto‑retry budget so a manual retry starts fresh.
        autoRetryJob?.cancel()
        networkErrorJob?.cancel()
        networkRecoveryHandler.stopMonitoring()
        probeGeneration++
        autoRetryCount = 0
        hasPendingError = false
    }

    fun onPlaybackRecovered() {
        if (!hasPendingError) return
        hasPendingError = false
        autoRetryCount = 0
        probeGeneration++
        logDebug("NETWORK_PROBE: retry counters reset after stable playback")
    }

    fun onMediaLoaded() {
        autoRetryJob?.cancel()
        networkErrorJob?.cancel()
        networkRecoveryHandler.stopMonitoring()
        autoRetryCount = 0
        probeGeneration++
        hasPendingError = false
        logDebug("NETWORK_PROBE: all counters reset (new media loaded)")
    }

    fun onRelease() {
        autoRetryJob?.cancel()
        networkErrorJob?.cancel()
        networkRecoveryHandler.stopMonitoring()
    }

    fun handleError(
        errorType: PlaybackError,
        exoError: PlaybackException? = null,
        cdnHostname: String? = null,
        decoderState: PlayerDecoderState? = null,
        mediaUrl: String? = null
    ) {
        networkErrorJob?.cancel()
        val attempt = ++probeGeneration
        hasPendingError = true

        onDiagnosticsStarted?.invoke()
        logDebug("NETWORK_PROBE: handleError CALLED — errorType=$errorType, exoError=${exoError?.errorCodeName}, assetId=$assetId")

        networkErrorJob = playerScope.launch {
            logDebug("NETWORK_PROBE: starting — attempt=$attempt, errorType=$errorType, exoError=${exoError?.errorCodeName}")

            delay(NETWORK_ERROR_DEBOUNCE_MS)

            val diagnostics = probeRunner.run(cdnHostname, mediaUrl)

            yield()
            if (attempt != probeGeneration) return@launch

            val hasInternet = diagnostics.internetReachable
            val isExhausted = autoRetryCount >= MAX_AUTO_RETRIES
            val isRetrying = hasInternet && !isExhausted

            val (finalError, message, rootCause) = classifyError(errorType, diagnostics)

            if (!hasInternet) {
                // Offline: wait for OS to signal network is restored, then retry once.
                // No timer polling — the OS event fires immediately on reconnect.
                logDebug("NETWORK_PROBE: no internet — starting recovery monitoring")
                autoRetryJob?.cancel()
                networkRecoveryHandler.startMonitoring {
                    playerScope.launch { retryPlayback() }
                }
            }

            val displayAttempt = autoRetryCount + 1
            val playerId = SentryLogger.generatePlayerIdString()

            addSentryBreadcrumb(rootCause, displayAttempt, isRetrying, diagnostics, finalError, exoPlayer, playerId)
            val sentryEventId = sendSentryEvent(exoError, rootCause, finalError, diagnostics, playerId, isRetrying, exoPlayer, decoderState)
            Log.e(DEBUG_TAG, "Network error: $message (sentry: ${sentryEventId ?: "null"})", exoError)

            listener(
                finalError, message,
                diagnostics.copy(
                    retryAttempt = if (isRetrying) displayAttempt else 0,
                    playerId = if (!isRetrying && hasInternet) playerId else null
                )
            )

            if (hasInternet && !isExhausted) {
                // Internet is reachable but playback still failed.
                // Use exponential backoff: 2s → 4s → 8s, then stop.
                val delayMs = INITIAL_RETRY_DELAY_MS * (1L shl autoRetryCount)
                autoRetryCount++
                logDebug("NETWORK_PROBE: exponential backoff retry — attempt $displayAttempt/$MAX_AUTO_RETRIES, delay=${delayMs}ms")
                autoRetryJob?.cancel()
                autoRetryJob = playerScope.launch {
                    delay(delayMs)
                    retryPlayback()
                }
            } else if (hasInternet) {
                logDebug("NETWORK_PROBE: all retries exhausted after $displayAttempt attempts")
            }
        }
    }

    private fun classifyError(errorType: PlaybackError, diagnostics: NetworkDiagnostics): Triple<PlaybackError, String, String> {
        return when {
            !diagnostics.internetReachable && diagnostics.proxyConfigured ->
                Triple(errorType, "Proxy server unreachable", "proxy_unreachable")
            !diagnostics.internetReachable ->
                Triple(errorType, "No internet connection", "no_internet")
            !diagnostics.dnsResolves ->
                Triple(PlaybackError.VIDEO_SERVICE_BLOCKED, "Video service unreachable", "dns_failure")
            diagnostics.cdnReachable == false -> {
                // If the CDN endpoint is unreachable we treat it as a blocked video service.
                // Previously we tried to corroborate with DNS or server failures, which could mask
                // genuine CDN‑only issues. The caller can still inspect dnsResolves/serverReachable
                // for additional context.
                Triple(PlaybackError.VIDEO_SERVICE_BLOCKED, "Video service unreachable", "cdn_unreachable")
            }
            !diagnostics.serverReachable ->
                Triple(PlaybackError.VIDEO_SERVICE_BLOCKED, "Video service unreachable", "server_blocked")
            else ->
                Triple(PlaybackError.UNSPECIFIED, "Unable to load the video", "unknown")
        }
    }

    private fun addSentryBreadcrumb(
        rootCause: String, displayAttempt: Int, isRetrying: Boolean,
        diagnostics: NetworkDiagnostics, finalError: PlaybackError, exoPlayer: Player, playerId: String
    ) {
        val stateName = when (exoPlayer.playbackState) {
            Player.STATE_IDLE -> "idle"
            Player.STATE_BUFFERING -> "buffering"
            Player.STATE_READY -> "ready"
            Player.STATE_ENDED -> "ended"
            else -> "unknown"
        }
        Sentry.addBreadcrumb(Breadcrumb().apply {
            setMessage(if (isRetrying) "Exponential backoff retry scheduled" else "Network error shown to user")
            setData("root_cause", rootCause)
            setData("retry_attempt", displayAttempt.toString())
            setData("internet_reachable", diagnostics.internetReachable.toString())
            setData("dns_resolves", diagnostics.dnsResolves.toString())
            setData("server_reachable", diagnostics.serverReachable.toString())
            setData("cdn_reachable", diagnostics.cdnReachable?.toString() ?: "null")
            setData("proxy_configured", diagnostics.proxyConfigured.toString())
            setData("final_error", finalError.name)
            setData("player_state", stateName)
            setData("player_id", playerId)
        })
    }

    private fun sendSentryEvent(
        exoError: PlaybackException?, rootCause: String, finalError: PlaybackError,
        diagnostics: NetworkDiagnostics, playerId: String, isRetrying: Boolean,
        player: Player? = null, decoderState: PlayerDecoderState? = null
    ): String? {
        if (isRetrying) return null  // Don't spam Sentry during backoff attempts; log on final failure only
        if (!diagnostics.internetReachable) return null
        return if (exoError != null) {
            SentryLogger.logPlaybackException(exoError, assetId, playerId, rootCause = rootCause, context = appContext, player = player, decoderState = decoderState)
        } else {
            SentryLogger.logMessageWithEnrichment(
                message = "Network error: $rootCause",
                level = io.sentry.SentryLevel.WARNING,
                context = appContext,
                player = player,
                tags = mapOf(
                    "playerId" to playerId,
                    "assetId" to assetId,
                    "rootCause" to rootCause,
                    "finalError" to finalError.name,
                    "network_internet" to diagnostics.internetReachable.toString(),
                    "network_dns" to diagnostics.dnsResolves.toString(),
                    "network_server" to diagnostics.serverReachable.toString(),
                    "network_cdn" to (diagnostics.cdnReachable?.toString() ?: "skipped"),
                    "network_proxy" to diagnostics.proxyConfigured.toString()
                )
            )
        }
    }

}
