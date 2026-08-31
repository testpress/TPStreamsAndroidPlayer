package com.tpstreams.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlin.random.Random

@UnstableApi
internal class TextWatermarkController(parent: TPStreamsPlayerView) : BaseWatermarkController<WatermarkConfig>(parent) {

    private var animator: ValueAnimator? = null
    private var randomXFrac: Float = 0f
    private var randomYFrac: Float = 0f
    private var applyCounter: Int = 0

    override fun getTargetX(): Int = config?.x ?: 0
    override fun getTargetY(): Int = config?.y ?: 0
    override fun getTargetOpacity(): Float = config?.opacity ?: 0.3f

    override fun apply(config: WatermarkConfig?) {
        super.apply(config)
        if (config == null) return

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

    override fun remove() {
        animator?.cancel()
        animator = null
        super.remove()
    }

    override fun onPlayerStateChanged(isPlaying: Boolean, playbackState: Int) {
        super.onPlayerStateChanged(isPlaying, playbackState)
        val hasEnded = playbackState == Player.STATE_ENDED
        updateVisibilityForState(isPlaying, hasEnded)
    }

    override fun onViewDetached() {
        super.onViewDetached()
        animator?.pause()
    }

    override fun onViewAttached() {
        updateVisibilityForState(currentIsPlaying)
        super.onViewAttached()
    }

    override fun createViews(config: WatermarkConfig) {
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

    override fun getAnimationCurrentPosition(): Pair<Float, Float>? {
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
}
