package com.tpstreams.player.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight clock/time context that can be attached to Sentry events.
 *
 * - server time comes from HTTP "Date" response headers (if observed).
 */
internal object ClockDriftDiagnostics {

    // serverOffsetMs = serverEpochMs - deviceEpochMs (at time we observed the server "Date" header)
    private val serverOffsetMs = AtomicLong(UNKNOWN_LONG)
    private val lastServerDateHeaderEpochMs = AtomicLong(UNKNOWN_LONG)
    private val lastServerDateObservedAtEpochMs = AtomicLong(UNKNOWN_LONG)

    private val rfc1123Parser = ThreadLocal.withInitial {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
    }

    private val humanLocalFormatter = ThreadLocal.withInitial {
        // Example: 2026-04-24 16:41:20.465 +05:30
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS XXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
    }

    fun recordServerDateHeader(dateHeader: String?, observedAtEpochMs: Long = System.currentTimeMillis()) {
        if (dateHeader.isNullOrBlank()) return

        val parsedEpochMs = try {
            rfc1123Parser.get()?.parse(dateHeader)?.time
        } catch (_: Throwable) {
            null
        } ?: return

        lastServerDateHeaderEpochMs.set(parsedEpochMs)
        lastServerDateObservedAtEpochMs.set(observedAtEpochMs)
        serverOffsetMs.set(parsedEpochMs - observedAtEpochMs)
    }

    fun buildSentryClockTags(nowEpochMs: Long = System.currentTimeMillis()): Map<String, String> {
        val tags = linkedMapOf<String, String>()

        tags["device.current_time"] = formatHumanLocal(nowEpochMs)

        val offset = getServerOffsetMs()
        if (offset != null) {
            val serverNow = nowEpochMs + offset
            val driftMs = nowEpochMs - serverNow
            tags["device.server_time"] = formatHumanLocal(serverNow)
            tags["device.time_drift_ms"] = driftMs.toString()
            tags["device.time_drift"] = formatDurationMs(driftMs)
            tags["device.server_time_source"] = "http_date_header"
        }

        return tags
    }

    fun buildSentryClockContext(nowEpochMs: Long = System.currentTimeMillis()): Map<String, Any> {
        val context = linkedMapOf<String, Any>()

        context["device.current_time"] = formatHumanLocal(nowEpochMs)

        val offset = getServerOffsetMs()
        if (offset != null) {
            val serverNow = nowEpochMs + offset
            val driftMs = nowEpochMs - serverNow
            context["server.time"] = formatHumanLocal(serverNow)
            context["device.time_drift_ms"] = driftMs
            context["device.time_drift"] = formatDurationMs(driftMs)
            context["server.time_source"] = "http_date_header"
        }

        return context
    }

    private fun formatHumanLocal(epochMs: Long): String {
        return humanLocalFormatter.get()?.format(Date(epochMs)) ?: epochMs.toString()
    }

    private fun formatDurationMs(durationMs: Long): String {
        val isNegative = durationMs < 0
        var remainingMs = kotlin.math.abs(durationMs)

        val totalSeconds = remainingMs / 1000
        remainingMs %= 1000

        val seconds = (totalSeconds % 60).toInt()
        val totalMinutes = totalSeconds / 60
        val minutes = (totalMinutes % 60).toInt()
        val hours = (totalMinutes / 60).toInt()

        val sign = if (isNegative) "-" else "+"
        return if (hours > 0) {
            String.format(Locale.US, "%s%dh %dm %ds", sign, hours, minutes, seconds)
        } else if (minutes > 0) {
            String.format(Locale.US, "%s%dm %ds", sign, minutes, seconds)
        } else {
            String.format(Locale.US, "%s%ds", sign, seconds)
        }
    }

    private fun getServerOffsetMs(): Long? {
        val offset = serverOffsetMs.get()
        return if (offset == UNKNOWN_LONG) null else offset
    }

    private const val UNKNOWN_LONG = Long.MIN_VALUE
}
