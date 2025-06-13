package com.tpstreams.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel

const val TAG = "PlayerUIViewModel"

class PlayerUIViewModel(application: Application) : AndroidViewModel(application) {

    // Use application context to avoid memory leaks
    var player: TPStreamsPlayer = createDefaultPlayer(application)
    
    private fun createDefaultPlayer(application: Application): TPStreamsPlayer {
        // Initialize the SDK with the correct organization ID
        TPStreamsPlayer.init("9q94nm", application.applicationContext)
        
        return TPStreamsPlayer.create(
            context = application.applicationContext,
            assetId = "ACGhHuD7DEa",  // Non-DRM content ID
            accessToken = "5bea276d-7882-4f8f-951a-c628622817e0",
            shouldAutoPlay = false
        )
    }
    
    fun updatePlayer(newPlayer: TPStreamsPlayer) {
        // Release the old player
        player.release()
        
        // Update with the new player
        player = newPlayer
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}