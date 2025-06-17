package com.tpstreams.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel

const val TAG = "PlayerUIViewModel"

class PlayerUIViewModel(application: Application) : AndroidViewModel(application) {

    // Use application context to avoid memory leaks
    val player: TPStreamsPlayer by lazy {
        TPStreamsPlayer.create(
            context = application.applicationContext,
            assetId = "BEArYFdaFbt",
            accessToken = "e6a1b485-daad-42eb-8cf2-6b6e51631092",
            shouldAutoPlay = false
        )
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}