package com.tpstreams.player.ui.controller

import android.view.MotionEvent
import android.view.View
import androidx.media3.common.util.UnstableApi

/** Coordinates zoom and double-tap gesture ownership before child controls receive touches. */
@UnstableApi
internal class TouchGestureCoordinator(
    private val host: View,
    private val zoomController: PlayerZoomController,
    private val doubleTapSeekController: DoubleTapSeekController
) {
    private var zoomInterceptionActive = false

    fun dispatchTouchEvent(ev: MotionEvent, dispatchCancel: (MotionEvent) -> Unit): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            zoomInterceptionActive = false
        }

        if (zoomController.isEnabled) {
            val handledByZoom = zoomController.handleTouchEvent(ev)
            val isMultiTouchZoom = zoomController.isZoomed && ev.pointerCount > 1

            if (!zoomInterceptionActive && (zoomController.isInteracting || isMultiTouchZoom || handledByZoom)) {
                val cancelEvent = MotionEvent.obtain(
                    ev.downTime,
                    ev.eventTime,
                    MotionEvent.ACTION_CANCEL,
                    ev.x,
                    ev.y,
                    0
                )
                dispatchCancel(cancelEvent)
                cancelEvent.recycle()
                zoomInterceptionActive = true
            }

            if (zoomInterceptionActive || handledByZoom) {
                host.parent?.requestDisallowInterceptTouchEvent(true)
                if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                    zoomInterceptionActive = false
                }
                return true
            }
        }

        return doubleTapSeekController.isEnabled &&
            doubleTapSeekController.isSeeking &&
            doubleTapSeekController.handleTouchEvent(ev)
    }

    fun onTouchEvent(ev: MotionEvent): Boolean {
        return doubleTapSeekController.isEnabled && doubleTapSeekController.handleTouchEvent(ev)
    }
}
