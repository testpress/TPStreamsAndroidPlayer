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

    private var textUpdateJob: Job? = null
    private var pingPongAnimator: ValueAnimator? = null
    private var scope: CoroutineScope? = null

    private fun ensureScope(): CoroutineScope {
        scope?.let { if (it.isActive) return it }
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        return scope!!
    }

    // ── Public API ───────────────────────────────────────────────────────

    fun apply(config: WatermarkConfig?) {
        remove()
        if (config == null) return

        this.config = config

        // Capture player state before setting up views so visibility is correct on first show
        val player = parent.getPlayer()
        if (player != null) {
            currentIsPlaying = player.isPlaying
            currentIsAdPlaying = player.isPlayingAd
        }

        createViews(config)
        addToParent()

        // Start invisible to prevent flicker; visibility resolved after positioning
        container?.visibility = View.INVISIBLE

        if (config.content is WatermarkContent.Text) {
            startTextUpdater(config.content)
        }

        // Reposition after views are added and measured
        container?.post {
            if (!parent.isAttachedToWindow) return@post
            reposition()
            config?.style?.animation?.let {
                startAnimation(it)
                // Snap to animation's from position before becoming visible
                val pp = it as? WatermarkAnimation.PingPong
                if (pp != null) {
                    applyAnimatedPosition(pp.from.first, pp.from.second)
                }
            }
            // Now safe to resolve visibility — watermark is at the correct position
            updateVisibilityForState(currentIsPlaying, currentIsAdPlaying)
        }
    }

    fun show() {
        manualVisibility = true
        container?.visibility = View.VISIBLE
    }

    fun hide() {
        manualVisibility = false
        container?.visibility = View.GONE
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
    }

    fun updatePosition(xFraction: Float, yFraction: Float) {
        dynamicXFraction = xFraction.coerceIn(0f, 1f)
        dynamicYFraction = yFraction.coerceIn(0f, 1f)
        reposition()
    }

    fun onParentLayout() {
        reposition()
    }

    fun onPlayerStateChanged(isPlaying: Boolean, isAdPlaying: Boolean) {
        currentIsPlaying = isPlaying
        currentIsAdPlaying = isAdPlaying
        updateVisibilityForState(isPlaying, isAdPlaying)
    }

    fun destroy() {
        remove()
        scope?.cancel()
        scope = null
    }

    /** Pause animations, cancel text updates, and cancel scope during detach. */
    fun onViewDetached() {
        pingPongAnimator?.pause()
        textUpdateJob?.cancel()
        textUpdateJob = null
        scope?.cancel()
        scope = null
    }

    /** Resume animations and text updates after fullscreen transition. */
    fun onViewAttached() {
        pingPongAnimator?.resume()
        val content = config?.content
        if (content is WatermarkContent.Text) {
            startTextUpdater(content)
        }
        // Re-evaluate visibility and reposition for new parent dimensions
        updateVisibilityForState(currentIsPlaying, currentIsAdPlaying)
        reposition()
    }

    // ── View Creation ────────────────────────────────────────────────────

    private fun createViews(config: WatermarkConfig) {
        container = FrameLayout(parent.context).apply {
            isClickable = false
            isFocusable = false
        }

        watermarkView = when (val content = config.content) {
            is WatermarkContent.Text -> createTextView(content)
        }

        container!!.addView(watermarkView)
        applySize(config.style.size)
        container!!.alpha = config.style.opacity

        if (config.style.elevation > 0f) {
            container!!.elevation = dpToPx(config.style.elevation)
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
        // Insert before the error overlay so watermark sits below error/loading UI.
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

        // When animation is active, use its current animated position instead of static config
        val animXy = getAnimationCurrentPosition()
        val (xFrac, yFrac) = if (animXy != null) {
            animXy
        } else {
            resolvePosition(cfg.style.position)
        }

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

    /** Places the watermark container at the given fractional position, margin-clamped. */
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

        c.translationX = x
        c.translationY = y
    }

    private fun WatermarkGravity.toFraction(): Pair<Float, Float> = when (this) {
        WatermarkGravity.TOP_LEFT      -> 0f to 0f
        WatermarkGravity.TOP_CENTER    -> 0.5f to 0f
        WatermarkGravity.TOP_RIGHT     -> 1f to 0f
        WatermarkGravity.CENTER_LEFT   -> 0f to 0.5f
        WatermarkGravity.CENTER        -> 0.5f to 0.5f
        WatermarkGravity.CENTER_RIGHT  -> 1f to 0.5f
        WatermarkGravity.BOTTOM_LEFT   -> 0f to 1f
        WatermarkGravity.BOTTOM_CENTER -> 0.5f to 1f
        WatermarkGravity.BOTTOM_RIGHT  -> 1f to 1f
    }

    // ── Visibility ───────────────────────────────────────────────────────

    private fun updateVisibilityForState(isPlaying: Boolean, isAdPlaying: Boolean) {
        if (manualVisibility != null) return

        val cfg = config ?: return
        val visible = when {
            isAdPlaying && !cfg.style.visibleDuringAds -> false
            !isPlaying && !isAdPlaying && !cfg.style.visibleWhenPaused -> false
            else -> true
        }
        container?.visibility = if (visible) View.VISIBLE else View.GONE

        if (pingPongAnimator != null) {
            if (visible && pingPongAnimator!!.isPaused) {
                pingPongAnimator!!.resume()
            } else if (!visible && pingPongAnimator!!.isRunning) {
                pingPongAnimator!!.pause()
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
                val newText = content.textProvider()
                if (tv.text != newText) {
                    tv.text = newText
                }
            }
        }
    }

    // ── Animation ────────────────────────────────────────────────────────

    private fun startAnimation(animation: WatermarkAnimation) {
        when (animation) {
            is WatermarkAnimation.PingPong -> startPingPongAnimation(animation)
        }
    }

    private fun startPingPongAnimation(animation: WatermarkAnimation.PingPong) {
        pingPongAnimator?.cancel()
        val (fromX, fromY) = animation.from
        val (toX, toY) = animation.to

        pingPongAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animation.durationMs
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val currentX = fromX + (toX - fromX) * fraction
                val currentY = fromY + (toY - fromY) * fraction
                applyAnimatedPosition(currentX, currentY)
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
