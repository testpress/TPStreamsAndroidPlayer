package com.tpstreams.player.ui

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.media3.common.util.UnstableApi

/**
 * Custom view that clips a curved Bezier arc shape on the left or right side of the screen
 * and renders an expanding circular tap ripple from the exact double-tap touch coordinates,
 * replicating YouTube's signature double-tap seek effect.
 */
@UnstableApi
internal class CircleClipTapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#20EEEEEE") // 12% translucent white
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#18FFFFFF") // 10% translucent white ripple
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
