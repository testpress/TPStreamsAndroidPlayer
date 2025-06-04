package com.tpstreams.player

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
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
    
    private var fullscreenButton: ImageButton? = null
    private var onFullscreenClickListener: (() -> Unit)? = null
    private var isFullscreen = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        
        // Set up settings icon
        settingsIcon = findViewById(R.id.exo_settings_icon)
        settingsIcon?.setOnClickListener {
            onSettingsClickListener?.invoke()
        }
        
        // Set up fullscreen button
        fullscreenButton = findViewById(androidx.media3.ui.R.id.exo_fullscreen)
        fullscreenButton?.visibility = View.VISIBLE
        fullscreenButton?.setOnClickListener {
            onFullscreenClickListener?.invoke()
        }
        
        // Hide default settings button
        findViewById<ImageButton>(androidx.media3.ui.R.id.exo_settings)?.visibility = View.GONE
    }

    /**
     * Set a click listener for the settings icon
     */
    fun setOnSettingsClickListener(listener: () -> Unit) {
        this.onSettingsClickListener = listener
    }
    
    /**
     * Set a click listener for the fullscreen button
     */
    fun setOnFullscreenClickListener(listener: () -> Unit) {
        this.onFullscreenClickListener = listener
    }
    
    /**
     * Set the fullscreen button state (expanded or collapsed)
     */
    fun setFullscreenState(fullscreen: Boolean) {
        this.isFullscreen = fullscreen
        updateFullscreenButtonDrawable()
    }
    
    private fun updateFullscreenButtonDrawable() {
        fullscreenButton?.setImageResource(
            if (isFullscreen) 
                androidx.media3.ui.R.drawable.exo_icon_fullscreen_exit 
            else 
                androidx.media3.ui.R.drawable.exo_icon_fullscreen_enter
        )
    }
}
