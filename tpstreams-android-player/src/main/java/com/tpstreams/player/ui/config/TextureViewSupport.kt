package com.tpstreams.player.ui.config

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.util.Xml
import com.tpstreams.player.R
import org.xmlpull.v1.XmlPullParser

/** TextureView-specific construction support used by Flutter platform-view integration. */
internal object TextureViewSupport {
    private const val TAG = "TextureViewSupport"

    fun attributeSet(context: Context): AttributeSet? {
        return try {
            val parser = context.resources.getLayout(R.layout.tpstreams_player_view_attrs)
            var state = 0
            while (state != XmlPullParser.START_TAG && state != XmlPullParser.END_DOCUMENT) {
                state = parser.next()
            }
            Xml.asAttributeSet(parser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse TextureView AttributeSet; falling back to SurfaceView", e)
            null
        }
    }
}
