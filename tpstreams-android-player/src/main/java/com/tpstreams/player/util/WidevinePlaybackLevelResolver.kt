package com.tpstreams.player.util

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaDrm
import android.os.Build
import android.util.Log
import androidx.media3.common.PlaybackException
import java.util.UUID

/**
 * Resolves and caches which Widevine level to use for DRM playback.
 *
 * Resolution is non-blocking:
 * 1. Return persisted L3 if a previous session marked this device as permanently L3-only.
 * 2. Use native L3 when the device reports L3.
 * 3. Default to native L1 for L1-capable devices without proactive background probing.
 * 4. When playback fails with a DRM error and fallback is enabled, [forceL3ForSession] or
 *    [persistForceL3] is triggered during playback error recovery.
 */
internal object WidevinePlaybackLevelResolver {

    private const val TAG = "WidevinePlaybackLevel"
    private const val PREFS_NAME = "tpstreams_drm_playback"
    private const val KEY_FORCE_L3 = "force_l3"

    private val WIDEVINE_UUID: UUID = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cachedLevel: WidevinePlaybackLevel? = null

    @Volatile
    private var nativeLevel: String? = null

    fun initialize(context: Context) {
        if (cachedLevel != null) return

        synchronized(this) {
            if (cachedLevel != null) return

            val applicationContext = context.applicationContext
            appContext = applicationContext
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            nativeLevel = readNativeWidevineLevel()
            val forceL3Persisted = prefs.getBoolean(KEY_FORCE_L3, false)

            if (forceL3Persisted || nativeLevel == "L3") {
                cachedLevel = WidevinePlaybackLevel.L3
                Log.i(
                    TAG,
                    "Resolved playback level=L3, native=$nativeLevel, persistedL3=$forceL3Persisted",
                )
            } else {
                cachedLevel = WidevinePlaybackLevel.L1
                Log.i(
                    TAG,
                    "Defaulting playback level to L1 (native=$nativeLevel)",
                )
            }
        }
    }

    fun getPlaybackLevel(): WidevinePlaybackLevel {
        return cachedLevel ?: WidevinePlaybackLevel.L1
    }

    fun getPlaybackLevelOrNull(): WidevinePlaybackLevel? = cachedLevel

    fun getNativeWidevineLevel(): String? = nativeLevel

    fun isNativeL3(): Boolean = nativeLevel == "L3"

    fun shouldForceL3(): Boolean = getPlaybackLevelOrNull() == WidevinePlaybackLevel.L3

    /**
     * Forces L3 for the current in-memory session only.
     * Does NOT write to SharedPrefs — the next app launch defaults back to native level.
     * Use for transient DRM failures (license acquisition errors, system errors) where
     * the device may recover on the next launch.
     * For permanent failures (revoked keybox/certificate), use [persistForceL3] instead.
     */
    fun forceL3ForSession() {
        cachedLevel = WidevinePlaybackLevel.L3
        Log.i(TAG, "L3 forced for current session only (not persisted)")
    }

    fun persistForceL3(context: Context? = appContext) {
        val prefs = (context ?: appContext)?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return
        persistForceL3(prefs)
        cachedLevel = WidevinePlaybackLevel.L3
        Log.i(TAG, "Persisted L3 playback level for future sessions")
    }

    /**
     * Returns true for DRM errors that are permanent device-level failures.
     * These warrant persisting L3 across all future sessions (e.g. revoked keybox).
     */
    fun isDrmPermanentFailure(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED
    }

    /**
     * Returns true for any DRM error that should trigger an L3 fallback retry.
     * Covers both permanent failures (provisioning) and transient ones
     * (license acquisition, system errors, disallowed operations, hardware crypto failures).
     * Excludes [PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED] which has its own renewal path.
     */
    fun isDrmFallbackError(error: PlaybackException): Boolean {
        if (when (error.errorCode) {
                PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
                PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
                PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
                PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION -> true
                else -> false
            }
        ) {
            return true
        }
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is android.media.MediaCodec.CryptoException) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun persistForceL3(prefs: SharedPreferences) {
        prefs.edit().putBoolean(KEY_FORCE_L3, true).apply()
    }

    private fun readNativeWidevineLevel(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) return null
        var mediaDrm: MediaDrm? = null
        return try {
            mediaDrm = MediaDrm(WIDEVINE_UUID)
            mediaDrm.getPropertyString("securityLevel")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read native Widevine level: ${e.message}")
            null
        } finally {
            mediaDrm?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    it.close()
                } else {
                    @Suppress("DEPRECATION")
                    it.release()
                }
            }
        }
    }
}
