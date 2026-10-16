package com.tpstreams.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.ui.DoubleTapSeekOverlayView
import kotlin.math.hypot

/**
 * Controller for YouTube-style double-tap seeking in [TPStreamsPlayerView].
 *
 * Double-tapping the left side rewinds playback; double-tapping the right side
 * fast-forwards playback. Repeated taps within the accumulation window add more
 * time (e.g. 10s -> 20s -> 30s) and show visual feedback via [DoubleTapSeekOverlayView].
 */
@UnstableApi
internal class DoubleTapSeekController(
    private val playerView: TPStreamsPlayerView
) {

    /**
     * Whether double-tap seeking is enabled.
     */
    var isEnabled: Boolean = true
        set(value) {
            field = value
            if (!value && isSeeking) {
                cancelSeeking()
            }
        }

    /**
     * Seek increment in milliseconds for each tap (default: 10,000ms / 10s).
     */
    var seekIncrementMs: Long = DEFAULT_SEEK_INCREMENT_MS
        set(value) {
            require(value > 0) { "seekIncrementMs must be > 0, was $value" }
            field = value
        }

    /**
     * Whether to show the visual ripple & chevron overlay during seek.
     */
    var showSeekOverlay: Boolean = true

    @VisibleForTesting
    internal var overlayView: DoubleTapSeekOverlayView? = null

    private val handler: Handler by lazy {
        val looper = Looper.getMainLooper() ?: Looper.myLooper()
        Handler(looper ?: Looper.getMainLooper())
    }
    private val touchSlop: Int by lazy {
        try {
            ViewConfiguration.get(playerView.context)?.scaledTouchSlop ?: 24
        } catch (_: Exception) {
            24
        }
    }
    private val doubleTapSlop: Float get() = touchSlop * 2.5f

    @VisibleForTesting
    internal var timeProvider: () -> Long = { SystemClock.uptimeMillis() }

    private var downX = 0f
    private var downY = 0f
    private var isDragging = false

    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var lastTapZone = Zone.NONE

    private var pendingSingleTapRunnable: Runnable? = null
    private var dismissRunnable: Runnable? = null

    @VisibleForTesting
    internal var isSeeking = false
        private set

    @VisibleForTesting
    internal var activeSide = Zone.NONE
        private set

    @VisibleForTesting
    internal var accumulatedSeekMs = 0L
        private set

    @VisibleForTesting
    internal var virtualTargetPositionMs = 0L
        private set

    enum class Zone {
        NONE, LEFT, RIGHT, CENTER
    }

    companion object {
        const val DEFAULT_SEEK_INCREMENT_MS = 10_000L
        const val DOUBLE_TAP_TIMEOUT_MS = 300L
        const val ACCUMULATION_TIMEOUT_MS = 650L
        const val LEFT_ZONE_RATIO = 0.45f
        const val RIGHT_ZONE_RATIO = 0.55f
    }

    /**
     * Attaches the [DoubleTapSeekOverlayView] into the player view hierarchy.
     */
    fun setup() {
        if (overlayView != null) return

        val overlay = DoubleTapSeekOverlayView(playerView.context)
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        val exoOverlay = playerView.findViewById<FrameLayout>(androidx.media3.ui.R.id.exo_overlay)
        if (exoOverlay != null) {
            exoOverlay.addView(overlay, lp)
        } else {
            playerView.addView(overlay, lp)
        }
        overlay.onAnimationEndListener = {
            finishSeeking()
        }
        overlayView = overlay
    }

    /**
     * Intercepts touch events for double-tap seek detection.
     *
     * @return true if the event was consumed by the seek controller.
     */
    fun handleTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled) return false

        val viewWidth = playerView.width
        if (viewWidth <= 0) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging && hypot(ev.x - downX, ev.y - downY) > touchSlop) {
                    isDragging = true
                    cancelPendingSingleTap()
                }
                return isSeeking || !isDragging
            }

            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    return false
                }

                val now = timeProvider()
                val currentZone = determineZone(ev.x, viewWidth)

                if (currentZone == Zone.CENTER) {
                    cancelPendingSingleTap()
                    if (isSeeking) {
                        finishSeeking()
                    } else {
                        toggleControllerVisibility()
                    }
                    lastTapZone = Zone.NONE
                    return true
                }

                // If currently actively seeking on the same side and within accumulation window
                if (isSeeking && activeSide == currentZone && (now - lastTapTime) < ACCUMULATION_TIMEOUT_MS) {
                    accumulateSeek(currentZone, ev.x, ev.y)
                    lastTapTime = now
                    return true
                }

                // If currently seeking on the opposite side, finish the old seek and start a new one
                if (isSeeking && activeSide != currentZone) {
                    finishSeeking()
                }

                // Check for double tap
                val isDoubleTapCandidate = lastTapZone == currentZone &&
                        (now - lastTapTime) < DOUBLE_TAP_TIMEOUT_MS &&
                        hypot(ev.x - lastTapX, ev.y - lastTapY) < doubleTapSlop

                if (isDoubleTapCandidate) {
                    cancelPendingSingleTap()
                    startSeeking(currentZone, ev.x, ev.y)
                    lastTapTime = now
                    return true
                }

                // First tap in seek zone: schedule single tap toggle with double-tap timeout
                cancelPendingSingleTap()
                lastTapTime = now
                lastTapX = ev.x
                lastTapY = ev.y
                lastTapZone = currentZone

                pendingSingleTapRunnable = Runnable {
                    toggleControllerVisibility()
                    lastTapZone = Zone.NONE
                    pendingSingleTapRunnable = null
                }
                handler.postDelayed(pendingSingleTapRunnable!!, DOUBLE_TAP_TIMEOUT_MS)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                cancelPendingSingleTap()
                return false
            }
        }

        return false
    }

    private fun determineZone(x: Float, width: Int): Zone {
        return when {
            x < width * LEFT_ZONE_RATIO -> Zone.LEFT
            x > width * RIGHT_ZONE_RATIO -> Zone.RIGHT
            else -> Zone.CENTER
        }
    }

    private fun startSeeking(zone: Zone, touchX: Float, touchY: Float) {
        val player = playerView.getPlayer() ?: return
        if (!player.isCurrentMediaItemSeekable) return

        if (overlayView == null) setup()

        isSeeking = true
        activeSide = zone
        accumulatedSeekMs = seekIncrementMs

        val isForward = zone == Zone.RIGHT
        virtualTargetPositionMs = calculateInitialTargetPosition(isForward)

        // Hide controller so it does not interfere with the seek animation
        playerView.hideController()

        if (showSeekOverlay) {
            overlayView?.showSeek(isForward, (accumulatedSeekMs / 1000L).toInt(), touchX, touchY)
        }

        try {
            playerView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Exception) {}

        scheduleDismiss()
    }

    private fun accumulateSeek(zone: Zone, touchX: Float, touchY: Float) {
        val player = playerView.getPlayer() ?: return
        if (!player.isCurrentMediaItemSeekable) return

        accumulatedSeekMs += seekIncrementMs
        val isForward = zone == Zone.RIGHT
        virtualTargetPositionMs = calculateAccumulatedTargetPosition(isForward)

        if (showSeekOverlay) {
            overlayView?.showSeek(isForward, (accumulatedSeekMs / 1000L).toInt(), touchX, touchY)
        }

        try {
            playerView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Exception) {}

        scheduleDismiss()
    }

    private fun calculateInitialTargetPosition(isForward: Boolean): Long {
        val player = playerView.getPlayer() ?: return 0L
        val current = player.currentPosition
        val duration = player.duration

        return if (isForward) {
            val target = current + seekIncrementMs
            if (duration != C.TIME_UNSET) target.coerceAtMost(duration) else target
        } else {
            (current - seekIncrementMs).coerceAtLeast(0L)
        }
    }

    private fun calculateAccumulatedTargetPosition(isForward: Boolean): Long {
        val player = playerView.getPlayer() ?: return 0L
        val duration = player.duration

        return if (isForward) {
            val target = virtualTargetPositionMs + seekIncrementMs
            if (duration != C.TIME_UNSET) target.coerceAtMost(duration) else target
        } else {
            (virtualTargetPositionMs - seekIncrementMs).coerceAtLeast(0L)
        }
    }

    private fun scheduleDismiss() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = Runnable {
            finishSeeking()
        }
        handler.postDelayed(dismissRunnable!!, ACCUMULATION_TIMEOUT_MS)
    }

    @VisibleForTesting
    internal fun finishSeeking() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null

        if (isSeeking && accumulatedSeekMs > 0L) {
            val player = playerView.getPlayer()
            if (player != null && player.isCurrentMediaItemSeekable) {
                player.seekTo(virtualTargetPositionMs)
            }
        }

        isSeeking = false
        activeSide = Zone.NONE
        accumulatedSeekMs = 0L
        virtualTargetPositionMs = 0L
        lastTapZone = Zone.NONE
        overlayView?.hideSeek(animated = true)
    }

    private fun cancelSeeking() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null
        isSeeking = false
        activeSide = Zone.NONE
        accumulatedSeekMs = 0L
        virtualTargetPositionMs = 0L
        lastTapZone = Zone.NONE
        overlayView?.hideSeek(animated = false)
    }

    private fun cancelPendingSingleTap() {
        pendingSingleTapRunnable?.let { handler.removeCallbacks(it) }
        pendingSingleTapRunnable = null
    }

    private fun toggleControllerVisibility() {
        if (playerView.isControllerFullyVisible) {
            playerView.hideController()
        } else {
            playerView.showController()
        }
    }

    fun onDetachedFromWindow() {
        cancelPendingSingleTap()
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null
        isSeeking = false
    }
}
