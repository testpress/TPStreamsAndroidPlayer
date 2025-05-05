package com.tpstreams.player

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

@UnstableApi
class TPStreamsPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr) {

//    init {
//        post {
//            Log.d("TPStreamsPlayerView", "Post Init")
//            val controlBar = findViewById<LinearLayout>(androidx.media3.ui.R.id.exo_basic_controls)
//            if (controlBar != null) {
//                val downloadButton = ImageButton(context).apply {
//                    id = generateViewId()
//                    setImageResource(R.drawable.ic_download)
//                    background = null
//                    contentDescription = "Download"
//                    layoutParams = ViewGroup.MarginLayoutParams(
//                        ViewGroup.LayoutParams.WRAP_CONTENT,
//                        ViewGroup.LayoutParams.WRAP_CONTENT
//                    ).apply { marginStart = 16 }
//
//                    setOnClickListener {
//                        val tpPlayer = player as? TPStreamsPlayer
//                        tpPlayer?.let {
//                            Log.d("TPStreamsPlayerView", "Download clicked")
//                            // TODO: trigger actual download
//                        }
//                    }
//                }
//                controlBar.addView(downloadButton)
//            } else {
//                Log.w("TPStreamsPlayerView", "Control bar not found")
//            }
//        }
//    }
}
