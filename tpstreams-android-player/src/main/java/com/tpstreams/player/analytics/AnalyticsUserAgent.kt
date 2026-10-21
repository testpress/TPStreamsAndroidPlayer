package com.tpstreams.player.analytics

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.tpstreams.player.BuildConfig

object AnalyticsUserAgent {
    fun build(context: Context): String {
        val appName = try {
            val appInfo = context.applicationInfo
            context.packageManager?.getApplicationLabel(appInfo)?.toString()?.ifBlank { "TPStreamsPlayer" } ?: "TPStreamsPlayer"
        } catch (_: Throwable) {
            "TPStreamsPlayer"
        }

        val appVersion = try {
            val pInfo = context.packageManager?.getPackageInfo(context.packageName, 0)
            pInfo?.versionName ?: BuildConfig.SDK_VERSION
        } catch (_: Throwable) {
            BuildConfig.SDK_VERSION
        }

        val isTablet = try {
            (context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        } catch (_: Throwable) {
            false
        }

        val osVersion = try { Build.VERSION.RELEASE } catch (_: Throwable) { "Unknown" }
        val deviceModel = try {
            Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android Device"
        } catch (_: Throwable) {
            "Android Device"
        }
        val browserDeviceSuffix = if (isTablet) {
            "Safari/537.36"
        } else {
            "Mobile Safari/537.36"
        }

        // Keep the SDK identity, but use the standard Android shape expected by
        // backend user-agent parsers. The Mobile token is intentionally omitted
        // for tablets so normal device classification remains intact.
        return "Mozilla/5.0 (Linux; Android $osVersion; $deviceModel) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) $browserDeviceSuffix " +
            "$appName/$appVersion"
    }
}
