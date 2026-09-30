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

    @Test
    fun enterFullscreen_whenHostBarsInitiallyHidden_keepsBarsHiddenOnExit() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val rootLayout = FrameLayout(activity)
                activity.setContentView(rootLayout)

                val playerView = TPStreamsPlayerView(activity)
                rootLayout.addView(playerView)

                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                // Simulate host having hidden the system bars beforehand (e.g. immersive mode)
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())

                val fullscreenMode = FullscreenMode(playerView)

                fullscreenMode.enterFullscreen()
                assertTrue(fullscreenMode.isInFullscreenMode())

                fullscreenMode.exitFullscreen()
                assertFalse(fullscreenMode.isInFullscreenMode())
                // Ensure exit doesn't leave the view stranded or fail
                assertEquals(rootLayout, playerView.parent)
            }
        }
    }

    @Test
    fun enterFullscreen_activityRecreatedWithoutConfigChanges_restoresFullscreenAutomatically() {
        var retainedPlayer: TPStreamsPlayer? = null

        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                try {
                    TPStreamsSDK.init("test_org")
                } catch (_: Exception) {}

                val player = TPStreamsPlayer.create(
                    context = activity,
                    assetId = "test_asset",
                    accessToken = "test_token"
                )
                retainedPlayer = player

                val rootLayout = FrameLayout(activity)
                activity.setContentView(rootLayout)

                val originalContainer = FrameLayout(activity)
                rootLayout.addView(originalContainer)

                val playerView = TPStreamsPlayerView(activity)
                originalContainer.addView(playerView)
                playerView.player = player

                val fullscreenMode = FullscreenMode(playerView)
                fullscreenMode.enterFullscreen()

                assertTrue(player.isFullscreenRequested)
                assertTrue(fullscreenMode.isInFullscreenMode())
                assertEquals(activity.window.decorView, playerView.parent)
            }

            // Recreate activity (simulating configuration change on orientation change without configChanges)
            scenario.recreate()

            scenario.onActivity { activity2 ->
                val newRootLayout = FrameLayout(activity2)
                activity2.setContentView(newRootLayout)

                val newOriginalContainer = FrameLayout(activity2)
                newRootLayout.addView(newOriginalContainer)

                val newPlayerView = TPStreamsPlayerView(activity2)
                newOriginalContainer.addView(newPlayerView)

                // Reattach the retained player to the new view in recreated activity
                newPlayerView.player = retainedPlayer

                assertTrue("Player should still have isFullscreenRequested = true", retainedPlayer!!.isFullscreenRequested)
            }

            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity2 ->
                // Look for the playerView in DecorView of activity2
                val decorView = activity2.window.decorView as android.view.ViewGroup
                var foundInDecor = false
                for (i in 0 until decorView.childCount) {
                    if (decorView.getChildAt(i) is TPStreamsPlayerView) {
                        foundInDecor = true
                        break
                    }
                }
                assertTrue("New playerView should be automatically attached to DecorView after recreation", foundInDecor)
            }
        }
    }

    @Test
    fun exitFullscreen_activityRecreatedWithoutConfigChanges_staysInOriginalContainer() {
        var retainedPlayer: TPStreamsPlayer? = null

        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                try {
                    TPStreamsSDK.init("test_org")
                } catch (_: Exception) {}

                val player = TPStreamsPlayer.create(
                    context = activity,
                    assetId = "test_asset",
                    accessToken = "test_token"
                )
                retainedPlayer = player

                val rootLayout = FrameLayout(activity)
                activity.setContentView(rootLayout)

                val originalContainer = FrameLayout(activity)
                rootLayout.addView(originalContainer)

                val playerView = TPStreamsPlayerView(activity)
                originalContainer.addView(playerView)
                playerView.player = player

                val fullscreenMode = FullscreenMode(playerView)
                fullscreenMode.enterFullscreen()
                fullscreenMode.exitFullscreen()

                assertFalse(player.isFullscreenRequested)
                assertFalse(fullscreenMode.isInFullscreenMode())
                assertEquals(originalContainer, playerView.parent)
            }

            // Recreate activity
            scenario.recreate()

            var newOriginalContainerRef: FrameLayout? = null
            var newPlayerViewRef: TPStreamsPlayerView? = null

            scenario.onActivity { activity2 ->
                val newRootLayout = FrameLayout(activity2)
                activity2.setContentView(newRootLayout)

                val newOriginalContainer = FrameLayout(activity2)
                newRootLayout.addView(newOriginalContainer)
                newOriginalContainerRef = newOriginalContainer

                val newPlayerView = TPStreamsPlayerView(activity2)
                newOriginalContainer.addView(newPlayerView)
                newPlayerViewRef = newPlayerView

                // Reattach the retained player
                newPlayerView.player = retainedPlayer

                assertFalse("Player should have isFullscreenRequested = false", retainedPlayer!!.isFullscreenRequested)
            }

            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                assertEquals(
                    "PlayerView should remain in original container after recreation",
                    newOriginalContainerRef,
                    newPlayerViewRef?.parent
                )
            }
        }
    }
}
