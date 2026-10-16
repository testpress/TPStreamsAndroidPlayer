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
 * tap ripple from the exact touch coordinate, and [SecondsView] for the animated 3-triangle
 * wave and accumulated seconds text.
 */
@UnstableApi
internal class DoubleTapSeekOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val circleClipTapView: CircleClipTapView
    private val secondsView: SecondsView

    var onAnimationEndListener: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.double_tap_seek_overlay, this, true)

        circleClipTapView = findViewById(R.id.circle_clip_tap_view)
        secondsView = findViewById(R.id.seconds_view)

        circleClipTapView.performAtEnd = {
            secondsView.stop()
            secondsView.visibility = View.INVISIBLE
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
        secondsView.visibility = View.VISIBLE

        secondsView.isForward = isForward
        secondsView.seconds = accumulatedSeconds

        updateSecondsViewPosition(isForward)

        circleClipTapView.resetAnimation {
            circleClipTapView.updatePosition(touchX, touchY)
        }

        secondsView.start()
    }

    private fun updateSecondsViewPosition(isForward: Boolean) {
        val halfWidth = if (width > 0) (width * 0.5f).toInt() else LayoutParams.WRAP_CONTENT

        val lp = (secondsView.layoutParams as? LayoutParams) ?: LayoutParams(
            halfWidth,
            LayoutParams.WRAP_CONTENT
        )
        lp.width = halfWidth
        lp.gravity = if (isForward) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL
        secondsView.layoutParams = lp
    }

    /**
     * Immediately dismisses and cancels the seek overlay animation.
     */
    fun hideSeek(animated: Boolean = true) {
        circleClipTapView.endAnimation()
        secondsView.stop()
        secondsView.visibility = View.INVISIBLE
        visibility = View.INVISIBLE
    }

    @VisibleForTesting
    internal fun isOverlayVisible(): Boolean = visibility == View.VISIBLE

    @VisibleForTesting
    internal fun getSeconds(): Int = secondsView.seconds

    @VisibleForTesting
    internal fun isForward(): Boolean = secondsView.isForward
}
