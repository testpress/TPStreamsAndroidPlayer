package com.tpstreams.player

import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals

internal object PlayerViewTestSupport {
    const val DEFAULT_ORG_ID = "9q94nm"
    const val DEFAULT_NON_DRM_ASSET_ID = "4Zs4MNd5Ksj"
    const val DEFAULT_DRM_ASSET_ID = "7xbZeQzR36h"
    const val DEFAULT_ACCESS_TOKEN = "3d9838f3-db51-4fc3-8472-075ab5e40b64"
    const val DEFAULT_DOWNLOAD_RESOLUTION = "360p"

    fun instrumentationArgumentOrDefault(name: String, default: String): String =
        InstrumentationRegistry.getArguments().getString(name)?.takeIf { it.isNotBlank() } ?: default

    fun requiredInstrumentationArgument(name: String): String {
        val value = InstrumentationRegistry.getArguments().getString(name)?.trim()
        assumeTrue(
            "Missing required instrumentation argument '$name'. Provide it from CI secrets/device-test configuration.",
            !value.isNullOrEmpty()
        )
        return value!!
    }

    fun createIdlePlayer(
        activity: FullscreenTestActivity,
        assetId: String
    ): TPStreamsPlayer = TPStreamsPlayer.create(
        context = activity,
        assetId = assetId,
        accessToken = "player-view-test-token",
        shouldAutoPlay = false
    )

    fun assertSecureFlag(activity: FullscreenTestActivity, expected: Boolean) {
        val isSet = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        assertEquals("FLAG_SECURE state", expected, isSet)
    }
}

