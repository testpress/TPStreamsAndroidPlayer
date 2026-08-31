package com.tpstreams.player

import android.graphics.Color

/**
 * Base interface for watermark overlay configurations.
 */
sealed interface BaseWatermarkConfig {
    val x: Int
    val y: Int
    val opacity: Float
}

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
data class TextWatermarkConfig @JvmOverloads constructor(
    val text: String,
    override val x: Int = 0,
    override val y: Int = 0,
    val color: Int = Color.WHITE,
    val textSize: Float = 14f,
    override val opacity: Float = 0.3f,
    val animation: WatermarkAnimation? = null,
) : BaseWatermarkConfig {
    init {
        require(text.isNotBlank()) { "text must not be blank" }
        require(x in 0..100) { "x must be 0-100, was $x" }
        require(y in 0..100) { "y must be 0-100, was $y" }
        require(opacity in 0f..1f) { "opacity must be 0.0-1.0, was $opacity" }
    }
}

/**
 * Typealias for backward compatibility with existing code using [WatermarkConfig].
 */
typealias WatermarkConfig = TextWatermarkConfig

/**
 * Configuration for displaying an image watermark overlay on the video player.
 *
 * @property imageUrl The HTTPS URL of the image to display (PNG recommended for transparency).
 * @property width The width in dp (default: 48).
 * @property height The height in dp (default: 48).
 * @property x The horizontal position percentage (0-100, default: 92).
 * @property y The vertical position percentage (0-100, default: 88).
 * @property opacity The opacity from 0.0 (transparent) to 1.0 (opaque) (default: 1.0f).
 */
data class ImageWatermarkConfig @JvmOverloads constructor(
    val imageUrl: String,
    val width: Int = 48,
    val height: Int = 48,
    override val x: Int = 92,
    override val y: Int = 88,
    override val opacity: Float = 1.0f,
) : BaseWatermarkConfig {
    init {
        require(imageUrl.isNotBlank()) { "imageUrl must not be blank" }
        require(width > 0) { "width must be positive, was $width" }
        require(height > 0) { "height must be positive, was $height" }
        require(x in 0..100) { "x must be 0-100, was $x" }
        require(y in 0..100) { "y must be 0-100, was $y" }
        require(opacity in 0f..1f) { "opacity must be 0.0-1.0, was $opacity" }
    }
}

/**
 * Animation configuration for a text watermark.
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
     * Moves horizontally back and forth across the screen at the fixed [TextWatermarkConfig.y] position.
     * [TextWatermarkConfig.x] is ignored as the horizontal position is driven by the animation.
     */
    PING_PONG,

    /**
     * Periodically relocates the watermark to random (X, Y) positions within the active video area.
     * Note: Both [TextWatermarkConfig.x] and [TextWatermarkConfig.y] are ignored as coordinates are randomized across the entire frame.
     */
    RANDOM,
}
