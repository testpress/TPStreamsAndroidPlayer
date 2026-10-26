package com.tpstreams.player

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import androidx.activity.ComponentActivity
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.tpstreams.player.constants.NetworkDiagnostics
import com.tpstreams.player.constants.PlaybackError
import com.tpstreams.player.ui.PlayerErrorViewController
import com.tpstreams.player.ui.PlayerSheetManager
import com.tpstreams.player.ui.controller.Captions
import com.tpstreams.player.ui.controller.DoubleTapSeekController
import com.tpstreams.player.ui.controller.DownloadActions
import com.tpstreams.player.ui.config.PlayerViewAttributes
import com.tpstreams.player.ui.config.TextureViewSupport
import com.tpstreams.player.ui.controller.AutoFullscreenController
import com.tpstreams.player.ui.controller.DrmSurfaceGuard
import com.tpstreams.player.ui.controller.FullscreenMode
import com.tpstreams.player.ui.controller.PlaybackStateUiController
import com.tpstreams.player.ui.controller.SecureFlagManager
import com.tpstreams.player.ui.controller.PlayerZoomController
import com.tpstreams.player.ui.controller.SettingsPanel
import com.tpstreams.player.ui.controller.TouchGestureCoordinator
import com.tpstreams.player.ui.bottomsheet.AdvancedResolutionBottomSheet
import com.tpstreams.player.ui.bottomsheet.CaptionsBottomSheet
import com.tpstreams.player.ui.bottomsheet.DownloadActionBottomSheet
import com.tpstreams.player.ui.bottomsheet.DownloadOptionsBottomSheet
import com.tpstreams.player.ui.bottomsheet.PlaybackSpeedBottomSheet
import com.tpstreams.player.ui.bottomsheet.PlayerSettingsBottomSheet
import com.tpstreams.player.ui.bottomsheet.QualityOptionsBottomSheet
import com.tpstreams.player.ui.host.ContextAccess
import com.tpstreams.player.ui.host.FullscreenHost
import com.tpstreams.player.ui.lifecycle.PlayerLifecycleManager
import com.tpstreams.player.ui.watermark.BaseWatermarkController
import com.tpstreams.player.ui.watermark.ImageWatermarkController
import com.tpstreams.player.ui.watermark.TextWatermarkController

@UnstableApi
class TPStreamsPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr), FullscreenHost {

    override val hostedView: View
        get() = this

    override val currentPlayer: TPStreamsPlayer?
        get() = getPlayer()

    /**
     * True when this view is actively using [TextureView] as its video rendering surface.
     * Used to guard against Widevine L1 DRM playback on TextureView, which would result
     * in a silent black screen.
     */
    private val isTextureViewMode: Boolean
        get() = videoSurfaceView is TextureView

    // Controllers
    private val fullscreenMode = FullscreenMode(this)
    private val downloadActions = DownloadActions(this)
    private val settingsPanel = SettingsPanel(this)
    private val captions = Captions(this)
    private val contextAccess = ContextAccess(this)
    private val watermarkControllers = mutableListOf<BaseWatermarkController<*>>()
    private val errorViewController = PlayerErrorViewController(this)
    private val sheetManager = PlayerSheetManager(settingsPanel, captions, downloadActions) { getPlayer() }
    private val zoomController = PlayerZoomController(this)
    private val doubleTapSeekController = DoubleTapSeekController(this)
    private val touchGestureCoordinator = TouchGestureCoordinator(this, zoomController, doubleTapSeekController)
    private val secureFlagManager = SecureFlagManager()
    private val autoFullscreenController = AutoFullscreenController(
        host = this,
        fullscreenMode = fullscreenMode,
        context = context,
        onLiveStreamStateChanged = ::updateLiveStreamUI
    )
    private val drmSurfaceGuard = DrmSurfaceGuard(
        isTextureView = { isTextureViewMode },
        getPlayer = ::getPlayer,
        showError = ::showErrorMessage
    )
    private val playbackStateUiController = PlaybackStateUiController(
        view = this,
        settingsPanel = settingsPanel,
        captions = captions,
        drmSurfaceGuard = drmSurfaceGuard,
        showLoading = ::showLoading,
        hideLoading = ::hideLoading,
        showError = ::showErrorMessage,
        hideError = ::hideErrorMessage,
        showDiagnostics = ::showNetworkDiagnostics,
        showDiagnosing = ::showDiagnosingState,
        updateLiveUi = ::updateLiveStreamUI,
        notifyWatermarkState = ::notifyWatermarkPlayerState
    )

    private var playerControlView: TPStreamsPlayerControlView? = null
    /**
     * The lifecycle manager is owned by [TPStreamsPlayer] and accessed here as a computed
     * property. This ensures the manager survives [setPlayer] surface-swap round-trips
     * (e.g. fullscreen transitions) without being recreated and losing its state.
     */
    override val lifecycleManager: PlayerLifecycleManager?
        get() = getPlayer()?.lifecycleManager

    private var bufferingView: View? = null

    private val liveBadge: View? by lazy { findViewById(R.id.live_badge) }
    private val durationView: View? by lazy { findViewById(androidx.media3.ui.R.id.exo_duration) }
    private val separatorView: View? by lazy { findViewById(R.id.exo_time_separator) }

    // Bottom sheets (delegated to sheetManager for backward compatibility)
    val settingsBottomSheet: PlayerSettingsBottomSheet get() = sheetManager.settingsBottomSheet
    val qualityOptionsBottomSheet: QualityOptionsBottomSheet get() = sheetManager.qualityOptionsBottomSheet
    val advancedResolutionBottomSheet: AdvancedResolutionBottomSheet get() = sheetManager.advancedResolutionBottomSheet
    val playbackSpeedBottomSheet: PlaybackSpeedBottomSheet get() = sheetManager.playbackSpeedBottomSheet
    val captionsBottomSheet: CaptionsBottomSheet get() = sheetManager.captionsBottomSheet
    val downloadOptionsBottomSheet: DownloadOptionsBottomSheet get() = sheetManager.downloadOptionsBottomSheet
    val downloadActionBottomSheet: DownloadActionBottomSheet get() = sheetManager.downloadActionBottomSheet

    init {
        PlayerViewAttributes.from(context, attrs, defStyleAttr)?.let { attributes ->
            zoomController.isEnabled = attributes.pinchToZoomEnabled
            zoomController.maxScale = attributes.maxZoomScale
            zoomController.showZoomIndicator = attributes.showZoomIndicator
            zoomController.zoomOnlyInFullscreen = attributes.zoomOnlyInFullscreen
            doubleTapSeekController.isEnabled = attributes.doubleTapToSeekEnabled
            attributes.doubleTapSeekIncrementMs?.let { doubleTapSeekController.seekIncrementMs = it }
        }
        onFinishInflate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        playbackStateUiController.attachPlaybackListener(getPlayer())
        errorViewController.ensureErrorOverlaySetup()
        zoomController.setup()

        // Re-apply FLAG_SECURE on re-attach — handles fullscreen transitions where the view is
        // temporarily detached from its parent and re-parented to the decor view.
        if (getPlayer() != null) {
            getActivity()?.let(secureFlagManager::apply)
        }

        post {
            autoFullscreenController.onAttached()
            watermarkControllers.forEach { it.onViewAttached() }
            autoFullscreenController.restoreRequestedFullscreenIfNeeded()
        }
    }

    fun setAutoFullscreenOnRotateEnabled(enabled: Boolean) {
        autoFullscreenController.setEnabled(enabled)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        playerControlView = findViewById(androidx.media3.ui.R.id.exo_controller) as? TPStreamsPlayerControlView

        errorViewController.ensureErrorOverlaySetup()
        cacheBufferingView()
        setupSettingsButton()
        setupFullscreenButton()
        showFullscreenButton()
        setupControllerVisibilityListener()
        zoomController.setup()
        doubleTapSeekController.setup()
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
        zoomController.resetZoom(animated = false)
    }

    fun showFullscreenButton() {
        playerControlView?.findViewById<View>(androidx.media3.ui.R.id.exo_fullscreen)?.visibility = View.VISIBLE
    }

    fun toggleFullscreen() {
        if (!fullscreenMode.isInFullscreenMode()) {
            fullscreenMode.enterFullscreen()
        } else {
            val isPhysicallyLandscape = autoFullscreenController.isCurrentlyLandscape
            fullscreenMode.exitFullscreen(suppressAutoReentry = isPhysicallyLandscape)
        }

        post {
            val tpPlayer = getPlayer()
            if (tpPlayer != null) {
                updateLiveStreamUI(tpPlayer.isLiveStream)
            }
        }
    }

    fun enableAutoFullscreenOnRotate() {
        autoFullscreenController.enable()
    }

    fun disableAutoFullscreenOnRotate() {
        autoFullscreenController.disable()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        autoFullscreenController.onConfigurationChanged(newConfig)
    }

    override fun setPlayer(player: Player?) {
        // If the player is being removed while in fullscreen (e.g. host app calls setPlayer(null)
        // in onDestroy), restore the view hierarchy, orientation and system UI first — otherwise
        // the view stays stranded in the DecorView after the player is released.
        // Guard against isInTransition: exitFullscreen() calls setPlayer(null) internally as part
        // of its own transition — without this guard, restoreUI() would be called twice, with the
        // second call trying to remove a view that is no longer in the DecorView.
        if (player == null && fullscreenMode.isInFullscreenMode() && !fullscreenMode.isInTransition()) {
            val activity = getActivity() as? ComponentActivity
            if (activity != null && !activity.isChangingConfigurations && !activity.isFinishing && !activity.isDestroyed) {
                fullscreenMode.restoreUI(activity)
            }
        }

        // Use super.getPlayer() for the identity check so non-TPStreams players are compared
        // correctly, and so this also runs after fullscreen cleanup for an already-empty view.
        if (player == super.getPlayer()) return

        errorViewController.ensureErrorOverlaySetup()

        // Clean up previous player
        val previousPlayer = getPlayer()
        if (previousPlayer is TPStreamsPlayer) {
            previousPlayer.onSurfaceDetached()
        }
        playbackStateUiController.unbind(previousPlayer)

        super.setPlayer(player)

        // Apply any resolution preference that was set before the player was attached
        settingsPanel.applyResolutionPreference()

        if (player is TPStreamsPlayer) {
            player.onSurfaceAttached()
        }



        if (player == null) {
            // Explicitly clear FLAG_SECURE when the player is released.
            secureFlagManager.remove()
        }
        if (player != null) {
            // Apply FLAG_SECURE for all playback to block screen recording and screenshots.
            getActivity()?.let(secureFlagManager::apply)
            playbackStateUiController.bind(player)
            if (player is TPStreamsPlayer) {
                autoFullscreenController.restoreRequestedFullscreenIfNeeded()
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
        playbackStateUiController.detachPlaybackListener(getPlayer())
        autoFullscreenController.onDetached()
        watermarkControllers.forEach { it.onViewDetached() }
        zoomController.resetZoom(animated = false)
        zoomController.destroy()
        doubleTapSeekController.onDetachedFromWindow()

        // Always remove FLAG_SECURE on detach. In a Single-Activity architecture the Activity
        // is rarely finishing during normal navigation, so guarding on isFinishing would leak
        // the flag to unrelated screens. onAttachedToWindow() re-applies it when the view is
        // re-attached for fullscreen transitions.
        secureFlagManager.remove()
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

    override fun getActivity() = contextAccess.getActivity()

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

    fun setWatermarks(configs: List<BaseWatermarkConfig>) {
        clearWatermarks()

        configs.forEach { config ->
            val controller: BaseWatermarkController<*> = when (config) {
                is TextWatermarkConfig -> TextWatermarkController(this).also { it.apply(config) }
                is ImageWatermarkConfig -> ImageWatermarkController(this).also { it.apply(config) }
            }
            watermarkControllers.add(controller)
        }
    }

    fun clearWatermarks() {
        watermarkControllers.forEach { it.destroy() }
        watermarkControllers.clear()
    }

    private var externalControllerVisibilityListener: PlayerView.ControllerVisibilityListener? = null

    override fun setControllerVisibilityListener(listener: PlayerView.ControllerVisibilityListener?) {
        this.externalControllerVisibilityListener = listener
    }

    private fun setupControllerVisibilityListener() {
        super.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            val isVisible = visibility == View.VISIBLE
            playerControlView?.postInvalidate()
            postInvalidate()
            watermarkControllers.forEach { it.onControlsVisibilityChanged(isVisible) }
            externalControllerVisibilityListener?.onVisibilityChanged(visibility)
        })
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
        /**
         * Returns an [AttributeSet] that configures [PlayerView] to render video using [TextureView].
         * Pass this as the [attrs] parameter when instantiating [TPStreamsPlayerView] in Flutter
         * Texture Layer Hybrid Composition mode ([PlatformViewsService.initSurfaceAndroidView]).
         *
         * **WARNING:** [TextureView] is only safe for non-DRM or Widevine L3 (software) content.
         * Widevine L1 (hardware-secure) DRM requires a protected native surface, which only
         * [android.view.SurfaceView] provides. Playing L1 DRM content in TextureView mode will
         * result in a **silent black screen** (audio plays, video does not render).
         *
         * For L1 DRM playback, use the default constructor with `attrs = null` (SurfaceView).
         */
        fun attributeSetForTextureView(context: Context): AttributeSet? {
            return TextureViewSupport.attributeSet(context)
        }
    }

    interface OnZoomChangeListener {
        fun onZoomChanged(scale: Float, translationX: Float, translationY: Float)
        fun onZoomReset()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (touchGestureCoordinator.dispatchTouchEvent(ev, ::dispatchCancelToChildren)) return true
        return super.dispatchTouchEvent(ev)
    }

    private fun dispatchCancelToChildren(ev: MotionEvent) {
        super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (touchGestureCoordinator.onTouchEvent(ev)) return true
        return super.onTouchEvent(ev)
    }

    /**
     * Enables or disables pinch-to-zoom (up to 8x) gestures.
     * Enabled by default.
     */
    var isPinchToZoomEnabled: Boolean
        get() = zoomController.isEnabled
        set(value) { zoomController.isEnabled = value }

    /**
     * The maximum scale factor allowed when zooming. Defaults to 8.0f.
     */
    var maxZoomScale: Float
        get() = zoomController.maxScale
        set(value) { zoomController.maxScale = value }

    /**
     * Whether to show the floating zoom indicator badge (e.g. "2.4x") while zooming.
     * Defaults to true.
     */
    var showZoomIndicator: Boolean
        get() = zoomController.showZoomIndicator
        set(value) { zoomController.showZoomIndicator = value }

    /**
     * The current zoom scale factor (1.0f means unzoomed).
     */
    val currentZoomScale: Float
        get() = zoomController.currentScale

    /**
     * True if the player view is currently zoomed in.
     */
    val isZoomed: Boolean
        get() = zoomController.isZoomed

    /**
     * Resets zoom back to 1.0x (Fit) and centers the content.
     */
    fun resetZoom(animated: Boolean = true) {
        zoomController.resetZoom(animated)
    }

    /**
     * Returns true if the player is currently in fullscreen mode.
     */
    internal fun isInFullscreenMode(): Boolean = fullscreenMode.isInFullscreenMode()

    /**
     * When true, pinch-to-zoom is only active while in fullscreen mode.
     * When false (default), pinch-to-zoom works in both inline and fullscreen modes.
     */
    var zoomOnlyInFullscreen: Boolean
        get() = zoomController.zoomOnlyInFullscreen
        set(value) { zoomController.zoomOnlyInFullscreen = value }

    /**
     * Sets a listener to receive zoom scale and reset events.
     */
    fun setOnZoomChangeListener(listener: OnZoomChangeListener?) {
        zoomController.onZoomChangeListener = listener
    }

    /**
     * Enables or disables double-tap to seek (10s back on left, 10s forward on right).
     * Enabled by default.
     */
    var isDoubleTapToSeekEnabled: Boolean
        get() = doubleTapSeekController.isEnabled
        set(value) { doubleTapSeekController.isEnabled = value }

    /**
     * The seek increment in milliseconds for double-tap seek. Defaults to 10,000ms (10 seconds).
     */
    var doubleTapSeekIncrementMs: Long
        get() = doubleTapSeekController.seekIncrementMs
        set(value) { doubleTapSeekController.seekIncrementMs = value }

    /**
     * Whether to show the visual ripple & chevron overlay during double-tap seeking.
     * Defaults to true.
     */
    var showDoubleTapSeekOverlay: Boolean
        get() = doubleTapSeekController.showSeekOverlay
        set(value) { doubleTapSeekController.showSeekOverlay = value }
}

