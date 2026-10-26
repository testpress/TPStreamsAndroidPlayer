package com.tpstreams.player.ui.controller

import android.util.Log
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import java.util.WeakHashMap

/** Owns the Activity-scoped reference count for FLAG_SECURE. */
internal class SecureFlagManager {
    private var activity: FragmentActivity? = null

    fun apply(target: FragmentActivity) {
        if (activity != null) return
        activity = target
        acquire(target)
    }

    fun remove() {
        val target = activity ?: return
        activity = null
        release(target)
    }

    private companion object {
        private const val TAG = "SecureFlagManager"
        private val activeViewCount = WeakHashMap<FragmentActivity, Int>()

        fun acquire(activity: FragmentActivity) {
            val count = (activeViewCount[activity] ?: 0) + 1
            activeViewCount[activity] = count
            if (count == 1) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                Log.d(TAG, "FLAG_SECURE set (active player views: 1)")
            } else {
                Log.d(TAG, "FLAG_SECURE already set (active player views: $count)")
            }
        }

        fun release(activity: FragmentActivity) {
            val count = ((activeViewCount[activity] ?: 0) - 1).coerceAtLeast(0)
            if (count == 0) {
                activeViewCount.remove(activity)
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                Log.d(TAG, "FLAG_SECURE cleared (no active player views)")
            } else {
                activeViewCount[activity] = count
                Log.d(TAG, "FLAG_SECURE kept (active player views: $count)")
            }
        }
    }
}
