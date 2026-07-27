package com.tpstreams.player.util

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.media3.common.Player
import com.tpstreams.player.BuildConfig
import com.tpstreams.player.data.PlayerDecoderState
import io.sentry.Sentry

internal object DiagnosticSender {

    private const val TAG = "DiagnosticSender"

    fun send(
        player: Player?,
        context: Context,
        triggerReason: String,
        playbackType: String,
        errorCategory: String? = null,
        errorCode: String? = null,
        assetId: String? = null,
        decoderState: PlayerDecoderState? = null
    ) {
        if (!FlightRecorder.sendAndCheck()) {
            Log.d(TAG, "Skipping send — already sent this session")
            return // Already sent for this session
        }

        Log.d(TAG, "Sentry enabled? ${io.sentry.Sentry.isEnabled()}")

        Sentry.withScope { scope ->
            Log.d(TAG, "withScope callback executing")
            scope.setTag("diagnostic_session", "true")
            scope.setTag("trigger", triggerReason)
            FlightRecorder.getSessionId()?.let { scope.setTag("session_id", it) }
            assetId?.let { scope.setTag("asset_id", it) }
            scope.setTag("playback_type", playbackType)
            errorCategory?.let { scope.setTag("error_category", it) }
            errorCode?.let { scope.setTag("error_code", it) }
            
            scope.setTag("sdk_version", BuildConfig.SDK_VERSION)
            val isTestApk = context.packageName.contains("test", ignoreCase = true)
            scope.setTag("is_test_apk", isTestApk.toString())
            scope.setTag("device_manufacturer", Build.MANUFACTURER)
            scope.setTag("device_model", Build.MODEL)
            
            // Decoder Info
            decoderState?.videoDecoderName?.let { scope.setTag("video_decoder", it) }
            decoderState?.audioDecoderName?.let { scope.setTag("audio_decoder", it) }
            
            // Enrich with existing SentryLogger logic for device, network, player state, decoder info
            SentryLogger.enrichScope(
                context = context,
                player = player,
                decoderState = decoderState,
                errorCategory = errorCategory,
                scope = scope
            )

            try {
                FlightRecorder.logConnectivitySnapshot(NetworkInfoProvider.getNetworkInfo(context))
            } catch (_: Exception) { /* best-effort */ }

            // Flight Recorder specific extras
            val analyticsSummary = FlightRecorder.getAnalyticsSummary()
            scope.setExtra("analytics_summary", analyticsSummary)
            scope.setExtra("last_50_events", FlightRecorder.getLastEvents(50).joinToString("\n"))
            scope.setExtra("statistics", FlightRecorder.getStats().toString())
            scope.setExtra("timeline_summary", FlightRecorder.getTimelineSummary().toString())

            scope.addAttachment(FlightRecorder.getSentryAttachment())
            
            val stats = FlightRecorder.getStats()
            val duration = stats["sessionDurationMs"] ?: 0
            val ttf = FlightRecorder.getTimelineSummary()["firstFrameTimeMs"] ?: -1

            val displayAssetId = assetId ?: "UnknownAsset"
            val eventId = Sentry.captureMessage("Diagnostic Session: $displayAssetId — ${duration}ms — TTF ${ttf}ms\n\n$analyticsSummary")
            Log.d(TAG, "captureMessage returned eventId: $eventId")
        }
        
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, "Diagnostics sent to Sentry", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        FlightRecorder.stopSession()
        Log.d(TAG, "Diagnostic send complete")
    }
}
