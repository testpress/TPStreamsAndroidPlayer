package com.tpstreams.player

import android.content.pm.ActivityInfo
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class FullscreenModeDeviceTest {

    @Test
    fun enterAndExitFullscreen_togglesSystemBarsAndInsetsBehavior() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val rootLayout = FrameLayout(activity)
                activity.setContentView(rootLayout)

                val originalContainer = FrameLayout(activity)
                rootLayout.addView(originalContainer)

                val playerView = TPStreamsPlayerView(activity)
                originalContainer.addView(playerView)

                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                val initialBehavior = controller.systemBarsBehavior

                val fullscreenMode = FullscreenMode(playerView)

                // 1. Enter Fullscreen
                fullscreenMode.enterFullscreen()

                assertTrue("Should be in fullscreen mode", fullscreenMode.isInFullscreenMode())
                assertEquals(
                    "Player view should be attached to DecorView",
                    activity.window.decorView,
                    playerView.parent
                )
                assertEquals(
                    "System bars behavior should be BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE",
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
                    controller.systemBarsBehavior
                )
                assertEquals(
                    "Activity orientation should switch to SENSOR_LANDSCAPE",
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                    activity.requestedOrientation
                )

                // 2. Exit Fullscreen
                fullscreenMode.exitFullscreen()

                assertFalse("Should not be in fullscreen mode", fullscreenMode.isInFullscreenMode())
                assertEquals(
                    "Player view should be restored to its original container",
                    originalContainer,
                    playerView.parent
                )
                assertEquals(
                    "System bars behavior should be restored to its initial value",
                    initialBehavior,
                    controller.systemBarsBehavior
                )
            }
        }
    }

    @Test
    fun enterFullscreen_preservesCustomHostSystemBarsBehaviorOnExit() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val rootLayout = FrameLayout(activity)
                activity.setContentView(rootLayout)

                val playerView = TPStreamsPlayerView(activity)
                rootLayout.addView(playerView)

                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                // Explicitly set a custom host behavior
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT

                val fullscreenMode = FullscreenMode(playerView)

                fullscreenMode.enterFullscreen()
                assertEquals(
                    "Should temporarily switch to transient swipe behavior",
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
                    controller.systemBarsBehavior
                )

                fullscreenMode.exitFullscreen()
                assertEquals(
                    "Should restore host's custom BEHAVIOR_DEFAULT on exit",
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT,
                    controller.systemBarsBehavior
                )
            }
        }
    }
}
