package com.tpstreams.player.util

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * Singleton to manage global playback history for production debugging.
 * Stores a chronological list of video events to help trace decoder/memory issues across multiple sessions.
 */
internal object PlaybackHistoryManager {
    private const val MAX_LOG_LINES = 500
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

    /**
     * Clears all history. Usually done at app level if required.
     */
    fun clearHistory() {
        logHistory.clear()
        logCount.set(0)
    }
}
