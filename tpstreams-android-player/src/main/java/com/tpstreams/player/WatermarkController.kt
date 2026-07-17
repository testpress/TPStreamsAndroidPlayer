package com.tpstreams.player

import android.animation.ValueAnimator
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@androidx.media3.common.util.UnstableApi
internal class WatermarkController(private val parent: TPStreamsPlayerView) {

    private var container: FrameLayout? = null
    private var watermarkView: View? = null
    private var config: WatermarkConfig? = null

    private var dynamicXFraction: Float? = null
    private var dynamicYFraction: Float? = null

    private var manualVisibility: Boolean? = null
    private var currentIsPlaying = false
    private var currentIsAdPlaying = false
    private var hasPlaybackStarted = false

    private var textUpdateJob: Job? = null
    private var pingPongAnimator: ValueAnimator? = null
    private var scope: CoroutineScope? = null
    private var applyCounter: Int = 0

    private fun ensureScope(): CoroutineScope {
        scope?.let { if (it.isActive) return it }
        scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        return scope!!
    }

    // ── Public API ───────────────────────────────────────────────────────

    fun apply(config: WatermarkConfig?) {
        remove()
        if (config == null) return

        this.config = config

        val player = parent.getPlayer()
        if (player != null) {
            currentIsPlaying = player.isPlaying
            currentIsAdPlaying = player.isPlayingAd
            hasPlaybackStarted = player.isPlaying
        }

        createViews(config)
        addToParent()

        container?.visibility = View.INVISIBLE

        val applyGeneration = ++applyCounter
        container?.post {
            if (!parent.isAttachedToWindow) return@post
            if (applyGeneration != applyCounter) return@post
            reposition()

            if (hasPlaybackStarted) {
                config.style.animation?.let { startPingPongAnimation(it) }
                if (config.content is WatermarkContent.Text) {
                    startTextUpdater(config.content)
                }
            }

            updateVisibilityForState(currentIsPlaying, currentIsAdPlaying)
        }
    }

    fun show() {
        manualVisibility = true
        container?.visibility = View.VISIBLE
        pingPongAnimator?.resume()
    }

    fun hide() {
        manualVisibility = false
        container?.visibility = View.GONE
        pingPongAnimator?.pause()
    }

    fun remove() {
        textUpdateJob?.cancel()
        textUpdateJob = null
        pingPongAnimator?.cancel()
        pingPongAnimator = null
        container?.let { parent.removeView(it) }
        watermarkView = null
        container = null
        config = null
        dynamicXFraction = null
        dynamicYFraction = null
        manualVisibility = null
        hasPlaybackStarted = false
    }

    fun updatePosition(xFraction: Float, yFraction: Float) {
        dynamicXFraction = xFraction.coerceIn(0f, 1f)
        dynamicYFraction = yFraction.coerceIn(0f, 1f)
        pingPongAnimator?.cancel()
        pingPongAnimator = null
        reposition()
    }

    fun onParentLayout() {
        reposition()
    }

    fun onPlayerStateChanged(isPlaying: Boolean, isAdPlaying: Boolean) {
        currentIsPlaying = isPlaying
        currentIsAdPlaying = isAdPlaying

        if (isPlaying && !hasPlaybackStarted) {
            hasPlaybackStarted = true
            config?.let { cfg ->
                cfg.style.animation?.let { startPingPongAnimation(it) }
                if (cfg.content is WatermarkContent.Text) {
                    startTextUpdater(cfg.content)
                }
            }
        }

        updateVisibilityForState(isPlaying, isAdPlaying)
    }

    fun destroy() {
        remove()
        scope?.cancel()
        scope = null
    }

    fun onViewDetached() {
        pingPongAnimator?.pause()
        textUpdateJob?.cancel()
        textUpdateJob = null
        scope?.cancel()
        scope = null
    }

    fun onViewAttached() {
        if (hasPlaybackStarted) {
            pingPongAnimator?.resume()
            val content = config?.content
            if (content is WatermarkContent.Text) {
                startTextUpdater(content)
            }
        }
        updateVisibilityForState(currentIsPlaying, currentIsAdPlaying)
        reposition()
    }

    // ── View Creation ────────────────────────────────────────────────────

    private fun createViews(config: WatermarkConfig) {
        val c = FrameLayout(parent.context).apply {
            isClickable = false
            isFocusable = false
        }
        container = c

        watermarkView = when (val content = config.content) {
            is WatermarkContent.Text -> createTextView(content)
        }

        c.addView(watermarkView)
        applySize(config.style.size)
        c.alpha = config.style.opacity

        if (config.style.elevation > 0f) {
            c.elevation = dpToPx(config.style.elevation)
        }
    }

    private fun createTextView(content: WatermarkContent.Text): TextView {
        return TextView(parent.context).apply {
            text = content.textProvider()
            setTextColor(content.color)
            textSize = content.textSizeSp
            content.typeface?.let { typeface = it }
            if (content.shadowRadius > 0f) {
                setShadowLayer(content.shadowRadius, content.shadowDx, content.shadowDy, content.shadowColor)
            }
            isClickable = false
            isFocusable = false
        }
    }

    private fun addToParent() {
        val c = container ?: return
        val insertIndex = parent.getWatermarkInsertIndex()
        parent.addView(c, insertIndex)
    }

    // ── Sizing ───────────────────────────────────────────────────────────

    private fun applySize(size: WatermarkSize) {
        val c = container ?: return
        when (size) {
            is WatermarkSize.WrapContent -> {
                c.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }
            is WatermarkSize.Fixed -> {
                c.layoutParams = FrameLayout.LayoutParams(
                    dpToPx(size.widthDp).toInt(),
                    dpToPx(size.heightDp).toInt()
                )
            }
            is WatermarkSize.Scale -> {
                c.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                val scale = size.percent.coerceIn(0f, 1f)
                c.scaleX = scale
                c.scaleY = scale
            }
        }
    }

    // ── Positioning ──────────────────────────────────────────────────────

    private fun reposition() {
        val c = container ?: return
        val cfg = config ?: return
        if (parent.width == 0 || parent.height == 0) return
        if (c.width == 0 || c.height == 0) return

        val animXy = getAnimationCurrentPosition()
        val (xFrac, yFrac) = animXy ?: resolvePosition(cfg.style.position)

        placeAt(xFrac, yFrac)
    }

    private fun getAnimationCurrentPosition(): Pair<Float, Float>? {
        val animator = pingPongAnimator ?: return null
        if (!animator.isRunning && !animator.isPaused) return null
        val fraction = animator.animatedValue as? Float ?: return null
        val animation = config?.style?.animation as? WatermarkAnimation.PingPong ?: return null
        val (fromX, fromY) = animation.from
        val (toX, toY) = animation.to
        return (fromX + (toX - fromX) * fraction) to (fromY + (toY - fromY) * fraction)
    }

    private fun resolvePosition(position: WatermarkPosition): Pair<Float, Float> {
        val dx = dynamicXFraction
        val dy = dynamicYFraction
        if (dx != null && dy != null) return dx to dy

        return when (position) {
            is WatermarkPosition.Static -> position.gravity.toFraction()
            is WatermarkPosition.Dynamic -> position.xFraction to position.yFraction
        }
    }

    private fun placeAt(xFrac: Float, yFrac: Float) {
        val c = container ?: return
        val cfg = config ?: return
        val parentWidth = parent.width
        val parentHeight = parent.height
        if (parentWidth == 0 || parentHeight == 0) return

        val viewWidth = c.width
        val viewHeight = c.height
        if (viewWidth == 0 || viewHeight == 0) return

        val density = parent.context.resources.displayMetrics.density
        val margins = cfg.style.margins
        val ml = margins.left * density
        val mt = margins.top * density
        val mr = margins.right * density
        val mb = margins.bottom * density

        val x = (parentWidth * xFrac - viewWidth / 2f)
            .coerceIn(ml, (parentWidth - viewWidth - mr).coerceAtLeast(ml))
        val y = (parentHeight * yFrac - viewHeight / 2f)
            .coerceIn(mt, (parentHeight - viewHeight - mb).coerceAtLeast(mt))

        c.pivotX = viewWidth * xFrac
        c.pivotY = viewHeight * yFrac
        c.translationX = x
        c.translationY = y
    }

    // ── Visibility ───────────────────────────────────────────────────────

    private fun updateVisibilityForState(isPlaying: Boolean, isAdPlaying: Boolean) {
        if (manualVisibility != null) return

        val cfg = config ?: return

        if (!hasPlaybackStarted) {
            container?.visibility = View.GONE
            return
        }

        val visible = when {
            isAdPlaying && !cfg.style.visibleDuringAds -> false
            !isPlaying && !isAdPlaying && !cfg.style.visibleWhenPaused -> false
            else -> true
        }
        container?.visibility = if (visible) View.VISIBLE else View.GONE

        pingPongAnimator?.let { animator ->
            if (visible && animator.isPaused) {
                animator.resume()
            } else if (!visible && animator.isRunning) {
                animator.pause()
            }
        }
    }

    // ── Dynamic Text ─────────────────────────────────────────────────────

    private fun startTextUpdater(content: WatermarkContent.Text) {
        textUpdateJob?.cancel()
        textUpdateJob = ensureScope().launch {
            while (isActive) {
                delay(TEXT_UPDATE_INTERVAL_MS)
                val tv = watermarkView as? TextView ?: break
                val newText = try {
                    content.textProvider()
                } catch (e: Exception) {
                    tv.text.toString()
                }
                if (tv.text != newText) {
                    tv.text = newText
                }
            }
        }
    }

    // ── Animation ────────────────────────────────────────────────────────

    private fun startPingPongAnimation(animation: WatermarkAnimation) {
        if (animation !is WatermarkAnimation.PingPong) return

        pingPongAnimator?.cancel()
        val (fromX, fromY) = animation.from
        val (toX, toY) = animation.to

        pingPongAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animation.durationMs
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                applyAnimatedPosition(
                    fromX + (toX - fromX) * fraction,
                    fromY + (toY - fromY) * fraction
                )
            }
            start()
        }
    }

    private fun applyAnimatedPosition(xFrac: Float, yFrac: Float) {
        placeAt(xFrac, yFrac)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun dpToPx(dp: Float): Float =
        dp * parent.context.resources.displayMetrics.density

    companion object {
        private const val TEXT_UPDATE_INTERVAL_MS = 1000L
    }
}
