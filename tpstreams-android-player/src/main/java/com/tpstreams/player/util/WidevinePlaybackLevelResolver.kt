package com.tpstreams.player.util

import android.content.Context
import android.content.SharedPreferences
import android.media.DeniedByServerException
import android.media.MediaDrm
import android.os.Build
import android.util.Log
import androidx.media3.common.PlaybackException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cachedLevel: WidevinePlaybackLevel? = null

    @Volatile
    private var nativeLevel: String? = null

    @Volatile
    private var allowFallbackToL3: Boolean = false

    private val initLock = Any()

    enum class ProvisioningProbeResult {
        SUCCESS,
        PERMANENT_FAILURE,
        TRANSIENT_FAILURE
    }

    fun initialize(context: Context, allowFallbackToL3: Boolean = false) {
        synchronized(initLock) {
            this.allowFallbackToL3 = this.allowFallbackToL3 || allowFallbackToL3
            if (cachedLevel != null) return

            val applicationContext = context.applicationContext
            appContext = applicationContext
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            nativeLevel = readNativeWidevineLevel()
            val forceL3Persisted = prefs.getBoolean(KEY_FORCE_L3, false)

            if (nativeLevel == "L3" || (this.allowFallbackToL3 && forceL3Persisted)) {
                cachedLevel = WidevinePlaybackLevel.L3
                Log.i(
                    TAG,
                    "Resolved playback level=L3, native=$nativeLevel, persistedL3=$forceL3Persisted, fallbackAllowed=${this.allowFallbackToL3}",
                )
            } else {
                cachedLevel = WidevinePlaybackLevel.L1
                Log.i(
                    TAG,
                    "Defaulting playback level to L1 (native=$nativeLevel, fallbackAllowed=${this.allowFallbackToL3})",
                )
                if (this.allowFallbackToL3) {
                    probeScope.launch {
                        when (runProvisioningProbe()) {
                            ProvisioningProbeResult.PERMANENT_FAILURE -> {
                                Log.w(TAG, "Provisioning probe permanently rejected device; persisting L3")
                                synchronized(initLock) {
                                    persistForceL3ToPrefs(prefs)
                                }
                            }
                            ProvisioningProbeResult.SUCCESS -> {
                                Log.i(TAG, "Provisioning probe succeeded; maintaining L1")
                            }
                            ProvisioningProbeResult.TRANSIENT_FAILURE -> {
                                Log.i(
                                    TAG,
                                    "Provisioning probe transient failure; maintaining L1 for this session without persisting L3",
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun isFallbackAllowed(): Boolean = allowFallbackToL3

    fun shouldUseL3Drm(): Boolean {
        if (nativeLevel == "L3") return true
        if (!allowFallbackToL3) return false
        return cachedLevel == WidevinePlaybackLevel.L3
    }

    fun getPlaybackLevel(): WidevinePlaybackLevel {
        return cachedLevel ?: WidevinePlaybackLevel.L1
    }

    fun getPlaybackLevelOrNull(): WidevinePlaybackLevel? = cachedLevel

    fun getNativeWidevineLevel(): String? = nativeLevel

    fun isAlreadyOnL3PlaybackLevel(): Boolean = getPlaybackLevelOrNull() == WidevinePlaybackLevel.L3

    fun forceL3ForSession() {
        if (!allowFallbackToL3) return
        synchronized(initLock) {
            cachedLevel = WidevinePlaybackLevel.L3
        }
        Log.i(TAG, "L3 forced for current session only (not persisted)")
    }

    fun persistForceL3(context: Context? = appContext) {
        if (!allowFallbackToL3) return
        val prefs = (context ?: appContext)?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return
        synchronized(initLock) {
            persistForceL3ToPrefs(prefs)
        }
    }

    fun isDrmPermanentFailure(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED
    }

    fun isDrmFallbackError(error: PlaybackException): Boolean {
        if (when (error.errorCode) {
                PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
                PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
                PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
                PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
                PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> true
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

    private fun persistForceL3ToPrefs(prefs: SharedPreferences) {
        if (prefs.getBoolean(KEY_FORCE_L3, false)) {
            cachedLevel = WidevinePlaybackLevel.L3
            Log.i(TAG, "L3 playback level already persisted; skipping duplicate write")
            return
        }
        prefs.edit().putBoolean(KEY_FORCE_L3, true).apply()
        cachedLevel = WidevinePlaybackLevel.L3
        Log.i(TAG, "Persisted L3 playback level for future sessions")
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
            releaseMediaDrm(mediaDrm)
        }
    }

    private fun runProvisioningProbe(): ProvisioningProbeResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return ProvisioningProbeResult.TRANSIENT_FAILURE
        }

        var mediaDrm: MediaDrm? = null
        return try {
            mediaDrm = MediaDrm(WIDEVINE_UUID)
            val request = mediaDrm.getProvisionRequest()
            val defaultUrl = request.defaultUrl
            if (defaultUrl.isNullOrBlank()) {
                Log.w(TAG, "Widevine provisioning probe failed: empty defaultUrl")
                return ProvisioningProbeResult.TRANSIENT_FAILURE
            }

            val httpRequest = Request.Builder()
                .url(defaultUrl)
                .post(request.data.toRequestBody("application/octet-stream".toMediaType()))
                .build()

            provisioningClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    Log.w(TAG, "Widevine provisioning probe failed: HTTP $code")
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
            }
        } catch (e: DeniedByServerException) {
            Log.w(TAG, "Widevine provisioning probe denied by server: ${e.message}")
            ProvisioningProbeResult.PERMANENT_FAILURE
        } catch (e: Exception) {
            Log.w(TAG, "Widevine provisioning probe failed: ${e.message}")
            ProvisioningProbeResult.TRANSIENT_FAILURE
        } finally {
            releaseMediaDrm(mediaDrm)
        }
    }

    private fun releaseMediaDrm(mediaDrm: MediaDrm?) {
        mediaDrm ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mediaDrm.close()
        } else {
            @Suppress("DEPRECATION")
            mediaDrm.release()
        }
    }
}
