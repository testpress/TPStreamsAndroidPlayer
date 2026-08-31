package com.tpstreams.player

import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout

@UnstableApi
internal abstract class BaseWatermarkController<T : BaseWatermarkConfig>(protected val parent: TPStreamsPlayerView) {

    protected var container: FrameLayout? = null
    protected var config: T? = null
    protected var contentFrame: AspectRatioFrameLayout? = null
    protected var currentIsPlaying = false

    protected abstract fun createViews(config: T)
    protected abstract fun getTargetX(): Int
    protected abstract fun getTargetY(): Int
    protected abstract fun getTargetOpacity(): Float
    protected open fun getAnimationCurrentPosition(): Pair<Float, Float>? = null

    open fun apply(config: T?) {
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
    }

    open fun remove() {
        container?.let { contentFrame?.removeView(it) }
        container = null
        config = null
        contentFrame = null
    }

    open fun destroy() {
        remove()
    }

    open fun onParentLayout() {
        reposition()
    }

    open fun onPlayerStateChanged(isPlaying: Boolean, playbackState: Int = Player.STATE_IDLE) {
        currentIsPlaying = isPlaying
    }

    open fun onViewDetached() {}

    open fun onViewAttached() {
        reposition()
    }

    open fun onControlsVisibilityChanged(visible: Boolean) {}

    protected fun addToParent() {
        val c = container ?: return
        val frame = contentFrame
        if (frame == null) {
            Log.w(TAG, "exo_content_frame not found — watermark will not be displayed. " +
                "Ensure the player layout contains an AspectRatioFrameLayout with id exo_content_frame.")
            return
        }
        frame.addView(c, frame.childCount)
    }

    fun reposition() {
        val c = container ?: return
        val frame = contentFrame ?: return
        if (frame.width == 0 || frame.height == 0) return
        if (c.width == 0 || c.height == 0) return

        val animXy = getAnimationCurrentPosition()
        val (xFrac, yFrac) = animXy ?: (getTargetX() / 100f to getTargetY() / 100f)

        placeAt(xFrac, yFrac)
    }

    protected fun placeAt(xFrac: Float, yFrac: Float) {
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

    companion object {
        private const val TAG = "BaseWatermarkController"
    }
}
