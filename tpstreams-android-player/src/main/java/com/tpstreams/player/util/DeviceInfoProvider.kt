package com.tpstreams.player.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Permission-free provider of device hardware and OS information.
 *
 * Context-free fields (manufacturer, model, brand, version, SDK, emulator) are
 * collected once and cached permanently. Context-dependent fields (screen
 * resolution, locale) are collected fresh on every call via the supplied
 * [context] parameter — no global state required.
 *
 * Tags (included in every Sentry event): manufacturer, device_model, device_brand,
 * hardware, android_version, sdk_int, is_emulator, screen_resolution
 * Context (full details on demand): all fields including device_locale
 */
internal object DeviceInfoProvider {

    // ── Context-free fields: cached once ────────────────────────────────

    private val manufacturer: String? by lazy { safeGet { Build.MANUFACTURER } }
    private val deviceModel: String? by lazy { safeGet { Build.MODEL } }
    private val deviceBrand: String? by lazy { safeGet { Build.BRAND } }
    private val chipBrand: String? by lazy { safeGet { resolveChipBrand() } }
    private val androidVersion: String? by lazy { safeGet { Build.VERSION.RELEASE } }
    private val sdkInt: Int? by lazy { safeGet { Build.VERSION.SDK_INT } }
    private val emulator: Boolean? by lazy { safeGet { isEmulator() } }

    // ── Public API ──────────────────────────────────────────────────────

    /** Returns tags for searchability in Sentry. Context needed for screen resolution. */
    fun getTags(context: Context? = null): Map<String, String> {
        return buildMap {
            manufacturer?.let { put("manufacturer", it) }
            deviceModel?.let { put("device_model", it) }
            deviceBrand?.let { put("device_brand", it) }
            chipBrand?.let { put("hardware", it) }
            androidVersion?.let { put("android_version", it) }
            sdkInt?.let { put("sdk_int", it.toString()) }
            emulator?.let { put("is_emulator", it.toString()) }
            if (context != null) {
                safeGet { resolveScreenResolution(context) }?.let { put("screen_resolution", it) }
            }
        }
    }

    /** Returns the full device context for Sentry's contexts mechanism. */
    fun getContext(context: Context? = null): Map<String, Any> {
        return buildMap {
            manufacturer?.let { put("manufacturer", it) }
            deviceModel?.let { put("device_model", it) }
            deviceBrand?.let { put("device_brand", it) }
            chipBrand?.let { put("hardware", it) }
            androidVersion?.let { put("android_version", it) }
            sdkInt?.let { put("sdk_int", it) }
            if (context != null) {
                safeGet { resolveScreenResolution(context) }?.let { put("screen_resolution", it) }
                safeGet { resolveDeviceLocale(context) }?.let { put("device_locale", it) }
            }
            emulator?.let { put("is_emulator", it) }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun resolveScreenResolution(context: Context): String? {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return null
        val display = wm.defaultDisplay ?: return null
        val metrics = DisplayMetrics().also { display.getRealMetrics(it) }
        return "${metrics.widthPixels}x${metrics.heightPixels}@${metrics.densityDpi}dpi"
    }

    private fun resolveChipBrand(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MANUFACTURER?.takeIf { it.isNotBlank() }
        } else {
            Build.HARDWARE?.takeIf { it.isNotBlank() }
        }
    }

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT
        val model = Build.MODEL
        val hardware = Build.HARDWARE
        return fingerprint.contains("google_sdk") ||
                fingerprint.contains("generic") ||
                model.contains("sdk") ||
                model.contains("emulator") ||
                hardware.contains("goldfish") ||
                hardware.contains("ranchu")
    }

    private fun resolveDeviceLocale(context: Context): String? {
        val config = context.resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.locales.get(0).toLanguageTag()
        } else {
            @Suppress("DEPRECATION")
            config.locale.toLanguageTag()
        }
    }

    private fun <T> safeGet(block: () -> T?): T? = try {
        block()
    } catch (e: Exception) {
        android.util.Log.d(TAG, "Failed to collect device info field", e)
        null
    }

    private const val TAG = "DeviceInfoProvider"
}
