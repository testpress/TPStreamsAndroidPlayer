package com.tpstreams.player.ui.controller

import android.view.View
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.TPStreamsPlayerView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

@UnstableApi
class PlaybackStateUiControllerTest {

    @Test
    fun render_buffering_hidesExistingErrorBeforeShowingLoading() {
        val view = mock(View::class.java)
        val playerView = mock(TPStreamsPlayerView::class.java)
        val player = mock(Player::class.java)
        `when`(player.playbackState).thenReturn(Player.STATE_BUFFERING)

        var loadingCalls = 0
        var hideErrorCalls = 0
        val controller = PlaybackStateUiController(
            view = view,
            settingsPanel = SettingsPanel(playerView),
            captions = Captions(playerView),
            drmSurfaceGuard = DrmSurfaceGuard(
                isTextureView = { false },
                getPlayer = { null },
                showError = {}
            ),
            showLoading = { loadingCalls++ },
            hideLoading = {},
            showError = {},
            hideError = { hideErrorCalls++ },
            showDiagnostics = { _, _ -> },
            showDiagnosing = {},
            updateLiveUi = {},
            notifyWatermarkState = {}
        )

        controller.render(player)

        assertEquals("Buffering should show the loading state once", 1, loadingCalls)
        assertEquals("Buffering should clear any stale error overlay once", 1, hideErrorCalls)
    }
}
