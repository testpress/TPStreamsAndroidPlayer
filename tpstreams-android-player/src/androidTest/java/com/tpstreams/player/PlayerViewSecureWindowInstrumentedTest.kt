package com.tpstreams.player

import android.widget.FrameLayout
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerViewSecureWindowInstrumentedTest {

    @Test
    fun multipleAttachedViews_keepSecureFlag_untilLastPlayerIsRemoved() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            var firstPlayer: TPStreamsPlayer? = null
            var secondPlayer: TPStreamsPlayer? = null

            try {
                scenario.onActivity { activity ->
                    TPStreamsSDK.init("player-view-test")
                    val root = FrameLayout(activity)
                    val firstView = TPStreamsPlayerView(activity)
                    val secondView = TPStreamsPlayerView(activity)
                    activity.setContentView(root)
                    root.addView(firstView, FrameLayout.LayoutParams(1, 1))
                    root.addView(secondView, FrameLayout.LayoutParams(1, 1))

                    firstPlayer = PlayerViewTestSupport.createIdlePlayer(activity, "secure-first")
                    secondPlayer = PlayerViewTestSupport.createIdlePlayer(activity, "secure-second")
                    firstView.player = firstPlayer
                    secondView.player = secondPlayer

                    PlayerViewTestSupport.assertSecureFlag(activity, true)
                    firstView.player = null
                    PlayerViewTestSupport.assertSecureFlag(activity, true)
                    secondView.player = null
                    PlayerViewTestSupport.assertSecureFlag(activity, false)
                }
            } finally {
                scenario.onActivity {
                    firstPlayer?.release()
                    secondPlayer?.release()
                }
            }
        }
    }
}

