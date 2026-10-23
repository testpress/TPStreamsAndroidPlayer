package com.tpstreams.player

import android.view.View
import android.widget.SeekBar
import androidx.fragment.app.DialogFragment
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerBottomSheetInteractionInstrumentedTest {

    @Test
    fun settings_quality_and_playbackSpeedSheets_openWithExpectedOptions() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val playerView = TPStreamsPlayerView(activity)
                activity.setContentView(playerView)

                playerView.settingsBottomSheet.show(activity.supportFragmentManager)
            }
            val settings = awaitDialogView(scenario, PlayerSettingsBottomSheet.TAG)
            assertNotNull("Settings sheet is missing the Quality option", settings.findViewById<View>(R.id.quality_item))
            assertNotNull("Settings sheet is missing the Captions option", settings.findViewById<View>(R.id.captions_item))
            assertNotNull("Settings sheet is missing the Playback Speed option", settings.findViewById<View>(R.id.playback_speed_item))
            scenario.onActivity {
                (it.supportFragmentManager.findFragmentByTag(PlayerSettingsBottomSheet.TAG) as? DialogFragment)
                    ?.dismiss()
            }

            scenario.onActivity { activity ->
                val view = requirePlayerView(activity, "before opening the Quality sheet")
                view.qualityOptionsBottomSheet.setCurrentQuality(QualityOptionsBottomSheet.QUALITY_AUTO)
                view.qualityOptionsBottomSheet.show(activity.supportFragmentManager)
            }
            val quality = awaitDialogView(scenario, QualityOptionsBottomSheet.TAG)
            assertNotNull("Quality sheet is missing the Auto option", quality.findViewById<View>(R.id.auto_quality_option))
            assertNotNull("Quality sheet is missing the Higher quality option", quality.findViewById<View>(R.id.higher_quality_option))
            assertNotNull("Quality sheet is missing the Data saver option", quality.findViewById<View>(R.id.data_saver_option))
            assertNotNull("Quality sheet is missing the Advanced option", quality.findViewById<View>(R.id.advanced_quality_option))
            scenario.onActivity {
                (it.supportFragmentManager.findFragmentByTag(QualityOptionsBottomSheet.TAG) as? DialogFragment)
                    ?.dismiss()
            }

            scenario.onActivity { activity ->
                requirePlayerView(activity, "before opening the Playback Speed sheet")
                    .playbackSpeedBottomSheet.show(activity.supportFragmentManager)
            }
            val speed = awaitDialogView(scenario, PlaybackSpeedBottomSheet.TAG)
            assertNotNull("Playback Speed sheet is missing its seek bar", speed.findViewById<SeekBar>(R.id.speed_seekbar))
            assertNotNull("Playback Speed sheet is missing the 1.5x preset", speed.findViewById<View>(R.id.speed_1_5x_button))
        }
    }

    @Test
    fun selectingResolutionFromAdvancedSheet_changesActualVideoHeight() {
        val orgId = PlayerViewTestSupport.instrumentationArgumentOrDefault("orgId", PlayerViewTestSupport.DEFAULT_ORG_ID)
        val assetId = PlayerViewTestSupport.instrumentationArgumentOrDefault("assetId", PlayerViewTestSupport.DEFAULT_NON_DRM_ASSET_ID)
        val token = PlayerViewTestSupport.instrumentationArgumentOrDefault("accessToken", PlayerViewTestSupport.DEFAULT_ACCESS_TOKEN)
        val ready = CountDownLatch(1)
        var player: TPStreamsPlayer? = null

        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                TPStreamsSDK.init(orgId, allowFallbackToL3 = true)
                val view = TPStreamsPlayerView(activity)
                activity.setContentView(view)
                player = TPStreamsPlayer.create(activity, assetId, token, shouldAutoPlay = false)
                player!!.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) ready.countDown()
                    }
                })
                view.player = player
            }

            assumeTrue("asset did not become ready", ready.await(30, TimeUnit.SECONDS))
            val available = scenarioOnActivity(scenario) { player!!.getAvailableVideoResolutions() }
            assumeTrue("asset has fewer than two video resolutions", available.size >= 2)
            val target = available.last()

            scenario.onActivity { activity ->
                val view = requirePlayerView(activity, "before opening the Advanced Resolution sheet")
                view.advancedResolutionBottomSheet.setAvailableResolutions(available.map { "${it}p" })
                view.advancedResolutionBottomSheet.show(activity.supportFragmentManager)
            }
            val sheet = awaitDialogView(scenario, AdvancedResolutionBottomSheet.TAG)
            assertEquals(
                "Advanced Resolution sheet did not render all available resolutions: $available",
                available.size,
                sheet.findViewById<RecyclerView>(R.id.resolutions_recycler_view).adapter?.itemCount
            )
            val targetPosition = available.indexOf(target)
            scenario.onActivity {
                val list = requirePlayerView(it, "while selecting resolution").advancedResolutionBottomSheet.dialog
                    ?.findViewById<RecyclerView>(R.id.resolutions_recycler_view)
                    ?: throw AssertionError("Resolution list was not created")
                list.scrollToPosition(targetPosition)
                list.findViewHolderForAdapterPosition(targetPosition)?.itemView?.performClick()
                    ?: throw AssertionError("Resolution option was not rendered")
            }

            val changed = waitUntil(5_000) {
                scenarioOnActivity(scenario) { player!!.videoSize.height == target }
            }
            assertTrue(
                "Selecting ${target}p from the Advanced Resolution sheet did not change the actual video height " +
                    "within 5 seconds; actual height=${scenarioOnActivity(scenario) { player!!.videoSize.height }}",
                changed
            )
            assertEquals(
                "The actual video height does not match the selected ${target}p resolution",
                target,
                scenarioOnActivity(scenario) { player!!.videoSize.height }
            )
            scenario.onActivity { player!!.release() }
        }
    }

    private fun awaitDialogView(
        scenario: ActivityScenario<FullscreenTestActivity>,
        tag: String,
    ): View {
        var view: View? = null
        waitUntil(2_000) {
            scenario.onActivity { view = it.supportFragmentManager.findFragmentByTag(tag)?.view }
            view != null
        }
        return view ?: throw AssertionError("Bottom sheet $tag did not open")
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }

    private fun <T> scenarioOnActivity(
        scenario: ActivityScenario<FullscreenTestActivity>,
        block: FullscreenTestActivity.() -> T,
    ): T {
        var result: T? = null
        scenario.onActivity { result = block(it) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun findPlayerView(activity: FullscreenTestActivity): TPStreamsPlayerView? {
        fun search(view: View): TPStreamsPlayerView? {
            if (view is TPStreamsPlayerView) return view
            if (view is android.view.ViewGroup) {
                for (index in 0 until view.childCount) search(view.getChildAt(index))?.let { return it }
            }
            return null
        }
        return search(activity.findViewById(android.R.id.content))
    }

    private fun requirePlayerView(
        activity: FullscreenTestActivity,
        action: String,
    ): TPStreamsPlayerView = findPlayerView(activity)
        ?: throw AssertionError("PlayerView was not present $action")
}
