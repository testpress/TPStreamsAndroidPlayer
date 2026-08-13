package com.tpstreams.player

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.util.DecoderInfoProvider

/**
 * Debug overlay drawn on top of the video surface.
 *
 * Shows the Widevine security level actually enforced on the DRM session (device
 * default in brackets), the active video/audio decoders (hardware or software,
 * codec name, MIME type, resolution and bitrate) and the number of live decoders.
 * Refreshes itself once a second while the player is attached so the values stay
 * current as tracks change.
 */
@OptIn(UnstableApi::class)
internal class DebugOverlay(private val view: TPStreamsPlayerView) {

    private val handler = Handler(Looper.getMainLooper())
    private var textView: TextView? = null
    private var attached = false

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!attached) return
            update()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    fun attach() {
        if (attached) return
        attached = true
        val parent = view
        val tv = TextView(parent.context).apply {
            setBackgroundColor(Color.argb(180, 0, 0, 0))
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(8), dp(6), dp(8), dp(6))
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            topMargin = dp(44) // clear of the top control bar
            leftMargin = dp(8)
        }
        tv.layoutParams = params
        parent.addView(tv, parent.childCount)
        textView = tv
        update()
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
    }

    fun detach() {
        if (!attached) return
        attached = false
        handler.removeCallbacks(refreshRunnable)
        textView?.let { view.removeView(it) }
        textView = null
    }

    fun update() {
        val tv = textView ?: return
        val player = view.getPlayer() as? TPStreamsPlayer ?: return
        val state = player.getDecoderState()

        val enforced = DecoderInfoProvider.getEnforcedWidevineSecurityLevel()
        val device = DecoderInfoProvider.getWidevineSecurityLevel()
        val drmLine = when {
            enforced != null -> "DRM: L${enforced} enforced (device L${device ?: "?"})"
            else -> "DRM: device default L${device ?: "?"}"
        }

        val activeCount = com.tpstreams.player.util.CodecManager.getActiveDecoderCount()

        tv.text = buildString {
            appendLine(drmLine)
            appendLine(videoLine(state))
            appendLine(audioLine(state))
            append("Decoders active: $activeCount")
        }
    }

    private fun videoLine(state: com.tpstreams.player.data.PlayerDecoderState): String {
        val name = state.videoDecoderName
        val mode = when (state.videoDecoderIsHardware) {
            true -> "hw"
            false -> "sw"
            null -> "?"
        }
        val size = if (state.videoWidth != null && state.videoHeight != null) {
            "${state.videoWidth}x${state.videoHeight}"
        } else {
            null
        }
        val kbps = state.videoBitrate?.div(1000)?.let { "${it}kbps" }
        val details = listOfNotNull(size, kbps).joinToString(" ")
        return if (name != null) {
            "Video: ${shortName(name)} ($mode) ${state.videoMimeType ?: ""} $details".trimEnd()
        } else {
            "Video: not initialized"
        }
    }

    private fun audioLine(state: com.tpstreams.player.data.PlayerDecoderState): String {
        val name = state.audioDecoderName
        val mode = when (state.audioDecoderIsHardware) {
            true -> "hw"
            false -> "sw"
            null -> "?"
        }
        val kbps = state.audioBitrate?.div(1000)?.let { "${it}kbps" }
        return if (name != null) {
            "Audio: ${shortName(name)} ($mode) ${state.audioMimeType ?: ""} $kbps".trimEnd()
        } else {
            "Audio: not initialized"
        }
    }

    private fun shortName(decoderName: String): String {
        // "c2.android.avc.decoder" -> "android.avc", "OMX.google.h264.decoder" -> "google.h264"
        return decoderName
            .removePrefix("c2.")
            .removePrefix("OMX.")
            .removeSuffix(".decoder")
            .removeSuffix(".secure")
            .takeLast(24)
    }

    private fun dp(value: Int): Int =
        (value * view.resources.displayMetrics.density).toInt()

    private companion object {
        const val REFRESH_INTERVAL_MS = 1000L
    }
}
