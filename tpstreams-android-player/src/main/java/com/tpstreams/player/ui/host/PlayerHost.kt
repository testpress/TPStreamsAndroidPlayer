package com.tpstreams.player.ui.host

import android.view.View
import androidx.fragment.app.FragmentActivity
import com.tpstreams.player.ui.lifecycle.PlayerLifecycleManager
import com.tpstreams.player.TPStreamsPlayer

/**
 * Narrow host contract shared by view controllers that need player and activity state.
 */
internal interface PlayerHost {
    val currentPlayer: TPStreamsPlayer?
    val lifecycleManager: PlayerLifecycleManager?

    fun getActivity(): FragmentActivity?
    fun setFullscreenButtonState(isFullscreen: Boolean)
}

/**
 * Additional host contract required by the fullscreen controller to move the hosted view.
 */
internal interface FullscreenHost : PlayerHost {
    val hostedView: View
}
