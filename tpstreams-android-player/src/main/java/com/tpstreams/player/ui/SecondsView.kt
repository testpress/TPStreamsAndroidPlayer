package com.tpstreams.player.ui

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
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
 * Layout group that displays and animates the 3 play triangles and seconds text indicator
 * during double-tap seeking, identical to YouTube's UI.
 */
@UnstableApi
internal class SecondsView @JvmOverloads constructor(
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

    /**
     * Defines the duration for a full cycle of the triangle wave animation.
     */
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

    /**
     * Formats and sets the accumulated seconds text.
     */
    var seconds: Int = 0
        set(value) {
            field = value
            secondsTextView.text = "$value seconds"
        }

    /**
     * Mirrors the triangle container for rewind (180°) vs forward (0°).
     */
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
