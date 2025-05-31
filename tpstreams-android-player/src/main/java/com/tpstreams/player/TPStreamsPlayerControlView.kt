package com.tpstreams.player

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.ImageButton
import androidx.media3.common.Player
import androidx.media3.ui.PlayerControlView

@androidx.media3.common.util.UnstableApi
class TPStreamsPlayerControlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerControlView(context, attrs, defStyleAttr) {

    private var settingsIcon: ImageButton? = null
    private var onSettingsClickListener: (() -> Unit)? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        
        // Find and set up the settings icon
        settingsIcon = findViewById(R.id.exo_settings_icon)
        Log.d("TPStreamsPlayerControlView", "Settings icon found: ${settingsIcon != null}")
        
        settingsIcon?.setOnClickListener {
            Log.d("TPStreamsPlayerControlView", "Settings icon clicked")
            onSettingsClickListener?.invoke()
        }
        
        // Hide the default settings button
        hideDefaultSettingsButton()
    }
    
    private fun hideDefaultSettingsButton() {
        // Find and hide the default ExoPlayer settings button
        val settingsButton = findViewById<ImageButton>(androidx.media3.ui.R.id.exo_settings)
        settingsButton?.visibility = View.GONE
    }

    /**
     * Set a click listener for the settings icon
     */
    fun setOnSettingsClickListener(listener: () -> Unit) {
        this.onSettingsClickListener = listener
    }
}
