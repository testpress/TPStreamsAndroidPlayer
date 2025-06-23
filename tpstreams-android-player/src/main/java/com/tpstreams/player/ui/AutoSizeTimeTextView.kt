package com.tpstreams.player.ui

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView

class AutoSizeTimeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val minTextSize = 10f
    private val maxTextSize = 24f
    private var initialTextSize = textSize
    
    private val sampleTimePatterns = arrayOf(
        "0:00", "00:00", "0:00:00", "00:00:00", "99:59:59"
    )
    
    init {
        maxLines = 1
        ellipsize = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        
        if (mode == MeasureSpec.EXACTLY) {
            val exactWidth = MeasureSpec.getSize(widthMeasureSpec)
            val textWidth = paint.measureText(text.toString())
            
            if (textWidth > exactWidth) {
                val scaleFactor = exactWidth / textWidth
                val newTextSize = Math.max(minTextSize, Math.min(textSize * scaleFactor, maxTextSize))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, newTextSize)
            } else {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, initialTextSize)
            }
            
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        
        if (mode == MeasureSpec.AT_MOST || mode == MeasureSpec.UNSPECIFIED) {
            var maxWidth = paint.measureText(text?.toString() ?: "")
            
            if (text?.toString()?.matches(Regex(".*[0-9]:[0-9].*")) == true) {
                for (pattern in sampleTimePatterns) {
                    val patternWidth = paint.measureText(pattern)
                    maxWidth = Math.max(maxWidth, patternWidth)
                }
            }
            
            val desiredWidth = maxWidth.toInt() + paddingLeft + paddingRight
            
            val newWidthMeasureSpec = MeasureSpec.makeMeasureSpec(desiredWidth, MeasureSpec.EXACTLY)
            
            super.onMeasure(newWidthMeasureSpec, heightMeasureSpec)
            return
        }
        
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(text, type)
        requestLayout()
    }
} 