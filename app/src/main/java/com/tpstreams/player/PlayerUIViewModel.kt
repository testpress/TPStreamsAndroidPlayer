package com.tpstreams.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class PlayerUIViewModel(application: Application) : AndroidViewModel(application) {

    private var _player: TPStreamsPlayer? = null
    val player: TPStreamsPlayer?
        get() = _player

    fun initPlayer(assetId: String, accessToken: String) {
        if (_player == null) {
            _player = TPStreamsPlayer.create(
                context = getApplication<Application>().applicationContext,
                assetId = assetId,
                accessToken = accessToken,
                shouldAutoPlay = true,
                enableDownload = true
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        _player?.release()
    }
}