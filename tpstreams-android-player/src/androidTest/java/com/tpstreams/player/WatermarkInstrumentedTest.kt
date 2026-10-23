package com.tpstreams.player

import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class WatermarkInstrumentedTest {

    @Test
    fun textAndImageWatermarks_areAppliedTogetherAndCanBeCleared() {
        ActivityScenario.launch(FullscreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val playerView = TPStreamsPlayerView(activity)
                activity.setContentView(playerView)

                val contentFrame = playerView.findViewById<android.view.ViewGroup>(
                    androidx.media3.ui.R.id.exo_content_frame
                )
                val baseChildCount = contentFrame.childCount

                playerView.setWatermarks(
                    listOf(
                        TextWatermarkConfig("user-123", x = 20, y = 30),
                        ImageWatermarkConfig("https://example.com/watermark.png"),
                    )
                )

                assertEquals("Text and image watermarks should each add one overlay", baseChildCount + 2, contentFrame.childCount)
                assertTrue("Text watermark should be present in the content frame", findText(contentFrame, "user-123"))

                playerView.clearWatermarks()
                assertEquals("Clearing watermarks should remove every overlay", baseChildCount, contentFrame.childCount)
            }
        }
    }

    private fun findText(view: android.view.View, text: CharSequence): Boolean {
        if (view is TextView && view.text == text) return true
        if (view !is android.view.ViewGroup) return false
        return (0 until view.childCount).any { findText(view.getChildAt(it), text) }
    }

    @Test
    fun invalidWatermarkConfiguration_isRejectedOnDevice() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            TextWatermarkConfig(text = "", opacity = 0.5f)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ImageWatermarkConfig(imageUrl = "", width = 10, height = 10)
        }
    }
}
