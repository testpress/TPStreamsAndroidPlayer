package com.tpstreams.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import kotlin.random.Random

@androidx.media3.common.util.UnstableApi
internal class WatermarkController(private val parent: TPStreamsPlayerView) {

    private var container: FrameLayout? = null
    private var config: WatermarkConfig? = null
    private var contentFrame: AspectRatioFrameLayout? = null

    private var currentIsPlaying = false

    private var animator: ValueAnimator? = null
    private var randomXFrac: Float = 0f
    private var randomYFrac: Float = 0f
    private var applyCounter: Int = 0

    // ── Public API ───────────────────────────────────────────────────────

    fun apply(config: WatermarkConfig?) {
        remove()
        if (config == null) return

        this.config = config
        contentFrame = parent.findViewById(androidx.media3.ui.R.id.exo_content_frame)

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

            val anim = config.animation
            when (anim?.type) {
                WatermarkAnimationType.PING_PONG -> startPingPongAnimation(anim)
                WatermarkAnimationType.RANDOM -> startRandomAnimation(anim)
                null -> {}
            }

            reposition()
            updateVisibilityForState(currentIsPlaying)
        }
    }

    fun remove() {
        animator?.cancel()
        animator = null
        container?.let { contentFrame?.removeView(it) }
        container = null
        config = null
        contentFrame = null
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
        animator?.pause()
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
        val frame = contentFrame
        if (frame == null) {
            Log.w(TAG, "exo_content_frame not found — watermark will not be displayed. " +
                "Ensure the player layout contains an AspectRatioFrameLayout with id exo_content_frame.")
            return
        }
        frame.addView(c, frame.childCount)
    }

    // ── Positioning ──────────────────────────────────────────────────────

    private fun reposition() {
        val c = container ?: return
        val cfg = config ?: return
        val frame = contentFrame ?: return
        if (frame.width == 0 || frame.height == 0) return
        if (c.width == 0 || c.height == 0) return

        val animXy = getAnimationCurrentPosition()
        val (xFrac, yFrac) = animXy ?: (cfg.x / 100f to cfg.y / 100f)

        placeAt(xFrac, yFrac)
    }

    private fun getAnimationCurrentPosition(): Pair<Float, Float>? {
        val anim = config?.animation ?: return null
        val activeAnimator = animator ?: return null
        if (!activeAnimator.isRunning && !activeAnimator.isPaused) return null
        val cfg = config ?: return null

        return when (anim.type) {
            WatermarkAnimationType.PING_PONG -> {
                val fraction = activeAnimator.animatedValue as? Float ?: return null
                fraction to (cfg.y / 100f)
            }
            WatermarkAnimationType.RANDOM -> {
                randomXFrac to randomYFrac
            }
        }
    }

    private fun placeAt(xFrac: Float, yFrac: Float) {
        val c = container ?: return
        val frame = contentFrame ?: return
        val parentWidth = frame.width
        val parentHeight = frame.height
        if (parentWidth == 0 || parentHeight == 0) return

        val viewWidth = c.width
        val viewHeight = c.height
        if (viewWidth == 0 || viewHeight == 0) return

        val maxX = (parentWidth - viewWidth).coerceAtLeast(0)
        val maxY = (parentHeight - viewHeight).coerceAtLeast(0)

        val x = xFrac * maxX
        val y = yFrac * maxY

        c.pivotX = viewWidth * xFrac
        c.pivotY = viewHeight * yFrac
        c.translationX = x
        c.translationY = y
    }

    // Watermark is always visible. The animation pauses when not playing.

    private fun updateVisibilityForState(isPlaying: Boolean, hasEnded: Boolean = false) {
        container?.visibility = View.VISIBLE

        animator?.let { anim ->
            val shouldAnimate = isPlaying && !hasEnded
            if (shouldAnimate && anim.isPaused) {
                anim.resume()
            } else if (!shouldAnimate && anim.isRunning) {
                anim.pause()
            }
        }
    }

    // ── Animation ────────────────────────────────────────────────────────

    private fun startPingPongAnimation(animation: WatermarkAnimation) {
        animator?.cancel()

        val durationMs = animation.duration.coerceAtLeast(WatermarkAnimation.MIN_DURATION_MS)

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { reposition() }
            start()
        }
    }

    private fun startRandomAnimation(animation: WatermarkAnimation) {
        animator?.cancel()

        val durationMs = animation.duration.coerceAtLeast(WatermarkAnimation.MIN_DURATION_MS)
        randomXFrac = Random.nextFloat()
        randomYFrac = Random.nextFloat()

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            repeatMode = ValueAnimator.RESTART
            repeatCount = ValueAnimator.INFINITE
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationRepeat(animation: Animator) {
                    randomXFrac = Random.nextFloat()
                    randomYFrac = Random.nextFloat()
                    reposition()
                }
            })
            start()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "WatermarkController"
    }
}
