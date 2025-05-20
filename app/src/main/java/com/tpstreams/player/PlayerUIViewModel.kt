package com.tpstreams.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val TAG = "PlayerUIViewModel"

class PlayerUIViewModel(application: Application) : AndroidViewModel(application) {

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen

    // Use application context to avoid memory leaks
    val player: TPStreamsPlayer by lazy {
        TPStreamsPlayer.create(
            context = application.applicationContext,
            assetId = "8rEx9apZHFF",
            accessToken = "19aa0055-d965-4654-8fce-b804e70a46b0",
            shouldAutoPlay = false
        )
    }

    fun toggleFullscreen() {
        Log.d(TAG, "toggleFullscreen: called with isFullscreen = ${_isFullscreen.value}")
        _isFullscreen.value = !_isFullscreen.value
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}