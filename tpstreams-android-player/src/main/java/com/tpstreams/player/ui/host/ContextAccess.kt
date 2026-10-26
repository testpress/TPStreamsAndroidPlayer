package com.tpstreams.player.ui.host

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.TPStreamsPlayerView

@UnstableApi
internal class ContextAccess(private val view: TPStreamsPlayerView) {
    fun getActivity(): FragmentActivity? {
        var ctx: Context = view.context
        while (ctx is Context) {
            if (ctx is FragmentActivity) {
                return ctx
            }
            if (ctx is android.content.ContextWrapper) {
                ctx = ctx.baseContext
            } else {
                break
            }
        }
        return null
    }

    fun getLifecycleOwner(): LifecycleOwner? = getActivity() as? LifecycleOwner
}
