package com.tpstreams.player

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.tpstreams.player.constants.PlaybackError
import com.tpstreams.player.R

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
    
    private var errorOverlay: View? = null
    private var errorTextView: TextView? = null
    private var bufferingView: View? = null

    private val playbackStateListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@TPStreamsPlayerView.keepScreenOn = isPlaying
            lifecycleManager?.onPlaybackStateChanged(isPlaying)
        }
        
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> {
                    showLoading()
                }
                Player.STATE_BUFFERING -> {
                    showLoading()
                    hideErrorMessage()
                }
                Player.STATE_READY -> {
                    hideLoading()
                    hideErrorMessage()
                }
                Player.STATE_ENDED -> {
                    hideLoading()
                }
            }
        }
        
        override fun onIsLoadingChanged(isLoading: Boolean) {
            if (isLoading) {
                showLoading()
            } else {
                val player = getPlayer()
                if (player?.playbackState == Player.STATE_READY) {
                    hideLoading()
                }
            }
        }
        
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                hideErrorMessage()
            }
        }
    }
    
    private val tracksStateListener = object : Player.Listener {
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            settingsPanel.updateAvailableResolutions()
            captions.updateAvailableCaptions()
        }
        
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                captions.updateAvailableCaptions()
            }
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
        ensureErrorOverlaySetup()
        
        post {
            enableAutoFullscreenOnRotate()
            registerWithLifecycle()
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        playerControlView = findViewById(androidx.media3.ui.R.id.exo_controller) as? TPStreamsPlayerControlView
        
        ensureErrorOverlaySetup()
        cacheBufferingView()
        setupSettingsButton()
        setupFullscreenButton()
        showFullscreenButton()
    }
    
    private fun ensureErrorOverlaySetup() {
        if (errorOverlay != null) return
        
        try {
            val overlay = android.view.LayoutInflater.from(context)
                .inflate(R.layout.error_overlay, this, false)
            
            overlay.layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            
            addView(overlay, childCount)
            overlay.bringToFront()
            
            errorOverlay = overlay
            errorTextView = overlay.findViewById(R.id.error_message_text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup error overlay", e)
        }
    }
    
    private fun cacheBufferingView() {
        if (bufferingView == null) {
            bufferingView = findViewById(androidx.media3.ui.R.id.exo_buffering)
        }
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
        
        post {
            val tpPlayer = getPlayer()
            if (tpPlayer != null) {
                updateLiveStreamUI(tpPlayer.isLiveStream)
            }
        }
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
        val manager = lifecycleManager
        if (lifecycleOwner != null && manager != null) {
            lifecycleOwner.lifecycle.addObserver(manager)
        }
    }
    
    private fun unregisterFromLifecycle() {
        val lifecycleOwner = contextAccess.getLifecycleOwner()
        val manager = lifecycleManager
        if (lifecycleOwner != null && manager != null) {
            lifecycleOwner.lifecycle.removeObserver(manager)
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
                    
                    val tpPlayer = getPlayer()
                    if (tpPlayer != null) {
                        updateLiveStreamUI(tpPlayer.isLiveStream)
                    }
                }
            }
        }
    }

    override fun setPlayer(player: Player?) {
        if (player == getPlayer()) return
        
        ensureErrorOverlaySetup()
        
        // Clean up previous player
        val previousPlayer = getPlayer()
        if (previousPlayer is TPStreamsPlayer) {
            previousPlayer.listener = null
            previousPlayer.onLiveStreamStatusChanged = null
            previousPlayer.removeListener(tracksStateListener)
        }
        previousPlayer?.removeListener(playbackStateListener)
        
        super.setPlayer(player)
        
        lifecycleManager = player?.let { PlayerLifecycleManager(it) }
        registerWithLifecycle()
        
        if (player != null) {
            showLoading()
            player.addListener(playbackStateListener)
            
            if (player is TPStreamsPlayer) {
                player.addListener(tracksStateListener)
                val existingListener = player.listener
                player.listener = object : TPStreamsPlayer.Listener {
                    override fun onAccessTokenExpired(videoId: String, callback: (String) -> Unit) {
                        existingListener?.onAccessTokenExpired(videoId, callback)
                            ?: callback("")
                    }
                    
                    override fun onError(error: PlaybackError, message: String) {
                        hideLoading()
                        post { showErrorMessage(message) }
                        existingListener?.onError(error, message)
                    }
                }
                captions.updateAvailableCaptions()
                
                player.onLiveStreamStatusChanged = { isLiveStream ->
                    updateLiveStreamUI(isLiveStream)
                }
                updateLiveStreamUI(player.isLiveStream)
                
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            updateLiveStreamUI(player.isLiveStream)
                        }
                    }
                })
            }
        } else {
            hideErrorMessage()
            hideLoading()
            updateLiveStreamUI(false)
        }
    }

    /**
     * Updates the UI elements for live stream playback
     * Shows LIVE badge and hides duration for active live streams
     */
    private fun updateLiveStreamUI(isLiveStream: Boolean) {
        val liveBadge = findViewById<View>(R.id.live_badge)
        if (liveBadge != null) {
            liveBadge.visibility = if (isLiveStream) View.VISIBLE else View.GONE
        } else {
            Log.e(TAG, "LIVE badge not found!")
        }
        
        val durationView = findViewById<View>(androidx.media3.ui.R.id.exo_duration)
        val separatorView = findViewById<View>(R.id.exo_time_separator)
        
        if (isLiveStream) {
            durationView?.visibility = View.GONE
            separatorView?.visibility = View.GONE
        } else {
            durationView?.visibility = View.VISIBLE
            separatorView?.visibility = View.VISIBLE
        }
        
        if (durationView == null) {
            Log.e(TAG, "Duration view not found!")
        }
        if (separatorView == null) {
            Log.e(TAG, "Separator view not found!")
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        
        // Ensure error overlay is properly laid out when view is measured
        errorOverlay?.let { overlay ->
            if (overlay.visibility == View.VISIBLE && (overlay.width == 0 || overlay.height == 0)) {
                val overlayWidth = right - left
                val overlayHeight = bottom - top
                if (overlayWidth > 0 && overlayHeight > 0) {
                    overlay.measure(
                        View.MeasureSpec.makeMeasureSpec(overlayWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(overlayHeight, View.MeasureSpec.EXACTLY)
                    )
                    overlay.layout(0, 0, overlayWidth, overlayHeight)
                }
            }
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
    
    private fun showErrorMessage(message: String) {
        ensureErrorOverlaySetup()
        
        val overlay = errorOverlay ?: return
        val textView = errorTextView ?: return
        
        overlay.visibility = View.VISIBLE
        overlay.bringToFront()
        
        // Measure and layout overlay to ensure proper dimensions (critical for React Native)
        post {
            val parentWidth = width
            val parentHeight = height
            
            if (parentWidth > 0 && parentHeight > 0) {
                overlay.measure(
                    View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(parentHeight, View.MeasureSpec.EXACTLY)
                )
                overlay.layout(0, 0, parentWidth, parentHeight)
            } else {
                overlay.requestLayout()
            }
            overlay.invalidate()
        }
        
        if (isDecoderError(message)) {
            setHtmlText(textView, message)
        } else {
            textView.text = message
        }
    }
    
    private fun hideErrorMessage() {
        errorOverlay?.visibility = View.GONE
    }
    
    private fun showLoading() {
        bufferingView?.visibility = View.VISIBLE
    }
    
    private fun hideLoading() {
        bufferingView?.visibility = View.GONE
    }
    
    private fun isDecoderError(message: String): Boolean {
        return listOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED.toString(),
            PlaybackException.ERROR_CODE_DECODING_FAILED.toString()
        ).any { message.contains(it) }
    }
    
    private fun setHtmlText(textView: TextView, message: String) {
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(message)
        }
    }

    companion object {
        private const val TAG = "TPStreamsPlayerView"
    }
}
