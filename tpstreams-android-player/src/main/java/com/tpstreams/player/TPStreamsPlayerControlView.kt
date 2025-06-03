package com.tpstreams.player

import android.annotation.SuppressLint
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
    
    private var fullscreenButton: ImageButton? = null
    private var onFullscreenClickListener: (() -> Unit)? = null
    private var isFullscreen = false

    @SuppressLint("LongLogTag")
    override fun onFinishInflate() {
        super.onFinishInflate()
        
        // Find and set up the settings icon
        settingsIcon = findViewById(R.id.exo_settings_icon)
        Log.d("TPStreamsPlayerControlView", "Settings icon found: ${settingsIcon != null}")
        
        settingsIcon?.setOnClickListener {
            Log.d("TPStreamsPlayerControlView", "Settings icon clicked")
            onSettingsClickListener?.invoke()
        }
        
        // Find and set up the fullscreen button
        fullscreenButton = findViewById(androidx.media3.ui.R.id.exo_fullscreen)
        Log.d("TPStreamsPlayerControlView", "Fullscreen button found: ${fullscreenButton != null}")
        
        if (fullscreenButton != null) {
            Log.d("TPStreamsPlayerControlView", "Fullscreen button visibility: ${
                when(fullscreenButton!!.visibility) {
                    View.VISIBLE -> "VISIBLE"
                    View.INVISIBLE -> "INVISIBLE"
                    View.GONE -> "GONE"
                    else -> "UNKNOWN"
                }
            }")
            
            // Make sure it's visible
            fullscreenButton!!.visibility = View.VISIBLE
            
            fullscreenButton?.setOnClickListener {
                Log.d("TPStreamsPlayerControlView", "Fullscreen button clicked")
                onFullscreenClickListener?.invoke()
            }
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
