package com.tpstreams.player

import android.graphics.Color

data class WatermarkConfig(
    val text: String,
    val position: WatermarkPosition = WatermarkPosition.CENTER_LEFT,
    val color: Int = Color.WHITE,
    val textSize: Float = 14f,
    val opacity: Float = 0.3f,
    val animation: WatermarkAnimation? = null,
)

enum class WatermarkPosition {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    CENTER_LEFT,
    CENTER,
    CENTER_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT;

    internal fun toFraction(): Pair<Float, Float> = when (this) {
        TOP_LEFT      -> 0f to 0f
        TOP_CENTER    -> 0.5f to 0f
        TOP_RIGHT     -> 1f to 0f
        CENTER_LEFT   -> 0f to 0.5f
        CENTER        -> 0.5f to 0.5f
        CENTER_RIGHT  -> 1f to 0.5f
        BOTTOM_LEFT   -> 0f to 1f
        BOTTOM_CENTER -> 0.5f to 1f
        BOTTOM_RIGHT  -> 1f to 1f
    }
}

data class WatermarkAnimation(
    val type: WatermarkAnimationType,
    val duration: Long = 10_000L,
) {
    companion object {
        internal const val MIN_DURATION_MS = 100L
    }
}

enum class WatermarkAnimationType {
    PING_PONG,
}
