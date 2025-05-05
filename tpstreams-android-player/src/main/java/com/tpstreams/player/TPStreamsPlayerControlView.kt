package com.tpstreams.player

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.media3.common.Player
import androidx.media3.ui.PlayerControlView

@androidx.media3.common.util.UnstableApi
class TPStreamsPlayerControlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerControlView(context, attrs, defStyleAttr) {

    private var centerPlayPauseButton: View? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateCenterButtonVisibility()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateCenterButtonVisibility()
        }
    }

    override fun setPlayer(player: Player?) {
        super.setPlayer(player)
        player?.addListener(listener)
        updateCenterButtonVisibility()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        centerPlayPauseButton = findViewById(R.id.exo_play_pause)
        updateCenterButtonVisibility()
    }

    private fun updateCenterButtonVisibility() {
        val player = player ?: return
        val visible = !player.isPlaying || player.playbackState == Player.STATE_ENDED
        centerPlayPauseButton?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        player?.removeListener(listener)
    }
}
