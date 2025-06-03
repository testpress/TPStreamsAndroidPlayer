package com.tpstreams.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.provider.Settings
import android.util.Log
import android.view.OrientationEventListener

/**
 * Helper class to detect orientation changes and handle auto-rotation
 */
internal class OrientationListener(val context: Context): OrientationEventListener(context) {
    private var isLandscape = false
    private val isAutoRotationIsON: Boolean
        get() = Settings.System.getInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION, 0
        ) == 1
    private var listener: OnOrientationChangeListener? = null

    override fun onOrientationChanged(orientation: Int) {
        // Log orientation changes for debugging
        Log.d("OrientationListener", "onOrientationChanged: $orientation, autoRotation: ${isAutoRotationIsON}")
        
        // If auto-rotation is off, we shouldn't change orientation
        if(!isAutoRotationIsON) {
            return
        }

        // Handle ORIENTATION_UNKNOWN (-1) case
        if (orientation == ORIENTATION_UNKNOWN) {
            return
        }

        val newIsLandscape = isOrientationLandscape(orientation)
        if (isLandscape != newIsLandscape) {
            Log.d("OrientationListener", "Orientation changed from ${if(isLandscape) "landscape" else "portrait"} to ${if(newIsLandscape) "landscape" else "portrait"}")
            isLandscape = newIsLandscape
            listener?.onChange(isLandscape)
        }
    }

    private fun isOrientationLandscape(orientation: Int): Boolean {
        // Consider landscape if orientation is between 60-120 or 240-300 degrees
        return (orientation in 60..120 || orientation in 240..300)
    }

    fun setOnChangeListener(listener: OnOrientationChangeListener) {
        this.listener = listener
    }

    fun start() {
        if (canDetectOrientation()) {
            Log.d("OrientationListener", "Starting orientation detection")
            enable()
        } else {
            Log.e("OrientationListener", "Cannot detect orientation")
        }
    }

    fun stop() {
        Log.d("OrientationListener", "Stopping orientation detection")
        disable()
    }
}

/**
 * Interface for orientation change callbacks
 */
internal fun interface OnOrientationChangeListener {
    fun onChange(isLandscape: Boolean)
} 