package com.tpstreams.player.util

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/** Stores a bounded history for one player instance. */
internal class PlaybackHistoryManager {
    private val logHistory = ConcurrentLinkedDeque<String>()
    private val logCount = AtomicInteger(0)

    /**
     * Records a log message in the global history.
     */
    fun recordLog(message: String) {
        logHistory.addLast(message)
        logCount.incrementAndGet()

        while (logCount.get() > MAX_LOG_LINES) {
            if (logHistory.pollFirst() != null) {
                logCount.decrementAndGet()
            } else {
                break
            }
        }
    }

    /**
     * Returns the entire history as a formatted string block.
     * Use this when attaching to Sentry or other error reporting.
     */
    fun getFullHistory(): String {
        return logHistory.joinToString("\n")
    }

    /**
     * Returns the history as a list of strings if needed for tabular context.
     */
    fun getHistoryList(): List<String> {
        return logHistory.toList()
    }

    private companion object {
        const val MAX_LOG_LINES = 500
    }
}
