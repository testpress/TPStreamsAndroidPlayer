package com.tpstreams.player

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.util.Log
import android.view.View
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
    PlaybackSpeedBottomSheet.PlaybackSpeedListener,
    CaptionsBottomSheet.CaptionsOptionsListener,
    DownloadOptionsBottomSheet.DownloadSelectionListener {

    // Controllers
    private val fullscreenMode = FullscreenMode(this)
    private val downloadActions = DownloadActions(this)
    private val settingsPanel = SettingsPanel(this)
    private val captions = Captions(this)
    private val contextAccess = ContextAccess(this)
    
    private var playerControlView: TPStreamsPlayerControlView? = null
    private var orientationEventListener: OrientationListener? = null
    private var autoFullscreenEnabled = false
    var lifecycleManager: PlayerLifecycleManager? = null

    private val playbackStateListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@TPStreamsPlayerView.keepScreenOn = isPlaying
            lifecycleManager?.onPlaybackStateChanged(isPlaying)
        }
    }
    
    // Bottom sheets
    val settingsBottomSheet: PlayerSettingsBottomSheet by lazy {
        PlayerSettingsBottomSheet().apply {
            setSettingsListener(this@TPStreamsPlayerView)
        }
    }
    
    val qualityOptionsBottomSheet: QualityOptionsBottomSheet by lazy {
        QualityOptionsBottomSheet().apply {
            setQualityOptionsListener(this@TPStreamsPlayerView)
            setCurrentQuality(settingsPanel.getCurrentQuality())
        }
    }
    
    val advancedResolutionBottomSheet: AdvancedResolutionBottomSheet by lazy {
        AdvancedResolutionBottomSheet().apply {
            setResolutionSelectionListener(this@TPStreamsPlayerView)
            setSelectedResolution(settingsPanel.getCurrentQuality())
        }
    }
    
    val playbackSpeedBottomSheet: PlaybackSpeedBottomSheet by lazy {
        PlaybackSpeedBottomSheet().apply {
            setPlaybackSpeedListener(this@TPStreamsPlayerView)
            setCurrentSpeed(settingsPanel.getPlaybackSpeed())
        }
    }
    
    val captionsBottomSheet: CaptionsBottomSheet by lazy {
        CaptionsBottomSheet().apply {
            setCaptionsOptionsListener(this@TPStreamsPlayerView)
            setCurrentLanguage(captions.getCurrentCaptionLanguage())
        }
    }
    
    val downloadOptionsBottomSheet: DownloadOptionsBottomSheet by lazy {
        DownloadOptionsBottomSheet().apply {
            setDownloadSelectionListener(this@TPStreamsPlayerView)
        }
    }
    
    val downloadActionBottomSheet: DownloadActionBottomSheet by lazy {
        DownloadActionBottomSheet().apply {
            setDownloadActionListener(object : DownloadActionBottomSheet.DownloadActionListener {
                override fun onDeleteDownloadConfirmed() {
                    downloadActions.deleteCurrentDownload()
                }
                
                override fun onPauseDownloadConfirmed() {
                    downloadActions.pauseCurrentDownload()
                }
                
                override fun onResumeDownloadConfirmed() {
                    downloadActions.resumeCurrentDownload()
                }
                
                override fun onCancelDownloadConfirmed() {
                    downloadActions.deleteCurrentDownload()
                }
            })
        }
    }

    init {
        onFinishInflate()
        post {
            registerWithLifecycle()
        }
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        getPlayer()?.addListener(playbackStateListener)
        post {
            enableAutoFullscreenOnRotate()
            registerWithLifecycle()
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        playerControlView = findViewById(androidx.media3.ui.R.id.exo_controller) as? TPStreamsPlayerControlView
        
        setupSettingsButton()
        setupFullscreenButton()
        showFullscreenButton()
    }
    
    private fun setupSettingsButton() {
        playerControlView?.setOnSettingsClickListener {
            settingsPanel.showSettings()
        }
    }
    
    private fun setupFullscreenButton() {
        playerControlView?.setOnFullscreenClickListener {
            toggleFullscreen()
        }
    }
    
    override fun setFullscreenButtonState(isFullscreen: Boolean) {
        playerControlView?.setFullscreenState(isFullscreen)
    }
    
    fun toggleFullscreen() {
        if (!fullscreenMode.isInFullscreenMode()) fullscreenMode.enterFullscreen() else fullscreenMode.exitFullscreen()
    }

    fun showFullscreenButton() {
        playerControlView?.findViewById<View>(androidx.media3.ui.R.id.exo_fullscreen)?.visibility = View.VISIBLE
    }

    fun enableAutoFullscreenOnRotate() {
        disableAutoFullscreenOnRotate()
        
        orientationEventListener = OrientationListener(context).apply {
            setOnChangeListener { isLandscape ->
                post {
                    if (isLandscape) {
                        if (!fullscreenMode.isInFullscreenMode()) {
                            fullscreenMode.enterFullscreen()
                        }
                    } else {
                        if (fullscreenMode.isInFullscreenMode()) {
                            fullscreenMode.exitFullscreen()
                        }
                    }
                }
            }
            start()
        }
        
        autoFullscreenEnabled = true
    }

    fun disableAutoFullscreenOnRotate() {
        orientationEventListener?.stop()
        orientationEventListener = null
        autoFullscreenEnabled = false
    }

    private fun registerWithLifecycle() {
        val lifecycleOwner = contextAccess.getLifecycleOwner()
        if (lifecycleOwner != null && lifecycleManager != null) {
            Log.d(TAG, "Registering with lifecycle")
            lifecycleOwner.lifecycle.addObserver(lifecycleManager!!)
        }
    }
    
    private fun unregisterFromLifecycle() {
        val lifecycleOwner = contextAccess.getLifecycleOwner()
        if (lifecycleOwner != null && lifecycleManager != null) {
            Log.d(TAG, "Unregistering from lifecycle")
            lifecycleOwner.lifecycle.removeObserver(lifecycleManager!!)
        }
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
                        fullscreenMode.enterFullscreen()
                    }
                    Configuration.ORIENTATION_PORTRAIT -> {
                        setFullscreenButtonState(false)
                        fullscreenMode.exitFullscreen()
                    }
                }
                
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

    override fun setPlayer(player: Player?) {
        getPlayer()?.removeListener(playbackStateListener)
        super.setPlayer(player)
        
        lifecycleManager = player?.let { PlayerLifecycleManager(it) }
        registerWithLifecycle()
        
        player?.addListener(playbackStateListener)
        
        if (player is TPStreamsPlayer) {
            player.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    settingsPanel.updateAvailableResolutions()
                    captions.updateAvailableCaptions()
                }
                
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        captions.updateAvailableCaptions()
                    }
                }
            })
            
            captions.updateAvailableCaptions()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        getPlayer()?.removeListener(playbackStateListener)
        unregisterFromLifecycle()
        disableAutoFullscreenOnRotate()
    }

    // Implementation of PlayerSettingsBottomSheet.SettingsListener
    override fun onQualitySelected() = settingsPanel.showQualityOptionsBottomSheet()
    override fun onCaptionsSelected() = captions.showCaptionsBottomSheet()
    override fun onPlaybackSpeedSelected() = settingsPanel.showPlaybackSpeedBottomSheet()
    override fun onDownloadSelected() = downloadActions.onDownloadSelected()
    override fun getCurrentQuality() = settingsPanel.getCurrentQuality()
    override fun getCurrentCaptionStatus() = captions.getCurrentCaptionStatus()
    override fun getPlaybackSpeed() = settingsPanel.getPlaybackSpeed()
    override fun getCurrentDownloadStatus() = downloadActions.getCurrentDownloadStatus()
    override fun getDownloadIcon() = downloadActions.getDownloadIcon()
    override fun isDownloadEnabled() = settingsPanel.isDownloadEnabled()

    // Implementation of QualityOptionsBottomSheet.QualityOptionsListener
    override fun onAutoQualitySelected() = settingsPanel.onAutoQualitySelected()
    override fun onHigherQualitySelected() = settingsPanel.onHigherQualitySelected()
    override fun onDataSaverSelected() = settingsPanel.onDataSaverSelected()
    override fun onAdvancedSelected() = settingsPanel.showAdvancedResolutionBottomSheet()

    // Implementation of AdvancedResolutionBottomSheet.ResolutionSelectionListener
    override fun onResolutionSelected(resolution: String) = settingsPanel.onResolutionSelected(resolution)

    // Implementation of PlaybackSpeedBottomSheet.PlaybackSpeedListener
    override fun onSpeedSelected(speed: Float) = settingsPanel.onSpeedSelected(speed)

    // Implementation of CaptionsBottomSheet.CaptionsOptionsListener
    override fun onCaptionsDisabled() = captions.onCaptionsDisabled()
    override fun onCaptionLanguageSelected(language: String) = captions.onCaptionLanguageSelected(language)
    override fun getCurrentCaptionLanguage() = captions.getCurrentCaptionLanguage()

    // Implementation of DownloadOptionsBottomSheet.DownloadSelectionListener
    override fun onDownloadResolutionSelected(resolution: String) = downloadActions.onDownloadResolutionSelected(resolution)

    override fun getPlayer(): TPStreamsPlayer? {
        return super.getPlayer() as? TPStreamsPlayer
    }

    fun getActivity() = contextAccess.getActivity()

    companion object {
        private const val TAG = "TPStreamsPlayerView"
    }
}
