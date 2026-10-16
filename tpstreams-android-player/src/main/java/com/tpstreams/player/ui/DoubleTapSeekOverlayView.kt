package com.tpstreams.player.ui

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
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

/**
 * Custom view that clips a curved Bezier arc shape on the left or right side of the screen
 * and renders an expanding circular tap ripple from the exact double-tap touch coordinates.
 */
@UnstableApi
internal class CircleClipTapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#20EEEEEE")
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#18FFFFFF")
    }

    private var widthPx = 0
    private var heightPx = 0

    private val shapePath = Path()

    @VisibleForTesting
    internal var isLeft = true
        private set

    private var cX = 0f
    private var cY = 0f

    private var currentRadius = 0f
    private var minRadius: Float = 0f
    private var maxRadius: Float = 0f

    private var valueAnimator: ValueAnimator? = null
    private var forceReset = false

    var arcSize: Float = 40f * resources.displayMetrics.density
        set(value) {
            field = value
            updatePathShape()
        }

    var animationDuration: Long = 650L

    var performAtEnd: () -> Unit = {}

    init {
        val dm = resources.displayMetrics
        widthPx = dm.widthPixels
        heightPx = dm.heightPixels

        minRadius = 30f * dm.density
        maxRadius = 400f * dm.density

        updatePathShape()
    }

    fun updatePosition(x: Float, y: Float) {
        cX = x
        cY = y

        val newIsLeft = x <= widthPx / 2f
        if (isLeft != newIsLeft) {
            isLeft = newIsLeft
            updatePathShape()
        }
    }

    private fun invalidateWithCurrentRadius(factor: Float) {
        currentRadius = minRadius + ((maxRadius - minRadius) * factor)
        invalidate()
    }

    private fun updatePathShape() {
        if (widthPx <= 0 || heightPx <= 0) return

        val halfWidth = widthPx * 0.5f
        shapePath.reset()

        val w = if (isLeft) 0f else widthPx.toFloat()
        val f = if (isLeft) 1 else -1

        shapePath.moveTo(w, 0f)
        shapePath.lineTo(f * (halfWidth - arcSize) + w, 0f)
        shapePath.quadTo(
            f * (halfWidth + arcSize) + w,
            heightPx.toFloat() / 2f,
            f * (halfWidth - arcSize) + w,
            heightPx.toFloat()
        )
        shapePath.lineTo(w, heightPx.toFloat())
        shapePath.close()

        invalidate()
    }

    private fun getCircleAnimator(): ValueAnimator {
        if (valueAnimator == null) {
            valueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = animationDuration
                addUpdateListener {
                    invalidateWithCurrentRadius(it.animatedValue as Float)
                }
                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {
                        visibility = VISIBLE
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (!forceReset) performAtEnd()
                    }

                    override fun onAnimationRepeat(animation: Animator) {}
                    override fun onAnimationCancel(animation: Animator) {}
                })
            }
        }
        return valueAnimator!!
    }

    fun startAnimation() {
        forceReset = false
        getCircleAnimator().cancel()
        getCircleAnimator().start()
    }

    fun resetAnimation(body: () -> Unit) {
        forceReset = true
        getCircleAnimator().cancel()
        body()
        forceReset = false
        getCircleAnimator().start()
    }

    fun endAnimation() {
        getCircleAnimator().cancel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        widthPx = w
        heightPx = h
        updatePathShape()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.clipPath(shapePath)
        canvas.drawPath(shapePath, backgroundPaint)
        canvas.drawCircle(cX, cY, currentRadius, circlePaint)
    }
}

/**
 * Layout group that displays and animates the 3 play triangles and seconds text indicator
 * during double-tap seeking.
 */
@UnstableApi
internal class DoubleTapSeekIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val trianglesContainer: LinearLayout
    private val secondsTextView: TextView
    private val icon1: ImageView
    private val icon2: ImageView
    private val icon3: ImageView

    init {
        LayoutInflater.from(context).inflate(R.layout.double_tap_seconds_view, this, true)

        trianglesContainer = findViewById(R.id.triangle_container)
        secondsTextView = findViewById(R.id.tv_seconds)
        icon1 = findViewById(R.id.icon_1)
        icon2 = findViewById(R.id.icon_2)
        icon3 = findViewById(R.id.icon_3)

        resetIcons()
    }

    var cycleDuration: Long = 750L
        set(value) {
            field = value
            val step = value / 5
            firstAnimator.duration = step
            secondAnimator.duration = step
            thirdAnimator.duration = step
            fourthAnimator.duration = step
            fifthAnimator.duration = step
        }

    var seconds: Int = 0
        set(value) {
            field = value
            secondsTextView.text = "$value seconds"
        }

    var isForward: Boolean = true
        set(value) {
            field = value
            trianglesContainer.rotation = if (value) 0f else 180f
        }

    val textView: TextView get() = secondsTextView

    fun start() {
        stop()
        firstAnimator.start()
    }

    fun stop() {
        firstAnimator.cancel()
        secondAnimator.cancel()
        thirdAnimator.cancel()
        fourthAnimator.cancel()
        fifthAnimator.cancel()
        resetIcons()
    }

    private fun resetIcons() {
        icon1.alpha = 0f
        icon2.alpha = 0f
        icon3.alpha = 0f
    }

    private val firstAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = cycleDuration / 5
            doOnStart { resetIcons() }
            addUpdateListener { icon1.alpha = it.animatedValue as Float }
            doOnEnd { secondAnimator.start() }
        }
    }

    private val secondAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = cycleDuration / 5
            doOnStart {
                icon1.alpha = 1f
                icon2.alpha = 0f
                icon3.alpha = 0f
            }
            addUpdateListener { icon2.alpha = it.animatedValue as Float }
            doOnEnd { thirdAnimator.start() }
        }
    }

    private val thirdAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = cycleDuration / 5
            doOnStart {
                icon1.alpha = 1f
                icon2.alpha = 1f
                icon3.alpha = 0f
            }
            addUpdateListener {
                val fraction = it.animatedValue as Float
                icon3.alpha = fraction
                icon1.alpha = 1f - fraction
            }
            doOnEnd { fourthAnimator.start() }
        }
    }

    private val fourthAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = cycleDuration / 5
            doOnStart {
                icon1.alpha = 0f
                icon2.alpha = 1f
                icon3.alpha = 1f
            }
            addUpdateListener {
                icon2.alpha = 1f - (it.animatedValue as Float)
            }
            doOnEnd { fifthAnimator.start() }
        }
    }

    private val fifthAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = cycleDuration / 5
            doOnStart {
                icon1.alpha = 0f
                icon2.alpha = 0f
                icon3.alpha = 1f
            }
            addUpdateListener {
                icon3.alpha = 1f - (it.animatedValue as Float)
            }
            doOnEnd { firstAnimator.start() }
        }
    }
}
