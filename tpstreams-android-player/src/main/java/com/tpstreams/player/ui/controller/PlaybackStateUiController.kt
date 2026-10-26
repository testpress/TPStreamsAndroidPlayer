package com.tpstreams.player.ui.controller

import android.view.View
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.TPStreamsPlayer
import com.tpstreams.player.constants.NetworkDiagnostics
import com.tpstreams.player.constants.PlaybackError

/** Binds player events to view state without making the view own playback policy. */
@UnstableApi
internal class PlaybackStateUiController(
    private val view: View,
    private val settingsPanel: SettingsPanel,
    private val captions: Captions,
    private val drmSurfaceGuard: DrmSurfaceGuard,
    private val showLoading: () -> Unit,
    private val hideLoading: () -> Unit,
    private val showError: (String) -> Unit,
    private val hideError: () -> Unit,
    private val showDiagnostics: (PlaybackError, NetworkDiagnostics) -> Unit,
    private val showDiagnosing: () -> Unit,
    private val updateLiveUi: (Boolean) -> Unit,
    private val notifyWatermarkState: () -> Unit
) {
    private val playbackListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            view.keepScreenOn = isPlaying
            if (isPlaying) hideError()
            notifyWatermarkState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> showLoading()
                Player.STATE_BUFFERING -> {
                    hideError()
                    showLoading()
                }
                Player.STATE_READY -> {
                    hideLoading()
                    hideError()
                }
                Player.STATE_ENDED -> hideLoading()
            }
            notifyWatermarkState()
        }
    }

    private val tracksListener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            if (drmSurfaceGuard.check()) return
            settingsPanel.updateAvailableResolutions()
            captions.updateAvailableCaptions()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) captions.updateAvailableCaptions()
        }
    }

    private val viewPlayerListener = object : TPStreamsPlayer.ViewListener {
        override fun onError(error: PlaybackError, message: String) {
            hideLoading()
            view.post { showError(message) }
        }

        override fun onNetworkError(error: PlaybackError, message: String, diagnostics: NetworkDiagnostics) {
            view.post {
                hideLoading()
                showDiagnostics(error, diagnostics)
            }
        }

        override fun onNetworkDiagnosticsStarted() {
            view.post { showDiagnosing() }
        }
    }

    fun bind(player: Player) {
        player.addListener(playbackListener)
        render(player)
        if (player is TPStreamsPlayer) {
            player.addListener(tracksListener)
            player.addListener(viewPlayerListener)
            captions.updateAvailableCaptions()
            player.onLiveStreamStatusChanged = { updateLiveUi(it) }
            updateLiveUi(player.isLiveStream)
        }
    }

    fun attachPlaybackListener(player: Player?) {
        player?.addListener(playbackListener)
    }

    fun detachPlaybackListener(player: Player?) {
        player?.removeListener(playbackListener)
    }

    fun unbind(player: Player?) {
        player ?: return
        player.removeListener(playbackListener)
        if (player is TPStreamsPlayer) {
            player.removeListener(tracksListener)
            player.removeListener(viewPlayerListener)
            player.onLiveStreamStatusChanged = null
        }
    }

    fun render(player: Player) {
        when (player.playbackState) {
            Player.STATE_IDLE -> showLoading()
            Player.STATE_BUFFERING -> {
                hideError()
                showLoading()
            }
            Player.STATE_READY -> {
                hideLoading()
                hideError()
            }
            Player.STATE_ENDED -> hideLoading()
        }
        if (player is TPStreamsPlayer) updateLiveUi(player.isLiveStream)
    }
}
