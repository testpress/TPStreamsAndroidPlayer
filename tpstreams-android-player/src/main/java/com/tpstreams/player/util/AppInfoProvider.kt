package com.tpstreams.player.util

import android.content.Context
import android.os.Build

/**
 * Provides information about the host application consuming the SDK.
 */
internal object AppInfoProvider {

    fun getHostAppVersion(context: Context? = null): String? {
        if (context == null) return null
        val packageName = safeGet { getAppPackageName(context) }
        val versionName = safeGet { getAppVersionName(context) }
        val versionCode = safeGet { getAppVersionCode(context) }

        return if (packageName != null && versionName != null && versionCode != null) {
            "$packageName@$versionName+$versionCode"
        } else null
    }

    private fun getAppPackageName(context: Context): String? {
        return context.packageName
    }

    private fun getAppVersionName(context: Context): String? {
        return context.packageManager?.getPackageInfo(context.packageName, 0)?.versionName
    }

    private fun getAppVersionCode(context: Context): String? {
        val packageInfo = context.packageManager?.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode?.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toString()
        }
    }

    private fun <T> safeGet(block: () -> T?): T? = try {
        block()
    } catch (e: Exception) {
        null
    }
}
