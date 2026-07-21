package com.tpstreams.player

import android.graphics.Color

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
