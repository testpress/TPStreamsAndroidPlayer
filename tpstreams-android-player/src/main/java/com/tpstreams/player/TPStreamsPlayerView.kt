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
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

@UnstableApi
class TPStreamsPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr), PlayerSettingsBottomSheet.SettingsListener {

    private var playerControlView: TPStreamsPlayerControlView? = null
    private val settingsBottomSheet: PlayerSettingsBottomSheet by lazy {
        PlayerSettingsBottomSheet().apply {
            setSettingsListener(this@TPStreamsPlayerView)
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        playerControlView = findViewById(androidx.media3.ui.R.id.exo_controller) as? TPStreamsPlayerControlView
        Log.d("TPStreamsPlayerView", "PlayerControlView found: ${playerControlView != null}")
        
        // Set up the settings icon click listener
        setupSettingsButton()
    }
    
    private fun setupSettingsButton() {
        playerControlView?.setOnSettingsClickListener {
            showSettings()
        }
    }

    /**
     * Show the settings bottom sheet
     */
    fun showSettings() {
        Log.d("TPStreamsPlayerView", "Showing settings")
        val activity = getActivity()
        if (activity != null && !settingsBottomSheet.isAdded) {
            settingsBottomSheet.show(activity.supportFragmentManager, PlayerSettingsBottomSheet.TAG)
        } else {
            Log.e("TPStreamsPlayerView", "Cannot show settings: activity is null or bottom sheet already added")
        }
    }
    
    private fun getActivity(): FragmentActivity? {
        var ctx = context
        while (ctx is Context) {
            if (ctx is FragmentActivity) {
                return ctx
            }
            if (ctx is android.content.ContextWrapper) {
                ctx = ctx.baseContext
            } else {
                break
            }
        }
        return null
    }

    // Implementation of PlayerSettingsBottomSheet.SettingsListener
    override fun onQualitySelected() {
        Log.d("TPStreamsPlayerView", "Quality selected")
        // Implement quality selection logic
    }

    override fun onCaptionsSelected() {
        Log.d("TPStreamsPlayerView", "Captions selected")
        // Implement captions selection logic
    }

    override fun onPlaybackSpeedSelected() {
        Log.d("TPStreamsPlayerView", "Playback speed selected")
        // Implement playback speed selection logic
    }
}
