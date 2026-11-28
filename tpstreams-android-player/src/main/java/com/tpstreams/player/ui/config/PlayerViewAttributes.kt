package com.tpstreams.player.ui.config

import android.content.Context
import android.util.AttributeSet
import com.tpstreams.player.R

/** Parsed XML configuration for the view's gesture controllers. */
internal data class PlayerViewAttributes(
    val pinchToZoomEnabled: Boolean,
    val maxZoomScale: Float,
    val showZoomIndicator: Boolean,
    val zoomOnlyInFullscreen: Boolean,
    val doubleTapToSeekEnabled: Boolean,
    val doubleTapSeekIncrementMs: Long?
) {
    companion object {
        fun from(context: Context, attrs: AttributeSet?, defStyleAttr: Int): PlayerViewAttributes? {
            if (attrs == null) return null
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.TPStreamsPlayerView, defStyleAttr, 0)
            return try {
                val seekIncrement = typedArray.getInt(
                    R.styleable.TPStreamsPlayerView_double_tap_seek_increment_ms,
                    -1
                )
                PlayerViewAttributes(
                    pinchToZoomEnabled = typedArray.getBoolean(
                        R.styleable.TPStreamsPlayerView_enable_pinch_to_zoom,
                        true
                    ),
                    maxZoomScale = typedArray.getFloat(
                        R.styleable.TPStreamsPlayerView_max_zoom_scale,
                        8.0f
                    ).coerceAtLeast(1.0f),
                    showZoomIndicator = typedArray.getBoolean(
                        R.styleable.TPStreamsPlayerView_show_zoom_indicator,
                        true
                    ),
                    zoomOnlyInFullscreen = typedArray.getBoolean(
                        R.styleable.TPStreamsPlayerView_zoom_only_in_fullscreen,
                        false
                    ),
                    doubleTapToSeekEnabled = typedArray.getBoolean(
                        R.styleable.TPStreamsPlayerView_enable_double_tap_to_seek,
                        true
                    ),
                    doubleTapSeekIncrementMs = seekIncrement.takeIf { it > 0 }?.toLong()
                )
            } finally {
                typedArray.recycle()
            }
        }
    }
}
