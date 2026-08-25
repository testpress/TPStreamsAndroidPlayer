package com.tpstreams.player

import android.graphics.Color

/**
 * Configuration for displaying a text watermark overlay on the video player.
 *
 * @property text The text to display in the watermark.
 * @property x The horizontal position percentage (0-100). Ignored when [WatermarkAnimationType.RANDOM] is used.
 * @property y The vertical position percentage (0-100). Ignored when [WatermarkAnimationType.RANDOM] is used.
 * @property color The text color (default: [Color.WHITE]).
 * @property textSize The font size in sp (default: 14f).
 * @property opacity The opacity from 0.0 (transparent) to 1.0 (opaque) (default: 0.3f).
 * @property animation Optional animation to apply to the watermark.
 */
data class WatermarkConfig @JvmOverloads constructor(
    val text: String,
    val x: Int = 0,
    val y: Int = 0,
    val color: Int = Color.WHITE,
    val textSize: Float = 14f,
    val opacity: Float = 0.3f,
    val animation: WatermarkAnimation? = null,
) {
    init {
        require(x in 0..100) { "x must be 0-100, was $x" }
        require(y in 0..100) { "y must be 0-100, was $y" }
        require(opacity in 0f..1f) { "opacity must be 0.0-1.0, was $opacity" }
    }
}

/**
 * Animation configuration for a watermark.
 *
 * @property type The animation type to apply.
 * @property duration The duration of the animation cycle in milliseconds (default: 10,000ms).
 */
data class WatermarkAnimation(
    val type: WatermarkAnimationType,
    val duration: Long = 10_000L,
) {
    companion object {
        internal const val MIN_DURATION_MS = 100L
    }
}

enum class WatermarkAnimationType {
    /**
     * Moves horizontally back and forth across the screen at the fixed [WatermarkConfig.y] position.
     * [WatermarkConfig.x] is ignored as the horizontal position is driven by the animation.
     */
    PING_PONG,

    /**
     * Periodically relocates the watermark to random (X, Y) positions within the active video area.
     * Note: Both [WatermarkConfig.x] and [WatermarkConfig.y] are ignored as coordinates are randomized across the entire frame.
     */
    RANDOM,
}
