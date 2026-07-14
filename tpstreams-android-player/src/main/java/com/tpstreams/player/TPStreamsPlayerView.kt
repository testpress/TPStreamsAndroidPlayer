package com.tpstreams.player

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.core.content.ContextCompat
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.tpstreams.player.constants.NetworkDiagnostics
import com.tpstreams.player.constants.PlaybackError
import com.tpstreams.player.util.PlaybackHistoryManager
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
    private var autoFullscreenOnRotateEnabled = true
    private var autoFullscreenEnabled = false
    var lifecycleManager: PlayerLifecycleManager? = null
    
    private var errorOverlay: View? = null
    private var errorTextView: TextView? = null
    private var errorDescription: TextView? = null
    private var diagnosticsContainer: LinearLayout? = null
    private var errorSubtitle: TextView? = null
    private var errorDivider: View? = null
    private var retryLoader: View? = null
    private var retryIndicator: TextView? = null
    private var bufferingView: View? = null
    private var hasAcquiredSecureFlag = false
    
    private val liveBadge: View? by lazy { findViewById(R.id.live_badge) }
    private val durationView: View? by lazy { findViewById(androidx.media3.ui.R.id.exo_duration) }
    private val separatorView: View? by lazy { findViewById(R.id.exo_time_separator) }

    private val playbackStateListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@TPStreamsPlayerView.keepScreenOn = isPlaying
            lifecycleManager?.onPlaybackStateChanged(isPlaying)
            if (isPlaying) hideErrorMessage()
        }
        
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> {
                    showLoading()
                }
                Player.STATE_BUFFERING -> {
                    showLoading()
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
        
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
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
        
        // Re-apply FLAG_SECURE on re-attach — handles fullscreen transitions where the view is
        // temporarily detached from its parent and re-parented to the decor view.
        if (getPlayer() != null) {
            applySecureFlag()
        }

        post {
            if (autoFullscreenOnRotateEnabled) {
                enableAutoFullscreenOnRotate()
            }
            registerWithLifecycle()
        }
    }

    fun setAutoFullscreenOnRotateEnabled(enabled: Boolean) {
        if (autoFullscreenOnRotateEnabled == enabled) return
        autoFullscreenOnRotateEnabled = enabled
        if (enabled) {
            enableAutoFullscreenOnRotate()
        } else {
            disableAutoFullscreenOnRotate()
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
            errorDescription = overlay.findViewById(R.id.error_description)
            errorSubtitle = overlay.findViewById(R.id.error_subtitle)
            diagnosticsContainer = overlay.findViewById(R.id.diagnostics_container)
            retryLoader = overlay.findViewById(R.id.retry_loader)
            retryIndicator = overlay.findViewById(R.id.retry_indicator)
            errorDivider = overlay.findViewById(R.id.error_divider)
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
        
        if (autoFullscreenOnRotateEnabled && !autoFullscreenEnabled) {
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
            val message = "[${previousPlayer.playbackSessionId}] Surface DETACH"
            Log.d(TPStreamsPlayer.DEBUG_TAG, message)
            PlaybackHistoryManager.recordLog(message)
            previousPlayer.listener = null
            previousPlayer.onLiveStreamStatusChanged = null
            previousPlayer.removeListener(tracksStateListener)
        }
        previousPlayer?.removeListener(playbackStateListener)
        
        super.setPlayer(player)

        // Apply any resolution preference that was set before the player was attached
        settingsPanel.applyResolutionPreference()
        
        if (player is TPStreamsPlayer) {
            val message = "[${player.playbackSessionId}] Surface ATTACH"
            Log.d(TPStreamsPlayer.DEBUG_TAG, message)
            PlaybackHistoryManager.recordLog(message)
        }
        
        unregisterFromLifecycle()
        lifecycleManager = player?.let { PlayerLifecycleManager(it) }
        registerWithLifecycle()
        
        if (player == null) {
            // Explicitly clear FLAG_SECURE when the player is released.
            removeSecureFlag()
        }
        if (player != null) {
            // Apply FLAG_SECURE for all playback to block screen recording and screenshots.
            applySecureFlag()
            when (player.playbackState) {
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

                    override fun onNetworkError(error: PlaybackError, message: String, diagnostics: NetworkDiagnostics) {
                        post {
                            hideLoading()
                            showNetworkDiagnostics(error, diagnostics)
                        }
                        existingListener?.onNetworkError(error, message, diagnostics)
                    }

                    override fun onNetworkDiagnosticsStarted() {
                        post { showDiagnosingState() }
                        existingListener?.onNetworkDiagnosticsStarted()
                    }
                }
                captions.updateAvailableCaptions()
                
                player.onLiveStreamStatusChanged = { isLiveStream ->
                    updateLiveStreamUI(isLiveStream)
                }
                updateLiveStreamUI(player.isLiveStream)
                
                if (player.startInFullscreen) {
                    fullscreenMode.enterFullscreen()
                }
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
        liveBadge?.visibility = if (isLiveStream) View.VISIBLE else View.INVISIBLE
        
        if (isLiveStream) {
            durationView?.visibility = View.INVISIBLE
            separatorView?.visibility = View.INVISIBLE
        } else {
            durationView?.visibility = View.VISIBLE
            separatorView?.visibility = View.VISIBLE
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
        
        // Always remove FLAG_SECURE on detach. In a Single-Activity architecture the Activity
        // is rarely finishing during normal navigation, so guarding on isFinishing would leak
        // the flag to unrelated screens. onAttachedToWindow() re-applies it when the view is
        // re-attached for fullscreen transitions.
        removeSecureFlag()
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

    /**
     * Sets the desired video resolution for playback and updates the settings UI.
     * This is a user preference — the actual resolution may be capped by [TPStreamsPlayer.setMaxResolution].
     */
    fun setVideoResolution(height: Int) {
        require(height > 0) { "Resolution height must be positive: $height" }
        settingsPanel.setPreferredResolutionHeight(height)
    }

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
        
        // Isolate from network diagnostics UI
        textView.visibility = View.VISIBLE
        errorSubtitle?.visibility = View.GONE
        errorDescription?.visibility = View.GONE
        diagnosticsContainer?.visibility = View.GONE
        errorDivider?.visibility = View.GONE
        
        retryLoader?.visibility = View.GONE
        
        measureOverlay()
        
        if (isDecoderError(message)) {
            setHtmlText(textView, message)
        } else {
            textView.text = message
        }
    }
    
    private fun showDiagnosingState() {
        ensureErrorOverlaySetup()
        val overlay = errorOverlay ?: return

        overlay.visibility = View.VISIBLE
        overlay.bringToFront()

        errorTextView?.visibility = View.GONE
        errorDescription?.let {
            it.visibility = View.VISIBLE
            it.text = context.getString(R.string.network_diag_diagnosing)
        }
        errorSubtitle?.visibility = View.GONE
        diagnosticsContainer?.visibility = View.GONE
        errorDivider?.visibility = View.GONE

        retryLoader?.visibility = View.VISIBLE
        retryIndicator?.text = context.getString(R.string.network_diag_checking_connection)

        measureOverlay()
    }

    private fun showNetworkDiagnostics(error: PlaybackError, diagnostics: NetworkDiagnostics) {
        ensureErrorOverlaySetup()
        val overlay = errorOverlay ?: return

        overlay.visibility = View.VISIBLE
        overlay.bringToFront()

        // Isolate from standard error message text and diagnosing state.
        errorTextView?.visibility = View.GONE
        retryLoader?.visibility = View.GONE

        resolveDiagnosticText(error, diagnostics)
        buildDiagnosticsList(diagnostics)
        errorDivider?.visibility = View.VISIBLE

        errorSubtitle?.let {
            val id = diagnostics.playerId
            it.visibility = if (id != null) View.VISIBLE else View.GONE
            it.text = if (id != null) context.getString(R.string.network_diag_player_id, id) else ""
        }
        if (diagnostics.retryAttempt > 0) {
            retryLoader?.visibility = View.VISIBLE
            retryIndicator?.text = (1..diagnostics.maxRetries).joinToString(" ") { i ->
                if (i <= diagnostics.retryAttempt) "\u25CF" else "\u25CB"
            }
        }

        measureOverlay()
    }

    private fun resolveDiagnosticText(error: PlaybackError, diagnostics: NetworkDiagnostics) {
        val text = when {
            error == PlaybackError.VIDEO_SERVICE_BLOCKED -> {
                val isDnsFailure = !diagnostics.dnsResolves && diagnostics.internetReachable
                val isCdnProbeFailed = diagnostics.cdnReachable == false
                when {
                    isDnsFailure -> context.getString(R.string.network_diag_dns_failure)
                    isCdnProbeFailed -> context.getString(R.string.network_diag_cdn_blocked)
                    else -> context.getString(R.string.network_diag_generic_blocked)
                }
            }
            error == PlaybackError.UNSPECIFIED -> context.getString(R.string.network_diag_unknown_error)
            diagnostics.proxyConfigured -> context.getString(R.string.network_diag_proxy_unreachable)
            else -> context.getString(R.string.network_diag_no_internet)
        }
        errorDescription?.let {
            it.visibility = View.VISIBLE
            it.text = text
        }
    }

    private fun buildDiagnosticsList(diagnostics: NetworkDiagnostics) {
        val container = diagnosticsContainer ?: return
        container.visibility = View.VISIBLE
        container.removeAllViews()

        data class DiagItem(val label: String, val ok: Boolean?, val detail: String?)

        val items = mutableListOf(
            DiagItem(
                context.getString(R.string.network_diag_label_internet), diagnostics.internetReachable,
                diagnostics.internetLatencyMs?.let { "${it}ms" }
            ),
            DiagItem(
                context.getString(R.string.network_diag_label_video_server), diagnostics.serverReachable,
                diagnostics.serverDetail ?: diagnostics.serverLatencyMs?.let { "${it}ms" }
            )
        )
        items.add(DiagItem(context.getString(R.string.network_diag_label_dns), diagnostics.dnsResolves, null))
        items.add(
            DiagItem(
                context.getString(R.string.network_diag_label_cdn), if (diagnostics.cdnHostname == null) null else diagnostics.cdnReachable,
                if (diagnostics.cdnHostname == null) "\u2014" else diagnostics.cdnDetail ?: diagnostics.cdnLatencyMs?.let { "${it}ms" }
            )
        )
        if (diagnostics.proxyConfigured) {
            items.add(DiagItem(context.getString(R.string.network_diag_label_proxy), null, null))
        }

        items.forEach { item ->
            val color = when (item.ok) {
                true -> ContextCompat.getColor(context, R.color.network_diag_ok)
                false -> ContextCompat.getColor(context, R.color.network_diag_fail)
                null -> ContextCompat.getColor(context, R.color.network_diag_unknown)
            }
            val symbol = when (item.ok) {
                true -> "\u2713"
                false -> "\u2717"
                null -> "\u2014"
            }
            val detailText = if (item.detail != null) " \u00B7 ${item.detail}" else ""

            val fullText = "$symbol  ${item.label}$detailText"
            val detailStart = fullText.length - detailText.length

            val spannable = android.text.SpannableString(fullText)
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(color),
                0, fullText.length - detailText.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (detailText.isNotEmpty()) {
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(
                        ContextCompat.getColor(context, R.color.network_diag_detail)
                    ),
                    detailStart, fullText.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    android.text.style.AbsoluteSizeSpan(12, true),
                    detailStart, fullText.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            TextView(context).apply {
                text = spannable
                textSize = 12f
                setPadding(0, 3, 0, 3)
                container.addView(this)
            }
        }


    }

    private fun measureOverlay() {
        val overlay = errorOverlay ?: return
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

        /**
         * Tracks how many player views are active per Activity.
         *
         * FLAG_SECURE is a window-level flag shared by all views in an Activity. Using a simple
         * per-view boolean to track it causes two bugs in multi-view or Single-Activity scenarios:
         *   1. One view's removeSecureFlag() clears the flag while another view still needs it
         *      (regression — video content becomes screenshot/recording-capable).
         *   2. When a view is detached during navigation without the Activity finishing, the
         *      flag stays on unrelated screens (regression — screenshots blocked everywhere).
         *
         * Solution: ref-count acquisitions per Activity. FLAG_SECURE is set when count rises to 1
         * and cleared only when it drops back to 0. WeakHashMap ensures finished Activities
         * are not leaked.
         *
         * FLAG_SECURE is applied for all playback (DRM and non-DRM) to uniformly prevent
         * screen recording and screenshots while a player is on screen.
         */
        private val activePlayerViewCountByActivity = java.util.WeakHashMap<androidx.fragment.app.FragmentActivity, Int>()

        private fun acquireSecureFlag(activity: androidx.fragment.app.FragmentActivity) {
            val count = (activePlayerViewCountByActivity[activity] ?: 0) + 1
            activePlayerViewCountByActivity[activity] = count
            if (count == 1) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                Log.d(TAG, "FLAG_SECURE set (active player views: 1)")
            } else {
                Log.d(TAG, "FLAG_SECURE already set (active player views: $count)")
            }
        }

        private fun releaseSecureFlag(activity: androidx.fragment.app.FragmentActivity) {
            val count = ((activePlayerViewCountByActivity[activity] ?: 0) - 1).coerceAtLeast(0)
            if (count == 0) {
                activePlayerViewCountByActivity.remove(activity)
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                Log.d(TAG, "FLAG_SECURE cleared (no active player views)")
            } else {
                activePlayerViewCountByActivity[activity] = count
                Log.d(TAG, "FLAG_SECURE kept (active player views: $count)")
            }
        }
    }
    
    private fun applySecureFlag() {
        if (hasAcquiredSecureFlag) return
        val activity = getActivity() ?: return
        hasAcquiredSecureFlag = true
        acquireSecureFlag(activity)
    }

    private fun removeSecureFlag() {
        if (!hasAcquiredSecureFlag) return
        val activity = getActivity() ?: return
        hasAcquiredSecureFlag = false
        releaseSecureFlag(activity)
    }
}
