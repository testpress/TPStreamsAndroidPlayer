package com.tpstreams.player

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.tpstreams.player.constants.NetworkDiagnostics
import com.tpstreams.player.constants.PlaybackError
import com.tpstreams.player.ui.PlayerErrorViewController
import com.tpstreams.player.ui.PlayerSheetManager
import com.tpstreams.player.util.PlaybackHistoryManager

@UnstableApi
class TPStreamsPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr) {

    // Controllers
    private val fullscreenMode = FullscreenMode(this)
    private val downloadActions = DownloadActions(this)
    private val settingsPanel = SettingsPanel(this)
    private val captions = Captions(this)
    private val contextAccess = ContextAccess(this)
    private val watermarkControllers = mutableListOf<WatermarkController>()
    private val errorViewController = PlayerErrorViewController(this)
    private val sheetManager = PlayerSheetManager(settingsPanel, captions, downloadActions) { getPlayer() }

    private var playerControlView: TPStreamsPlayerControlView? = null
    private var orientationEventListener: OrientationListener? = null
    private var autoFullscreenOnRotateEnabled = true
    private var autoFullscreenEnabled = false
    var lifecycleManager: PlayerLifecycleManager? = null

    private var bufferingView: View? = null

    private val liveBadge: View? by lazy { findViewById(R.id.live_badge) }
    private val durationView: View? by lazy { findViewById(androidx.media3.ui.R.id.exo_duration) }
    private val separatorView: View? by lazy { findViewById(R.id.exo_time_separator) }

    private val playbackStateListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@TPStreamsPlayerView.keepScreenOn = isPlaying
            lifecycleManager?.onPlaybackStateChanged(isPlaying)
            if (isPlaying) hideErrorMessage()
            notifyWatermarkPlayerState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> showLoading()
                Player.STATE_BUFFERING -> showLoading()
                Player.STATE_READY -> {
                    hideLoading()
                    hideErrorMessage()
                }
                Player.STATE_ENDED -> hideLoading()
            }
            notifyWatermarkPlayerState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {}
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

    // Bottom sheets (delegated to sheetManager for backward compatibility)
    val settingsBottomSheet: PlayerSettingsBottomSheet get() = sheetManager.settingsBottomSheet
    val qualityOptionsBottomSheet: QualityOptionsBottomSheet get() = sheetManager.qualityOptionsBottomSheet
    val advancedResolutionBottomSheet: AdvancedResolutionBottomSheet get() = sheetManager.advancedResolutionBottomSheet
    val playbackSpeedBottomSheet: PlaybackSpeedBottomSheet get() = sheetManager.playbackSpeedBottomSheet
    val captionsBottomSheet: CaptionsBottomSheet get() = sheetManager.captionsBottomSheet
    val downloadOptionsBottomSheet: DownloadOptionsBottomSheet get() = sheetManager.downloadOptionsBottomSheet
    val downloadActionBottomSheet: DownloadActionBottomSheet get() = sheetManager.downloadActionBottomSheet

    init {
        onFinishInflate()
        post {
            registerWithLifecycle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        getPlayer()?.addListener(playbackStateListener)
        errorViewController.ensureErrorOverlaySetup()

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
            watermarkControllers.forEach { it.onViewAttached() }
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

        errorViewController.ensureErrorOverlaySetup()
        cacheBufferingView()
        setupSettingsButton()
        setupFullscreenButton()
        showFullscreenButton()
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

    fun showFullscreenButton() {
        playerControlView?.findViewById<View>(androidx.media3.ui.R.id.exo_fullscreen)?.visibility = View.VISIBLE
    }

    fun toggleFullscreen() {
        if (!fullscreenMode.isInFullscreenMode()) {
            fullscreenMode.enterFullscreen()
        } else {
            fullscreenMode.exitFullscreen()
        }

        post {
            val tpPlayer = getPlayer()
            if (tpPlayer != null) {
                updateLiveStreamUI(tpPlayer.isLiveStream)
            }
        }
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

        errorViewController.ensureErrorOverlaySetup()

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
                Player.STATE_IDLE -> showLoading()
                Player.STATE_BUFFERING -> {
                    showLoading()
                    hideErrorMessage()
                }
                Player.STATE_READY -> {
                    hideLoading()
                    hideErrorMessage()
                }
                Player.STATE_ENDED -> hideLoading()
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

        // Reposition watermark on layout changes (fullscreen, rotation, resize)
        watermarkControllers.forEach { it.onParentLayout() }

        // Ensure error overlay is properly laid out when view is measured
        errorViewController.onParentLayout(left, top, right, bottom)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        getPlayer()?.removeListener(playbackStateListener)
        unregisterFromLifecycle()
        disableAutoFullscreenOnRotate()
        watermarkControllers.forEach { it.onViewDetached() }

        // Always remove FLAG_SECURE on detach. In a Single-Activity architecture the Activity
        // is rarely finishing during normal navigation, so guarding on isFinishing would leak
        // the flag to unrelated screens. onAttachedToWindow() re-applies it when the view is
        // re-attached for fullscreen transitions.
        removeSecureFlag()
    }

    /**
     * Sets the desired video resolution for playback and updates the settings UI.
     * This is a user preference — the actual resolution may be capped by [TPStreamsPlayer.setMaxResolution].
     */
    fun setVideoResolution(height: Int) {
        require(height > 0) { "Resolution height must be positive: $height" }
        settingsPanel.setPreferredResolutionHeight(height)
    }

    override fun getPlayer(): TPStreamsPlayer? {
        return super.getPlayer() as? TPStreamsPlayer
    }

    fun getActivity() = contextAccess.getActivity()

    private fun showErrorMessage(message: String) {
        errorViewController.showErrorMessage(message)
    }

    private fun showDiagnosingState() {
        errorViewController.showDiagnosingState()
    }

    private fun showNetworkDiagnostics(error: PlaybackError, diagnostics: NetworkDiagnostics) {
        errorViewController.showNetworkDiagnostics(error, diagnostics)
    }

    private fun hideErrorMessage() {
        errorViewController.hideErrorMessage()
    }

    private fun showLoading() {
        bufferingView?.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        bufferingView?.visibility = View.GONE
    }

    // ── Watermark ────────────────────────────────────────────────────────

    fun setWatermarks(configs: List<WatermarkConfig>) {
        watermarkControllers.forEach { it.destroy() }
        watermarkControllers.clear()

        configs.forEach { config ->
            val controller = WatermarkController(this)
            watermarkControllers.add(controller)
            controller.apply(config)
        }
    }

    fun clearWatermarks() {
        watermarkControllers.forEach { it.destroy() }
        watermarkControllers.clear()
    }

    private fun notifyWatermarkPlayerState() {
        val player = getPlayer() ?: return
        watermarkControllers.forEach {
            it.onPlayerStateChanged(
                isPlaying = player.isPlaying,
                playbackState = player.playbackState
            )
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

    private var secureFlagActivity: androidx.fragment.app.FragmentActivity? = null

    private fun applySecureFlag() {
        if (secureFlagActivity != null) return
        val activity = getActivity() ?: return
        secureFlagActivity = activity
        acquireSecureFlag(activity)
    }

    private fun removeSecureFlag() {
        val activity = secureFlagActivity ?: return
        secureFlagActivity = null
        releaseSecureFlag(activity)
    }
}
