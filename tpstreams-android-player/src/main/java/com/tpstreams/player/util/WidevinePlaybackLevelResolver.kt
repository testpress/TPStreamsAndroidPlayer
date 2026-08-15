package com.tpstreams.player.util

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaDrm
import android.os.Build
import android.util.Log
import androidx.media3.common.PlaybackException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Resolves and caches which Widevine level to use for all DRM playback.
 *
 * Resolution is non-blocking:
 * 1. Return persisted L3 if a previous session marked this device as permanently L3-only.
 * 2. Use native L3 when the device reports L3.
 * 3. For L1-capable devices, immediately default to native L1 without blocking player creation,
 *    and kick off an asynchronous provisioning probe on a background dispatcher.
 * 4. Only persist L3 when the provisioning server explicitly rejects the device certificate
 *    (e.g., HTTP 4xx / DeniedByServerException); transient network/timeout/5xx errors do NOT persist L3.
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

    enum class ProvisioningProbeResult {
        SUCCESS,
        PERMANENT_FAILURE,
        TRANSIENT_FAILURE
    }

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
                // Default immediately to native level (L1) without blocking the calling thread
                cachedLevel = WidevinePlaybackLevel.L1
                Log.i(
                    TAG,
                    "Defaulting playback level to L1 (native=$nativeLevel); launching background provisioning probe",
                )
                // Run provisioning probe asynchronously in background
                CoroutineScope(Dispatchers.IO).launch {
                    when (runProvisioningProbe()) {
                        ProvisioningProbeResult.PERMANENT_FAILURE -> {
                            Log.w(TAG, "Provisioning probe permanently rejected device; persisting L3")
                            persistForceL3(prefs)
                        }
                        ProvisioningProbeResult.SUCCESS -> {
                            Log.i(TAG, "Provisioning probe succeeded; maintaining L1")
                        }
                        ProvisioningProbeResult.TRANSIENT_FAILURE -> {
                            Log.i(TAG, "Provisioning probe transient failure; maintaining L1 for this session without persisting L3")
                        }
                    }
                }
            }
        }
    }

    fun getPlaybackLevel(): WidevinePlaybackLevel {
        return cachedLevel ?: WidevinePlaybackLevel.L1
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
        probeResult: ProvisioningProbeResult,
        onPersistForceL3: () -> Unit,
    ): WidevinePlaybackLevel {
        if (forceL3Persisted || nativeLevel == "L3") {
            return WidevinePlaybackLevel.L3
        }

        return when (probeResult) {
            ProvisioningProbeResult.PERMANENT_FAILURE -> {
                onPersistForceL3()
                WidevinePlaybackLevel.L3
            }
            ProvisioningProbeResult.SUCCESS,
            ProvisioningProbeResult.TRANSIENT_FAILURE -> {
                WidevinePlaybackLevel.L1
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

    private fun runProvisioningProbe(): ProvisioningProbeResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) return ProvisioningProbeResult.TRANSIENT_FAILURE

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
                val code = response.code
                Log.w(TAG, "Widevine provisioning probe failed: HTTP $code")
                // HTTP 4xx (e.g. 400, 403, 404) indicates explicit client/certificate rejection by the server.
                // HTTP 5xx indicates transient server issue.
                return if (code in 400..499) {
                    ProvisioningProbeResult.PERMANENT_FAILURE
                } else {
                    ProvisioningProbeResult.TRANSIENT_FAILURE
                }
            }

            val responseBody = response.body?.bytes()
            if (responseBody == null) {
                Log.w(TAG, "Widevine provisioning probe failed: empty response body")
                return ProvisioningProbeResult.TRANSIENT_FAILURE
            }

            mediaDrm.provideProvisionResponse(responseBody)
            Log.i(TAG, "Widevine provisioning probe succeeded")
            ProvisioningProbeResult.SUCCESS
        } catch (e: Exception) {
            Log.w(TAG, "Widevine provisioning probe failed: ${e.message}")
            if (e is android.media.DeniedByServerException || e.javaClass.simpleName.contains("DeniedByServer")) {
                ProvisioningProbeResult.PERMANENT_FAILURE
            } else {
                ProvisioningProbeResult.TRANSIENT_FAILURE
            }
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
