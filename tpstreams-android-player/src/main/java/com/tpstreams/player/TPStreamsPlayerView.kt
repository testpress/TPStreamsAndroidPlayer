package com.tpstreams.player

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import java.util.Locale

@UnstableApi
class TPStreamsPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr), 
    PlayerSettingsBottomSheet.SettingsListener, 
    QualityOptionsBottomSheet.QualityOptionsListener,
    AdvancedResolutionBottomSheet.ResolutionSelectionListener,
    PlaybackSpeedBottomSheet.PlaybackSpeedListener,
    CaptionsOptionsBottomSheet.CaptionsOptionsListener {

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
    
    private val captionsOptionsBottomSheet: CaptionsOptionsBottomSheet by lazy {
        CaptionsOptionsBottomSheet().apply {
            setCaptionsOptionsListener(this@TPStreamsPlayerView)
            setCurrentLanguage(currentCaptionLanguage)
        }
    }
    
    // Current quality setting, updated when user changes quality
    private var currentQuality: String = QualityOptionsBottomSheet.QUALITY_AUTO
    private var availableResolutions: List<String> = emptyList()
    
    // Current playback speed, updated when user changes speed
    private var currentPlaybackSpeed: Float = 1.0f
    
    // Captions state
    private var currentCaptionLanguage: String? = null
    private var availableCaptions: List<Pair<String, String>> = emptyList()

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
        val TPSPlayer = player as? TPStreamsPlayer ?: return
        val availableHeights = TPSPlayer.getAvailableVideoResolutions()
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
        val activity = getActivity() ?: return
        updateAvailableCaptions()
        captionsOptionsBottomSheet.show(activity.supportFragmentManager)
    }

    override fun onPlaybackSpeedSelected() {
        Log.d("TPStreamsPlayerView", "Playback speed selected")
        val activity = getActivity() ?: return
        playbackSpeedBottomSheet.show(activity.supportFragmentManager)
    }
    
    override fun getCurrentQuality(): String {
        return currentQuality
    }
    
    override fun getCurrentCaptionStatus(): String {
        val TPSPlayer = player as? TPStreamsPlayer
        val activeTrack = TPSPlayer?.getActiveTextTrack()
        
        if (activeTrack != null) {
            return getLanguageName(activeTrack.first)
        }

        return if (currentCaptionLanguage == null) {
            "Off"
        } else {
            getLanguageName(currentCaptionLanguage!!)
        }
    }
    
    /**
     * Convert ISO language code to full language name using Java Locale
     */
    private fun getLanguageName(languageCode: String): String {
        try {
            val locale = Locale(languageCode)
            val displayLanguage = locale.getDisplayLanguage(Locale.ENGLISH)
            
            if (displayLanguage.equals(languageCode, ignoreCase = true) || displayLanguage.isEmpty()) {
                return languageCode.replaceFirstChar { it.uppercase() }
            }
            
            return displayLanguage
        } catch (e: Exception) {
            Log.e("TPStreamsPlayerView", "Error getting language name for $languageCode", e)
            return languageCode.replaceFirstChar { it.uppercase() }
        }
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
            // Add a listener to update resolutions and captions when tracks become available
            player.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    Log.d("TPStreamsPlayerView", "Tracks changed, updating resolutions and captions")
                    updateAvailableResolutions()
                    updateAvailableCaptions()
                }
                
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        Log.d("TPStreamsPlayerView", "Playback ready, updating captions")
                        updateAvailableCaptions()
                    }
                }
            })
            
            updateAvailableCaptions()
        }
    }

    /**
     * Update available captions based on the current video tracks
     */
    fun updateAvailableCaptions() {
        val TPSPlayer = player as? TPStreamsPlayer ?: return
        
        val textTracks = TPSPlayer.getAvailableTextTracks()
        
        if (textTracks.isNotEmpty()) {
            Log.d("TPStreamsPlayerView", "Updating available captions with ${textTracks.size} tracks")
            availableCaptions = textTracks
            captionsOptionsBottomSheet.setAvailableCaptions(textTracks)
            
            val activeTrack = TPSPlayer.getActiveTextTrack()
            if (activeTrack != null) {
                Log.d("TPStreamsPlayerView", "Active caption track found: ${activeTrack.second} (${activeTrack.first})")
                currentCaptionLanguage = activeTrack.first
                captionsOptionsBottomSheet.setCurrentLanguage(activeTrack.first)
            }
            
            textTracks.forEach { (language, label) ->
                Log.d("TPStreamsPlayerView", "Caption available: $label ($language)")
            }
        } else {
            Log.d("TPStreamsPlayerView", "No caption tracks available")
            availableCaptions = emptyList()
            captionsOptionsBottomSheet.setAvailableCaptions(emptyList())
        }
    }
    
    /**
     * Set the current caption language
     */
    fun setCurrentCaptionLanguage(language: String?) {
        if (this.currentCaptionLanguage == language) {
            Log.d("TPStreamsPlayerView", "Caption language unchanged: $language")
            return
        }
        
        Log.d("TPStreamsPlayerView", "Setting caption language: $language")
        this.currentCaptionLanguage = language
        captionsOptionsBottomSheet.setCurrentLanguage(language)
        
        (player as? TPStreamsPlayer)?.setTextTrackByLanguage(language)
        
        val activity = getActivity()
        if (activity != null && settingsBottomSheet.isAdded) {
            settingsBottomSheet.dismiss()
            settingsBottomSheet.show(activity.supportFragmentManager)
        }
    }

    override fun onCaptionsDisabled() {
        Log.d("TPStreamsPlayerView", "Captions disabled")
        setCurrentCaptionLanguage(null)
    }
    
    override fun onCaptionLanguageSelected(language: String) {
        Log.d("TPStreamsPlayerView", "Caption language selected: $language")
        setCurrentCaptionLanguage(language)
    }
    
    override fun getCurrentCaptionLanguage(): String? {
        return currentCaptionLanguage
    }

    override fun getPlayer(): TPStreamsPlayer? {
        return super.getPlayer() as? TPStreamsPlayer
    }
}
