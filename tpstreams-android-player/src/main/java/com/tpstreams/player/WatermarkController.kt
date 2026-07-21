package com.tpstreams.player

import android.animation.ValueAnimator
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.Player

@androidx.media3.common.util.UnstableApi
internal class WatermarkController(private val parent: TPStreamsPlayerView) {

    private var container: FrameLayout? = null
    private var config: WatermarkConfig? = null

    private var currentIsPlaying = false

    private var pingPongAnimator: ValueAnimator? = null
    private var applyCounter: Int = 0

    // ── Public API ───────────────────────────────────────────────────────

    fun apply(config: WatermarkConfig?) {
        remove()
        if (config == null) return

        this.config = config

        val player = parent.getPlayer()
        if (player != null) {
            currentIsPlaying = player.isPlaying
        }

        createViews(config)
        addToParent()

        container?.visibility = View.INVISIBLE

        val applyGeneration = ++applyCounter
        container?.post {
            if (!parent.isAttachedToWindow) return@post
            if (applyGeneration != applyCounter) return@post
            reposition()

            val anim = config.animation
            if (anim?.type == WatermarkAnimationType.PING_PONG) {
                startPingPongAnimation(anim)
            }

            updateVisibilityForState(currentIsPlaying)
        }
    }

    fun remove() {
        pingPongAnimator?.cancel()
        pingPongAnimator = null
        container?.let { parent.removeView(it) }
        container = null
        config = null
    }

    fun onParentLayout() {
        reposition()
    }

    fun onPlayerStateChanged(isPlaying: Boolean, playbackState: Int = Player.STATE_IDLE) {
        currentIsPlaying = isPlaying
        val hasEnded = playbackState == Player.STATE_ENDED
        updateVisibilityForState(isPlaying, hasEnded)
    }

    fun destroy() {
        remove()
    }

    fun onViewDetached() {
        pingPongAnimator?.pause()
    }

    fun onViewAttached() {
        updateVisibilityForState(currentIsPlaying)
        reposition()
    }

    // ── View Creation ────────────────────────────────────────────────────

    private fun createViews(config: WatermarkConfig) {
        val c = FrameLayout(parent.context).apply {
            isClickable = false
            isFocusable = false
        }
        container = c

        val tv = TextView(parent.context).apply {
            text = config.text
            setTextColor(config.color)
            textSize = config.textSize
            isClickable = false
            isFocusable = false
        }

        c.addView(tv)
        c.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        c.alpha = config.opacity
    }

    private fun addToParent() {
        val c = container ?: return
        val insertIndex = parent.getWatermarkInsertIndex()
        parent.addView(c, insertIndex)
    }

    // ── Positioning ──────────────────────────────────────────────────────

    private fun reposition() {
        val c = container ?: return
        val cfg = config ?: return
        if (parent.width == 0 || parent.height == 0) return
        if (c.width == 0 || c.height == 0) return

        val animXy = getAnimationCurrentPosition()
        val (xFrac, yFrac) = animXy ?: (cfg.x / 100f to cfg.y / 100f)

        placeAt(xFrac, yFrac)
    }

    private fun getAnimationCurrentPosition(): Pair<Float, Float>? {
        val animator = pingPongAnimator ?: return null
        if (!animator.isRunning && !animator.isPaused) return null
        val fraction = animator.animatedValue as? Float ?: return null
        val cfg = config ?: return null
        val yFrac = cfg.y / 100f
        return fraction to yFrac
    }

    private fun placeAt(xFrac: Float, yFrac: Float) {
        val c = container ?: return
        val parentWidth = parent.width
        val parentHeight = parent.height
        if (parentWidth == 0 || parentHeight == 0) return

        val viewWidth = c.width
        val viewHeight = c.height
        if (viewWidth == 0 || viewHeight == 0) return

        val density = parent.context.resources.displayMetrics.density
        val m = DEFAULT_MARGIN_DP * density

        val minX = m
        val maxX = (parentWidth - viewWidth - m).coerceAtLeast(m)
        val minY = m
        val maxY = (parentHeight - viewHeight - m).coerceAtLeast(m)

        val x = minX + xFrac * (maxX - minX)
        val y = minY + yFrac * (maxY - minY)

        c.pivotX = viewWidth * xFrac
        c.pivotY = viewHeight * yFrac
        c.translationX = x
        c.translationY = y
    }

    // Watermark is always visible. The animation pauses when not playing.

    private fun updateVisibilityForState(isPlaying: Boolean, hasEnded: Boolean = false) {
        container?.visibility = View.VISIBLE

        pingPongAnimator?.let { animator ->
            val shouldAnimate = isPlaying && !hasEnded
            if (shouldAnimate && animator.isPaused) {
                animator.resume()
            } else if (!shouldAnimate && animator.isRunning) {
                animator.pause()
            }
        }
    }

    // ── Animation ────────────────────────────────────────────────────────

    private fun startPingPongAnimation(animation: WatermarkAnimation) {
        pingPongAnimator?.cancel()

        val durationMs = animation.duration.coerceAtLeast(WatermarkAnimation.MIN_DURATION_MS)

        pingPongAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { reposition() }
            start()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    companion object {
        private const val DEFAULT_MARGIN_DP = 16
    }
}
