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
) : PlayerView(context, attrs, defStyleAttr), 
    PlayerSettingsBottomSheet.SettingsListener, 
    QualityOptionsBottomSheet.QualityOptionsListener,
    AdvancedResolutionBottomSheet.ResolutionSelectionListener,
    PlaybackSpeedBottomSheet.PlaybackSpeedListener {

    private var playerControlView: TPStreamsPlayerControlView? = null
    private val settingsBottomSheet: PlayerSettingsBottomSheet by lazy {
        PlayerSettingsBottomSheet().apply {
            setSettingsListener(this@TPStreamsPlayerView)
        }
    }
    
    private val qualityOptionsBottomSheet: QualityOptionsBottomSheet by lazy {
        QualityOptionsBottomSheet().apply {
            setQualityOptionsListener(this@TPStreamsPlayerView)
            setCurrentQuality(currentQuality)
        }
    }
    
    private val advancedResolutionBottomSheet: AdvancedResolutionBottomSheet by lazy {
        AdvancedResolutionBottomSheet().apply {
            setResolutionSelectionListener(this@TPStreamsPlayerView)
            setSelectedResolution(currentQuality)
        }
    }
    
    private val playbackSpeedBottomSheet: PlaybackSpeedBottomSheet by lazy {
        PlaybackSpeedBottomSheet().apply {
            setPlaybackSpeedListener(this@TPStreamsPlayerView)
            setCurrentSpeed(currentPlaybackSpeed)
        }
    }
    
    // Current quality setting, updated when user changes quality
    private var currentQuality: String = QualityOptionsBottomSheet.QUALITY_AUTO
    private var availableResolutions: List<String> = listOf("2160p", "1440p", "1080p", "720p", "480p", "360p", "240p", "144p")
    
    // Current playback speed, updated when user changes speed
    private var currentPlaybackSpeed: Float = 1.0f

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
            settingsBottomSheet.show(activity.supportFragmentManager)
        } else {
            Log.e("TPStreamsPlayerView", "Cannot show settings: activity is null or bottom sheet already added")
        }
    }
    
    /**
     * Set available resolutions for the current video
     */
    fun setAvailableResolutions(resolutions: List<String>) {
        this.availableResolutions = resolutions
        advancedResolutionBottomSheet.setAvailableResolutions(resolutions)
    }
    
    /**
     * Set the current quality
     */
    fun setCurrentQuality(quality: String) {
        this.currentQuality = quality
        qualityOptionsBottomSheet.setCurrentQuality(quality)
        advancedResolutionBottomSheet.setSelectedResolution(quality)
    }
    
    /**
     * Set the current playback speed
     */
    fun setPlaybackSpeed(speed: Float) {
        this.currentPlaybackSpeed = speed
        playbackSpeedBottomSheet.setCurrentSpeed(speed)
        player?.setPlaybackSpeed(speed)
    }
    
    /**
     * Get the current playback speed
     */
    fun getPlaybackSpeed(): Float {
        return currentPlaybackSpeed
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
        val activity = getActivity() ?: return
        qualityOptionsBottomSheet.show(activity.supportFragmentManager)
    }

    override fun onCaptionsSelected() {
        Log.d("TPStreamsPlayerView", "Captions selected")
        // Implement captions selection logic
    }

    override fun onPlaybackSpeedSelected() {
        Log.d("TPStreamsPlayerView", "Playback speed selected")
        val activity = getActivity() ?: return
        playbackSpeedBottomSheet.show(activity.supportFragmentManager)
    }
    
    override fun getCurrentQuality(): String {
        return currentQuality
    }
    
    // Implementation of QualityOptionsBottomSheet.QualityOptionsListener
    override fun onAutoQualitySelected() {
        Log.d("TPStreamsPlayerView", "Auto quality selected")
        setCurrentQuality(QualityOptionsBottomSheet.QUALITY_AUTO)
        // Implement auto quality selection logic
    }
    
    override fun onHigherQualitySelected() {
        Log.d("TPStreamsPlayerView", "Higher quality selected")
        // Select the highest available resolution
        setCurrentQuality(QualityOptionsBottomSheet.QUALITY_HIGHER)
        // Implement higher quality selection logic
    }
    
    override fun onDataSaverSelected() {
        Log.d("TPStreamsPlayerView", "Data saver selected")
        // Select a lower resolution like 480p
        setCurrentQuality(QualityOptionsBottomSheet.QUALITY_DATA_SAVER)
        // Implement data saver selection logic
    }
    
    override fun onAdvancedSelected() {
        Log.d("TPStreamsPlayerView", "Advanced selected")
        val activity = getActivity() ?: return
        advancedResolutionBottomSheet.show(activity.supportFragmentManager)
    }
    
    // Implementation of AdvancedResolutionBottomSheet.ResolutionSelectionListener
    override fun onResolutionSelected(resolution: String) {
        Log.d("TPStreamsPlayerView", "Resolution selected: $resolution")
        // When a specific resolution is selected from the advanced menu,
        // we should pass the actual resolution value, not a quality preset
        setCurrentQuality(resolution)
        // Implement specific resolution selection logic
    }
    
    // Implementation of PlaybackSpeedBottomSheet.PlaybackSpeedListener
    override fun onSpeedSelected(speed: Float) {
        Log.d("TPStreamsPlayerView", "Playback speed selected: $speed")
        setPlaybackSpeed(speed)
    }
}
