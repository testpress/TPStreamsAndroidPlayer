package com.tpstreams.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

@UnstableApi
class TPStreamsPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr) {

    private var playerControlView: TPStreamsPlayerControlView? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        playerControlView = findViewById(androidx.media3.ui.R.id.exo_controller) as? TPStreamsPlayerControlView
        Log.d("TPStreamsPlayerView", "PlayerControlView found: ${playerControlView != null}")
    }

    /**
     * Set a listener for settings menu options
     */
    fun setSettingsListener(listener: PlayerSettingsBottomSheet.SettingsListener) {
        playerControlView?.setSettingsListener(listener)
    }
    
    /**
     * Show the settings bottom sheet directly
     */
    fun showSettings() {
        Log.d("TPStreamsPlayerView", "Showing settings directly")
        playerControlView?.showSettingsBottomSheet()
    }
}
