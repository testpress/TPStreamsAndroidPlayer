package com.tpstreams.player

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.util.UnstableApi

@UnstableApi
class ContextAccess(private val view: TPStreamsPlayerView) {
    fun getActivity(): FragmentActivity? {
        var ctx = view.context
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

    fun getLifecycleOwner(): LifecycleOwner? {
        val activity = getActivity()
        return when {
            activity is LifecycleOwner -> activity
            else -> null
        }
    }
} 