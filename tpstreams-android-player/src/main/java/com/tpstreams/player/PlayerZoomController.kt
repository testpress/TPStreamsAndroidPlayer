package com.tpstreams.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

typealias OnZoomChangeListener = TPStreamsPlayerView.OnZoomChangeListener

/**
 * Manages pinch-to-zoom (up to 8x) gestures on [TPStreamsPlayerView].
 *
 * Applies scaling and translation transformations to the player's [AspectRatioFrameLayout]
 * (exo_content_frame), keeping video content synchronized while player controls and subtitles
 * remain unaffected.
 */
@UnstableApi
internal class PlayerZoomController(
    private val playerView: TPStreamsPlayerView
) {

    /**
     * Whether pinch-to-zoom and pan gestures are enabled.
     */
    var isEnabled: Boolean = true
        set(value) {
            field = value
            if (!value && isZoomed) {
                resetZoom(animated = false)
            }
        }

    /**
     * When true, zoom gestures are only allowed while the player is in fullscreen mode.
     * When false (default), zoom gestures work in both inline and fullscreen modes.
     */
    var zoomOnlyInFullscreen: Boolean = false
        set(value) {
            field = value
            if (value && isZoomed && !playerView.isInFullscreenMode()) {
                resetZoom(animated = false)
            }
        }

    /**
     * Evaluates whether zoom interactions are currently permitted.
     */
    val isGestureAllowed: Boolean
        get() = isEnabled && (!zoomOnlyInFullscreen || playerView.isInFullscreenMode())

    /**
     * Maximum allowed zoom scale factor. Defaults to 8.0f.
     */
    var maxScale: Float = 8.0f
        set(value) {
            require(value >= 1.0f) { "maxScale must be >= 1.0" }
            field = value
            if (currentScale > value) {
                settleZoom()
            }
        }

    val minScale: Float = 1.0f

    /**
     * Whether to display a floating zoom badge pill while zooming.
     */
    var showZoomIndicator: Boolean = true

    var onZoomChangeListener: TPStreamsPlayerView.OnZoomChangeListener? = null

    var currentScale: Float = 1.0f
        internal set

    var currentTranslationX: Float = 0.0f
        internal set

    var currentTranslationY: Float = 0.0f
        internal set

    companion object {
        const val SCALE_EPSILON = 0.01f
    }

    val isZoomed: Boolean
        get() = currentScale > (1.0f + SCALE_EPSILON) || abs(currentTranslationX) > 1f || abs(currentTranslationY) > 1f

    var isScaling: Boolean = false
        internal set

    var isPanning: Boolean = false
        internal set

    val isInteracting: Boolean
        get() = isScaling || isPanning

    @get:VisibleForTesting
    internal var contentFrame: AspectRatioFrameLayout? = null

    private var zoomBadge: TextView? = null
    private var animator: ValueAnimator? = null
    private var lastFocusX: Float = 0f
    private var lastFocusY: Float = 0f
    private var lastSnappedState: Int = -1

    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (!isGestureAllowed) return false
            animator?.cancel()
            isScaling = true
            playerView.parent?.requestDisallowInterceptTouchEvent(true)
            playerView.hideController()
            lastFocusX = detector.focusX
            lastFocusY = detector.focusY
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!isGestureAllowed) return false
            val frame = contentFrame ?: return false
            val factor = detector.scaleFactor
            if (factor.isNaN() || factor.isInfinite()) return false

            val newTargetScale = (currentScale * factor).coerceIn(1.0f, maxScale)
            if (newTargetScale <= 1.0f) {
                currentScale = 1.0f
                currentTranslationX = 0f
                currentTranslationY = 0f
            } else {
                val actualFactor = if (currentScale > 0f) newTargetScale / currentScale else 1f

                val centerX = frame.left + frame.width / 2f + currentTranslationX
                val centerY = frame.top + frame.height / 2f + currentTranslationY

                val vecX = detector.focusX - centerX
                val vecY = detector.focusY - centerY

                val deltaFocusX = detector.focusX - lastFocusX
                val deltaFocusY = detector.focusY - lastFocusY

                currentScale = newTargetScale
                currentTranslationX += deltaFocusX + vecX * (1f - actualFactor)
                currentTranslationY += deltaFocusY + vecY * (1f - actualFactor)
            }
            lastFocusX = detector.focusX
            lastFocusY = detector.focusY

            applyTransformation()
            updateZoomBadge(currentScale)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            settleZoom()
            scheduleHideZoomBadge()
        }
    }

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (!isGestureAllowed) return false
            if (isZoomed && !isScaling) {
                animator?.cancel()
                isPanning = true
                playerView.parent?.requestDisallowInterceptTouchEvent(true)
                playerView.hideController()

                currentTranslationX -= distanceX
                currentTranslationY -= distanceY

                val bounds = calculateBounds(currentScale)
                currentTranslationX = currentTranslationX.coerceIn(bounds.minX, bounds.maxX)
                currentTranslationY = currentTranslationY.coerceIn(bounds.minY, bounds.maxY)

                applyTransformation()
                return true
            }
            return false
        }
    }

    private val scaleDetector by lazy {
        ScaleGestureDetector(playerView.context, scaleListener).apply {
            isQuickScaleEnabled = false
        }
    }

    private val gestureDetector by lazy {
        GestureDetector(playerView.context, gestureListener)
    }

    fun setup() {
        contentFrame = playerView.findViewById(androidx.media3.ui.R.id.exo_content_frame)
        ensureZoomBadge()
    }

    fun handleTouchEvent(ev: MotionEvent): Boolean {
        if (!isGestureAllowed) return false
        if (contentFrame == null) {
            setup()
        }
        val frame = contentFrame ?: return false

        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            animator?.cancel()
            lastFocusX = ev.x
            lastFocusY = ev.y
        }

        val wasInteracting = isInteracting
        scaleDetector.onTouchEvent(ev)
        val gestureHandled = gestureDetector.onTouchEvent(ev)

        var handled = isInteracting || gestureHandled

        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (ev.pointerCount >= 2) {
                    lastFocusX = (ev.getX(0) + ev.getX(1)) / 2f
                    lastFocusY = (ev.getY(0) + ev.getY(1)) / 2f
                    playerView.hideController()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val remainingPointers = ev.pointerCount - 1
                if (remainingPointers == 1) {
                    val upIndex = ev.actionIndex
                    val remainingIndex = if (upIndex == 0) 1 else 0
                    lastFocusX = ev.getX(remainingIndex)
                    lastFocusY = ev.getY(remainingIndex)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasInteractingAtEnd = isPanning || isScaling || wasInteracting
                isPanning = false
                isScaling = false
                if (wasInteractingAtEnd) {
                    settleZoom()
                    scheduleHideZoomBadge()
                    handled = true
                }
            }
        }

        return handled
    }

    data class TranslationBounds(
        val minX: Float,
        val maxX: Float,
        val minY: Float,
        val maxY: Float
    )

    fun calculateBounds(scale: Float): TranslationBounds {
        val frame = contentFrame ?: return TranslationBounds(0f, 0f, 0f, 0f)
        val contentW = frame.width.toFloat()
        val contentH = frame.height.toFloat()
        val parentW = playerView.width.toFloat()
        val parentH = playerView.height.toFloat()

        if (contentW <= 0f || contentH <= 0f || parentW <= 0f || parentH <= 0f) {
            return TranslationBounds(0f, 0f, 0f, 0f)
        }

        val scaledW = contentW * scale
        val scaledH = contentH * scale

        val (minX, maxX) = if (scaledW > parentW) {
            val maxDiff = (scaledW - parentW) / 2f
            -maxDiff to maxDiff
        } else {
            0f to 0f
        }

        val (minY, maxY) = if (scaledH > parentH) {
            val maxDiff = (scaledH - parentH) / 2f
            -maxDiff to maxDiff
        } else {
            0f to 0f
        }

        return TranslationBounds(minX, maxX, minY, maxY)
    }

    fun calculateFillScale(): Float {
        val frame = contentFrame ?: return 1.0f
        val parentW = playerView.width.toFloat()
        val parentH = playerView.height.toFloat()
        val contentW = frame.width.toFloat()
        val contentH = frame.height.toFloat()

        if (contentW <= 0f || contentH <= 0f || parentW <= 0f || parentH <= 0f) {
            return 1.0f
        }

        val scaleX = parentW / contentW
        val scaleY = parentH / contentH
        return max(scaleX, scaleY)
    }

    fun zoomTo(targetScale: Float, focusX: Float, focusY: Float, animated: Boolean = true) {
        if (contentFrame == null) {
            setup()
        }
        val frame = contentFrame ?: return
        val clampedScale = targetScale.coerceIn(minScale, maxScale)

        val centerX = frame.left + frame.width / 2f
        val centerY = frame.top + frame.height / 2f
        val vecX = focusX - centerX
        val vecY = focusY - centerY

        val targetTx = vecX * (1f - clampedScale)
        val targetTy = vecY * (1f - clampedScale)

        val bounds = calculateBounds(clampedScale)
        val clampedTx = targetTx.coerceIn(bounds.minX, bounds.maxX)
        val clampedTy = targetTy.coerceIn(bounds.minY, bounds.maxY)

        if (animated) {
            updateZoomBadge(clampedScale)
            animateTo(clampedScale, clampedTx, clampedTy)
            scheduleHideZoomBadge()
        } else {
            animator?.cancel()
            currentScale = clampedScale
            currentTranslationX = clampedTx
            currentTranslationY = clampedTy
            applyTransformation()
            updateZoomBadge(clampedScale)
            scheduleHideZoomBadge()
        }
    }

    fun resetZoom(animated: Boolean = true) {
        animator?.cancel()
        val isAlreadyAtRest = currentScale == 1.0f && currentTranslationX == 0f && currentTranslationY == 0f
        if (isAlreadyAtRest) {
            scheduleHideZoomBadge(0L)
            return
        }
        if (animated) {
            animateTo(1.0f, 0f, 0f, 250L)
        } else {
            currentScale = 1.0f
            currentTranslationX = 0f
            currentTranslationY = 0f
            applyTransformation()
            onZoomChangeListener?.onZoomReset()
        }
        scheduleHideZoomBadge(0L)
    }

    fun settleZoom() {
        val frame = contentFrame ?: return
        if (frame.width <= 0 || frame.height <= 0) return

        val targetScale: Float = if (currentScale < minScale) {
            minScale
        } else if (currentScale > maxScale) {
            maxScale
        } else {
            currentScale
        }

        val bounds = calculateBounds(targetScale)
        val targetTx = currentTranslationX.coerceIn(bounds.minX, bounds.maxX)
        val targetTy = currentTranslationY.coerceIn(bounds.minY, bounds.maxY)

        if (targetScale != currentScale || targetTx != currentTranslationX || targetTy != currentTranslationY) {
            animateTo(targetScale, targetTx, targetTy)
        }
    }

    private fun animateTo(
        targetScale: Float,
        targetTx: Float,
        targetTy: Float,
        durationMs: Long = 250L
    ) {
        animator?.cancel()
        val startScale = currentScale
        val startTx = currentTranslationX
        val startTy = currentTranslationY

        var isCancelled = false
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    isCancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!isCancelled) {
                        if (currentScale <= (1.0f + SCALE_EPSILON)) {
                            currentScale = 1.0f
                            currentTranslationX = 0f
                            currentTranslationY = 0f
                            applyTransformation()
                            onZoomChangeListener?.onZoomReset()
                        }
                    }
                }
            })
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                currentScale = startScale + (targetScale - startScale) * fraction
                currentTranslationX = startTx + (targetTx - startTx) * fraction
                currentTranslationY = startTy + (targetTy - startTy) * fraction
                applyTransformation()
            }
            start()
        }
    }

    internal fun applyTransformation() {
        val frame = contentFrame ?: return
        if (frame.width > 0 && frame.height > 0) {
            frame.pivotX = frame.width / 2f
            frame.pivotY = frame.height / 2f
        }
        frame.scaleX = currentScale
        frame.scaleY = currentScale
        frame.translationX = currentTranslationX
        frame.translationY = currentTranslationY

        onZoomChangeListener?.onZoomChanged(currentScale, currentTranslationX, currentTranslationY)
    }

    private fun ensureZoomBadge() {
        if (zoomBadge != null || !showZoomIndicator) return
        val context = playerView.context
        val metrics = context.resources.displayMetrics
        val badge = TextView(context).apply {
            setBackgroundResource(R.drawable.tpstreams_zoom_badge_bg)
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            visibility = View.GONE
            alpha = 0f
            isClickable = false
            isFocusable = false

            val density = metrics.density
            val topMargin = (16 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                this.topMargin = topMargin
            }
        }
        playerView.addView(badge)
        zoomBadge = badge
    }

    fun getZoomBadgeText(scale: Float): String {
        val fillScale = calculateFillScale()
        val isNearOriginal = scale <= (1.0f + SCALE_EPSILON)
        val isNearFill = fillScale > (1.0f + SCALE_EPSILON) && abs(scale - fillScale) < (0.05f * fillScale)

        return when {
            isNearOriginal -> playerView.context.getString(R.string.zoom_original)
            isNearFill -> playerView.context.getString(R.string.zoom_to_fill)
            else -> String.format(Locale.US, "%.1fx", scale)
        }
    }

    private fun triggerHapticFeedbackIfNeeded(scale: Float) {
        val fillScale = calculateFillScale()
        val isNearOriginal = scale <= (1.0f + SCALE_EPSILON)
        val isNearFill = fillScale > (1.0f + SCALE_EPSILON) && abs(scale - fillScale) < (0.05f * fillScale)

        val currentState = when {
            isNearOriginal -> 1
            isNearFill -> 2
            else -> 0
        }

        if (currentState != 0 && currentState != lastSnappedState) {
            playerView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        lastSnappedState = currentState
    }

    private fun updateZoomBadge(scale: Float) {
        if (!showZoomIndicator) return
        ensureZoomBadge()
        val badge = zoomBadge ?: return
        badge.removeCallbacks(hideBadgeRunnable)
        badge.animate()?.cancel()
        badge.text = getZoomBadgeText(scale)
        badge.alpha = 1f
        badge.visibility = View.VISIBLE
        triggerHapticFeedbackIfNeeded(scale)
    }

    private val hideBadgeRunnable = Runnable {
        zoomBadge?.animate()
            ?.alpha(0f)
            ?.setDuration(250L)
            ?.withEndAction { zoomBadge?.visibility = View.GONE }
            ?.start()
    }

    private fun scheduleHideZoomBadge(delayMs: Long = 800L) {
        val badge = zoomBadge ?: return
        badge.removeCallbacks(hideBadgeRunnable)
        if (delayMs <= 0L) {
            badge.visibility = View.GONE
            badge.alpha = 0f
        } else {
            badge.postDelayed(hideBadgeRunnable, delayMs)
        }
    }

    fun destroy() {
        animator?.cancel()
        animator = null
        zoomBadge?.removeCallbacks(hideBadgeRunnable)
        zoomBadge?.let { playerView.removeView(it) }
        zoomBadge = null
        contentFrame = null
    }
}
