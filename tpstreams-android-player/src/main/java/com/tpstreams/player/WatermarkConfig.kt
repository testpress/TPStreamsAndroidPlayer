package com.tpstreams.player

import android.graphics.Color
import android.graphics.Typeface

// ── Content ──────────────────────────────────────────────────────────────

sealed class WatermarkContent {
    data class Text(
        val textProvider: () -> String,
        val color: Int = Color.WHITE,
        val textSizeSp: Float = 14f,
        val typeface: Typeface? = null,
        val shadowRadius: Float = 0f,
        val shadowDx: Float = 0f,
        val shadowDy: Float = 0f,
        val shadowColor: Int = Color.TRANSPARENT
    ) : WatermarkContent()
}

// ── Position ─────────────────────────────────────────────────────────────

sealed class WatermarkPosition {
    data class Static(val gravity: WatermarkGravity) : WatermarkPosition()
    data class Dynamic(val xFraction: Float, val yFraction: Float) : WatermarkPosition()
}

enum class WatermarkGravity {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT;

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

// ── Size ─────────────────────────────────────────────────────────────────

sealed class WatermarkSize {
    data object WrapContent : WatermarkSize()
    data class Fixed(val widthDp: Float, val heightDp: Float) : WatermarkSize()
    data class Scale(val percent: Float) : WatermarkSize()
}

// ── Animation ────────────────────────────────────────────────────────────

sealed class WatermarkAnimation {
    data class PingPong(
        val from: Pair<Float, Float>,
        val to: Pair<Float, Float>,
        val durationMs: Long = 5000L
    ) : WatermarkAnimation()
}

// ── Margins ──────────────────────────────────────────────────────────────

data class Margins(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
) {
    companion object {
        fun all(value: Float) = Margins(value, value, value, value)
        fun symmetric(horizontal: Float = 0f, vertical: Float = 0f) =
            Margins(horizontal, vertical, horizontal, vertical)
    }
}

// ── Style ────────────────────────────────────────────────────────────────

data class WatermarkStyle(
    val position: WatermarkPosition = WatermarkPosition.Static(WatermarkGravity.TOP_RIGHT),
    val margins: Margins = Margins.all(16f),
    val opacity: Float = 1f,
    val size: WatermarkSize = WatermarkSize.WrapContent,
    val visibleDuringAds: Boolean = true,
    val visibleWhenPaused: Boolean = true,
    val elevation: Float = 0f,
    val animation: WatermarkAnimation? = null
)

// ── Config ───────────────────────────────────────────────────────────────

class WatermarkConfig private constructor(
    val content: WatermarkContent,
    val style: WatermarkStyle = WatermarkStyle()
) {

    class Builder {
        private var content: WatermarkContent? = null
        private var position: WatermarkPosition = WatermarkPosition.Static(WatermarkGravity.TOP_RIGHT)
        private var margins: Margins = Margins.all(16f)
        private var opacity: Float = 1f
        private var size: WatermarkSize = WatermarkSize.WrapContent
        private var visibleDuringAds: Boolean = true
        private var visibleWhenPaused: Boolean = true
        private var elevation: Float = 0f
        private var animation: WatermarkAnimation? = null

        // ── Content ──────────────────────────────────────────────────────

        fun text(text: String): Builder {
            content = WatermarkContent.Text(textProvider = { text })
            return this
        }

        fun text(provider: () -> String): Builder {
            content = WatermarkContent.Text(textProvider = provider)
            return this
        }

        fun text(
            provider: () -> String,
            color: Int,
            sizeSp: Float,
            typeface: Typeface? = null,
            shadowRadius: Float = 0f,
            shadowDx: Float = 0f,
            shadowDy: Float = 0f,
            shadowColor: Int = Color.TRANSPARENT
        ): Builder {
            content = WatermarkContent.Text(
                textProvider = provider,
                color = color,
                textSizeSp = sizeSp,
                typeface = typeface,
                shadowRadius = shadowRadius,
                shadowDx = shadowDx,
                shadowDy = shadowDy,
                shadowColor = shadowColor
            )
            return this
        }

        fun textColor(color: Int): Builder {
            content = ensureTextContent().copy(color = color)
            return this
        }

        fun textSize(sizeSp: Float): Builder {
            content = ensureTextContent().copy(textSizeSp = sizeSp)
            return this
        }

        fun textTypeface(typeface: Typeface): Builder {
            content = ensureTextContent().copy(typeface = typeface)
            return this
        }

        fun textShadow(
            radius: Float,
            dx: Float = 0f,
            dy: Float = 0f,
            color: Int = Color.BLACK
        ): Builder {
            content = ensureTextContent().copy(
                shadowRadius = radius, shadowDx = dx, shadowDy = dy, shadowColor = color
            )
            return this
        }

        private fun ensureTextContent(): WatermarkContent.Text {
            val current = content
            if (current !is WatermarkContent.Text) {
                throw IllegalStateException("Call text() before textColor()/textSize()/textTypeface()/textShadow().")
            }
            return current
        }

        // ── Position ─────────────────────────────────────────────────────

        fun position(gravity: WatermarkGravity): Builder {
            position = WatermarkPosition.Static(gravity)
            return this
        }

        fun position(xFraction: Float, yFraction: Float): Builder {
            position = WatermarkPosition.Dynamic(
                xFraction = xFraction.coerceIn(0f, 1f),
                yFraction = yFraction.coerceIn(0f, 1f)
            )
            return this
        }

        // ── Style ────────────────────────────────────────────────────────

        fun margins(margins: Margins): Builder {
            this.margins = margins
            return this
        }

        fun margins(all: Float): Builder {
            this.margins = Margins.all(all)
            return this
        }

        fun margins(left: Float, top: Float, right: Float, bottom: Float): Builder {
            this.margins = Margins(left, top, right, bottom)
            return this
        }

        fun opacity(opacity: Float): Builder {
            this.opacity = opacity.coerceIn(0f, 1f)
            return this
        }

        fun size(size: WatermarkSize): Builder {
            this.size = size
            return this
        }

        fun visibleDuringAds(visible: Boolean): Builder {
            this.visibleDuringAds = visible
            return this
        }

        fun visibleWhenPaused(visible: Boolean): Builder {
            this.visibleWhenPaused = visible
            return this
        }

        fun elevation(elevationDp: Float): Builder {
            this.elevation = elevationDp
            return this
        }

        // ── Animation ────────────────────────────────────────────────────

        fun pingPong(
            fromGravity: WatermarkGravity,
            toGravity: WatermarkGravity,
            durationMs: Long = 5000L
        ): Builder {
            this.animation = WatermarkAnimation.PingPong(
                from = fromGravity.toFraction(),
                to = toGravity.toFraction(),
                durationMs = durationMs
            )
            return this
        }

        fun pingPong(
            fromX: Float, fromY: Float,
            toX: Float, toY: Float,
            durationMs: Long = 5000L
        ): Builder {
            this.animation = WatermarkAnimation.PingPong(
                from = fromX.coerceIn(0f, 1f) to fromY.coerceIn(0f, 1f),
                to = toX.coerceIn(0f, 1f) to toY.coerceIn(0f, 1f),
                durationMs = durationMs
            )
            return this
        }

        // ── Build ─────────────────────────────────────────────────────────

        fun build(): WatermarkConfig {
            val c = content ?: throw IllegalStateException("Watermark content not set. Call text().")
            val style = WatermarkStyle(
                position = position,
                margins = margins,
                opacity = opacity,
                size = size,
                visibleDuringAds = visibleDuringAds,
                visibleWhenPaused = visibleWhenPaused,
                elevation = elevation,
                animation = animation
            )
            return WatermarkConfig(content = c, style = style)
        }
    }
}
