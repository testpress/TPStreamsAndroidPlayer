package com.tpstreams.player.util

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaDrm
import android.os.Build
import android.util.Log
import androidx.media3.common.PlaybackException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Resolves and caches which Widevine level to use for all DRM playback.
 *
 * Resolution runs once during SDK/player initialization:
 * 1. Return persisted L3 if a previous session marked this device as L3-only.
 * 2. Use native L3 when the device reports L3.
 * 3. For L1-capable devices, run a Widevine provisioning probe (Google default URL).
 *    Provisioning HTTP failures (e.g. 500) persist L3 so L1 is never retried.
 */
internal object WidevinePlaybackLevelResolver {

    private const val TAG = "WidevinePlaybackLevel"
    private const val PREFS_NAME = "tpstreams_drm_playback"
    private const val KEY_FORCE_L3 = "force_l3"

    private val WIDEVINE_UUID: UUID = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")

    private val provisioningClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

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
            cachedLevel = if (forceL3Persisted || nativeLevel == "L3") {
                resolvePlaybackLevel(
                    forceL3Persisted = forceL3Persisted,
                    nativeWidevineLevel = nativeLevel,
                    provisioningSucceeded = { true },
                    onPersistForceL3 = { persistForceL3(prefs) },
                )
            } else {
                runBlocking(Dispatchers.IO) {
                    resolvePlaybackLevel(
                        forceL3Persisted = false,
                        nativeWidevineLevel = nativeLevel,
                        provisioningSucceeded = { runProvisioningProbe() },
                        onPersistForceL3 = { persistForceL3(prefs) },
                    )
                }
            }
            Log.i(
                TAG,
                "Resolved playback level=${cachedLevel!!.name}, native=$nativeLevel, persistedL3=${prefs.getBoolean(KEY_FORCE_L3, false)}",
            )
        }
    }

    fun getPlaybackLevel(): WidevinePlaybackLevel {
        return cachedLevel ?: throw IllegalStateException(
            "WidevinePlaybackLevelResolver.initialize(context) must be called before playback.",
        )
    }

    fun getPlaybackLevelOrNull(): WidevinePlaybackLevel? = cachedLevel

    fun getNativeWidevineLevel(): String? = nativeLevel

    fun shouldForceL3(): Boolean = getPlaybackLevelOrNull() == WidevinePlaybackLevel.L3

    /**
     * Forces L3 for the current in-memory session only.
     * Does NOT write to SharedPrefs — the next app launch re-runs the provisioning probe.
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

    internal fun resolvePlaybackLevel(
        forceL3Persisted: Boolean,
        nativeWidevineLevel: String?,
        provisioningSucceeded: () -> Boolean,
        onPersistForceL3: () -> Unit,
    ): WidevinePlaybackLevel {
        if (forceL3Persisted) {
            return WidevinePlaybackLevel.L3
        }

        when (nativeWidevineLevel) {
            "L3" -> return WidevinePlaybackLevel.L3
            "L1" -> {
                if (provisioningSucceeded()) {
                    return WidevinePlaybackLevel.L1
                }
                onPersistForceL3()
                return WidevinePlaybackLevel.L3
            }
            else -> {
                if (provisioningSucceeded()) {
                    return WidevinePlaybackLevel.L1
                }
                onPersistForceL3()
                return WidevinePlaybackLevel.L3
            }
        }
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

    private fun runProvisioningProbe(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) return false

        var mediaDrm: MediaDrm? = null
        return try {
            mediaDrm = MediaDrm(WIDEVINE_UUID)
            val request = mediaDrm.provisionRequest
            val response = provisioningClient.newCall(
                Request.Builder()
                    .url(request.defaultUrl)
                    .post(request.data.toRequestBody("application/octet-stream".toMediaType()))
                    .build(),
            ).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Widevine provisioning probe failed: HTTP ${response.code}")
                return false
            }

            val responseBody = response.body?.bytes()
            if (responseBody == null) {
                Log.w(TAG, "Widevine provisioning probe failed: empty response body")
                return false
            }

            mediaDrm.provideProvisionResponse(responseBody)
            Log.i(TAG, "Widevine provisioning probe succeeded")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Widevine provisioning probe failed: ${e.message}")
            false
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
