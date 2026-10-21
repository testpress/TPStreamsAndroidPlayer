package com.tpstreams.player.analytics

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.tpstreams.player.BuildConfig

internal object AnalyticsUserAgent {
    fun build(context: Context): String {
        val isTablet = try {
            (context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        } catch (_: Throwable) {
            false
        }

        val osVersion = try { Build.VERSION.RELEASE } catch (_: Throwable) { "Unknown" }
        val browserDeviceSuffix = if (isTablet) {
            "Safari/537.36"
        } else {
            "Mobile Safari/537.36"
        }
        val deviceCategory = if (isTablet) "Tablet" else "Mobile"

        // Keep the SDK identity, but use the standard Android shape expected by
        // backend user-agent parsers. The Mobile token is intentionally omitted
        // for tablets so normal device classification remains intact.
        return "Mozilla/5.0 (Linux; Android $osVersion; $deviceCategory) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) $browserDeviceSuffix " +
            "TPStreamsAndroidPlayer/${BuildConfig.SDK_VERSION}"
    }
}
