package com.tpstreams.player.presence

import android.os.Handler
import android.os.Looper

// Abstracts the heartbeat loop's delayed scheduling away from a real Android
// Handler/Looper so PresenceHeartbeatManager can be unit tested with a fake
// that fires immediately, without depending on Robolectric.
internal interface PresenceScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): Cancellable

    interface Cancellable {
        fun cancel()
    }
}

internal class HandlerPresenceScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : PresenceScheduler {
    override fun schedule(delayMillis: Long, action: () -> Unit): PresenceScheduler.Cancellable {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMillis)
        return object : PresenceScheduler.Cancellable {
            override fun cancel() {
                handler.removeCallbacks(runnable)
            }
        }
    }
}
