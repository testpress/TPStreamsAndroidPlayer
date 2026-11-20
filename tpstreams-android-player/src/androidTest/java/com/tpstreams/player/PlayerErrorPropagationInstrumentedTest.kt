package com.tpstreams.player

import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpstreams.player.constants.PlaybackError
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that an error raised during player initialization is displayed after the player view
 * attaches. This reproduces the timing of DRM failures that occur before the app assigns the
 * player to TPStreamsPlayerView.
 */
@RunWith(AndroidJUnit4::class)
class PlayerErrorPropagationInstrumentedTest {

    @Test
    fun errorRaisedBeforeViewAttachment_isDisplayedByPlayerView() {
        val playerRef = AtomicReference<TPStreamsPlayer>()
        val expectedMessage = "This device doesn't support playback of this video."

        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                TPStreamsSDK.init(
                    PlayerViewTestSupport.DEFAULT_ORG_ID,
                    TPStreamsSDK.Provider.TPStreams,
                    allowFallbackToL3 = false,
                )

                val player = TPStreamsPlayer.create(
                    context = activity,
                    assetId = PlayerViewTestSupport.DEFAULT_NON_DRM_ASSET_ID,
                    accessToken = PlayerViewTestSupport.DEFAULT_ACCESS_TOKEN,
                    shouldAutoPlay = false,
                )
                playerRef.set(player)

                // Simulate ExoPlayer/Media3 reporting the DRM failure before the view is wired.
                player.notifyError(PlaybackError.DRM_SCHEME_UNSUPPORTED, expectedMessage)

                val appErrorCount = AtomicInteger(0)
                player.listener = object : TPStreamsPlayer.Listener {
                    override fun onError(error: PlaybackError, message: String) {
                        appErrorCount.incrementAndGet()
                    }
                }
                assertEquals(0, appErrorCount.get())

                val playerView = TPStreamsPlayerView(activity)
                activity.setContentView(FrameLayout(activity).apply { addView(playerView) })
                playerView.player = player
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val errorText = activity.findViewById<TextView>(R.id.error_message_text)
                assertEquals(expectedMessage, errorText.text.toString())

                val secondView = TPStreamsPlayerView(activity)
                activity.setContentView(FrameLayout(activity).apply { addView(secondView) })
                secondView.player = playerRef.get()
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val errorText = activity.findViewById<TextView>(R.id.error_message_text)
                assertEquals(expectedMessage, errorText.text.toString())
                playerRef.get().release()
            }
        }
    }
}
