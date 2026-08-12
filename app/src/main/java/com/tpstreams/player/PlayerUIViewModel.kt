package com.tpstreams.player

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.AndroidViewModel
import com.tpstreams.player.TestpressPlayer

@OptIn(UnstableApi::class)
class PlayerUIViewModel(application: Application) : AndroidViewModel(application) {

    private var _player: TPStreamsPlayer? = null
    val player: TPStreamsPlayer?
        get() = _player

    fun initPlayer(assetId: String, accessToken: String, isTestpress: Boolean = false) {
        if (_player == null) {
            _player = if (isTestpress) {
                TestpressPlayer.create(
                    context = getApplication<Application>().applicationContext,
                    assetId = assetId,
                    accessToken = accessToken,
                    shouldAutoPlay = true,
                    enableDownload = true
                )
            } else {
                TPStreamsPlayer.create(
                    context = getApplication<Application>().applicationContext,
                    assetId = assetId,
                    accessToken = accessToken,
                    shouldAutoPlay = true,
                    enableDownload = true
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _player?.release()
    }
}