package com.tpstreams.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
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
    CaptionsOptionsBottomSheet.CaptionsOptionsListener,
    DownloadOptionsBottomSheet.DownloadSelectionListener {

    private var playerControlView: TPStreamsPlayerControlView? = null
    
    // Fullscreen related variables
    private var isFullscreen = false
    private var originalParent: ViewGroup? = null
    private var originalLayoutParams: ViewGroup.LayoutParams? = null
    private var backCallback: OnBackPressedCallback? = null
    private var orientationEventListener: OrientationListener? = null
    private var autoFullscreenEnabled = false
    
    // Lifecycle management
    private var lifecycleManager: PlayerLifecycleManager? = null
    
    init {
        onFinishInflate()
        // Automatically register with lifecycle when created
        post {
            registerWithLifecycle()
        }
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post {
            enableAutoFullscreenOnRotate()
            registerWithLifecycle()
        }
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
    
    private val downloadOptionsBottomSheet: DownloadOptionsBottomSheet by lazy {
        DownloadOptionsBottomSheet().apply {
            setDownloadSelectionListener(this@TPStreamsPlayerView)
            setAvailableResolutions(availableResolutions)
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
        
        setupSettingsButton()
        setupFullscreenButton()
        showFullscreenButton()
    }
    
    private fun setupSettingsButton() {
        playerControlView?.setOnSettingsClickListener {
            showSettings()
        }
    }
    
    private fun setupFullscreenButton() {
        playerControlView?.setOnFullscreenClickListener {
            toggleFullscreen()
        }
    }
    
    /**
     * Set the state of the fullscreen button
     */
    override fun setFullscreenButtonState(isFullscreen: Boolean) {
        playerControlView?.setFullscreenState(isFullscreen)
    }
    
    fun toggleFullscreen() {
        if (!isFullscreen) enterFullscreen() else exitFullscreen()
    }
    
    fun enterFullscreen() {
        val activity = getActivity() as? ComponentActivity ?: return
        if (isFullscreen) return

        lifecycleManager?.preservePlaybackStateAcrossTransition {
            val decorView = activity.window.decorView as ViewGroup

            originalParent = this.parent as? ViewGroup
            originalLayoutParams = this.layoutParams

            originalParent?.removeView(this)
            this.setBackgroundColor(Color.BLACK)
            decorView.addView(
                this,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            
            hideSystemUI(activity)

            isFullscreen = true
            setFullscreenButtonState(true)

            backCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isFullscreen) {
                        exitFullscreen()
                    } else {
                        isEnabled = false
                        activity.onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
            activity.onBackPressedDispatcher.addCallback(activity, backCallback!!)
        }
    }

    fun exitFullscreen() {
        val activity = getActivity() as? ComponentActivity ?: return
        if (!isFullscreen) return

        lifecycleManager?.preservePlaybackStateAcrossTransition {
            val decorView = activity.window.decorView as ViewGroup

            decorView.removeView(this)
            
            originalParent?.addView(this, originalLayoutParams)

            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

            showSystemUI(activity)

            backCallback?.remove()
            backCallback = null
            isFullscreen = false
            setFullscreenButtonState(false)
        }
    }
    
    private fun hideSystemUI(activity: ComponentActivity) {
        activity.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN
    }

    private fun showSystemUI(activity: ComponentActivity) {
        activity.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        
        if (!autoFullscreenEnabled) {
            val wasPlayingBefore = player?.isPlaying ?: false
            
            lifecycleManager?.setInTransition(true)
            post {
                when (newConfig.orientation) {
                    Configuration.ORIENTATION_LANDSCAPE -> {
                        setFullscreenButtonState(true)
                        enterFullscreen()
                    }
                    Configuration.ORIENTATION_PORTRAIT -> {
                        setFullscreenButtonState(false)
                        exitFullscreen()
                    }
                }
                
                // After orientation change, restore previous playing state
                post {
                    if (wasPlayingBefore && player?.isPlaying == false) {
                        player?.play()
                    } else if (!wasPlayingBefore && player?.isPlaying == true) {
                        player?.pause()
                    }
                    lifecycleManager?.setInTransition(false)
                }
            }
        }
    }

    /**
     * Show the settings bottom sheet
     */
    fun showSettings() {
        val activity = getActivity()
        if (activity != null && !settingsBottomSheet.isAdded) {
            settingsBottomSheet.show(activity.supportFragmentManager)
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
    override fun getPlaybackSpeed(): Float {
        // Get the actual current speed from the player
        val player = player
        return if (player != null) {
            player.playbackParameters.speed
        } else {
            currentPlaybackSpeed
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
        val activity = getActivity() ?: return
        qualityOptionsBottomSheet.show(activity.supportFragmentManager)
    }

    override fun onCaptionsSelected() {
        val activity = getActivity() ?: return
        updateAvailableCaptions()
        captionsOptionsBottomSheet.show(activity.supportFragmentManager)
    }

    override fun onPlaybackSpeedSelected() {
        val activity = getActivity() ?: return
        playbackSpeedBottomSheet.show(activity.supportFragmentManager)
    }
    
    override fun onDownloadSelected() {
        val activity = getActivity() ?: return
        
        // Make sure we have the latest resolutions
        updateAvailableResolutions()
        
        // Set the available resolutions to the download options bottom sheet
        downloadOptionsBottomSheet.setAvailableResolutions(availableResolutions)
        
        // Show the download options bottom sheet
        downloadOptionsBottomSheet.show(activity.supportFragmentManager)
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
        setCurrentQuality(QualityOptionsBottomSheet.QUALITY_AUTO)
    
        // Let the trackSelector handle it automatically (no constraints)
        val player = player as? TPStreamsPlayer
        val params = player?.getTrackSelector()?.buildUponParameters()
            ?.clearVideoSizeConstraints()
            ?.build()
        if (params != null) player.getTrackSelector().parameters = params
    }
    
    override fun onHigherQualitySelected() {
        setCurrentQuality(QualityOptionsBottomSheet.QUALITY_HIGHER)
        
        // Get the highest available resolution
        val highestResolution = availableResolutions.firstOrNull()?.dropLast(1)?.toIntOrNull()
        if (highestResolution != null) {
            (player as? TPStreamsPlayer)?.setVideoResolution(highestResolution)
        } else {
            onAutoQualitySelected()
        }
    }
    
    override fun onDataSaverSelected() {
        setCurrentQuality(QualityOptionsBottomSheet.QUALITY_DATA_SAVER)
        
        // Get the lowest available resolution
        val lowestResolution = availableResolutions.lastOrNull()?.dropLast(1)?.toIntOrNull()
        if (lowestResolution != null) {
            (player as? TPStreamsPlayer)?.setVideoResolution(lowestResolution)
        } else {
            onAutoQualitySelected()
        }
    }
    
    override fun onAdvancedSelected() {
        val activity = getActivity() ?: return
        advancedResolutionBottomSheet.show(activity.supportFragmentManager)
    }
    
    // Implementation of AdvancedResolutionBottomSheet.ResolutionSelectionListener
    override fun onResolutionSelected(resolution: String) {
        setCurrentQuality(resolution)
    
        val height = resolution.dropLast(1).toIntOrNull()
        if (height != null) {
            (player as? TPStreamsPlayer)?.setVideoResolution(height)
        }
    }
    
    // Implementation of PlaybackSpeedBottomSheet.PlaybackSpeedListener
    override fun onSpeedSelected(speed: Float) {
        setPlaybackSpeed(speed)
    }

    override fun setPlayer(player: Player?) {
        super.setPlayer(player)
        
        // Create new lifecycle manager for the player
        lifecycleManager = player?.let { PlayerLifecycleManager(it) }
        registerWithLifecycle()
        
        // Listen for player events
        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                lifecycleManager?.onPlaybackStateChanged(isPlaying)
            }
        })
        
        if (player is TPStreamsPlayer) {
            // Add a listener to update resolutions and captions when tracks become available
            player.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    updateAvailableResolutions()
                    updateAvailableCaptions()
                }
                
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
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
            availableCaptions = textTracks
            captionsOptionsBottomSheet.setAvailableCaptions(textTracks)
            
            val activeTrack = TPSPlayer.getActiveTextTrack()
            if (activeTrack != null) {
                currentCaptionLanguage = activeTrack.first
                captionsOptionsBottomSheet.setCurrentLanguage(activeTrack.first)
            }
        } else {
            availableCaptions = emptyList()
            captionsOptionsBottomSheet.setAvailableCaptions(emptyList())
        }
    }
    
    /**
     * Set the current caption language
     */
    fun setCurrentCaptionLanguage(language: String?) {
        if (this.currentCaptionLanguage == language) {
            return
        }
        
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
        setCurrentCaptionLanguage(null)
    }
    
    override fun onCaptionLanguageSelected(language: String) {
        setCurrentCaptionLanguage(language)
    }
    
    override fun getCurrentCaptionLanguage(): String? {
        return currentCaptionLanguage
    }

    override fun getPlayer(): TPStreamsPlayer? {
        return super.getPlayer() as? TPStreamsPlayer
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        
        // Remove lifecycle observer when detached
        unregisterFromLifecycle()
        
        if (!isFullscreen) {
            backCallback?.remove()
            backCallback = null
        }
        
        // Ensure we disable the orientation listener to prevent leaks
        disableAutoFullscreenOnRotate()
    }

    /**
     * Make sure the fullscreen button is visible
     */
    fun showFullscreenButton() {
        playerControlView?.findViewById<View>(androidx.media3.ui.R.id.exo_fullscreen)?.visibility = View.VISIBLE
    }

    /**
     * Enable auto fullscreen on device rotation
     */
    fun enableAutoFullscreenOnRotate() {
        // Stop any existing listener first
        disableAutoFullscreenOnRotate()
        
        // Create and start a new listener
        orientationEventListener = OrientationListener(context).apply {
            setOnChangeListener { isLandscape ->
                post {
                    if (isLandscape) {
                        if (!isFullscreen) {
                            enterFullscreen()
                        }
                    } else {
                        if (isFullscreen) {
                            exitFullscreen()
                        }
                    }
                }
            }
            start()
        }
        
        autoFullscreenEnabled = true
    }

    /**
     * Disable auto fullscreen on device rotation
     */
    fun disableAutoFullscreenOnRotate() {
        orientationEventListener?.stop()
        orientationEventListener = null
        autoFullscreenEnabled = false
    }

    private fun getLifecycleOwner(): LifecycleOwner? {
        val activity = getActivity()
        return when {
            activity is LifecycleOwner -> activity
            else -> null
        }
    }
    
    private fun registerWithLifecycle() {
        val lifecycleOwner = getLifecycleOwner()
        if (lifecycleOwner != null && lifecycleManager != null) {
            Log.d("TPStreamsPlayerView", "Registering with lifecycle")
            lifecycleOwner.lifecycle.addObserver(lifecycleManager!!)
        }
    }
    
    private fun unregisterFromLifecycle() {
        val lifecycleOwner = getLifecycleOwner()
        if (lifecycleOwner != null && lifecycleManager != null) {
            Log.d("TPStreamsPlayerView", "Unregistering from lifecycle")
            lifecycleOwner.lifecycle.removeObserver(lifecycleManager!!)
        }
    }

    // Implementation of DownloadOptionsBottomSheet.DownloadSelectionListener
    override fun onDownloadResolutionSelected(resolution: String) {
        Log.d(TAG, "Download requested for resolution: $resolution")
        // Here you would implement the actual download functionality
        // For example, using a DownloadService or similar
        
        // Example implementation:
        // val player = player as? TPStreamsPlayer ?: return
        // val currentUrl = player.getCurrentMediaUrl()
        // val downloadManager = TPStreamsDownloadManager.getInstance(context)
        // downloadManager.downloadVideo(currentUrl, resolution)
        
        // Show a toast or notification to the user
        android.widget.Toast.makeText(
            context,
            "Starting download for $resolution",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    companion object {
        private const val TAG = "TPStreamsPlayerView"
    }
}
