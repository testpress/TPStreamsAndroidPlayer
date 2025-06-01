package com.tpstreams.player

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.Player
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
    
    init {
        onFinishInflate()
    }

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
    private var availableResolutions: List<String> = emptyList()
    
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
     * Update available resolutions based on the current video tracks
     */
    fun updateAvailableResolutions() {
        val tpPlayer = player as? TPStreamsPlayer ?: return
        val availableHeights = tpPlayer.getAvailableVideoResolutions()
        val resolutionStrings = availableHeights.map { "${it}p" }
        setAvailableResolutions(resolutionStrings)
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
    
        // Let the trackSelector handle it automatically (no constraints)
        val player = player as? TPStreamsPlayer
        val params = player?.getTrackSelector()?.buildUponParameters()
            ?.clearVideoSizeConstraints()
            ?.build()
        if (params != null) player.getTrackSelector().parameters = params
    }
    
    override fun onHigherQualitySelected() {
        Log.d("TPStreamsPlayerView", "Higher quality selected")
        setCurrentQuality(QualityOptionsBottomSheet.QUALITY_HIGHER)
        
        // Get the highest available resolution
        val highestResolution = availableResolutions.firstOrNull()?.dropLast(1)?.toIntOrNull()
        if (highestResolution != null) {
            (player as? TPStreamsPlayer)?.setVideoResolution(highestResolution)
            Log.d("TPStreamsPlayerView", "Setting highest available resolution: ${highestResolution}p")
        } else {
            Log.w("TPStreamsPlayerView", "No resolutions available, defaulting to auto")
            onAutoQualitySelected()
        }
    }
    
    override fun onDataSaverSelected() {
        Log.d("TPStreamsPlayerView", "Data saver selected")
        setCurrentQuality(QualityOptionsBottomSheet.QUALITY_DATA_SAVER)
        
        // Get the lowest available resolution
        val lowestResolution = availableResolutions.lastOrNull()?.dropLast(1)?.toIntOrNull()
        if (lowestResolution != null) {
            (player as? TPStreamsPlayer)?.setVideoResolution(lowestResolution)
            Log.d("TPStreamsPlayerView", "Setting lowest available resolution: ${lowestResolution}p")
        } else {
            // If no resolutions available, default to a low value
            Log.w("TPStreamsPlayerView", "No resolutions available, defaulting to auto")
            onAutoQualitySelected()
        }
    }
    
    override fun onAdvancedSelected() {
        Log.d("TPStreamsPlayerView", "Advanced selected")
        val activity = getActivity() ?: return
        advancedResolutionBottomSheet.show(activity.supportFragmentManager)
    }
    
    // Implementation of AdvancedResolutionBottomSheet.ResolutionSelectionListener
    override fun onResolutionSelected(resolution: String) {
        Log.d("TPStreamsPlayerView", "Resolution selected: $resolution")
        setCurrentQuality(resolution)
    
        val height = resolution.dropLast(1).toIntOrNull()
        if (height != null) {
            (player as? TPStreamsPlayer)?.setVideoResolution(height)
        } else {
            Log.w("TPStreamsPlayerView", "Invalid resolution format: $resolution")
        }
    }
    
    // Implementation of PlaybackSpeedBottomSheet.PlaybackSpeedListener
    override fun onSpeedSelected(speed: Float) {
        Log.d("TPStreamsPlayerView", "Playback speed selected: $speed")
        setPlaybackSpeed(speed)
    }

    override fun setPlayer(player: Player?) {
        super.setPlayer(player)
        
        if (player is TPStreamsPlayer) {
            // Add a listener to update resolutions when tracks become available
            player.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    updateAvailableResolutions()
                }
            })
        }
    }
}
