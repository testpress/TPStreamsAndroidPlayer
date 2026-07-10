package com.tpstreams.player.util

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaDrm
import android.os.Build
import com.tpstreams.player.data.PlayerDecoderState
import java.util.UUID

/**
 * Provider of decoder and DRM security-level information.
 *
 * Widevine security level is collected once (lazy) and cached.
 * Decoder names, hardware flags, and MIME types are accepted as
 * [PlayerDecoderState] — typically sourced from the current
 * [com.tpstreams.player.TPStreamsPlayer] instance.
 *
 * Tags: widevine_security_level, active_decoder_count, video_decoder_name
 * Context: all DecoderInfo fields
 */
internal object DecoderInfoProvider {

    private const val WIDEVINE_UUID_STRING = "edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"
    private val WIDEVINE_UUID = UUID.fromString(WIDEVINE_UUID_STRING)

    /** Cached Widevine security level. Collected once via MediaDrm. */
    private val widevineLevel: String? by lazy { collectWidevineLevel() }

    /** Returns tags for Sentry events using the given [decoderState]. */
    fun buildTags(decoderState: PlayerDecoderState?): Map<String, String> {
        return buildMap {
            widevineLevel?.let { put("widevine_security_level", it) }
            val activeCount = CodecManager.getActiveDecoderCount()
            put("active_decoder_count", activeCount.toString())
            decoderState?.videoDecoderName?.let { put("video_decoder_name", it) }
            decoderState?.videoMimeType?.let { put("video_mime_type", it) }
            decoderState?.audioMimeType?.let { put("audio_mime_type", it) }
        }
    }

    /** Returns the full decoder context for Sentry using the given [decoderState]. */
    fun buildContext(decoderState: PlayerDecoderState?): Map<String, Any> {
        return buildMap {
            decoderState?.videoDecoderName?.let { put("video_decoder_name", it) }
            decoderState?.audioDecoderName?.let { put("audio_decoder_name", it) }
            decoderState?.videoDecoderIsHardware?.let { put("video_decoder_is_hardware", it) }
            decoderState?.audioDecoderIsHardware?.let { put("audio_decoder_is_hardware", it) }
            decoderState?.videoMimeType?.let { put("video_mime_type", it) }
            decoderState?.audioMimeType?.let { put("audio_mime_type", it) }
            val activeCount = CodecManager.getActiveDecoderCount()
            put("active_decoder_count", activeCount)
            widevineLevel?.let { put("widevine_security_level", it) }
        }
    }

/**
 * Determines whether the given [decoderName] is hardware-accelerated.
 *
 * API 29+: uses [MediaCodecInfo.isHardwareAccelerated] (reliable).
 * API 21-28: falls back to heuristic on decoder name patterns.
 *
 * **Heuristic caveats (API 21-28):**
 * - "omx.google." is always software (Android's reference implementation)
 * - ".sw." pattern indicates software decoder on some SoC implementations
 * - "avc.decoder" is typically software (e.g., on Qualcomm/MTK low-tier)
 * - **Not exhaustive** — some hardware decoders may be misclassified and
 *   some software decoders may not match these patterns. Consider this
 *   a best-effort signal, not authoritative.
 */
    fun isDecoderHardware(decoderName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+ has the reliable isHardwareAccelerated API
                findCodecInfo(decoderName)?.isHardwareAccelerated ?: heuristicIsHardware(decoderName)
            } else {
                heuristicIsHardware(decoderName)
            }
        } catch (_: Exception) {
            heuristicIsHardware(decoderName)
        }
    }

    private fun findCodecInfo(decoderName: String): MediaCodecInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (info in codecList.codecInfos) {
            if (info.name == decoderName) return info
        }
        return null
    }

    private fun heuristicIsHardware(decoderName: String): Boolean {
        val lower = decoderName.lowercase()
        // Software decoders typically have these patterns
        if (lower.contains("omx.google.") || lower.contains(".sw.") || lower.contains("avc.decoder")) return false
        return true
    }

    private fun collectWidevineLevel(): String? {
        return try {
            val mediaDrm = MediaDrm(WIDEVINE_UUID)
            try {
                mediaDrm.getPropertyString("securityLevel")
            } finally {
                // close() requires API 28+; release() works on all versions
                if (Build.VERSION.SDK_INT >= 28) mediaDrm.close() else mediaDrm.release()
            }
        } catch (_: Exception) {
            null
        }
    }
}
