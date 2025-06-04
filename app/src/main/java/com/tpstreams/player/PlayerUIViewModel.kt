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
            assetId = "8rEx9apZHFF",
            accessToken = "19aa0055-d965-4654-8fce-b804e70a46b0",
            shouldAutoPlay = false
        )
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}