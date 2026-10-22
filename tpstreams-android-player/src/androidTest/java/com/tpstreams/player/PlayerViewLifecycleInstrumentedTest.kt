package com.tpstreams.player

import android.widget.FrameLayout
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerViewLifecycleInstrumentedTest {

    @Test
    fun removingPlayerWhileFullscreen_restoresOriginalParentAndWindowState() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            var player: TPStreamsPlayer? = null

            try {
                scenario.onActivity { activity ->
                    TPStreamsSDK.init("player-view-test")
                    val root = FrameLayout(activity)
                    val originalContainer = FrameLayout(activity)
                    val playerView = TPStreamsPlayerView(activity)
                    activity.setContentView(root)
                    root.addView(originalContainer)
                    originalContainer.addView(playerView)

                    player = PlayerViewTestSupport.createIdlePlayer(activity, "fullscreen-cleanup")
                    playerView.player = player
                    playerView.toggleFullscreen()
                    assertEquals("Fullscreen PlayerView should be attached to the window decor", activity.window.decorView, playerView.parent)

                    playerView.player = null

                    assertEquals("Removing the player should restore the original parent", originalContainer, playerView.parent)
                    PlayerViewTestSupport.assertSecureFlag(activity, false)
                }
            } finally {
                scenario.onActivity { player?.release() }
            }
        }
    }

    @Test
    fun detachAndReattach_keepsPlayerAndReacquiresWindowProtection() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            var player: TPStreamsPlayer? = null

            try {
                scenario.onActivity { activity ->
                    TPStreamsSDK.init("player-view-test")
                    val root = FrameLayout(activity)
                    val playerView = TPStreamsPlayerView(activity)
                    activity.setContentView(root)
                    root.addView(playerView)

                    player = PlayerViewTestSupport.createIdlePlayer(activity, "detach-reattach")
                    playerView.player = player
                    PlayerViewTestSupport.assertSecureFlag(activity, true)

                    root.removeView(playerView)
                    PlayerViewTestSupport.assertSecureFlag(activity, false)

                    root.addView(playerView)
                    assertEquals("Reattached PlayerView should retain the same player", player, playerView.player)
                    PlayerViewTestSupport.assertSecureFlag(activity, true)
                }
            } finally {
                scenario.onActivity { player?.release() }
            }
        }
    }
}

