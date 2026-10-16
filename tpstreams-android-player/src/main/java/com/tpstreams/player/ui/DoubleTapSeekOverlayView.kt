package com.tpstreams.player.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.R

/**
 * YouTube-style visual overlay for double-tap seek feedback.
 *
 * Utilizes [CircleClipTapView] for the curved Bezier arc clipping and dynamic expanding
 * tap ripple from the exact touch coordinate, and [DoubleTapSeekIndicatorView] for the animated
 * 3-triangle wave and accumulated seconds text.
 */
@UnstableApi
internal class DoubleTapSeekOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val circleClipTapView: CircleClipTapView
    private val seekIndicatorView: DoubleTapSeekIndicatorView

    var onAnimationEndListener: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.double_tap_seek_overlay, this, true)

        circleClipTapView = findViewById(R.id.circle_clip_tap_view)
        seekIndicatorView = findViewById(R.id.seek_indicator_view)

        circleClipTapView.performAtEnd = {
            seekIndicatorView.stop()
            seekIndicatorView.visibility = View.INVISIBLE
            visibility = View.INVISIBLE
            onAnimationEndListener?.invoke()
        }

        isClickable = false
        isFocusable = false
        visibility = View.INVISIBLE
    }

    /**
     * Shows or updates the seek overlay with the accumulated seek seconds and touch coordinates.
     */
    fun showSeek(isForward: Boolean, accumulatedSeconds: Int, touchX: Float, touchY: Float) {
        visibility = View.VISIBLE
        seekIndicatorView.visibility = View.VISIBLE

        seekIndicatorView.isForward = isForward
        seekIndicatorView.seconds = accumulatedSeconds

        updateSeekIndicatorPosition(isForward)

        circleClipTapView.resetAnimation {
            circleClipTapView.updatePosition(touchX, touchY)
        }

        seekIndicatorView.start()
    }

    private fun updateSeekIndicatorPosition(isForward: Boolean) {
        val halfWidth = if (width > 0) (width * 0.5f).toInt() else LayoutParams.WRAP_CONTENT

        val lp = (seekIndicatorView.layoutParams as? LayoutParams) ?: LayoutParams(
            halfWidth,
            LayoutParams.WRAP_CONTENT
        )
        lp.width = halfWidth
        lp.gravity = if (isForward) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL
        seekIndicatorView.layoutParams = lp
    }

    /**
     * Immediately dismisses and cancels the seek overlay animation.
     */
    fun hideSeek(animated: Boolean = true) {
        circleClipTapView.endAnimation()
        seekIndicatorView.stop()
        seekIndicatorView.visibility = View.INVISIBLE
        visibility = View.INVISIBLE
    }

    @VisibleForTesting
    internal fun isOverlayVisible(): Boolean = visibility == View.VISIBLE

    @VisibleForTesting
    internal fun getSeconds(): Int = seekIndicatorView.seconds

    @VisibleForTesting
    internal fun isForward(): Boolean = seekIndicatorView.isForward
}
