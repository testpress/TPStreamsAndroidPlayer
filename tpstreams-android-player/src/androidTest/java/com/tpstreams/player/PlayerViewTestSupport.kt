package com.tpstreams.player

import android.view.WindowManager
import org.junit.Assert.assertEquals

internal object PlayerViewTestSupport {
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

